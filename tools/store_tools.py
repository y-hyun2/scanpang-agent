"""
store_tools.py
개별 매장 상세 정보 조회 — store_details 테이블 lazy 캐시.

흐름:
1) cache hit → 그대로 반환
2) miss → ① place_info에서 건물 좌표 조회
         → ② Kakao Local로 기본 메타(category_name, phone, addr) 1차 획득
         → ③ category_classifier로 category_key 분류
         → ④ details_fetchers.fetch_by_category로 카테고리별 풍부도 수집
         → ⑤ 결과를 store_details에 INSERT (UPSERT)
"""

from datetime import datetime, timezone
import json

from core.db import get_pool
from tools.place_tools import check_kakao_open_status
from tools.category_classifier import classify_category
from tools.details_fetchers import fetch_by_category
from tools.open_hours_normalizer import normalize_open_hours
from tools.translation import translate_fields, apply_lang


# 명동 기본 좌표 fallback
_DEFAULT_LAT = 37.5636
_DEFAULT_LNG = 126.9822

# 공식 홈페이지로 인정 안 하는 소셜·블로그·플랫폼 자체 페이지 도메인.
# place.map.kakao.com / map.naver.com 같은 지도 플랫폼 매장 상세는 그 자체가
# 공식 홈페이지가 아니라 매장 정보 페이지이므로 homepage 로 잡혀선 안 됨.
_SOCIAL_URL_PATTERNS = (
    "instagram.com", "facebook.com", "twitter.com", "x.com",
    "youtube.com", "youtu.be",
    "blog.naver.com", "cafe.naver.com", "post.naver.com",
    "tiktok.com", "pf.kakao.com", "kakao.com/o/", "linktr.ee",
    "place.map.kakao.com", "map.naver.com", "m.place.naver.com",
)


def _is_social_url(url: str) -> bool:
    if not url:
        return False
    lower = url.lower()
    return any(p in lower for p in _SOCIAL_URL_PATTERNS)


# 단어 끝이 이런 패턴이면 그 자체로 지점·위치 식별이 가능하다고 본다.
# 점/지점/본점/출장소 같은 명시적 지점 표시 + '~부/소/원/국' 같이 한국어에서
# 자체적으로 분점·조직단위를 가리키는 단어 + '~동/구/역/길/로' 같이 행정/도로
# 정보가 매장명에 들어간 경우 모두 포함.
_LOCATION_ENDINGS = (
    "점", "관", "스토어", "샵", "타운",
    "부", "소", "원", "국",
    "동", "구", "역", "길", "로",
)


def _has_location_marker(store_name: str) -> bool:
    """매장명 토큰 중 하나라도 위치/지점 식별 단어로 끝나는지."""
    for w in store_name.split():
        if len(w) >= 2 and any(w.endswith(suf) for suf in _LOCATION_ENDINGS):
            return True
    return False


def _enrich_store_name(store_name: str, addr: str) -> str:
    """
    'KB국민은행 ATM' 처럼 지점명 없는 generic 매장명에 addr 의 도로명/동/구를
    suffix 로 붙여 표시용 이름을 만든다. 시드 시점 1회 변환 — cache_id 는 원본
    store_name 으로 유지하고 DB store_name 컬럼만 갱신해 lookup 일관성은 보존.

    Rules:
    - 매장명 토큰 중 하나라도 _LOCATION_ENDINGS (점/지점/본점/출장소/부/소/원/
      국/동/구/역/길/로/관/스토어/샵 등) 로 끝나면 위치 식별 가능 → 그대로
    - 그 외엔 addr 에서 '~길'/'~로' → '~동' → '~구' 순으로 가장 정밀한 suffix 채택
    """
    if not store_name:
        return store_name
    if _has_location_marker(store_name):
        return store_name
    if not addr:
        return store_name
    for ending in (("길", "로"), ("동",), ("구",)):
        for token in addr.split():
            if any(token.endswith(suf) for suf in ending) and len(token) >= 2:
                return f"{store_name} {token}"
    return store_name


async def get_store_detail(
    place_id: str,
    store_name: str,
    lat: float | None = None,
    lng: float | None = None,
    language: str = "ko",
    progress_cb=None,
) -> dict:
    """
    Args:
        place_id:   건물 ufid (place_info FK). outdoor 진입 시나리오에서는
                    `__outdoor__` 같은 sentinel 또는 임의 키 허용.
        store_name: 매장명
        lat, lng:   (선택) 진입점이 GPS 또는 AR 마커 좌표를 들고 들어올 때 override.
                    None이면 place_info에서 건물 좌표 조회 (기존 시나리오 a).
                    AR 스캔/카테고리 필터 마커 탭(시나리오 b/c)는 마커 좌표 전달.

    Returns:
        store_details row dict. 모든 fetcher 실패 시 최소 정보만.
    """
    async def _progress(pct: int, msg: str):
        if progress_cb:
            await progress_cb(pct, msg)

    cache_id = f"{place_id}__{store_name}"
    pool     = await get_pool()

    # outdoor 시설(건물 내에 X) — store_details 우회. raw 테이블이 단일 출처.
    # · 화장실 = public_restrooms
    # · 지하철역 = subway_exits + TAGO
    # · 물품보관함 = subwayLockerInfo
    # · 기도실 = static_json
    # 건물 내 매장(카페/식당/쇼핑/은행 등)만 store_details lazy 캐시 적재.
    _OUTDOOR = {"restroom", "subway", "subway_station", "locker", "prayer_room"}
    pre_category = classify_category(category_name="", store_name=store_name)
    is_outdoor = pre_category in _OUTDOOR

    await _progress(10, "캐시 조회 중...")
    # ── ① 캐시 조회 — outdoor 가 아닐 때만 ──────────────────────────────────
    # floor_info_seed row 의 addr 는 building 주소 — 나중 fetcher 결과가 이와
    # 시·구 단위로 다르면(예: '경기 용인시 처인구' vs '경기 고양시 덕양구')
    # 다른 지역 동명 매장이 잘못 매칭된 케이스라 거절해야 함. 그 검증용 변수.
    expected_addr_seed = ""
    if not is_outdoor:
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT * FROM store_details WHERE id = $1", cache_id
            )
        # floor_info_seed 는 검색 카드용 lightweight row — open_hours/details/
        # image_urls 비어있음. 사용자가 카드 탭한 지금이 풀필드 fetch 타이밍이라
        # cache miss 로 취급해 아래 fetcher 디스패치 단계로 흘려보낸다.
        # 빈 source row(=과거 fetcher 가 결과 못 가져와 캐싱된 더미)도 같은 이유로 재시도.
        is_empty_cache = bool(row) and not (row["source"] or "").strip()
        if is_empty_cache:
            print(f"[store_tools] 빈 cache row 감지 — fetcher 재시도: {cache_id!r}")
        if row and row["source"] != "floor_info_seed" and not is_empty_cache:
            result = _row_to_dict(row)
            # 캐시 히트 + 영어 요청: translations["en"] 없으면 번역 후 DB 업데이트
            if language != "ko":
                trans = result.get("translations") or {}
                if not trans.get(language):
                    _lat = float(result["lat"]) if result.get("lat") else _DEFAULT_LAT
                    _lng = float(result["lng"]) if result.get("lng") else _DEFAULT_LNG
                    en_fields = await translate_fields(
                        {
                            "name":        result.get("store_name", ""),
                            "addr":        result.get("addr", ""),
                            "open_hours":  result.get("open_hours", ""),
                            "closed_days": result.get("closed_days", ""),
                        },
                        lat=_lat, lng=_lng, lang=language,
                    )
                    if en_fields:
                        trans[language] = en_fields
                        async with pool.acquire() as conn:
                            await conn.execute(
                                "UPDATE store_details SET translations=$1::jsonb WHERE id=$2",
                                json.dumps(trans), cache_id,
                            )
                        result["translations"] = trans
            # 번역값을 응답 필드에 오버레이
            _apply_translations_to_result(result, language)
            return result
        if row and row["source"] == "floor_info_seed":
            expected_addr_seed = row["addr"] or ""

    await _progress(30, "좌표 확인 중...")
    # ── ② 좌표 결정 ─────────────────────────────────────────────────────────
    # lat/lng가 인자로 들어오면 그대로 사용 (마커/GPS 진입).
    # 아니면 place_info에서 건물 좌표 조회 (시나리오 a).
    if lat is None or lng is None:
        async with pool.acquire() as conn:
            # 1차: place_info — 매장명/floor_info까지 등록된 정식 라우팅용
            coord = await conn.fetchrow(
                "SELECT lat, lng FROM place_info WHERE ufid = $1", place_id
            )
            # 2차: buildings.center_lat/lng — place_info에 row가 없어도 건물 폴리곤
            # 중심 좌표는 buildings에 들어있음. 명동 내 매장처럼 floor_info 아직
            # 안 채워진 케이스에서 명동 fallback 좌표보다 정확.
            if not coord or coord["lat"] is None:
                coord = await conn.fetchrow(
                    "SELECT center_lat AS lat, center_lng AS lng "
                    "FROM buildings WHERE ufid = $1",
                    place_id,
                )
        lat = float(coord["lat"]) if coord and coord["lat"] is not None else _DEFAULT_LAT
        lng = float(coord["lng"]) if coord and coord["lng"] is not None else _DEFAULT_LNG
    else:
        lat, lng = float(lat), float(lng)

    await _progress(50, "Kakao 매장 정보 조회 중...")
    # ── ③ Kakao Local 1차 — category_name 확보용 (분류 입력) ─────────────────
    kakao = await check_kakao_open_status(store_name, lat, lng) or {}
    category_name = kakao.get("category", "") or ""
    # 분류는 풀스트링("음식점 > 간식 > 도넛")으로 — short(level-2)만 보면
    # "간식"/"패스트푸드" 같이 룰에 없는 단어로 떨어져 "other"가 됨.
    category_full = kakao.get("category_full", "") or category_name
    category_key  = classify_category(category_full, store_name)
    print(f"[store_tools] {store_name!r} → category_name={category_name!r}, category_key={category_key!r}")

    await _progress(70, "상세 데이터 수집 중...")
    # ── ④ 카테고리별 fetcher 디스패치 ───────────────────────────────────────
    fetched = await fetch_by_category(
        category_key   = category_key,
        store_name     = store_name,
        lat            = lat,
        lng            = lng,
        building_ufid  = place_id,
        category_name  = category_name,
    )

    # 시·구 검증 — floor_info_seed row 의 building addr 와 fetched addr 가 시·구
    # 단위로 다르면 다른 지역 동명 매장이 잘못 잡힌 케이스('수다방' 외대 →
    # naver_place 가 고양시 수다방으로 fallback) 라 fetched 거절. floor_info_seed
    # row 는 그대로 유지.
    def _district(a: str) -> str:
        if not a: return ""
        parts = a.split()
        return " ".join(parts[1:3]) if len(parts) >= 3 else ""
    if expected_addr_seed and fetched.get("addr"):
        exp_d = _district(expected_addr_seed)
        got_d = _district(fetched.get("addr", ""))
        if exp_d and got_d and exp_d != got_d:
            print(f"[store_tools] 시·구 불일치 거절: expected={exp_d!r} got={got_d!r} — floor_info_seed 유지")
            return _row_to_dict(row)

    # fetcher 결과가 비어있으면 Kakao 1차 메타로 fallback
    phone      = fetched.get("phone")      or kakao.get("phone", "")
    addr       = fetched.get("addr")       or kakao.get("addr", "")
    # Kakao 1차에서 매장이 안 잡혀 category_name 이 빈 경우, fetcher(naver_place)
    # 결과의 category 로 보강. 안 보강하면 store_details.category 가 영구히 빈
    # 채로 캐싱돼서 AR overlay 칩이 사라짐.
    if not category_name:
        category_name = fetched.get("category", "") or ""
    # homepage 소셜 URL 필터 — 인스타·블로그는 제외.
    # fetcher 결과가 없거나 소셜이면 빈 값으로 둔다 (Kakao place_url 은 매장 공식
    # 홈페이지가 아니라 Kakao 자체 매장 페이지라 의미 없음 → fallback 제거).
    raw_homepage = fetched.get("homepage", "") or ""
    if _is_social_url(raw_homepage):
        print(f"[store_tools] 소셜/블로그 URL 제외: {raw_homepage}")
        raw_homepage = ""
    homepage   = raw_homepage
    open_hours = fetched.get("open_hours", "")
    closed_days = fetched.get("closed_days", "")
    image_urls = fetched.get("image_urls", []) or []
    floor      = fetched.get("floor", "") or None  # Naver Place base.road에서 추출
    details    = fetched.get("details", {}) or {}
    source     = fetched.get("source", "kakao" if kakao else "")

    # ── ④-b open_hours 구조화 — LLM 1회로 weekly schedule 정규화 ────────────
    # details.schedule 에 저장해 두면 /place/search·/place/detail 응답에서
    # LLM 호출 없이 is_open_now 를 정확 판정할 수 있다.
    # 단, subway 카테고리는 seoul_metro fetcher 가 details.schedule 을 자체 포맷
    # ({weekday_up, weekday_down}) 으로 채우므로 덮어쓰지 않는다.
    if open_hours and category_key not in ("subway", "subway_station"):
        schedule = await normalize_open_hours(open_hours)
        if schedule:
            details["schedule"] = schedule

    # 표시용 store_name 정규화 — 2 단계.
    # ① 사용자 입력 매장명이 Kakao 실 매장명과 너무 다르면 (partial_ratio < 70)
    #    Kakao 정답을 채택. 예: 사용자가 '을지병원 명동' 으로 등록했으나 실제 그
    #    좌표에는 '을지연세치과의원' 만 존재하는 케이스 — 잘못된 입력 교정.
    # ② 그 결과에 _enrich_store_name 으로 도로명/동/구 suffix 부착해 generic
    #    매장명 식별 보강.
    # cache_id 는 원본(파라미터) store_name 으로 유지해 캐시 lookup 일관성 보존.
    matched_name = (details.get("matched_name") if isinstance(details, dict) else "") or ""
    base_name = store_name
    if matched_name and matched_name != store_name:
        try:
            from rapidfuzz import fuzz as _fuzz
            score = _fuzz.partial_ratio(
                store_name.replace(" ", ""), matched_name.replace(" ", "")
            )
            if score < 70:
                print(f"[store_tools] 매장명 교정: {store_name!r} → {matched_name!r} (fuzz={score})")
                base_name = matched_name
        except ImportError:
            pass
    display_name = base_name

    # outdoor: store_details INSERT 우회.
    if is_outdoor:
        result = {
            "id":           cache_id,
            "place_id":     place_id,
            "store_name":   display_name,
            "category":     category_name,
            "category_key": category_key,
            "addr":         addr,
            "phone":        phone,
            "lat":          lat,
            "lng":          lng,
            "place_url":    kakao.get("place_url", ""),
            "details":      details,
            "open_hours":   open_hours,
            "closed_days":  closed_days,
            "homepage":     homepage,
            "image_urls":   image_urls,
            "floor":        floor,
            "source":       source,
            "translations": {},
        }
        if language != "ko":
            en_fields = await translate_fields(
                {"name": display_name, "addr": addr,
                 "open_hours": open_hours, "closed_days": closed_days},
                lat=lat, lng=lng, lang=language,
            )
            result["translations"] = {language: en_fields} if en_fields else {}
            _apply_translations_to_result(result, language)
        return result

    # ── ⑤ 영문 번역 (UPSERT 전 획득해 함께 저장) ────────────────────────────
    translations: dict = {}
    if language != "ko":
        en_fields = await translate_fields(
            {"name": display_name, "addr": addr,
             "open_hours": open_hours, "closed_days": closed_days},
            lat=lat, lng=lng, lang=language,
        )
        if en_fields:
            translations[language] = en_fields

    await _progress(88, "저장 중...")
    # ── ⑥ store_details UPSERT — 건물 내 매장만 ─────────────────────────────
    # 가드: fetcher 가 의미있는 결과(source + 최소 1개 필드) 가져왔을 때만 캐싱.
    # 그렇지 않으면 빈 row 가 store_details 에 영구 박혀서 이후 호출이 캐시 히트로
    # 빈 결과를 받아 playwright lazy load 가 영원히 안 도는 버그가 발생.
    # 관광·숙박 카테고리는 tourist_places, accommodation_places 별도 테이블로 관리.
    _SKIP_CATEGORIES = {"관광,명소", "관광지", "문화시설", "숙박", "호텔", "국가유산"}
    has_meaningful_data = bool(source) and bool(open_hours or addr or phone or image_urls or details)
    if category_name in _SKIP_CATEGORIES:
        print(f"[store_tools] {category_name!r} — store_details UPSERT 스킵 (별도 테이블 관리): {cache_id!r}")
    elif not has_meaningful_data:
        print(f"[store_tools] fetcher 결과 빈약 — UPSERT 스킵: {cache_id!r} (source={source!r})")
    else:
        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO store_details
                    (id, place_id, store_name, category, category_key,
                     addr, phone, lat, lng, place_url,
                     details, open_hours, closed_days, homepage, image_urls,
                     floor, source, last_updated, translations)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,
                        $11::jsonb,$12,$13,$14,$15::jsonb,$16,$17,$18,$19::jsonb)
                ON CONFLICT (id) DO UPDATE SET
                    category      = EXCLUDED.category,
                    category_key  = EXCLUDED.category_key,
                    addr          = EXCLUDED.addr,
                    phone         = EXCLUDED.phone,
                    place_url     = EXCLUDED.place_url,
                    details       = EXCLUDED.details,
                    open_hours    = EXCLUDED.open_hours,
                    closed_days   = EXCLUDED.closed_days,
                    homepage      = EXCLUDED.homepage,
                    image_urls    = EXCLUDED.image_urls,
                    floor         = EXCLUDED.floor,
                    source        = EXCLUDED.source,
                    last_updated  = EXCLUDED.last_updated,
                    translations  = store_details.translations || EXCLUDED.translations
                """,
                cache_id, place_id, display_name,
                category_name, category_key,
                addr, phone, lat, lng, kakao.get("place_url", ""),
                details,
                open_hours, closed_days, homepage,
                image_urls,
                floor, source, datetime.now(timezone.utc),
                translations,
            )

    result = {
        "id":           cache_id,
        "place_id":     place_id,
        "store_name":   display_name,
        "category":     category_name,
        "category_key": category_key,
        "addr":         addr,
        "phone":        phone,
        "lat":          lat,
        "lng":          lng,
        "place_url":    kakao.get("place_url", ""),
        "details":      details,
        "open_hours":   open_hours,
        "closed_days":  closed_days,
        "homepage":     homepage,
        "image_urls":   image_urls,
        "floor":        floor,
        "source":       source,
        "translations": translations,
    }
    _apply_translations_to_result(result, language)
    return result



def _row_to_dict(row) -> dict:
    """asyncpg Record → 일반 dict. JSONB 필드는 이미 파싱돼서 옴."""
    d = dict(row)
    for k in ("details", "image_urls", "translations"):
        v = d.get(k)
        if isinstance(v, str):
            try:
                d[k] = json.loads(v)
            except Exception:
                pass
    if d.get("translations") is None:
        d["translations"] = {}
    return d


def _apply_translations_to_result(result: dict, language: str) -> None:
    """translations[language] 값을 result의 최상위 필드에 오버레이 (in-place)."""
    if language == "ko":
        return
    t = (result.get("translations") or {}).get(language, {})
    if t.get("name"):
        result["store_name"] = t["name"]
    if t.get("addr"):
        result["addr"] = t["addr"]
    if t.get("open_hours"):
        result["open_hours"] = t["open_hours"]
    if t.get("closed_days"):
        result["closed_days"] = t["closed_days"]
