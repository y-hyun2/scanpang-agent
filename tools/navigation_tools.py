import httpx
import os
from typing import Optional
from dotenv import load_dotenv

load_dotenv()
TMAP_KEY = os.getenv("TMAP_API_KEY")
KAKAO_KEY = os.getenv("KAKAO_REST_API_KEY")


async def _call_tmap_poi(params: dict) -> list[dict]:
    """TMAP POI API 호출 → 파싱된 POI 리스트 반환 (결과 없으면 빈 리스트)"""
    async with httpx.AsyncClient() as client:
        resp = await client.get("https://apis.openapi.sk.com/tmap/pois", params=params)
        resp.raise_for_status()
        data = resp.json()
    raw_pois = data.get("searchPoiInfo", {}).get("pois", {}).get("poi", [])
    if not raw_pois:
        return []

    # 검색 키워드 추출 (params에서)
    search_keyword = params.get("searchKeyword", "")

    results = []
    for poi in raw_pois[:20]:  # 더 많이 가져와서 정확 매칭 찾기
        results.append({
            "id": poi.get("id", ""),
            "name": poi.get("name", ""),
            "pnsLat": poi.get("pnsLat") or poi.get("noorLat"),
            "pnsLon": poi.get("pnsLon") or poi.get("noorLon"),
            "address": f"{poi.get('upperAddrName','')} {poi.get('middleAddrName','')} {poi.get('lowerAddrName','')} {poi.get('detailAddrName','')}".strip(),
        })

    # 정확히 일치하는 POI를 맨 앞으로
    exact_matches = [p for p in results if p["name"] == search_keyword]
    contains_matches = [p for p in results if search_keyword in p["name"] and p not in exact_matches]
    others = [p for p in results if p not in exact_matches and p not in contains_matches]
    sorted_results = exact_matches + contains_matches + others

    return sorted_results[:5]


async def _call_kakao_keyword(keyword: str, lat: Optional[float] = None, lng: Optional[float] = None) -> list[dict]:
    """Kakao Local 키워드 검색 → TMAP과 동일한 POI dict 형태로 반환"""
    if not KAKAO_KEY:
        return []
    params: dict = {"query": keyword, "size": 5}
    if lat and lng:
        params["y"] = str(lat)
        params["x"] = str(lng)
        params["sort"] = "accuracy"  # 정확도순 (거리순은 "distance")
    headers = {"Authorization": f"KakaoAK {KAKAO_KEY}"}
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.get(
                "https://dapi.kakao.com/v2/local/search/keyword.json",
                params=params,
                headers=headers,
            )
            resp.raise_for_status()
            data = resp.json()
    except Exception:
        return []

    results = []
    for doc in data.get("documents", []):
        results.append({
            "id": doc.get("id", ""),
            "name": doc.get("place_name", ""),
            "pnsLat": doc.get("y", ""),
            "pnsLon": doc.get("x", ""),
            "address": doc.get("road_address_name") or doc.get("address_name", ""),
            "source": "kakao",
        })
    return results


def _dedupe_pois(tmap_results: list[dict], kakao_results: list[dict]) -> list[dict]:
    """TMAP + Kakao 결과 병합. 이름이 동일한 POI는 TMAP 쪽 유지, Kakao 고유 결과를 뒤에 추가."""
    seen_names = {p["name"] for p in tmap_results}
    merged = list(tmap_results)
    for kp in kakao_results:
        if kp["name"] not in seen_names:
            seen_names.add(kp["name"])
            merged.append(kp)
    return merged[:10]


async def search_poi(
    keyword: str,
    user_lat: Optional[float] = None,
    user_lng: Optional[float] = None,
) -> list[dict]:
    """
    TMAP + Kakao Local 병행 검색 후 병합.
    TMAP: 1차 5km → 2차 전국 fallback
    Kakao: 정확도순 검색 (유사도 랭킹이 우수하여 정식 명칭 매칭에 강함)
    """
    import asyncio

    base_params = {
        "version": 1,
        "searchKeyword": keyword,
        "searchType": "all",
        "count": 20,
        "appKey": TMAP_KEY,
    }

    async def _tmap_search() -> list[dict]:
        if user_lat and user_lng:
            params_5km = {
                **base_params,
                "searchtypCd": "R",
                "centerLat": user_lat,
                "centerLon": user_lng,
                "radius": 5,
            }
            results = await _call_tmap_poi(params_5km)
            if results:
                return results
            params_nationwide = {
                **base_params,
                "searchtypCd": "R",
                "centerLat": user_lat,
                "centerLon": user_lng,
            }
            return await _call_tmap_poi(params_nationwide)
        return await _call_tmap_poi(base_params)

    # TMAP과 Kakao 동시 검색
    tmap_results, kakao_results = await asyncio.gather(
        _tmap_search(),
        _call_kakao_keyword(keyword, user_lat, user_lng),
    )

    merged = _dedupe_pois(tmap_results, kakao_results)
    return merged if merged else kakao_results[:5]


async def get_pedestrian_route(
    start_lat: float,
    start_lng: float,
    end_poi_id: str = "",
    end_lat: float = 0.0,
    end_lng: float = 0.0,
    end_name: str = "목적지",
    search_option: int = 0,
) -> dict:
    """TMAP 보행자 경로 계산 → GeoJSON 파싱 결과 반환"""
    body = {
        "startX": str(start_lng),
        "startY": str(start_lat),
        "reqCoordType": "WGS84GEO",
        "resCoordType": "WGS84GEO",
        "startName": "현재위치",
        "endName": end_name,
        "searchOption": search_option,
    }
    if end_poi_id:
        body["endPoiId"] = end_poi_id
    body["endX"] = str(end_lng)
    body["endY"] = str(end_lat)

    headers = {"appKey": TMAP_KEY, "Content-Type": "application/json"}
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            "https://apis.openapi.sk.com/tmap/routes/pedestrian",
            json=body,
            headers=headers,
        )
        resp.raise_for_status()
        data = resp.json()
    features = data.get("features", [])

    route_line = []
    turn_points = []
    total_distance_m = 0
    total_time_min = 0
    # TMAP GeoJSON 순서: SP → LineString → GP → LineString → ... → EP
    # 직전 LineString의 distance = 해당 Point까지의 구간 거리
    last_segment_distance = 0

    for f in features:
        geom = f.get("geometry", {})
        props = f.get("properties", {})
        geom_type = geom.get("type")

        if geom_type == "Point":
            coords = geom.get("coordinates", [])
            point_type = props.get("pointType", "")
            if point_type == "SP":
                total_distance_m = int(props.get("totalDistance", 0))
                total_time_min = int(props.get("totalTime", 0)) // 60
            turn_points.append({
                "lat": coords[1],
                "lng": coords[0],
                "turnType": props.get("turnType", 11),
                "description": props.get("description", ""),
                "nearPoiName": props.get("nearPoiName", ""),
                "intersectionName": props.get("intersectionName", ""),
                "pointType": point_type,
                "facilityType": str(props.get("facilityType", "")),
                "segment_distance_m": last_segment_distance,
            })
            last_segment_distance = 0

        elif geom_type == "LineString":
            last_segment_distance = int(props.get("distance", 0))
            for coord in geom.get("coordinates", []):
                route_line.append({"lat": coord[1], "lng": coord[0]})

    return {
        "route_line": route_line,
        "turn_points": turn_points,
        "total_distance_m": total_distance_m,
        "total_time_min": total_time_min,
    }
