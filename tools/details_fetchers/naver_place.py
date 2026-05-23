"""
naver_place.py
카페·음식점·관광지·문화시설·쇼핑·숙박 — Naver Place detail에서 풍부도 추출.
기존 rag/automation/naver_map_scraper.py::fetch_place_detail 재사용.
"""

from rag.automation.naver_map_scraper import fetch_place_detail


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
    addr_hint: str = "",
) -> dict:
    """
    Naver Place에서 매장 상세를 가져와 store_details fetcher 포맷으로 변환.

    addr_hint가 있으면 주소로 검색한다 (Tour API add1 등).
    이름 검색보다 정확도가 높아 관광지·문화시설·숙박처럼 이름으로 안 잡히는
    장소에 유용하다.

    Returns:
        {phone, addr, homepage, open_hours, closed_days, image_urls,
         details: {category, conveniences}, source: "naver_place"}
        매칭 실패 시 빈 dict.
    """
    if not store_name:
        return {}

    query = addr_hint if addr_hint else store_name
    detail = await fetch_place_detail(query=query, expected_name=store_name)
    # addr_hint 검색 실패 시 장소명으로 재시도
    if not detail and addr_hint:
        detail = await fetch_place_detail(query=store_name, expected_name=store_name)
    if not detail:
        return {}

    conveniences = detail.get("conveniences", []) or []
    raw_category = detail.get("category", "") or category_name

    # 매장 검색 결과로 클릭된 실제 매장명이 우리가 요청한 이름과 다를 수 있음
    # (예: "스타벅스 명동점" 요청 → "스타벅스 포포인츠명동점" 클릭됨).
    # 매칭 검증은 fetch_place_detail에서 이미 통과한 것이라 신뢰 가능.
    matched_name = detail.get("name", "")

    # addr 우선순위:
    # 1. address_detail (m.place.naver.com 의 풀 주소 — Naver 검색결과 표시와 1:1)
    #    예: "서울 중구 을지로 30 을지로입구역7,8번출구방면(롯데백화점 들어가서 우측)"
    # 2. roadAddress (Apollo state — 깔끔한 도로명만)
    # 3. address (지번)
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
        "floor":       detail.get("floor", ""),  # base.road에서 추출한 정규화된 층 ("1F"/"B1")
        "details":     {
            "matched_name": matched_name,
            "category":     raw_category,
            "conveniences": conveniences,
            "place_id":     detail.get("place_id", ""),
            "menu":         detail.get("menu", []),
            "intro":        detail.get("intro", ""),
            "road_detail":  detail.get("road_detail", ""),  # 원본 위치 텍스트
        },
        "source":      "naver_place",
    }
