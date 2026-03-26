import httpx
import os
from typing import Optional
from dotenv import load_dotenv

load_dotenv()
TMAP_KEY = os.getenv("TMAP_API_KEY")


async def _call_tmap_poi(params: dict) -> list[dict]:
    """TMAP POI API 호출 → 파싱된 POI 리스트 반환 (결과 없으면 빈 리스트)"""
    async with httpx.AsyncClient() as client:
        resp = await client.get("https://apis.openapi.sk.com/tmap/pois", params=params)
        resp.raise_for_status()
        data = resp.json()
    raw_pois = data.get("searchPoiInfo", {}).get("pois", {}).get("poi", [])
    if not raw_pois:
        return []
    results = []
    for poi in raw_pois[:5]:
        results.append({
            "id": poi.get("id", ""),
            "name": poi.get("name", ""),
            "pnsLat": poi.get("pnsLat") or poi.get("noorLat"),
            "pnsLon": poi.get("pnsLon") or poi.get("noorLon"),
            "address": f"{poi.get('upperAddrName','')} {poi.get('middleAddrName','')} {poi.get('lowerAddrName','')} {poi.get('detailAddrName','')}".strip(),
        })
    return results


async def search_poi(
    keyword: str,
    user_lat: Optional[float] = None,
    user_lng: Optional[float] = None,
) -> list[dict]:
    """
    1차: 현재 위치 기준 5km 반경 내 거리순 검색
    결과 없으면 2차: 전국 거리순 fallback
    """
    base_params = {
        "version": 1,
        "searchKeyword": keyword,
        "searchType": "all",
        "count": 5,
        "appKey": TMAP_KEY,
    }

    if user_lat and user_lng:
        # 1차: 5km 반경
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

        # 2차 fallback: 전국 거리순 (radius 없음)
        params_nationwide = {
            **base_params,
            "searchtypCd": "R",
            "centerLat": user_lat,
            "centerLon": user_lng,
        }
        return await _call_tmap_poi(params_nationwide)

    # 위치 정보 없으면 키워드 검색만
    return await _call_tmap_poi(base_params)


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
