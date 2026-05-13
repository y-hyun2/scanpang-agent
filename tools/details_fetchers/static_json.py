"""
static_json.py
기도실 — rag/data/prayer_rooms.json 정적 데이터에서 매칭.
Phase 2에서 구현.
"""


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """TODO Phase 2: prayer_rooms.json 로드 → 좌표 근접 매장 반환."""
    return {}
