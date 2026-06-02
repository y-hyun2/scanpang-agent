"""
naver_place.py
카페·음식점·관광지·문화시설·쇼핑·숙박 — Naver Place detail에서 풍부도 추출.
기존 rag/automation/naver_map_scraper.py::fetch_place_detail 재사용.
"""

from rag.automation.naver_map_scraper import fetch_place_detail


async def _get_building_addr(building_ufid: str) -> str:
    """building_ufid(building_key)로 placeinfo.addr 조회 — 위치 필터용."""
    if not building_ufid or building_ufid.startswith("__"):
        return ""
    try:
        from core.db import get_pool
        pool = await get_pool()
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT addr FROM placeinfo WHERE building_key = $1",
                building_ufid,
            )
        return (row["addr"] or "") if row else ""
    except Exception:
        return ""


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
    addr_hint: str = "",
    structured_hours: bool = True,
) -> dict:
    """
    Naver Place에서 매장 상세를 가져와 store_details fetcher 포맷으로 변환.

    addr_hint 또는 building_ufid 기반 건물 주소를 expected_addr로 사용해
    동명 타 지역 매장이 잘못 매칭되는 것을 방지한다.
    """
    if not store_name:
        return {}

    # 위치 필터용 주소 확보: addr_hint 우선 → placeinfo 조회
    expected_addr = addr_hint
    if not expected_addr and building_ufid:
        expected_addr = await _get_building_addr(building_ufid)

    query = addr_hint if addr_hint else store_name
    detail = await fetch_place_detail(
        query=query,
        expected_name=store_name,
        expected_addr=expected_addr,
        structured_hours=structured_hours,
    )
    # addr_hint 검색 실패 시 장소명으로 재시도 (expected_addr는 유지)
    if not detail and addr_hint:
        detail = await fetch_place_detail(
            query=store_name,
            expected_name=store_name,
            expected_addr=expected_addr,
            structured_hours=structured_hours,
        )
    if not detail:
        return {}

    conveniences = detail.get("conveniences", []) or []
    raw_category = detail.get("category", "") or category_name
    matched_name = detail.get("name", "")

    full_addr = (
        detail.get("address_detail")
        or detail.get("roadAddress", "")
        or detail.get("address", "")
    )

    return {
        "phone":       detail.get("phone", ""),
        "addr":        full_addr,
        "homepage":    detail.get("homepage", ""),
        "category":    raw_category,
        "open_hours":  detail.get("open_hours", ""),
        "closed_days": detail.get("closed_days", ""),
        "image_urls":  detail.get("image_urls", []) or [],
        "floor":       detail.get("floor", ""),
        "details":     {
            "matched_name": matched_name,
            "category":     raw_category,
            "conveniences": conveniences,
            "place_id":     detail.get("place_id", ""),
            "menu":         detail.get("menu", []),
            "intro":        detail.get("intro", ""),
            "road_detail":  detail.get("road_detail", ""),
        },
        "source":      "naver_place",
    }
