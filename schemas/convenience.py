from pydantic import BaseModel


class ConvenienceRequest(BaseModel):
    message: str = ""      # 텍스트 검색 시
    category: str = ""     # 필터 탭 선택 시 (있으면 LLM 스킵)
    lat: float
    lng: float
    language: str = "ko"
    radius: int = 0        # 0이면 카테고리별 기본값 사용


class Facility(BaseModel):
    name: str
    distance_m: float
    lat: float
    lng: float
    address: str = ""
    phone: str = ""
    open_hours: str = ""
    extra: dict = {}       # 카테고리별 추가 필드 (wheelchair, locker 크기 등)


class ConvenienceResponse(BaseModel):
    speech: str
    category: str
    facilities: list[Facility]
    language: str
