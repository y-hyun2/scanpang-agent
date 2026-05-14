"""
schemas/search.py
매장 통합 검색 요청·응답 모델.
"""

from pydantic import BaseModel
from typing import List, Optional


class SearchRequest(BaseModel):
    query: str           # 사용자가 검색창에 입력한 키워드
    limit: int = 50      # 최대 결과 개수


class SearchResultItem(BaseModel):
    id: str                              # store_details.id (place_id__store_name)
    store_name: str
    category: Optional[str] = None       # Kakao raw category_name
    category_key: Optional[str] = None   # 분류기 키 (cafe, restaurant, ...)
    addr: Optional[str] = None
    phone: Optional[str] = None
    place_id: Optional[str] = None       # 건물 ufid
    lat: Optional[float] = None
    lng: Optional[float] = None
    floor: Optional[str] = None
    image_url: Optional[str] = None      # 첫 번째 이미지만
    # 백엔드에서 open_hours 를 파싱해 현재 KST 기준으로 판정.
    # None = 영업시간 정보 자체가 없거나 포맷 판정 불가 → 클라이언트는 "정보 없음"으로 처리.
    is_open_now: Optional[bool] = None


class SearchResponse(BaseModel):
    query: str
    count: int
    results: List[SearchResultItem]
