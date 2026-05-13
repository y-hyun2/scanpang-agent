"""
kakao_basic.py
편의점·은행·ATM·병원·약국 등 Kakao Local 결과만 있어도 충분한 카테고리용.
Phase 1에서 실제 구현하는 유일한 fetcher.
"""

from tools.place_tools import check_kakao_open_status


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """Kakao Local 키워드 검색 → 기본 메타데이터 반환."""
    kakao = await check_kakao_open_status(store_name, lat, lng)
    if not kakao:
        return {}
    return {
        "phone":      kakao.get("phone", ""),
        "addr":       kakao.get("addr", ""),
        "homepage":   kakao.get("place_url", ""),
        "category":   kakao.get("category", "") or category_name,
        "image_urls": [],
        "open_hours": "",
        "closed_days": "",
        "details":    {},
        "source":     "kakao",
    }
