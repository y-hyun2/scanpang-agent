"""
ecos.py
환전소 — 한국은행 ECOS API에서 오늘의 환율 가져오기.
Phase 2에서 구현. (환율은 매장별이 아니라 글로벌이므로 일자별 캐싱 권장.)
"""


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """TODO Phase 2: 한국은행 ECOS API → {rates_today: [{ccy, rate, country}]}."""
    return {}
