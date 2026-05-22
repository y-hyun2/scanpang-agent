"""
tour_api.py
관광지/문화시설/쇼핑/음식점/숙박 — Naver Place 실패 시 TourAPI fallback.
기존 rag/build_place_db.py::fetch_tour_info 재사용.

주의:
- fetch_tour_info는 호출 시 LLM 번역(translate_to_english)을 동시 호출하니
  비용이 다소 큼. lazy 캐시라 첫 호출만 영향.
- rag/build_place_db는 sentence_transformers 등 ML 모듈을 module-level import
  하므로 fetch_tour_info를 함수 안에서 lazy import 한다 (런타임 부담 회피).
"""


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """
    TourAPI에서 매장(주로 관광지/문화시설) 상세를 가져와 fetcher 포맷으로 변환.

    fetch_tour_info는 contentTypeId 12→14→15→25→28→32→38→39 순회로
    매칭되는 카테고리를 자동 탐색하니 store_details에선 category_key 안 봐도 됨.

    Returns:
        {homepage, open_hours, closed_days, image_urls,
         details: {description_en, admission_fee, parking_info, content_type},
         source: "tour_api"}
        매칭 실패 시 빈 dict.
    """
    if not store_name:
        return {}

    try:
        from tools.tour_api_client import fetch_tour_info
        info = await fetch_tour_info(place_name=store_name)
    except Exception as e:
        print(f"[tour_api] fetch_tour_info 실패 ({store_name!r}): {e}")
        return {}

    if not info:
        return {}

    image_url = info.get("image_url", "")
    image_urls = [image_url] if image_url else []

    return {
        "phone":       info.get("phone", ""),
        "addr":        info.get("addr", ""),
        "homepage":    info.get("homepage", ""),
        "open_hours":  info.get("open_hours", ""),
        "closed_days": info.get("closed_days", ""),
        "image_urls":  image_urls,
        "details":     {
            "overview":        info.get("overview", ""),
            "admission_fee":   info.get("admission_fee", ""),
            "parking_info":    info.get("parking_info", ""),
            "content_id":      info.get("content_id", ""),
            "content_type_id": info.get("content_type_id"),
        },
        "source":      "tour_api",
    }
