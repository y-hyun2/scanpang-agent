# PlaceAugmenting 프론트엔드 연동 계획

## Context
PlaceAugmenting(Android AR 앱)과 ScanPang Place Insight Agent(FastAPI 백엔드)를 연결하는 작업.
현재 프론트는 VWorld+Kakao로 건물명만 확인하고, 백엔드는 아직 호출하지 않는다.
두 프로젝트를 한 레포에서 관리하고 백엔드 API를 프론트에서 호출하도록 연동.

---

## Step 1: 프론트 레포 클론

```bash
cd /Users/yoonhyunyee/HUFS/Capstone/Scanpang_agent
git clone https://github.com/TaeHoon-Kim0902/PlaceAugmenting.git frontend/
```

결과 구조:
```
Scanpang_agent/
├── frontend/          ← PlaceAugmenting 클론
│   └── app/src/main/java/com/scanpang/placeaugmenting/
│       └── MainActivity.kt
├── agents/
├── rag/
├── main.py
└── ...
```

---

## Step 2: 백엔드 수정 (scanpang-agent)

### 2-1. schemas/place.py — building_name 파라미터 추가
```python
class PlaceRequest(BaseModel):
    place_id: str = ""
    building_name: Optional[str] = None      # 프론트가 보내는 건물명 (신규)
    user_message: str
    user_lat: float
    user_lng: float
    language: str = "en"
```

### 2-2. agents/place_insight_agent.py — 이름 → place_id 매핑 추가
```python
BUILDING_NAME_MAP = {
    "명동대성당": "myeongdong_cathedral",
    "롯데백화점 본점": "lotte_dept_myeongdong",
    "신세계백화점 본점": "shinsegae_myeongdong",
    "눈스퀘어": "noon_square_myeongdong",
    "명동예술극장": "myeongdong_art_theater",
    "N서울타워": "n_seoul_tower",
    "남산서울타워": "n_seoul_tower",
    "롯데시티호텔 명동": "lotte_city_hotel_myeongdong",
    "유네스코회관": "unesco_hall_myeongdong",
    "포스트타워": "post_tower_myeongdong",
    "서울중앙우체국": "post_tower_myeongdong",
    "대신파이낸스센터": "daishin_finance_center",
}

async def run_place_insight_agent(req: PlaceRequest) -> dict:
    place_id = req.place_id
    if not place_id and req.building_name:
        place_id = BUILDING_NAME_MAP.get(req.building_name, "")
    ...
```

---

## Step 3: 프론트 수정 (MainActivity.kt)

### 3-1. Retrofit 의존성 추가 (frontend/app/build.gradle.kts)
```kotlin
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
```

### 3-2. API 인터페이스 + 데이터 클래스 추가 (MainActivity.kt)
```kotlin
data class PlaceQueryRequest(
    val place_id: String = "",
    val building_name: String,
    val user_message: String = "What is this building?",
    val user_lat: Double,
    val user_lng: Double,
    val language: String = "en"
)

data class FloorInfo(val floor: String, val stores: List<String>)
data class ArOverlay(
    val name: String, val category: String,
    val floor_info: List<FloorInfo>,
    val open_hours: String, val closed_days: String,
    val homepage: String, val admission_fee: String
)
data class Docent(val speech: String, val follow_up_suggestions: List<String>)
data class PlaceQueryResponse(val ar_overlay: ArOverlay, val docent: Docent)

interface ScanpangApi {
    @POST("place/query")
    suspend fun queryPlace(@Body request: PlaceQueryRequest): PlaceQueryResponse
}
```

### 3-3. getFinalBuildingName() 성공 후 백엔드 호출 추가
```kotlin
val finalName = getFinalBuildingName(lat, lng)
val response = scanpangApi.queryPlace(
    PlaceQueryRequest(building_name = finalName, user_lat = lat, user_lng = lng)
)
// ar_overlay → bottom sheet UI 업데이트
// docent.speech → TTS 재생
```

---

## 연동 후 UI 매핑

| AR 화면 요소 | 백엔드 응답 필드 |
|---|---|
| 건물명 | `ar_overlay.name` |
| bottom sheet 영업시간 | `ar_overlay.open_hours` |
| bottom sheet 층별 매장 | `ar_overlay.floor_info` |
| TTS 음성 | `docent.speech` |
| 추천 질문 버튼 | `docent.follow_up_suggestions` |

---

## 수정 파일 목록
- `schemas/place.py`
- `agents/place_insight_agent.py`
- `frontend/app/build.gradle.kts`
- `frontend/app/src/main/java/com/scanpang/placeaugmenting/MainActivity.kt`

## 검증 방법
1. `uvicorn main:app --reload` 서버 실행
2. Postman: `POST /place/query` + `{"building_name": "명동대성당", "user_message": "...", "user_lat": 37.5628, "user_lng": 126.9875, "language": "en"}`
3. Android 앱 빌드 후 실기기에서 확인
