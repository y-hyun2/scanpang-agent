"""
convenience_tools.py
편의시설 검색 툴 모음.
- Kakao 카테고리/키워드 검색 (편의점, 약국, 병원 등)
- 서울시 Open API (공중화장실, 물품보관함)
- 수동 JSON (기도실)
"""

import asyncio
import json
import os
from math import atan2, cos, radians, sin, sqrt

import httpx
from dotenv import load_dotenv

load_dotenv()

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")
SEOUL_RESTROOM_API_KEY = os.getenv("SEOUL_RESTROOM_API_KEY", "")
SEOUL_LOCKER_API_KEY = os.getenv("SEOUL_LOCKER_API_KEY", "")
TMAP_API_KEY = os.getenv("TMAP_API_KEY", "")

PRAYER_ROOMS_PATH = os.path.join(os.path.dirname(__file__), "..", "rag", "data", "prayer_rooms.json")

# 카테고리별 Kakao 그룹 코드 및 기본 반경(m).
# Kakao 그룹 코드가 없는 카테고리(exchange, restroom, locker, prayer_room)는
# convenience_agent에서 별도 라우팅 (kakao_keyword / seoul_openapi / static_json).
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
    "tourist":           {"code": "AT4", "radius": 1000},   # 프론트 별칭
    "accommodation":     {"code": "AD5", "radius": 1000},   # 호텔/숙박
    "cultural":          {"code": "CT1", "radius": 1000},   # 문화시설(영화·박물관·미술관)
}

DEFAULT_RADIUS = {
    "exchange":    500,
    "restroom":    300,
    "locker":      1000,
    "prayer_room": 1000,
}


def _split_pipe(v: str | None) -> list[str]:
    """'남자|여자|' → ['남자','여자']. 공백/None은 빈 리스트."""
    if not v or not isinstance(v, str):
        return []
    return [p.strip() for p in v.split("|") if p.strip()]


def _clean_pipe(v: str | None) -> str:
    """'기타|05:00~23:00|' → '05:00~23:00'. 첫 토큰이 분류 라벨이면 떼고
    실제 시간 텍스트만. 토큰 1개면 그대로."""
    parts = _split_pipe(v)
    if not parts:
        return ""
    if len(parts) == 1:
        return parts[0]
    # 첫 토큰이 '기타','정시','상시' 같은 분류라면 두 번째 이후 우선
    label = {"기타", "정시", "상시", "수시"}
    if parts[0] in label:
        return " ".join(parts[1:])
    return " ".join(parts)


def _restroom_extra(row: dict) -> dict:
    """mgisToiletPoi row → 화장실 UI 필드. building_type/manager 류는 제외."""
    sexes      = _split_pipe(row.get("VALUE_04"))   # ['남자','여자']
    facilities = _split_pipe(row.get("VALUE_06"))   # ['기저귀교환대','비상벨',...]
    return {
        "open_type":          (_split_pipe(row.get("VALUE_01")) or [""])[0],  # '공공개방'
        "days_closed":        (_split_pipe(row.get("VALUE_03")) or [""])[0],  # '일요일'
        "has_male":           "남자" in sexes,
        "has_female":         "여자" in sexes,
        "has_disabled":       bool(_split_pipe(row.get("VALUE_05"))),
        "has_diaper_table":   any("기저귀" in f for f in facilities),
        "has_emergency_bell": any("비상" in f or "벨" in f for f in facilities),
        "extra_facilities":   facilities,  # 위 플래그에 안 잡힌 항목 노출용
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


async def _tmap_open_hours(name: str, lat: float, lng: float) -> str:
    """TMAP POI 검색으로 운영시간 조회. 없으면 빈 문자열 반환."""
    if not TMAP_API_KEY:
        return ""
    headers = {"appKey": TMAP_API_KEY, "Accept": "application/json"}
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            r = await client.get(
                "https://apis.openapi.sk.com/tmap/pois",
                headers=headers,
                params={
                    "version": 1,
                    "searchKeyword": name,
                    "centerLat": lat,
                    "centerLon": lng,
                    "radius": 200,
                    "count": 3,
                },
            )
            pois = r.json().get("searchPoiInfo", {}).get("pois", {}).get("poi", [])
            if not pois:
                return ""
            poi_id = pois[0].get("id", "")
            if not poi_id:
                return ""

            r2 = await client.get(
                f"https://apis.openapi.sk.com/tmap/pois/{poi_id}",
                headers=headers,
                params={"version": 1},
            )
            add_info = r2.json().get("poiDetailInfo", {}).get("additionalInfo", "")
            if add_info and "[영업시간]" in add_info:
                return add_info.split("[영업시간]")[1].split(";")[0].strip()
    except Exception:
        pass
    return ""


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
    """서울시 공중화장실 위치정보 (OA-22586, service=mgisToiletPoi).
    응답 필드: OBJECTID, ADDR_NEW, ADDR_OLD, COORD_X, COORD_Y, CONTS_NAME(건물명),
              GU_NAME, TEL_NO, VALUE_01(유형), VALUE_02(개방시간), ..."""
    if not SEOUL_RESTROOM_API_KEY:
        return []

    url = f"http://openapi.seoul.go.kr:8088/{SEOUL_RESTROOM_API_KEY}/json/mgisToiletPoi/1/1000/"
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        data = resp.json()

    rows = data.get("mgisToiletPoi", {}).get("row", [])
    results = []
    for row in rows:
        try:
            r_lat = float(row.get("COORD_Y") or 0)
            r_lng = float(row.get("COORD_X") or 0)
        except (ValueError, TypeError):
            continue
        if r_lat == 0 or r_lng == 0:
            continue
        dist = haversine_m(lat, lng, r_lat, r_lng)
        if dist <= radius:
            results.append({
                "name":       row.get("CONTS_NAME") or "공중화장실",
                "distance_m": round(dist, 1),
                "lat":        r_lat,
                "lng":        r_lng,
                "address":    row.get("ADDR_NEW") or row.get("ADDR_OLD") or "",
                "phone":      row.get("TEL_NO") or "",
                "open_hours": _clean_pipe(row.get("VALUE_02")),
                "extra":      _restroom_extra(row),
            })

    results = sorted(results, key=lambda x: x["distance_m"])[:5]

    # TMAP fallback: 상위 5개 운영시간 병렬 조회
    hours_list = await asyncio.gather(
        *[_tmap_open_hours(r["name"], r["lat"], r["lng"]) for r in results]
    )
    for r, hours in zip(results, hours_list):
        if hours:
            r["open_hours"] = hours

    return results


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
