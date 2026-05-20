"""user_preferences 테이블 CRUD 스키마.

장기적으론 Supabase Auth 의 auth.users.id 와 매핑되겠지만, 현재는 frontend
에서 발급한 device UUID 를 그대로 사용하는 anonymous 모드. RLS 정책은 도입 시
추가 — user_id == auth.uid().
"""
from typing import Optional
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
