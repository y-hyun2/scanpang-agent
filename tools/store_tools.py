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


# 명동 기본 좌표 fallback
_DEFAULT_LAT = 37.5636
_DEFAULT_LNG = 126.9822

# 공식 홈페이지로 인정 안 하는 소셜·블로그 도메인
_SOCIAL_URL_PATTERNS = (
    "instagram.com", "facebook.com", "twitter.com", "x.com",
    "youtube.com", "youtu.be",
    "blog.naver.com", "cafe.naver.com", "post.naver.com",
    "tiktok.com", "pf.kakao.com", "kakao.com/o/", "linktr.ee",
)


def _is_social_url(url: str) -> bool:
    if not url:
        return False
    lower = url.lower()
    return any(p in lower for p in _SOCIAL_URL_PATTERNS)


async def get_store_detail(
    place_id: str,
    store_name: str,
    lat: float | None = None,
    lng: float | None = None,
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
    cache_id = f"{place_id}__{store_name}"
    pool     = await get_pool()

    # outdoor 카테고리 — store_details 캐시 / INSERT 모두 우회.
    # 화장실·지하철·물품보관·기도실은 정부 raw 테이블이 단일 출처라 매장 캐시 X.
    _OUTDOOR = {"restroom", "subway", "subway_station", "locker", "prayer_room"}
    pre_category = classify_category(category_name="", store_name=store_name)
    is_outdoor = pre_category in _OUTDOOR

    # ── ① 캐시 조회 — outdoor 가 아닐 때만 ──────────────────────────────────
    if not is_outdoor:
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT * FROM store_details WHERE id = $1", cache_id
            )
        if row:
            return _row_to_dict(row)

    # ── ② 좌표 결정 ─────────────────────────────────────────────────────────
    # lat/lng가 인자로 들어오면 그대로 사용 (마커/GPS 진입).
    # 아니면 place_info에서 건물 좌표 조회 (시나리오 a).
    if lat is None or lng is None:
        async with pool.acquire() as conn:
            coord = await conn.fetchrow(
                "SELECT lat, lng FROM place_info WHERE ufid = $1", place_id
            )
        lat = float(coord["lat"]) if coord and coord["lat"] is not None else _DEFAULT_LAT
        lng = float(coord["lng"]) if coord and coord["lng"] is not None else _DEFAULT_LNG
    else:
        lat, lng = float(lat), float(lng)

    # ── ③ Kakao Local 1차 — category_name 확보용 (분류 입력) ─────────────────
    kakao = await check_kakao_open_status(store_name, lat, lng) or {}
    category_name = kakao.get("category", "") or ""
    category_key  = classify_category(category_name, store_name)
    print(f"[store_tools] {store_name!r} → category_name={category_name!r}, category_key={category_key!r}")

    # ── ④ 카테고리별 fetcher 디스패치 ───────────────────────────────────────
    fetched = await fetch_by_category(
        category_key   = category_key,
        store_name     = store_name,
        lat            = lat,
        lng            = lng,
        building_ufid  = place_id,
        category_name  = category_name,
    )

    # fetcher 결과가 비어있으면 Kakao 1차 메타로 fallback
    phone      = fetched.get("phone")      or kakao.get("phone", "")
    addr       = fetched.get("addr")       or kakao.get("addr", "")
    # homepage 소셜 URL 필터 — 인스타·블로그는 제외. fetcher 결과가 소셜이면
    # Kakao place_url로 fallback (Kakao place_url은 Kakao 자체 페이지라 항상 OK).
    raw_homepage = fetched.get("homepage", "") or ""
    if _is_social_url(raw_homepage):
        print(f"[store_tools] 소셜/블로그 URL 제외: {raw_homepage}")
        raw_homepage = ""
    homepage   = raw_homepage or kakao.get("place_url", "")
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

    # outdoor: store_details INSERT 우회. fetcher 결과를 그대로 응답으로 사용.
    if is_outdoor:
        return {
            "id":            cache_id,
            "place_id":      place_id,
            "store_name":    store_name,
            "category":      category_name,
            "category_key":  category_key,
            "addr":          addr,
            "phone":         phone,
            "lat":           lat,
            "lng":           lng,
            "place_url":     kakao.get("place_url", ""),
            "details":       details,
            "open_hours":    open_hours,
            "closed_days":   closed_days,
            "homepage":      homepage,
            "image_urls":    image_urls,
            "floor":         floor,
            "source":        source,
        }

    # ── ⑤ store_details UPSERT ──────────────────────────────────────────────
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO store_details
                (id, place_id, store_name, category, category_key,
                 addr, phone, lat, lng, place_url,
                 details, open_hours, closed_days, homepage, image_urls,
                 floor, source, last_updated)
            VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,
                    $11::jsonb,$12,$13,$14,$15::jsonb,$16,$17,$18)
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
                last_updated  = EXCLUDED.last_updated
            """,
            cache_id, place_id, store_name,
            category_name, category_key,
            addr, phone, lat, lng, kakao.get("place_url", ""),
            json.dumps(details, ensure_ascii=False),
            open_hours, closed_days, homepage,
            json.dumps(image_urls, ensure_ascii=False),
            floor, source, datetime.now(timezone.utc),
        )

    return {
        "id":            cache_id,
        "place_id":      place_id,
        "store_name":    store_name,
        "category":      category_name,
        "category_key":  category_key,
        "addr":          addr,
        "phone":         phone,
        "lat":           lat,
        "lng":           lng,
        "place_url":     kakao.get("place_url", ""),
        "details":       details,
        "open_hours":    open_hours,
        "closed_days":   closed_days,
        "homepage":      homepage,
        "image_urls":    image_urls,
        "floor":         floor,
        "source":        source,
    }


def _row_to_dict(row) -> dict:
    """asyncpg Record → 일반 dict. JSONB 필드는 이미 파싱돼서 옴."""
    d = dict(row)
    # asyncpg가 JSONB를 string으로 반환할 수도 있어 방어적으로 파싱
    for k in ("details", "image_urls"):
        v = d.get(k)
        if isinstance(v, str):
            try:
                d[k] = json.loads(v)
            except Exception:
                pass
    return d
