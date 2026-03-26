#!/bin/bash

# Navigation Agent 프로젝트 세팅 스크립트
# 실행: bash setup.sh

echo "📁 프로젝트 폴더 생성 중..."

mkdir -p agents tools schemas

echo "📦 패키지 설치 중..."
pip install fastapi uvicorn langchain langchain-openai httpx pydantic python-dotenv

echo "📄 파일 생성 중..."

# .env
cat > .env << 'EOF'
OPENAI_API_KEY=
TMAP_API_KEY=
EOF

# main.py
cat > main.py << 'EOF'
from fastapi import FastAPI
from schemas.navigation import NavRequest
from agents.navigation_agent import run_navigation_agent

app = FastAPI(title="ScanPang Navigation API")

@app.post("/navigation/query")
async def navigation_query(req: NavRequest):
    return await run_navigation_agent(req)
EOF

# schemas/navigation.py
cat > schemas/navigation.py << 'EOF'
from pydantic import BaseModel
from typing import List, Optional

class NavRequest(BaseModel):
    message: str
    lat: float
    lng: float

class LatLng(BaseModel):
    lat: float
    lng: float

class TurnPoint(BaseModel):
    lat: float
    lng: float
    turnType: int
    description: str
    nearPoiName: str
    pointType: str
    facilityType: str

class Destination(BaseModel):
    lat: float
    lng: float
    name: str

class ArCommand(BaseModel):
    type: str
    route_line: List[LatLng]
    turn_points: List[TurnPoint]
    destination: Destination
    total_distance_m: int
    total_time_min: int

class NavResponse(BaseModel):
    speech: str
    ar_command: ArCommand
EOF

# tools/navigation_tools.py
cat > tools/navigation_tools.py << 'EOF'
import httpx
import os
from typing import Optional

TMAP_KEY = os.getenv("TMAP_API_KEY")

async def search_poi(keyword: str, user_lat: Optional[float] = None, user_lng: Optional[float] = None) -> dict:
    """목적지 이름 → POI 정보 (id, pnsLat/pnsLon, noorLat/noorLon)"""
    params = {
        "version": 1,
        "searchKeyword": keyword,
        "searchType": "all",
        "count": 5,
        "appKey": TMAP_KEY,
    }
    if user_lat and user_lng:
        params.update({
            "searchtypCd": "R",
            "centerLon": user_lng,
            "centerLat": user_lat,
            "radius": 1,
        })
    async with httpx.AsyncClient() as client:
        resp = await client.get("https://apis.openapi.sk.com/tmap/pois", params=params)
        resp.raise_for_status()
    data = resp.json()
    pois = data["searchPoiInfo"]["pois"]["poi"]
    if not pois:
        return {}
    poi = pois[0]
    return {
        "id": poi.get("id", ""),
        "name": poi.get("name", keyword),
        "pnsLat": poi.get("pnsLat") or poi.get("noorLat"),
        "pnsLon": poi.get("pnsLon") or poi.get("noorLon"),
        "noorLat": poi.get("noorLat"),
        "noorLon": poi.get("noorLon"),
        "address": f"{poi.get('upperAddrName','')} {poi.get('middleAddrName','')} {poi.get('lowerAddrName','')} {poi.get('detailAddrName','')}".strip(),
    }


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
                "pointType": point_type,
                "facilityType": str(props.get("facilityType", "")),
            })

        elif geom_type == "LineString":
            for coord in geom.get("coordinates", []):
                route_line.append({"lat": coord[1], "lng": coord[0]})

    return {
        "route_line": route_line,
        "turn_points": turn_points,
        "total_distance_m": total_distance_m,
        "total_time_min": total_time_min,
    }
EOF

# agents/navigation_agent.py
cat > agents/navigation_agent.py << 'EOF'
import os
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from tools.navigation_tools import search_poi, get_pedestrian_route
from schemas.navigation import NavRequest

load_dotenv()

llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)

SYSTEM_PROMPT = """You are a navigation assistant for a solo traveler in Seoul, Korea.
Your responses will be read aloud via TTS, so keep them to 1-2 sentences maximum.

Rules:
- Use landmark names (nearPoiName) instead of distances when available
  GOOD: "Turn right at GS25 Myeongdong"
  BAD: "Turn right in 30 meters"
- If facilityType is 125/126/127, mention stairs/overpass/underpass
- If searchOption 30 was used, say "Stair-free route"
- Always respond in the same language as the user's message
- Arabic messages → respond in English
"""

async def run_navigation_agent(req: NavRequest) -> dict:
    # 1. POI 검색
    poi = await search_poi(req.message, req.lat, req.lng)
    if not poi:
        return {
            "speech": "Sorry, I couldn't find that destination. Please try again.",
            "ar_command": None,
        }

    # 2. 경로 계산
    route = await get_pedestrian_route(
        start_lat=req.lat,
        start_lng=req.lng,
        end_poi_id=poi.get("id", ""),
        end_lat=float(poi["pnsLat"]),
        end_lng=float(poi["pnsLon"]),
        end_name=poi["name"],
        search_option=0,
    )

    # 3. LLM으로 자연어 안내 생성
    first_turn = next(
        (tp for tp in route["turn_points"] if tp["pointType"] == "GP"),
        None,
    )
    context = f"""
User message: {req.message}
Destination: {poi['name']} ({poi.get('address', '')})
Total distance: {route['total_distance_m']}m
Total time: {route['total_time_min']} minutes
First instruction: {first_turn['description'] if first_turn else 'Head to destination'}
Landmark at first turn: {first_turn['nearPoiName'] if first_turn else ''}
Turn type: {first_turn['turnType'] if first_turn else 11}
Facility type: {first_turn['facilityType'] if first_turn else ''}
"""
    response = llm.invoke([
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": context},
    ])
    speech = response.content

    return {
        "speech": speech,
        "ar_command": {
            "type": "start_navigation",
            "route_line": route["route_line"],
            "turn_points": route["turn_points"],
            "destination": {
                "lat": float(poi["pnsLat"]),
                "lng": float(poi["pnsLon"]),
                "name": poi["name"],
            },
            "total_distance_m": route["total_distance_m"],
            "total_time_min": route["total_time_min"],
        },
    }
EOF

echo ""
echo "✅ 세팅 완료!"
echo ""
echo "다음 단계:"
echo "  1. .env 파일에 API 키 입력"
echo "  2. uvicorn main:app --reload --port 8000"
echo "  3. Postman으로 POST http://localhost:8000/navigation/query 테스트"
echo ""
echo "Postman body:"
echo '  {"message": "캄풍쿠 어떻게 가?", "lat": 37.5636, "lng": 126.9822}'
