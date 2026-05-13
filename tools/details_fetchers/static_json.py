"""
static_json.py
기도실 — rag/data/prayer_rooms.json 정적 데이터에서 좌표·이름 매칭.
"""

import json
import os
from functools import lru_cache

_DATA_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "rag", "data", "prayer_rooms.json",
)


@lru_cache(maxsize=1)
def _load_prayer_rooms() -> list[dict]:
    """JSON 1회만 로딩 후 메모리 캐시."""
    try:
        with open(_DATA_PATH, encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, list) else []
    except Exception as e:
        print(f"[static_json] prayer_rooms.json 로드 실패: {e}")
        return []


def _haversine_m(a_lat: float, a_lng: float, b_lat: float, b_lng: float) -> float:
    """두 좌표 거리(m). 좌표 유효성 검사는 호출 측 책임."""
    import math
    R = 6_371_000
    dlat = math.radians(b_lat - a_lat)
    dlng = math.radians(b_lng - a_lng)
    h = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(a_lat)) * math.cos(math.radians(b_lat))
         * math.sin(dlng / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(h), math.sqrt(1 - h))


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """
    이름이 정확히 일치하거나 좌표 50m 이내인 첫 기도실을 반환.
    Returns:
        {phone, addr, open_hours, details: {facilities, capacity, name_en, ...},
         source: "static_json"}
    """
    rooms = _load_prayer_rooms()
    if not rooms:
        return {}

    norm_name = (store_name or "").strip().lower()

    # 1차: 이름 정확/부분 매칭
    for r in rooms:
        rn = (r.get("name", "") or "").strip().lower()
        if norm_name and (norm_name == rn or norm_name in rn or rn in norm_name):
            return _to_fetcher_output(r)

    # 2차: 좌표 50m 이내
    if lat and lng:
        for r in rooms:
            r_lat, r_lng = r.get("lat"), r.get("lng")
            if r_lat is None or r_lng is None:
                continue
            if _haversine_m(lat, lng, r_lat, r_lng) <= 50:
                return _to_fetcher_output(r)

    return {}


def _to_fetcher_output(r: dict) -> dict:
    """prayer_rooms.json row → fetcher 표준 출력."""
    return {
        "phone":       r.get("phone", "") or "",
        "addr":        r.get("address", "") or "",
        "homepage":    "",
        "open_hours":  r.get("open_hours", "") or "",
        "closed_days": "",
        "image_urls":  [],
        "details":     {
            "name_en":             r.get("name_en", ""),
            "facilities":          r.get("facilities", {}) or {},
            "capacity":            r.get("capacity"),
            "availability_status": r.get("availability_status", ""),
            "notes":               r.get("notes", ""),
        },
        "source":      "static_json",
    }
