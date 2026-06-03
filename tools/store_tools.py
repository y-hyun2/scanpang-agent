"""
store_tools.py
개별 매장 상세 정보 조회 — storedetails 테이블 lazy 캐시.

흐름:
1) cache hit (place_id + store_name 매칭) → 그대로 반환
2) miss → ① placeinfo에서 건물 좌표 조회
         → ② Kakao Local로 기본 메타(category_name, phone, addr) 1차 획득
         → ③ category_classifier로 category_key 분류
         → ④ details_fetchers.fetch_by_category로 카테고리별 풍부도 수집
         → ⑤ storedetails UPSERT + store_hours 저장
"""

from datetime import datetime, timezone
import json

from core.db import get_pool
from tools.place_tools import check_kakao_open_status
from tools.category_classifier import classify_category
from tools.details_fetchers import fetch_by_category
from tools.translation import translate_fields, apply_lang


# 명동 기본 좌표 fallback
_DEFAULT_LAT = 37.5636
_DEFAULT_LNG = 126.9822

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


_LOCATION_ENDINGS = (
    "점", "관", "스토어", "샵", "타운",
    "부", "소", "원", "국",
    "동", "구", "역", "길", "로",
)


def _has_location_marker(store_name: str) -> bool:
    for w in store_name.split():
        if len(w) >= 2 and any(w.endswith(suf) for suf in _LOCATION_ENDINGS):
            return True
    return False


def _enrich_store_name(store_name: str, addr: str) -> str:
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


async def _next_store_id(conn, place_id: str) -> str:
    """building_key + 4자리 시퀀스 ID 생성. {place_id}{seq:04d}"""
    count = await conn.fetchval(
        "SELECT COUNT(*) FROM storedetails WHERE place_id = $1",
        place_id,
    )
    return f"{place_id}{int(count) + 1:04d}"


async def get_store_detail(
    place_id: str,
    store_name: str,
    lat: float | None = None,
    lng: float | None = None,
    language: str = "ko",
    progress_cb=None,
    floor_category: str = "",
) -> dict:
    """
    Args:
        place_id:   building.building_key
        store_name: 매장명
        lat, lng:   (선택) 진입점 좌표. None이면 placeinfo에서 조회.

    Returns:
        storedetails row dict.
    """
    async def _progress(pct: int, msg: str):
        if progress_cb:
            await progress_cb(pct, msg)

    pool = await get_pool()

    _OUTDOOR = {"restroom", "subway", "subway_station", "locker", "prayer_room"}
    pre_category = classify_category(category_name="", store_name=store_name)
    is_outdoor = pre_category in _OUTDOOR

    await _progress(10, "캐시 조회 중...")
    # ── ① 캐시 조회 ──────────────────────────────────────────────────────────
    expected_addr_seed = ""
    row = None
    if not is_outdoor:
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT * FROM storedetails WHERE place_id = $1 AND store_name = $2 LIMIT 1",
                place_id, store_name,
            )
        is_empty_cache = bool(row) and not (row["source"] or "").strip()
        if is_empty_cache:
            print(f"[store_tools] 빈 cache row 감지 — fetcher 재시도: {place_id}/{store_name!r}")
        if row and row["source"] != "floor_info_seed" and not is_empty_cache:
            result = _row_to_dict(row)
            if language != "ko":
                trans = result.get("translations") or {}
                existing_lang = trans.get(language, {})
                _lat = float(result["lat"]) if result.get("lat") else _DEFAULT_LAT
                _lng = float(result["lng"]) if result.get("lng") else _DEFAULT_LNG
                d = result.get("details") or {}
                convs = d.get("conveniences") or [] if isinstance(d, dict) else []
                _fields = {
                    "name":         result.get("store_name", ""),
                    "addr":         result.get("addr", ""),
                    "open_hours":   result.get("raw_open_hours", ""),
                    "closed_days":  result.get("raw_closed_days", ""),
                    "conveniences": ", ".join(str(c) for c in convs if c),
                }
                _missing = {k: v for k, v in _fields.items()
                            if not existing_lang.get(k) and v and str(v).strip()}
                if _missing:
                    en_fields = await translate_fields(
                        _missing, lat=_lat, lng=_lng, lang=language,
                    )
                    if en_fields:
                        merged = {**existing_lang, **en_fields}
                        trans[language] = merged
                        async with pool.acquire() as conn:
                            await conn.execute(
                                "UPDATE storedetails SET translations=$1::jsonb WHERE id=$2",
                                json.dumps(trans), row["id"],
                            )
                        result["translations"] = trans
            _apply_translations_to_result(result, language)
            return result
        if row and row["source"] == "floor_info_seed":
            expected_addr_seed = row["addr"] or ""

        if not expected_addr_seed and place_id and not place_id.startswith("__"):
            async with pool.acquire() as conn:
                pi = await conn.fetchrow(
                    "SELECT addr FROM placeinfo WHERE building_key = $1", place_id
                )
            if pi and pi["addr"]:
                expected_addr_seed = pi["addr"]

    await _progress(30, "좌표 확인 중...")
    # ── ② 좌표 결정 ──────────────────────────────────────────────────────────
    if lat is None or lng is None:
        async with pool.acquire() as conn:
            coord = await conn.fetchrow(
                "SELECT lat, lng FROM placeinfo WHERE building_key = $1", place_id
            )
            if not coord or coord["lat"] is None:
                coord = await conn.fetchrow(
                    "SELECT center_lat AS lat, center_lng AS lng "
                    "FROM building WHERE building_key = $1",
                    place_id,
                )
        if not coord or coord["lat"] is None or coord["lng"] is None:
            # 명동 기본값 폴백 금지 — 좌표 없는 매장을 엉뚱한 위치로 검색하지 않는다.
            raise ValueError(f"좌표 없음 — placeinfo/building 미등록: {place_id!r}")
        lat = float(coord["lat"])
        lng = float(coord["lng"])
    else:
        lat, lng = float(lat), float(lng)

    await _progress(50, "Kakao 매장 정보 조회 중...")
    # ── ③ Kakao Local 1차 ────────────────────────────────────────────────────
    kakao = await check_kakao_open_status(store_name, lat, lng) or {}
    # 표시용 category: 카카오 우선, 카카오에 없으면 floor_info leaf("분식 > 종합분식" → "종합분식")
    floor_cat_disp = floor_category.split(">")[-1].strip() if floor_category else ""
    category_name = kakao.get("category", "") or floor_cat_disp or ""
    category_full = kakao.get("category_full", "") or category_name
    # 분류도 카카오 우선 → 카카오가 비거나 'other'면 floor_info 로 폴백.
    # (카카오 dapi 가 영차떡볶이처럼 인덱스에 없는 매장을 놓치는 경우만 floor_info 보강)
    category_key = classify_category(category_full, store_name)
    if category_key == "other" and floor_category:
        category_key = classify_category(floor_category, store_name)
    print(f"[store_tools] {store_name!r} → category_key={category_key!r}")

    await _progress(70, "상세 데이터 수집 중...")
    # ── ④ 카테고리별 fetcher 디스패치 ───────────────────────────────────────
    fetched = await fetch_by_category(
        category_key  = category_key,
        store_name    = store_name,
        lat           = lat,
        lng           = lng,
        building_ufid = place_id,
        category_name = category_name,
    )

    def _district(a: str) -> str:
        if not a: return ""
        parts = a.split()
        return " ".join(parts[1:3]) if len(parts) >= 3 else ""
    if expected_addr_seed and fetched.get("addr"):
        exp_d = _district(expected_addr_seed)
        got_d = _district(fetched.get("addr", ""))
        if exp_d and got_d and exp_d != got_d:
            print(f"[store_tools] 시·구 불일치 거절: expected={exp_d!r} got={got_d!r}")
            if row:
                return _row_to_dict(row)
            raise ValueError(f"시·구 불일치 — {exp_d!r} vs {got_d!r}")

    phone       = fetched.get("phone")      or kakao.get("phone", "")
    addr        = fetched.get("addr")       or kakao.get("addr", "")
    if not category_name:
        category_name = fetched.get("category", "") or floor_category or ""
    raw_homepage = fetched.get("homepage", "") or ""
    if _is_social_url(raw_homepage):
        raw_homepage = ""
    homepage    = raw_homepage
    open_hours  = fetched.get("open_hours", "")
    closed_days = fetched.get("closed_days", "")
    image_urls  = fetched.get("image_urls", []) or []
    floor       = fetched.get("floor", "") or None
    details     = fetched.get("details", {}) or {}
    source      = fetched.get("source", "kakao" if kakao else "")

    # matched_name 기반 표시명 교정
    matched_name = (details.get("matched_name") if isinstance(details, dict) else "") or ""
    base_name = store_name
    if matched_name and matched_name != store_name:
        try:
            from rapidfuzz import fuzz as _fuzz
            score = _fuzz.partial_ratio(
                store_name.replace(" ", ""), matched_name.replace(" ", "")
            )
            if score < 70:
                base_name = matched_name
        except ImportError:
            pass
    display_name = base_name

    # outdoor: storedetails INSERT 우회
    if is_outdoor:
        result = {
            "id":              f"{place_id}__outdoor__{store_name}",
            "place_id":        place_id,
            "store_name":      display_name,
            "category":        category_name,
            "category_key":    category_key,
            "addr":            addr,
            "phone":           phone,
            "lat":             lat,
            "lng":             lng,
            "place_url":       kakao.get("place_url", ""),
            "details":         details,
            "raw_open_hours":  open_hours,
            "raw_closed_days": closed_days,
            "homepage":        homepage,
            "image_urls":      image_urls,
            "floor":           floor,
            "source":          source,
            "translations":    {},
        }
        if language != "ko":
            _convs = (details or {}).get("conveniences") or []
            en_fields = await translate_fields(
                {
                    "name":         display_name,
                    "addr":         addr,
                    "open_hours":   open_hours,
                    "closed_days":  closed_days,
                    "conveniences": ", ".join(str(c) for c in _convs if c),
                },
                lat=lat, lng=lng, lang=language,
            )
            result["translations"] = {language: en_fields} if en_fields else {}
            _apply_translations_to_result(result, language)
        return result

    # ── ⑤ 번역 ───────────────────────────────────────────────────────────────
    translations: dict = {}
    if language != "ko":
        _convs = (details or {}).get("conveniences") or []
        en_fields = await translate_fields(
            {
                "name":         display_name,
                "addr":         addr,
                "open_hours":   open_hours,
                "closed_days":  closed_days,
                "conveniences": ", ".join(str(c) for c in _convs if c),
            },
            lat=lat, lng=lng, lang=language,
        )
        if en_fields:
            translations[language] = en_fields

    await _progress(88, "저장 중...")
    # ── ⑥ storedetails UPSERT ────────────────────────────────────────────────
    _SKIP_DB_KEYS = {"tourist", "cultural", "accommodation"}
    has_meaningful_data = bool(source) and bool(open_hours or addr or phone or image_urls or details)

    store_id = None
    if category_key in _SKIP_DB_KEYS:
        print(f"[store_tools] {category_key!r} — storedetails UPSERT 스킵: {place_id}/{store_name!r}")
    elif not place_id:
        print(f"[store_tools] place_id 빈 값 — UPSERT 스킵: {store_name!r}")
    elif not has_meaningful_data:
        print(f"[store_tools] fetcher 결과 빈약 — UPSERT 스킵 (source={source!r}): {store_name!r}")
    else:
        async with pool.acquire() as conn:
            # 기존 row 있으면 id 재사용, 없으면 새 시퀀스 id 생성
            existing = await conn.fetchrow(
                "SELECT id FROM storedetails "
                "WHERE place_id = $1 AND store_name = $2 AND COALESCE(floor, '') = COALESCE($3, '')",
                place_id, display_name, floor,
            )
            store_id = existing["id"] if existing else await _next_store_id(conn, place_id)

            await conn.execute(
                """
                INSERT INTO storedetails
                    (id, place_id, store_name, category, category_key,
                     addr, phone, lat, lng, place_url,
                     details, raw_open_hours, raw_closed_days, homepage, image_urls,
                     floor, source, last_updated, translations)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,
                        $11::jsonb,$12,$13,$14,$15::jsonb,$16,$17,$18,$19::jsonb)
                ON CONFLICT (place_id, store_name, COALESCE(floor, '')) DO UPDATE SET
                    category        = EXCLUDED.category,
                    category_key    = EXCLUDED.category_key,
                    addr            = EXCLUDED.addr,
                    phone           = EXCLUDED.phone,
                    place_url       = EXCLUDED.place_url,
                    details         = EXCLUDED.details,
                    raw_open_hours  = EXCLUDED.raw_open_hours,
                    raw_closed_days = EXCLUDED.raw_closed_days,
                    homepage        = EXCLUDED.homepage,
                    image_urls      = EXCLUDED.image_urls,
                    source          = EXCLUDED.source,
                    last_updated    = EXCLUDED.last_updated,
                    translations    = storedetails.translations || EXCLUDED.translations
                """,
                store_id, place_id, display_name,
                category_name, category_key,
                addr, phone, lat, lng, kakao.get("place_url", ""),
                details,
                open_hours or None, closed_days or None, homepage,
                image_urls,
                floor, source, datetime.now(timezone.utc),
                translations,
            )

            # 영업시간 정규화(store_hours 생성)는 시딩과 분리.
            # raw_open_hours 만 저장하고, 별도 배치(scripts/normalize_store_hours.py)가
            # 나중에 LLM 정규화 → store_hours 테이블을 채운다.
            # 이유: 스크래핑(느림/취약)과 정규화(재실행 필요)를 decouple.

    result = {
        "id":              store_id or f"{place_id}__{store_name}",
        "place_id":        place_id,
        "store_name":      display_name,
        "category":        category_name,
        "category_key":    category_key,
        "addr":            addr,
        "phone":           phone,
        "lat":             lat,
        "lng":             lng,
        "place_url":       kakao.get("place_url", ""),
        "details":         details,
        "raw_open_hours":  open_hours,
        "raw_closed_days": closed_days,
        "homepage":        homepage,
        "image_urls":      image_urls,
        "floor":           floor,
        "source":          source,
        "translations":    translations,
    }
    _apply_translations_to_result(result, language)
    return result


def _row_to_dict(row) -> dict:
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
    if language == "ko":
        return
    t = (result.get("translations") or {}).get(language, {})
    if t.get("name"):
        result["store_name"] = t["name"]
    if t.get("addr"):
        result["addr"] = t["addr"]
    if t.get("open_hours"):
        result["raw_open_hours"] = t["open_hours"]
    if t.get("closed_days"):
        result["raw_closed_days"] = t["closed_days"]
    if t.get("conveniences"):
        d = result.get("details")
        if isinstance(d, dict) and d.get("conveniences"):
            convs_en = [c.strip() for c in t["conveniences"].split(",") if c.strip()]
            result["details"] = {**d, "conveniences": convs_en}


async def hours_cache_by_names(names: list[str], lang: str = "ko") -> dict[str, list[dict]]:
    """매장명 → [{phone, open_hours}] — store_hours(정규화 영업시간) 기반.

    storedetails ⨝ store_hours 로 매장별 구조화 영업시간을 사람이 읽는 문자열로
    포맷한다. 동명 매장은 각각 한 항목으로 누적(전화로 구분 가능).
    search_agent 의 영업시간 캐시 채우기에 사용.
    """
    from collections import defaultdict
    from tools.open_hours_parser import format_schedule_text

    uniq = [n for n in {nm for nm in names if nm}]
    if not uniq:
        return {}
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT sd.id, sd.store_name, sd.phone, "
            "       sh.day_of_week, sh.open_time, sh.close_time, sh.last_order "
            "FROM storedetails sd JOIN store_hours sh ON sh.store_id = sd.id "
            "WHERE sd.store_name = ANY($1::text[])",
            uniq,
        )
    by_id: dict[str, list] = defaultdict(list)
    id_meta: dict[str, tuple] = {}
    for r in rows:
        by_id[r["id"]].append(r)
        id_meta[r["id"]] = (r["store_name"], r["phone"])
    cache: dict[str, list[dict]] = defaultdict(list)
    for sid, rs in by_id.items():
        nm, phone = id_meta[sid]
        text = format_schedule_text(rs, lang=lang)
        if text:
            cache[nm].append({"phone": phone or "", "open_hours": text})
    return dict(cache)
