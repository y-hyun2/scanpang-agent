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

from tools.convenience_tools import haversine_m

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


def halal_restaurant_search(
    lat: float, lng: float, radius: int = 0, halal_type: str = ""
) -> list:
    """
    JSON에서 할랄 식당 검색.
    halal_type: "HALAL_MEAT" / "SEAFOOD" / "VEGGIE" / "" (전체)
    Returns: 거리순 상위 10개 list[dict]
    """
    if radius <= 0:
        radius = DEFAULT_RADIUS["restaurant"]

    restaurants = _load_restaurants()
    results = []

    # halal_type 필터 매핑: HALAL_MEAT → "HALAL MEAT"
    type_filter = halal_type.replace("_", " ").upper() if halal_type else ""

    for r in restaurants:
        r_lat = r.get("latitude")
        r_lng = r.get("longitude")
        if r_lat is None or r_lng is None:
            continue

        dist = haversine_m(lat, lng, r_lat, r_lng)
        if dist > radius:
            continue

        # halal_type 필터
        if type_filter and type_filter not in r.get("halal_type", "").upper():
            continue

        # menu_examples → 이름 리스트로 변환
        menu_names = []
        for m in r.get("menu_examples", []):
            if isinstance(m, dict):
                menu_names.append(f"{m.get('name_en', m.get('name_ko', ''))}")
            else:
                menu_names.append(str(m))

        # opening_hours: dict → 오늘 요일 기준 문자열
        oh = r.get("opening_hours", "")
        if isinstance(oh, dict):
            days = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
            kst = timezone(timedelta(hours=9))
            today_idx = datetime.now(kst).weekday()
            oh_today = oh.get(days[today_idx], "")
            oh_str = oh_today if oh_today else "정보 없음"
        else:
            oh_str = str(oh) if oh else ""

        results.append({
            "restaurant_id": r.get("restaurant_id", ""),
            "name_ko": r.get("name_ko", ""),
            "name_en": r.get("name_en", ""),
            "halal_type": r.get("halal_type", ""),
            "muslim_cooks_available": r.get("muslim_cooks_available"),
            "no_alcohol_sales": r.get("no_alcohol_sales"),
            "cuisine_type": r.get("cuisine_type", []),
            "menu_examples": menu_names,
            "distance_m": round(dist, 1),
            "lat": r_lat,
            "lng": r_lng,
            "address": r.get("address", ""),
            "phone": r.get("phone", ""),
            "opening_hours": oh_str,
            "break_time": _dict_to_today_str(r.get("break_time")),
            "last_order": _dict_to_today_str(r.get("last_order")),
        })

    results.sort(key=lambda x: x["distance_m"])
    return results[:20]


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


def halal_prayer_room_search(lat: float, lng: float, radius: int = 0) -> list:
    """
    JSON에서 기도실 검색.
    Returns: 거리순 상위 5개 list[dict]
    """
    if radius <= 0:
        radius = DEFAULT_RADIUS["prayer_room"]

    rooms = _load_prayer_rooms()
    results = []

    for r in rooms:
        r_lat = r.get("lat")
        r_lng = r.get("lng")
        if r_lat is None or r_lng is None:
            continue

        dist = haversine_m(lat, lng, r_lat, r_lng)
        if dist > radius:
            continue

        results.append({
            "name": r.get("name", ""),
            "name_en": r.get("name_en", ""),
            "distance_m": round(dist, 1),
            "lat": r_lat,
            "lng": r_lng,
            "address": r.get("address", ""),
            "floor": r.get("floor", ""),
            "open_hours": r.get("open_hours", ""),
            "facilities": r.get("facilities", {}),
            "availability_status": r.get("availability_status", "unknown"),
        })

    results.sort(key=lambda x: x["distance_m"])
    return results[:5]
