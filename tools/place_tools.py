import os
import httpx

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")


async def check_kakao_open_status(place_name: str, lat: float, lng: float) -> dict:
    """
    Kakao Local API로 장소 기본 정보 조회.
    주로 build_place_db.py에서 사용하며, 런타임에 실시간 영업여부가 필요할 때도 활용 가능.
    """
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {
        "query": place_name,
        "x": str(lng),
        "y": str(lat),
        "radius": 2000,
        "size": 1,
    }
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers, params=params)
        resp.raise_for_status()
        data = resp.json()

    documents = data.get("documents", [])
    if not documents:
        return {}

    doc = documents[0]
    cat_parts = doc.get("category_name", "").split(" > ")
    return {
        "name_ko": doc.get("place_name", ""),
        "category": cat_parts[1] if len(cat_parts) > 1 else cat_parts[0],
        "lat": float(doc.get("y", 0)),
        "lng": float(doc.get("x", 0)),
        "addr": doc.get("road_address_name", ""),
        "phone": doc.get("phone", ""),
        "place_url": doc.get("place_url", ""),
    }
