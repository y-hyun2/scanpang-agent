from pydantic import BaseModel
from typing import List, Optional


class HalalRequest(BaseModel):
    category: str = ""       # prayer_time | qibla | restaurant | prayer_room
    message: str = ""        # 자유 텍스트 → LLM이 카테고리 추출
    lat: float
    lng: float
    language: str = "en"     # en(기본) / ko / ar / ja / zh
    halal_type: str = ""     # HALAL_MEAT | SEAFOOD | VEGGIE (식당 필터)
    radius: int = 0          # 0 = 카테고리별 기본값


class PrayerTimeData(BaseModel):
    fajr: str
    dhuhr: str
    asr: str
    maghrib: str
    isha: str
    hijri_date: str
    gregorian_date: str


class QiblaData(BaseModel):
    direction: float
    lat: float
    lng: float


class HalalRestaurant(BaseModel):
    restaurant_id: str
    name_ko: str
    name_en: str
    halal_type: str
    muslim_cooks_available: Optional[bool] = None
    no_alcohol_sales: Optional[bool] = None
    cuisine_type: List[str] = []
    menu_examples: List[str] = []
    distance_m: float
    lat: float
    lng: float
    address: str = ""
    phone: str = ""
    opening_hours: str = ""
    break_time: str = ""
    last_order: str = ""


class PrayerRoomDetail(BaseModel):
    name: str
    name_en: str = ""
    distance_m: float
    lat: float
    lng: float
    address: str = ""
    floor: str = ""
    open_hours: str = ""
    facilities: dict = {}
    availability_status: str = "unknown"


class HalalResponse(BaseModel):
    speech: str
    category: str
    language: str
    prayer_times: Optional[PrayerTimeData] = None
    qibla: Optional[QiblaData] = None
    restaurants: List[HalalRestaurant] = []
    prayer_rooms: List[PrayerRoomDetail] = []
