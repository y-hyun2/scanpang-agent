"""
naver_place.py
관광지·문화시설·쇼핑·음식점·숙박·카페 — Naver Place detail에서 풍부도 추출.
Phase 2에서 fetch_place_detail 재사용해 구현 예정.
"""


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """TODO Phase 2: Naver Place detail 호출 → 메뉴/소개/이미지/영업시간 추출."""
    return {}
