"""
halal_tools.py
Halal Agent용 도구 함수들.
- Aladhan API: 기도 시간, 키블라 방향
- JSON 파일 기반: 할랄 식당, 기도실 검색
"""

import json
import os
from datetime import datetime, timezone, timedelta

import httpx

# ── 경로 ─────────────────────────────────────────────────────────────────────

_DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "rag", "data")
RESTAURANTS_PATH = os.path.join(_DATA_DIR, "myeongdong_restaurants.json")
PRAYER_ROOMS_PATH = os.path.join(_DATA_DIR, "prayer_rooms.json")

# ── 기본 반경 ────────────────────────────────────────────────────────────────

DEFAULT_RADIUS = {
    "restaurant": 1000,
    "prayer_room": 2000,
}

# ── 기도 시간 캐시 (같은 날짜+위치면 변하지 않음) ─────────────────────────────

_prayer_time_cache: dict = {}


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
        return _prayer_time_cache[cache_key]

    url = f"http://api.aladhan.com/v1/timings/{date}"
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

    result = {
        "fajr": timings.get("Fajr", ""),
        "dhuhr": timings.get("Dhuhr", ""),
        "asr": timings.get("Asr", ""),
        "maghrib": timings.get("Maghrib", ""),
        "isha": timings.get("Isha", ""),
        "hijri_date": f"{hijri.get('day', '')} {hijri.get('month', {}).get('en', '')} {hijri.get('year', '')}",
        "gregorian_date": gregorian.get("date", ""),
    }
    _prayer_time_cache[cache_key] = result
    return result


# ── Aladhan API: 키블라 방향 ─────────────────────────────────────────────────

async def fetch_qibla_direction(lat: float, lng: float) -> dict:
    """
    Aladhan Qibla API 호출.
    Returns: {"direction": 232.07, "lat": ..., "lng": ...}
    """
    url = f"http://api.aladhan.com/v1/qibla/{lat}/{lng}"
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


# ── 할랄 식당 검색 (JSON) ────────────────────────────────────────────────────

_restaurants_cache: list = []


def _load_restaurants() -> list:
    global _restaurants_cache
    if _restaurants_cache:
        return _restaurants_cache
    if not os.path.exists(RESTAURANTS_PATH):
        print(f"[Halal] WARNING: {RESTAURANTS_PATH} 없음")
        return []
    with open(RESTAURANTS_PATH, "r", encoding="utf-8") as f:
        _restaurants_cache = json.load(f)
    print(f"[Halal] 할랄 식당 {len(_restaurants_cache)}개 로드")
    return _restaurants_cache


async def halal_restaurant_search(
    lat: float, lng: float, radius: int = 0, halal_type: str = ""
) -> list:
    """
    halal_restaurants 테이블(PostGIS) 거리 검색.
    halal_type: "HALAL_MEAT" / "SEAFOOD" / "VEGGIE" / "" (전체)
    Returns: 거리순 상위 20개 list[dict]
    """
    from core.db import get_pool
    if radius <= 0:
        radius = DEFAULT_RADIUS["restaurant"]
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
            WHERE ST_DWithin(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography, $3)
              AND ($4 = '' OR UPPER(halal_type) LIKE '%' || $4 || '%')
            ORDER BY dist
            LIMIT 20
            """,
            float(lat), float(lng), float(radius), type_filter,
        )

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


# ── 기도실 검색 (JSON) ───────────────────────────────────────────────────────

_prayer_rooms_cache: list = []


def _load_prayer_rooms() -> list:
    global _prayer_rooms_cache
    if _prayer_rooms_cache:
        return _prayer_rooms_cache
    if not os.path.exists(PRAYER_ROOMS_PATH):
        print(f"[Halal] WARNING: {PRAYER_ROOMS_PATH} 없음")
        return []
    with open(PRAYER_ROOMS_PATH, "r", encoding="utf-8") as f:
        _prayer_rooms_cache = json.load(f)
    print(f"[Halal] 기도실 {len(_prayer_rooms_cache)}개 로드")
    return _prayer_rooms_cache


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
