from typing import List, Literal, Optional

from pydantic import BaseModel


class PlaceRequest(BaseModel):
    heading: float                       # ARCore geospatialPose.heading (0=북, 90=동)
    user_lat: float
    user_lng: float
    user_alt: float = 0.0                # ARCore geospatialPose.altitude (m)
    pitch: float = 0.0                   # 카메라 상하 각도 (도, +위 -아래)
    user_message: str = "이 건물에 대해 알려줘"
    language: str = "ko"
    bd_mgt_sn: Optional[str] = None


class StoreItem(BaseModel):
    name: str = ""
    category: str = ""


class FloorInfo(BaseModel):
    floor: Optional[str] = None
    stores: List[StoreItem] = []


class ArOverlay(BaseModel):
    name: str
    category: str
    floor_info: List[FloorInfo]
    halal_info: str
    image_url: str
    homepage: str
    open_hours: str
    closed_days: str
    parking_info: str
    admission_fee: str
    # AR 건물 floating 패널의 chip 표시용. 비건/할랄 정보처럼 단순 문자열.
    address: str = ""
    phone: str = ""
    # store_details cache key 의 building 파트. frontend 가 층별 매장 탭 시
    # /place/store(place_id=ufid) 호출 → store_details row 의 place_id 가
    # 일관되게 채워져 fetcher 결과가 동일 행에 누적된다.
    ufid: str = ""
    is_estimated: bool = False
    status: Literal["partial", "ready"] = "ready"
    floor_info_loading: bool = False
    coverage_rate: Optional[float] = None
    last_updated: Optional[str] = None


class Docent(BaseModel):
    speech: str
    follow_up_suggestions: List[str]


class PlaceResponse(BaseModel):
    ar_overlay: ArOverlay
    docent: Docent
