import json
import os
from datetime import datetime
from schemas.restaurant import GeneralRestaurantDetail, GeneralRestaurantMenu

_BASE = os.path.join(os.path.dirname(__file__), "../rag/data")
GENERAL_PATHS = [
    os.path.join(_BASE, "myeongdong_general_restaurants.json"),
    os.path.join(_BASE, "yongin_general_restaurants.json"),
]
HALAL_PATH = os.path.join(_BASE, "myeongdong_restaurants.json")

_DAY_LABELS = {"mon": "월", "tue": "화", "wed": "수", "thu": "목", "fri": "금", "sat": "토", "sun": "일"}
_DAY_KEYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]


def _is_open_today(hours_dict: dict | None) -> bool:
    if not hours_dict:
        return True
    today_key = _DAY_KEYS[datetime.now().weekday()]
    val = hours_dict.get(today_key, "")
    return bool(val) and val != "closed"


def _format_hours(hours_dict: dict | None) -> str:
    if not hours_dict:
        return ""
    parts = []
    for key, label in _DAY_LABELS.items():
        val = hours_dict.get(key)
        if val == "closed":
            parts.append(f"{label} 휴무")
        elif val:
            parts.append(f"{label} {val}")
    return ", ".join(parts)


def _load_json(path: str) -> list:
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def get_restaurant_detail(name: str) -> GeneralRestaurantDetail | None:
    # 일반 식당 JSON 검색 (지역별 파일 순회)
    for path in GENERAL_PATHS:
        for r in _load_json(path):
            if r.get("name_ko") == name or r.get("name_en") == name:
                return GeneralRestaurantDetail(
                    restaurant_id=r.get("restaurant_id", ""),
                    name_ko=r.get("name_ko", ""),
                    name_en=r.get("name_en", ""),
                    cuisine_type=r.get("cuisine_type", []),
                    food_keywords=r.get("food_keywords", []),
                    menu_examples=[
                        GeneralRestaurantMenu(
                            name_ko=m.get("name_ko", ""),
                            name_en=m.get("name_en", ""),
                            price_krw=m.get("price_krw", 0),
                        )
                        for m in r.get("menu_examples", [])
                    ],
                    short_description_ko=r.get("short_description_ko", ""),
                    address=r.get("address", ""),
                    phone=r.get("phone", ""),
                    opening_hours=_format_hours(r.get("opening_hours")),
                    break_time=_format_hours(r.get("break_time")),
                    last_order=_format_hours(r.get("last_order")),
                    latitude=r.get("latitude"),
                    longitude=r.get("longitude"),
                    source="general",
                    is_open_today=_is_open_today(r.get("opening_hours")),
                )

    # 할랄 식당 JSON 검색
    for r in _load_json(HALAL_PATH):
        if r.get("name_ko") == name or r.get("name_en") == name:
            menus = r.get("menu_examples", [])
            return GeneralRestaurantDetail(
                restaurant_id=r.get("restaurant_id", ""),
                name_ko=r.get("name_ko", ""),
                name_en=r.get("name_en", ""),
                cuisine_type=r.get("cuisine_type", []),
                food_keywords=r.get("food_keywords", []),
                menu_examples=[
                    GeneralRestaurantMenu(
                        name_ko=m.get("name_ko", m) if isinstance(m, dict) else m,
                        name_en=m.get("name_en", "") if isinstance(m, dict) else "",
                        price_krw=m.get("price_krw", 0) if isinstance(m, dict) else 0,
                    )
                    for m in menus
                ],
                short_description_ko=r.get("short_description_ko", ""),
                address=r.get("address", ""),
                phone=r.get("phone", ""),
                opening_hours=r.get("opening_hours", ""),
                break_time=r.get("break_time", ""),
                last_order=r.get("last_order", ""),
                latitude=r.get("lat"),
                longitude=r.get("lng"),
                source="halal",
                is_open_today=True,
            )

    return None
