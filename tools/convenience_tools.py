"""
convenience_tools.py
편의시설 검색 툴 모음.
- Kakao 카테고리/키워드 검색 (편의점, 약국, 병원 등)
- 서울시 Open API (공중화장실, 물품보관함)
- 수동 JSON (기도실)
"""

import json
import os
from math import atan2, cos, radians, sin, sqrt

import httpx
from dotenv import load_dotenv

load_dotenv()

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")
SEOUL_RESTROOM_API_KEY = os.getenv("SEOUL_RESTROOM_API_KEY", "")
SEOUL_LOCKER_API_KEY = os.getenv("SEOUL_LOCKER_API_KEY", "")

PRAYER_ROOMS_PATH = os.path.join(os.path.dirname(__file__), "..", "rag", "data", "prayer_rooms.json")

# 카테고리별 Kakao 코드 및 기본 반경(m)
CATEGORY_CONFIG = {
    "convenience_store": {"code": "CS2", "radius": 300},
    "cafe":              {"code": "CE7", "radius": 300},
    "restaurant":        {"code": "FD6", "radius": 300},
    "pharmacy":          {"code": "PM9", "radius": 500},
    "hospital":          {"code": "HP8", "radius": 500},
    "bank":              {"code": "BK9", "radius": 500},
    "atm":               {"code": "BK9", "radius": 300},
    "shopping":          {"code": "MT1", "radius": 500},
    "parking":           {"code": "PK6", "radius": 300},
    "subway":            {"code": "SW8", "radius": 1000},
    "tourist_info":      {"code": "AT4", "radius": 1000},
}

DEFAULT_RADIUS = {
    "exchange":    500,
    "restroom":    300,
    "locker":      1000,
    "prayer_room": 1000,
}


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """두 좌표 사이의 거리(m) 계산"""
    R = 6371000
    dlat = radians(lat2 - lat1)
    dlng = radians(lng2 - lng1)
    a = sin(dlat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlng / 2) ** 2
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))


def get_radius(category: str, custom_radius: int) -> int:
    if custom_radius > 0:
        return custom_radius
    cfg = CATEGORY_CONFIG.get(category)
    if cfg:
        return cfg["radius"]
    return DEFAULT_RADIUS.get(category, 500)


async def kakao_category_search(category: str, lat: float, lng: float, radius: int) -> list[dict]:
    """Kakao 카테고리 검색 → 시설 목록 반환"""
    cfg = CATEGORY_CONFIG.get(category)
    if not cfg:
        return []

    url = "https://dapi.kakao.com/v2/local/search/category.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {
        "category_group_code": cfg["code"],
        "x": str(lng),
        "y": str(lat),
        "radius": radius,
        "sort": "distance",
        "size": 15,
    }
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers, params=params)
        resp.raise_for_status()
        docs = resp.json().get("documents", [])

    return [
        {
            "name": d.get("place_name", ""),
            "distance_m": float(d.get("distance", 0)),
            "lat": float(d.get("y", lat)),
            "lng": float(d.get("x", lng)),
            "address": d.get("road_address_name", "") or d.get("address_name", ""),
            "phone": d.get("phone", ""),
            "open_hours": "",
            "extra": {},
        }
        for d in docs
    ]


async def kakao_keyword_search(keyword: str, lat: float, lng: float, radius: int) -> list[dict]:
    """Kakao 키워드 검색 (환전소 등 카테고리 코드 없는 경우)"""
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {
        "query": keyword,
        "x": str(lng),
        "y": str(lat),
        "radius": radius,
        "sort": "distance",
        "size": 15,
    }
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers, params=params)
        resp.raise_for_status()
        docs = resp.json().get("documents", [])

    return [
        {
            "name": d.get("place_name", ""),
            "distance_m": float(d.get("distance", 0)),
            "lat": float(d.get("y", lat)),
            "lng": float(d.get("x", lng)),
            "address": d.get("road_address_name", "") or d.get("address_name", ""),
            "phone": d.get("phone", ""),
            "open_hours": "",
            "extra": {},
        }
        for d in docs
    ]


async def seoul_restroom_search(lat: float, lng: float, radius: int) -> list[dict]:
    """서울시 공중화장실 Open API (OA-22586)"""
    if not SEOUL_RESTROOM_API_KEY:
        return []

    url = f"http://openapi.seoul.go.kr:8088/{SEOUL_RESTROOM_API_KEY}/json/SearchPublicToiletPOIService/1/1000/"
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        data = resp.json()

    rows = data.get("SearchPublicToiletPOIService", {}).get("row", [])
    results = []
    for row in rows:
        try:
            r_lat = float(row.get("Y_WGS84") or 0)
            r_lng = float(row.get("X_WGS84") or 0)
        except (ValueError, TypeError):
            continue
        if r_lat == 0 or r_lng == 0:
            continue
        dist = haversine_m(lat, lng, r_lat, r_lng)
        if dist <= radius:
            results.append({
                "name": row.get("FNAME", "공중화장실"),
                "distance_m": round(dist, 1),
                "lat": r_lat,
                "lng": r_lng,
                "address": row.get("ANAME", ""),
                "phone": "",
                "open_hours": "",
                "extra": {},
            })

    return sorted(results, key=lambda x: x["distance_m"])


async def seoul_locker_search(lat: float, lng: float, radius: int) -> list[dict]:
    """서울 교통공사 물품보관함 Open API (OA-22731)"""
    if not SEOUL_LOCKER_API_KEY:
        return []

    url = f"http://openapi.seoul.go.kr:8088/{SEOUL_LOCKER_API_KEY}/json/subwayLockerInfo/1/1000/"
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        data = resp.json()

    rows = data.get("subwayLockerInfo", {}).get("row", [])
    results = []
    for row in rows:
        try:
            r_lat = float(row.get("위도") or 0)
            r_lng = float(row.get("경도") or 0)
        except (ValueError, TypeError):
            continue
        if r_lat == 0 or r_lng == 0:
            continue
        dist = haversine_m(lat, lng, r_lat, r_lng)
        if dist <= radius:
            station = row.get("역명", "")
            location = row.get("설치위치", "")
            results.append({
                "name": f"{station}역 물품보관함" if station else "물품보관함",
                "distance_m": round(dist, 1),
                "lat": r_lat,
                "lng": r_lng,
                "address": location,
                "phone": "",
                "open_hours": "",
                "extra": {
                    "location_detail": location,
                    "small": row.get("소형", ""),
                    "medium": row.get("중형", ""),
                    "large": row.get("대형", ""),
                },
            })

    return sorted(results, key=lambda x: x["distance_m"])


def prayer_room_search(lat: float, lng: float, radius: int) -> list[dict]:
    """수동 JSON 기반 기도실 검색"""
    try:
        with open(PRAYER_ROOMS_PATH, encoding="utf-8") as f:
            rooms = json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return []

    results = []
    for room in rooms:
        try:
            r_lat = float(room.get("lat", 0))
            r_lng = float(room.get("lng", 0))
        except (ValueError, TypeError):
            continue
        dist = haversine_m(lat, lng, r_lat, r_lng)
        if dist <= radius:
            results.append({
                "name": room.get("name", "기도실"),
                "distance_m": round(dist, 1),
                "lat": r_lat,
                "lng": r_lng,
                "address": room.get("address", ""),
                "phone": room.get("phone", ""),
                "open_hours": room.get("open_hours", ""),
                "extra": {},
            })

    return sorted(results, key=lambda x: x["distance_m"])
