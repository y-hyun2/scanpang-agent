"""
govt_api.py
pipeline.py에서 사용하는 외부 API 헬퍼 함수.
build_place_db.py에서 ML 의존성(sentence_transformers 등) 없이 분리.
"""

import os
from typing import Optional

import httpx
from dotenv import load_dotenv

load_dotenv()

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")
STORE_API_KEY      = os.getenv("STORE_API_KEY", "")
JUSO_API_KEY       = os.getenv("JUSO_API_KEY", "")

MYEONGDONG_LAT = 37.5636
MYEONGDONG_LNG = 126.9822


async def fetch_kakao_info(place_name: str, kakao_name: Optional[str] = None) -> dict:
    """건물명으로 Kakao Local API 검색 → 주소·전화·카테고리 반환."""
    query = kakao_name or place_name
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            "https://dapi.kakao.com/v2/local/search/keyword.json",
            headers={"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"},
            params={
                "query": query,
                "x": str(MYEONGDONG_LNG),
                "y": str(MYEONGDONG_LAT),
                "radius": 2000,
                "size": 10,
            },
        )
        resp.raise_for_status()
        docs = resp.json().get("documents", [])

    if not docs:
        return {}
    matched = next((d for d in docs if d.get("place_name") == query), None)
    doc = matched or docs[0]
    cat_parts = doc.get("category_name", "").split(" > ")
    return {
        "name_ko":  doc.get("place_name", place_name),
        "category": cat_parts[1] if len(cat_parts) > 1 else cat_parts[0],
        "lat":      float(doc.get("y", MYEONGDONG_LAT)),
        "lng":      float(doc.get("x", MYEONGDONG_LNG)),
        "addr":     doc.get("road_address_name", ""),
        "phone":    doc.get("phone", ""),
    }


async def fetch_building_key(road_addr: str) -> Optional[str]:
    """도로명주소 → 소상공인 API용 건물관리번호(bdMgtSn) 조회."""
    if not road_addr or not JUSO_API_KEY:
        return None
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            "https://business.juso.go.kr/addrlink/addrLinkApi.do",
            params={
                "confmKey":    JUSO_API_KEY,
                "keyword":     road_addr,
                "currentPage": 1,
                "countPerPage": 1,
                "resultType":  "json",
            },
        )
        juso_list = resp.json().get("results", {}).get("juso", [])
    return juso_list[0].get("bdMgtSn") if juso_list else None


async def fetch_floor_info(building_key: str) -> list:
    """소상공인 API → 건물 층별 매장 목록 반환."""
    async with httpx.AsyncClient(timeout=60) as client:
        try:
            resp = await client.get(
                "http://apis.data.go.kr/B553077/api/open/sdsc2/storeListInBuilding",
                params={
                    "serviceKey": STORE_API_KEY,
                    "key":        building_key,
                    "numOfRows":  1000,
                    "pageNo":     1,
                    "type":       "json",
                },
            )
            resp.raise_for_status()
            items = resp.json().get("body", {}).get("items", [])
        except Exception as e:
            print(f"[govt_api] 소상공인 API 오류 ({building_key}): {e}")
            return []

    from tools.floor_utils import normalize_floor, floor_sort_key

    floor_map: dict[str, list] = {}
    for item in items:
        # flrNo("1"/"B1"/"지하1층") → 정규화 ("1F"/"B1"). 빈/모호값은 "기타".
        floor      = normalize_floor(item.get("flrNo", ""))
        store_name = item.get("bizesNm", "").strip()
        biz_type   = item.get("indsMclsNm", "").strip()
        if store_name:
            floor_map.setdefault(floor, []).append(
                {"name": store_name, "category": biz_type}
            )

    return [
        {"floor": f, "stores": s}
        for f, s in sorted(floor_map.items(), key=lambda x: floor_sort_key(x[0]))
    ]
