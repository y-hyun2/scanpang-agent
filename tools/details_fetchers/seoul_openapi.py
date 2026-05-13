"""
seoul_openapi.py
화장실(OA-22586) / 물품보관함(OA-22731) — 서울시 OpenAPI.
기존 tools/convenience_tools.py::seoul_restroom_search/seoul_locker_search 재사용.
"""

from tools.convenience_tools import seoul_restroom_search, seoul_locker_search

# 검색 반경 (m). 너무 좁으면 결과 0개. 너무 넓으면 무관한 시설 매칭.
_SEARCH_RADIUS = 500


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """
    매장명 기반으로 화장실/물품보관함 어느 쪽인지 판별 후 해당 API 호출.

    좌표 근접 매장 중 이름 매칭(부분일치) 우선, 없으면 가장 가까운 1개 채택.

    Returns:
        {phone, addr, open_hours, image_urls, details: {...}, source: "seoul_openapi"}
        매칭 실패 시 빈 dict.
    """
    if not (lat and lng):
        return {}

    # 매장명 또는 category_name으로 어느 API 쓸지 판단
    is_locker = "보관" in store_name or "락커" in store_name or "보관함" in store_name
    is_restroom = ("화장실" in store_name or "공중화장실" in store_name
                   or "화장실" in (category_name or ""))

    if is_locker:
        rows = await seoul_locker_search(lat, lng, _SEARCH_RADIUS)
        return _pick(rows, store_name, kind="locker")
    if is_restroom:
        rows = await seoul_restroom_search(lat, lng, _SEARCH_RADIUS)
        return _pick(rows, store_name, kind="restroom")
    return {}


def _pick(rows: list[dict], store_name: str, kind: str) -> dict:
    """rows 중 이름 부분일치 우선, 없으면 가장 가까운 1개."""
    if not rows:
        return {}
    norm = (store_name or "").strip().lower()
    selected = None
    if norm:
        for r in rows:
            if norm in (r.get("name", "") or "").lower():
                selected = r
                break
    if selected is None:
        selected = rows[0]  # rows는 distance_m 오름차순

    extra = selected.get("extra", {}) or {}
    if kind == "locker":
        details = {
            "small":           extra.get("small", ""),
            "medium":          extra.get("medium", ""),
            "large":           extra.get("large", ""),
            "location_detail": extra.get("location_detail", ""),
            "distance_m":      selected.get("distance_m"),
        }
    else:  # restroom
        details = {
            "distance_m": selected.get("distance_m"),
            "note":       selected.get("address", "")  # "개방형 화장실" 등
        }

    return {
        "phone":       selected.get("phone", ""),
        "addr":        selected.get("address", ""),
        "homepage":    "",
        "open_hours":  selected.get("open_hours", ""),
        "image_urls":  [],
        "details":     details,
        "source":      "seoul_openapi",
    }
