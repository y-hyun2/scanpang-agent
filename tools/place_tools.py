import os
import httpx

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")


async def check_kakao_open_status(
    place_name: str, lat: float, lng: float, radius: int = 1000,
) -> dict:
    """
    Kakao Local API로 장소 기본 정보 조회.
    store_details에서는 place_info.floor_info의 매장명이 들어오므로 같은 건물
    소속이 일반적이지만, Kakao에 등록된 매장 좌표가 건물 입구/별관/실제 매장 위치
    중 어디인지 일정치 않아 거리 오차가 종종 300m를 넘는다. 기본 1km로 잡아
    누락을 줄이고, 동명 체인은 Kakao가 거리순 정렬한 첫 결과를 채택해 어느 정도 방어.
    """
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {
        "query": place_name,
        "x": str(lng),
        "y": str(lat),
        "radius": radius,
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
    category_full = doc.get("category_name", "")
    cat_parts = category_full.split(" > ")
    return {
        "name_ko": doc.get("place_name", ""),
        # UI 표시·DB 저장용 짧은 카테고리 (level-2 or level-1)
        "category": cat_parts[1] if len(cat_parts) > 1 else cat_parts[0],
        # 분류기(classify_category)용 풀 카테고리 — "음식점 > 간식 > 도넛" 등
        # level-2만 보면 "간식" 같은 모호한 단어로 떨어져 "other"가 되니
        # 풀스트링을 분류 입력으로 써서 "음식점"/"도넛" 부분문자열 매치를 살린다.
        "category_full": category_full,
        "lat": float(doc.get("y", 0)),
        "lng": float(doc.get("x", 0)),
        "addr": doc.get("road_address_name", ""),
        "phone": doc.get("phone", ""),
        "place_url": doc.get("place_url", ""),
    }
