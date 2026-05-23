"""user_preferences 테이블 CRUD 스키마.

user_id 는 Supabase Auth 의 auth.users.id (Google/Kakao OAuth uid).
RLS 정책은 도입 시 user_id == auth.uid() 로 처리.
"""
from typing import Optional, List
from pydantic import BaseModel
from uuid import UUID


class UserPreferencesUpsertRequest(BaseModel):
    user_id: UUID
    display_name: Optional[str] = None
    language: Optional[str] = None              # 'ko' | 'en' | 'ar' 등
    value_added: Optional[str] = None           # 'HALAL' | 'VEGAN' | 'GENERAL'


class UserPreferencesResponse(BaseModel):
    user_id: UUID
    display_name: Optional[str] = None
    language: Optional[str] = None
    value_added: Optional[str] = None
    saved_places: list = []
    search_history: list = []
    recently_viewed_places: list = []


class SavedPlacesUpdateRequest(BaseModel):
    """saved_places 전체 list 교체. frontend SavedPlacesStore 가 변경될 때 호출."""
    items: List[dict] = []  # [{id, name, category, distanceLine, tags, target, savedOrder}]


class SearchHistoryUpdateRequest(BaseModel):
    """search_history 전체 list 교체. 최근 검색어(string) 목록."""
    items: List[str] = []


class RecentlyViewedUpdateRequest(BaseModel):
    """recently_viewed_places 전체 list 교체. frontend RecentlyViewedStore 변경 시 호출.
    형식: [{id, name, category, target, viewedAt, lat, lng}]. 길이 캡(20)은 frontend 가 관리."""
    items: List[dict] = []


class InquirySubmitRequest(BaseModel):
    """1:1 문의 — ContactScreen 의 카테고리/제목/내용."""
    user_id: UUID
    category: str
    title: str
    content: str


class InquirySubmitResponse(BaseModel):
    id: int
    user_id: UUID
    status: str
