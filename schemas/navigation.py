from pydantic import BaseModel
from typing import List, Optional


# ── 1단계: /navigation/search ─────────────────────────────────────────────────

class NavRequest(BaseModel):
    message: str
    lat: float
    lng: float


class PoiCandidate(BaseModel):
    poi_id: str
    name: str
    address: str
    pns_lat: float
    pns_lon: float
    recommended: bool = False   # LLM이 추천한 후보에만 True


class SearchResponse(BaseModel):
    speech: str                 # "캄풍쿠를 찾았어요. 서울 중구 명동 맞나요?"
    candidates: List[PoiCandidate]
    intent: str                 # "specific_place" | "category_search"
    language: str               # "ko" | "en" | "ar"


# ── 2단계: /navigation/route ──────────────────────────────────────────────────

class DestinationInput(BaseModel):
    poi_id: str
    pns_lat: float
    pns_lon: float
    name: str


class RouteRequest(BaseModel):
    lat: float
    lng: float
    destination: DestinationInput
    language: str = "ko"        # SearchResponse에서 그대로 전달


# ── Route 응답 ────────────────────────────────────────────────────────────────

class LatLng(BaseModel):
    lat: float
    lng: float


class TurnPoint(BaseModel):
    lat: float
    lng: float
    turnType: int
    description: str
    nearPoiName: str
    intersectionName: str
    pointType: str
    facilityType: str
    segment_distance_m: int
    speech: str = ""


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
    ar_command: Optional[ArCommand]
