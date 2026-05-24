from pydantic import BaseModel


class StoreRequest(BaseModel):
    place_id: str       # 어느 건물 소속인지
    store_name: str     # 매장명 (소상공인 API의 bizesNm)
    language: str = "ko"  # 응답 언어 — "ko"(기본) / "en"
    # 사용자 현재 GPS — place_id 가 빈값(AR 탐색 안 한 상태)이거나 outdoor 매장일 때
    # Kakao Local API 검색 좌표 기준점으로 사용. 정확도 ↑ + 다른 지역 동명 매장 오매칭 ↓.
    lat: float | None = None
    lng: float | None = None


