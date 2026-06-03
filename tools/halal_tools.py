"""
halal_tools.py
Halal Agent용 도구 함수들.
- Aladhan API: 기도 시간, 키블라 방향
- PostGIS DB: 할랄 식당(halal_restaurants), 기도실(prayer_rooms) 거리 검색
"""

import json
from datetime import datetime, timezone, timedelta

import httpx

# ── 기본 반경 ────────────────────────────────────────────────────────────────

# 할랄 매장은 전국적으로 명동 등 일부 지역에만 19건 등록돼 있어, radius 1km 면
# 외대 같은 명동 밖 사용자에겐 무조건 0건 — '조건에 맞는 장소 없음' 이 떠버림.
# 거리 정렬은 ST_Distance 로 정확히 되므로 radius 를 크게 두고 사용자가 거리
# 보고 판단하게 한다. 50km 면 서울+경기 전역 커버.
DEFAULT_RADIUS = {
    "restaurant": 50_000,
    "prayer_room": 50_000,
}

# ── 기도 시간 캐시 (같은 날짜+위치면 변하지 않음) ─────────────────────────────

_prayer_time_cache: dict = {}


# ── 다음 기도 계산 (캐시 없이 호출 시각 기준) ──────────────────────────────────

def _compute_next_prayer(times: dict) -> dict:
    """캐시된 기도 시간 dict에서 현재 KST 기준 다음 기도 이름·시간을 계산."""
    prayer_map = [
        ("Fajr",    times.get("fajr", "")),
        ("Dhuhr",   times.get("dhuhr", "")),
        ("Asr",     times.get("asr", "")),
        ("Maghrib", times.get("maghrib", "")),
        ("Isha",    times.get("isha", "")),
    ]
    kst = timezone(timedelta(hours=9))
    now = datetime.now(kst)
    for name, t in prayer_map:
        if not t:
            continue
        try:
            prayer_dt = now.replace(
                hour=int(t.split(":")[0]),
                minute=int(t.split(":")[1]),
                second=0,
                microsecond=0,
            )
            if prayer_dt > now:
                return {"next_prayer": name, "next_prayer_time": t}
        except (ValueError, IndexError):
            continue
    return {"next_prayer": "", "next_prayer_time": ""}


# ── Aladhan API: 기도 시간 ───────────────────────────────────────────────────

async def fetch_prayer_times(lat: float, lng: float, date: str = "") -> dict:
    """
    Aladhan Prayer Times API 호출.
    date: DD-MM-YYYY 형식. 비어있으면 오늘(KST) 자동.
    Returns: {"fajr": "04:52", "dhuhr": "12:15", ..., "hijri_date": "...", "gregorian_date": "..."}
    """
    if not date:
        kst = timezone(timedelta(hours=9))
        date = datetime.now(kst).strftime("%d-%m-%Y")

    cache_key = f"{date}_{lat:.2f}_{lng:.2f}"
    if cache_key in _prayer_time_cache:
        return {**_prayer_time_cache[cache_key], **_compute_next_prayer(_prayer_time_cache[cache_key])}

    url = f"https://api.aladhan.com/v1/timings/{date}"
    params = {"latitude": lat, "longitude": lng, "method": 3}

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json().get("data", {})
    except Exception as e:
        print(f"[Halal] Aladhan prayer times 오류: {e}")
        return {}

    timings = data.get("timings", {})
    hijri = data.get("date", {}).get("hijri", {})
    gregorian = data.get("date", {}).get("gregorian", {})

    # 기도 시간 5개 + 날짜 정보만 캐시 — next_prayer는 호출 시각 기준 매번 계산
    cached = {
        "fajr":           timings.get("Fajr", ""),
        "dhuhr":          timings.get("Dhuhr", ""),
        "asr":            timings.get("Asr", ""),
        "maghrib":        timings.get("Maghrib", ""),
        "isha":           timings.get("Isha", ""),
        "hijri_date":     f"{hijri.get('day', '')} {hijri.get('month', {}).get('en', '')} {hijri.get('year', '')}",
        "gregorian_date": gregorian.get("date", ""),
    }
    _prayer_time_cache[cache_key] = cached

    return {**cached, **_compute_next_prayer(cached)}


# ── Aladhan API: 키블라 방향 ─────────────────────────────────────────────────

async def fetch_qibla_direction(lat: float, lng: float) -> dict:
    """
    Aladhan Qibla API 호출.
    Returns: {"direction": 232.07, "lat": ..., "lng": ...}
    """
    url = f"https://api.aladhan.com/v1/qibla/{lat}/{lng}"
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            data = resp.json().get("data", {})
    except Exception as e:
        print(f"[Halal] Aladhan qibla 오류: {e}")
        return {"direction": 0.0, "lat": lat, "lng": lng}

    return {
        "direction": data.get("direction", 0.0),
        "lat": lat,
        "lng": lng,
    }


# ── 할랄 식당 검색 (PostGIS) ─────────────────────────────────────────────────

async def halal_restaurant_search(
    lat: float, lng: float, radius: int = 0, halal_type: str = ""
) -> list:
    """
    halal_restaurants 테이블(PostGIS) 거리 정렬 검색.
    halal_type: "HALAL_MEAT" / "SEAFOOD" / "VEGGIE" / "" (전체)
    radius 인자는 호환성 위해 받지만 사용하지 않음 — 다른 카테고리와 일관되게
    거리 정렬 + LIMIT 만 적용. 사용자가 카드에 표시된 거리 보고 판단.
    """
    from core.db import get_pool
    type_filter = halal_type.replace("_", " ").upper() if halal_type else ""

    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT restaurant_id, name_ko, name_en, halal_type,
                   muslim_cooks_available, no_alcohol_sales,
                   cuisine_type::text AS cuisine_type,
                   menu_examples::text AS menu_examples,
                   short_description_ko, address, phone,
                   opening_hours::text AS opening_hours,
                   break_time::text AS break_time,
                   last_order::text AS last_order,
                   lat, lng,
                   ST_Distance(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography) AS dist
            FROM halal_restaurants
            WHERE ($3 = '' OR UPPER(halal_type) LIKE '%' || $3 || '%')
            ORDER BY dist
            LIMIT 50
            """,
            float(lat), float(lng), type_filter,
        )

    return _process_halal_rows(rows)


# ── 할랄 식당 상호명 검색 (메뉴 질의용) ──────────────────────────────────────

async def halal_restaurant_by_name(name: str, lat: float, lng: float) -> list:
    """
    상호명(부분 일치)으로 halal_restaurants 조회 — "부산집 메뉴 뭐 있어?" 같은
    특정 매장 질의용. 거리 정렬과 동일한 필드(menu_examples 포함)를 반환해
    위치 기반 검색 결과와 동일 형식으로 다룬다.

    매칭: name_ko/name_en 부분 일치(ILIKE). 정확 일치를 먼저, 그다음 거리순.
    lat/lng 는 거리 계산용(없으면 0).
    """
    from core.db import get_pool
    kw = (name or "").strip()
    if not kw:
        return []

    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT restaurant_id, name_ko, name_en, halal_type,
                   muslim_cooks_available, no_alcohol_sales,
                   cuisine_type::text AS cuisine_type,
                   menu_examples::text AS menu_examples,
                   short_description_ko, address, phone,
                   opening_hours::text AS opening_hours,
                   break_time::text AS break_time,
                   last_order::text AS last_order,
                   lat, lng,
                   ST_Distance(geom, ST_SetSRID(ST_MakePoint($3, $2), 4326)::geography) AS dist
            FROM halal_restaurants
            WHERE name_ko ILIKE '%' || $1 || '%' OR name_en ILIKE '%' || $1 || '%'
            ORDER BY (LOWER(name_ko) = LOWER($1) OR LOWER(name_en) = LOWER($1)) DESC, dist
            LIMIT 10
            """,
            kw, float(lat), float(lng),
        )

    return _process_halal_rows(rows)


def _process_halal_rows(rows: list) -> list:
    """halal_restaurants 행 리스트 → 표준 dict 리스트 (거리/이름검색 공용)."""
    results = []
    kst = timezone(timedelta(hours=9))
    today_idx = datetime.now(kst).weekday()
    days = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
    for r in rows:
        menu = json.loads(r["menu_examples"]) if r["menu_examples"] else []
        menu_names = [
            (m.get("name_en") or m.get("name_ko") or "") if isinstance(m, dict) else str(m)
            for m in menu
        ]
        oh_raw = json.loads(r["opening_hours"]) if r["opening_hours"] else {}
        if isinstance(oh_raw, dict):
            oh_str = oh_raw.get(days[today_idx], "") or "정보 없음"
        else:
            oh_str = str(oh_raw) if oh_raw else ""
        bt = json.loads(r["break_time"]) if r["break_time"] else {}
        lo = json.loads(r["last_order"]) if r["last_order"] else {}
        results.append({
            "restaurant_id": r["restaurant_id"],
            "name_ko":       r["name_ko"],
            "name_en":       r["name_en"],
            "halal_type":    r["halal_type"],
            "muslim_cooks_available": r["muslim_cooks_available"],
            "no_alcohol_sales":       r["no_alcohol_sales"],
            "cuisine_type":  json.loads(r["cuisine_type"]) if r["cuisine_type"] else [],
            "menu_examples": menu_names,
            "short_description_ko": r["short_description_ko"] or "",
            "distance_m":    round(r["dist"], 1),
            "lat":           float(r["lat"]) if r["lat"] is not None else None,
            "lng":           float(r["lng"]) if r["lng"] is not None else None,
            "address":       r["address"] or "",
            "phone":         r["phone"] or "",
            "opening_hours": oh_str,
            "break_time":    _dict_to_today_str(bt),
            "last_order":    _dict_to_today_str(lo),
        })
    return results


def _dict_to_today_str(val) -> str:
    """요일별 dict → 오늘 요일에 해당하는 값 문자열 반환."""
    if not val or not isinstance(val, dict):
        return str(val) if val else ""
    days = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
    kst = timezone(timedelta(hours=9))
    today_idx = datetime.now(kst).weekday()
    today_val = val.get(days[today_idx])
    return str(today_val) if today_val else ""


# ── 기도실 검색 (PostGIS) ────────────────────────────────────────────────────

async def halal_prayer_room_search(lat: float, lng: float, radius: int = 0) -> list:
    """
    PostGIS prayer_rooms 테이블 거리 검색 (ST_DWithin + ST_Distance 정렬).
    Returns: 거리순 list[dict] — schemas.halal.PrayerRoomItem 형식.
    """
    from core.db import get_pool
    if radius <= 0:
        radius = DEFAULT_RADIUS["prayer_room"]

    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT name, name_en, address, floor, open_hours,
                   facilities::text AS facilities,
                   availability_status, lat, lng,
                   ST_Distance(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography) AS dist
            FROM prayer_rooms
            WHERE ST_DWithin(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography, $3)
            ORDER BY dist
            """,
            float(lat), float(lng), float(radius),
        )

    results = []
    for r in rows:
        try: fac = json.loads(r["facilities"]) if r["facilities"] else {}
        except Exception: fac = {}
        results.append({
            "name":               r["name"] or "",
            "name_en":            r["name_en"] or "",
            "distance_m":         round(r["dist"], 1),
            "lat":                float(r["lat"]) if r["lat"] is not None else None,
            "lng":                float(r["lng"]) if r["lng"] is not None else None,
            "address":            r["address"] or "",
            "floor":              r["floor"] or "",
            "open_hours":         r["open_hours"] or "",
            "facilities":         fac,
            "availability_status": r["availability_status"] or "unknown",
        })
    return results[:5]
