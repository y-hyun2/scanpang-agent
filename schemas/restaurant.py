from pydantic import BaseModel
from typing import List, Optional


class RestaurantDetailRequest(BaseModel):
    name: str


class GeneralRestaurantMenu(BaseModel):
    name_ko: str = ""
    name_en: str = ""
    price_krw: int = 0


class GeneralRestaurantDetail(BaseModel):
    restaurant_id: str = ""
    name_ko: str = ""
    name_en: str = ""
    cuisine_type: List[str] = []
    food_keywords: List[str] = []
    menu_examples: List[GeneralRestaurantMenu] = []
    short_description_ko: str = ""
    address: str = ""
    phone: str = ""
    opening_hours: str = ""
    break_time: str = ""
    last_order: str = ""
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    source: str = "general"  # "general" | "halal"
    is_open_today: bool = True
