"""
tour_api.py
관광지/문화시설/쇼핑/음식점/숙박 — TourAPI fallback (Naver Place 실패 시).
Phase 2에서 rag/build_place_db.py::fetch_tour_info 재사용해 구현.
"""


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """TODO Phase 2: TourAPI searchKeyword2/detailCommon2/detailIntro2 호출."""
    return {}
