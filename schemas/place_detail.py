"""
schemas/place_detail.py
통합 PlaceDetailScreen 백엔드 응답 모델.

store_details row + 카테고리별 details JSONB + 파싱된 is_open_now 를 합쳐
프론트가 한 번에 다 받아 그릴 수 있도록.
"""
from pydantic import BaseModel
from typing import List, Optional, Any


class PlaceDetailRequest(BaseModel):
    id: str   # store_details.id (place_id__store_name)


class PlaceDetailResponse(BaseModel):
    # ── 기본 식별 + 위치 ──
    id: str
    store_name: str
    place_id: Optional[str] = None
    lat: Optional[float] = None
    lng: Optional[float] = None

    # ── 화면 표시 메타 ──
    category: Optional[str] = None          # Kakao raw category_name ("음식점 > 한식")
    category_key: Optional[str] = None      # 분류기 키 (cafe, restaurant, ...)
    addr: Optional[str] = None
    phone: Optional[str] = None
    floor: Optional[str] = None
    homepage: Optional[str] = None
    place_url: Optional[str] = None         # Kakao Place URL

    # ── 영업 정보 ──
    open_hours: Optional[str] = None
    closed_days: Optional[str] = None
    is_open_now: Optional[bool] = None      # KST 현재 시각 기준 파싱 결과

    # ── 이미지 ──
    image_urls: List[str] = []              # 전체 갤러리 (검색 응답과 달리 전부)

    # ── 카테고리별 세부 (details JSONB 펼친 dict) ──
    # 예: cafe → {"menu": [...]}, restaurant → {"menu": [...], "last_order": "..."},
    #      hospital → {"departments": [...]}, exchange → {"rates": [...]} 등
    details: dict[str, Any] = {}

    # ── 메타 ──
    source: Optional[str] = None            # naver_place / kakao_basic / tour_api / ...
    last_updated: Optional[str] = None      # ISO 문자열
