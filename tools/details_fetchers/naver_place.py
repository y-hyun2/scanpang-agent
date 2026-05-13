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
) -> dict:
    """
    Naver Place에서 매장 상세를 가져와 store_details fetcher 포맷으로 변환.

    Naver Place는 매장 단위로 페이지가 있어(`/place/{id}`) 영업시간·휴무·전화·
    홈페이지·편의시설(주차/와이파이 등)을 한 번에 잡을 수 있다. 카페·음식점은
    이 데이터만으로도 사용자에게 보여줄 정보 대부분이 채워진다.

    Returns:
        {phone, addr, homepage, open_hours, closed_days, image_urls,
         details: {category, conveniences}, source: "naver_place"}
        매칭 실패 시 빈 dict.
    """
    if not store_name:
        return {}

    # 좌표 근처 매장이 잡히도록 fuzzy 매칭. expected_name만 줘서 같은 이름
    # (스타벅스 다른 지점 등)의 매장이 잡혀도 거절되지 않게 함.
    detail = await fetch_place_detail(query=store_name, expected_name=store_name)
    if not detail:
        return {}

    conveniences = detail.get("conveniences", []) or []
    raw_category = detail.get("category", "") or category_name

    # 매장 검색 결과로 클릭된 실제 매장명이 우리가 요청한 이름과 다를 수 있음
    # (예: "스타벅스 명동점" 요청 → "스타벅스 포포인츠명동점" 클릭됨).
    # 매칭 검증은 fetch_place_detail에서 이미 통과한 것이라 신뢰 가능.
    matched_name = detail.get("name", "")

    return {
        "phone":       detail.get("phone", ""),
        "addr":        detail.get("roadAddress", "") or detail.get("address", ""),
        "homepage":    detail.get("homepage", ""),
        "category":    raw_category,
        "open_hours":  detail.get("open_hours", ""),
        "closed_days": detail.get("closed_days", ""),
        "image_urls":  detail.get("image_urls", []) or [],
        "details":     {
            "matched_name": matched_name,
            "category":     raw_category,
            "conveniences": conveniences,
            "place_id":     detail.get("place_id", ""),
            "menu":         detail.get("menu", []),
            "intro":        detail.get("intro", ""),
        },
        "source":      "naver_place",
    }
