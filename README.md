# ScanPang — 무슬림 관광객을 위한 AR 관광 플랫폼

서울(명동)을 방문하는 무슬림 관광객을 위한 AR 기반 관광 안내 서비스.
건물 인식, AR 길찾기, 할랄 식당/기도실 검색, 기도 시간/키블라 방향 안내를 제공합니다.

---

## 프로젝트 구조

```
Scanpang_agent/
│
├── main.py                         # FastAPI 서버 진입점 (6개 엔드포인트)
├── requirements.txt
├── .env                            # API 키 (.gitignore)
│
├── agents/                         # 백엔드 AI 에이전트
│   ├── place_insight_agent.py      #   건물 인식 → AR 오버레이 + 도슨트 TTS
│   ├── navigation_agent.py         #   AR 길찾기 (검색 + 경로 + 턴별 TTS)
│   ├── convenience_agent.py        #   주변 편의시설 (15개 카테고리)
│   └── halal_agent.py              #   할랄 식당 / 기도실 / 기도시간 / 키블라
│
├── tools/                          # 외부 API 래퍼 + 데이터 도구
│   ├── building_raycast.py         #   VWorld 3D 폴리곤 STRtree 레이캐스팅
│   ├── navigation_tools.py         #   TMAP POI + 보행자 경로
│   ├── convenience_tools.py        #   Kakao / 서울시 Open API
│   ├── halal_tools.py              #   Aladhan + 할랄 JSON 검색
│   ├── place_tools.py              #   Kakao Local
│   └── store_tools.py              #   매장 상세 + Chroma 캐싱
│
├── schemas/                        # Pydantic 요청/응답 모델
│   ├── place.py                    #   ArOverlay, Docent, FloorInfo
│   ├── navigation.py               #   TurnPoint, ArCommand, LatLng
│   ├── convenience.py              #   Facility, ConvenienceResponse
│   ├── halal.py                    #   PrayerTimeData, HalalRestaurant
│   └── store.py                    #   StoreDetail
│
├── rag/                            # 데이터 구축 스크립트 + 정적 데이터
│   ├── build_place_db.py           #   명동 10개 건물 → Chroma 구축
│   ├── build_vworld_buildings.py   #   VWorld 건물 폴리곤 사전 적재
│   └── data/
│       ├── vworld_buildings.json   #     명동 2km 건물 29,831건 (12.6MB)
│       ├── myeongdong_restaurants.json  # 할랄 식당 20개
│       ├── prayer_rooms.json       #     기도실 10개
│       └── places_manual.json      #     건물 수동 보완 데이터
│
├── chroma_db/                      # ChromaDB 벡터 저장소
│   ├── place_info/                 #   건물 기본정보 + 층별 매장
│   └── store_detail/               #   개별 매장 상세 (on-demand 캐시)
│
└── frontend/                       # Android 프론트엔드 (단일 APK)
    ├── build.gradle.kts
    ├── app/src/main/
    │   ├── AndroidManifest.xml
    │   └── java/
    │       ├── com/scanpang/app/              # Compose UI + 백엔드 API 연동
    │       │   ├── MainActivity.kt            #   앱 진입점 (Compose NavHost)
    │       │   ├── navigation/                #   라우팅 (AppNavHost, ScanPangApp)
    │       │   ├── screens/                   #   화면
    │       │   │   ├── HomeScreen.kt          #     홈 (기도시간, 키블라, 검색)
    │       │   │   ├── SearchDefaultScreen.kt #     검색
    │       │   │   ├── SearchResultsScreen.kt #     검색 결과
    │       │   │   ├── NearbyHalalRestaurantsScreen.kt  # 할랄 식당 목록
    │       │   │   ├── NearbyPrayerRoomsScreen.kt       # 기도실 목록
    │       │   │   ├── RestaurantDetailScreen.kt        # 식당 상세
    │       │   │   ├── PrayerRoomDetailScreen.kt        # 기도실 상세
    │       │   │   ├── QiblaDirectionScreen.kt          # 키블라 나침반
    │       │   │   ├── SavedPlacesScreen.kt   #     저장한 장소
    │       │   │   ├── ProfileScreen.kt       #     프로필/설정
    │       │   │   ├── SplashScreen.kt        #     스플래시
    │       │   │   ├── onboarding/            #     온보딩 (언어, 이름, 선호)
    │       │   │   └── ar/
    │       │   │       ├── ArExploreScreen.kt       # AR 탐색 UI
    │       │   │       └── ArNavigationMapScreen.kt  # AR 길안내 UI
    │       │   ├── components/                #   공용 UI 컴포넌트
    │       │   │   ├── ar/                    #     AR 오버레이 컴포넌트
    │       │   │   └── ...                    #     검색카드, 필터칩, 탭바 등
    │       │   ├── data/
    │       │   │   ├── remote/                #   백엔드 API 연동
    │       │   │   │   ├── RetrofitClient.kt  #     Retrofit 설정 (localhost:8000)
    │       │   │   │   ├── ScanPangApi.kt     #     API 인터페이스 + 전체 DTO
    │       │   │   │   └── ScanPangViewModel.kt  #  ViewModel (모든 API 호출)
    │       │   │   ├── OnboardingPreferences.kt
    │       │   │   ├── SavedPlacesStore.kt
    │       │   │   └── SearchHistoryPreferences.kt
    │       │   ├── ar/
    │       │   │   ├── AgentService.kt        #   AR 채팅 에이전트 (/place/query)
    │       │   │   ├── ArExploreTtsController.kt  # TTS 컨트롤러
    │       │   │   ├── ArSpeechRecognizerHelper.kt # STT 헬퍼
    │       │   │   ├── VoiceAgent.kt          #   음성 에이전트
    │       │   │   └── explore/
    │       │   │       └── PlaceAugmentingActivity.kt  # AR 탐색 엔진
    │       │   │           # ARCore VPS + building_raycast + /place/query
    │       │   │           # 건물 마커 동적 배치, 층별 정보, 도슨트 TTS
    │       │   ├── qibla/                     #   키블라/기도시간
    │       │   └── ui/                        #   테마, 에셋
    │       │
    │       └── com/hufs/arnavigation_com/     # AR 길안내 엔진
    │           ├── ArNavigationActivity.kt    #   ARCore + SceneView 3D 경로
    │           ├── data/remote/               #   Navigation API 서비스
    │           ├── presentation/              #   AR ViewModel
    │           └── util/ArFrameCallback.java  #   K2 컴파일러 우회
    │
    └── local.properties                       # API 키 (Git 추적 안 됨)
```

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| 백엔드 | FastAPI, Python 3.11 |
| LLM | OpenAI GPT-4o |
| 건물 인식 | VWorld WFS 건물 폴리곤 + Shapely STRtree 레이캐스팅 |
| 길찾기 | TMAP 보행자 경로 + LLM 턴별 TTS |
| 장소 정보 | Kakao Local, TourAPI, 소상공인 API, Juso API |
| 편의시설 | Kakao 카테고리/키워드, 서울시 Open API |
| 기도 시간/키블라 | Aladhan API |
| 벡터 DB | ChromaDB (place_info, store_detail) |
| 프론트엔드 | Jetpack Compose + ARCore Geospatial + SceneView |

---

## 실행 방법

### 백엔드

```bash
# 1. 가상환경 + 패키지
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt

# 2. DB 구축 (최초 1회)
python -m rag.build_place_db
python -m rag.build_vworld_buildings

# 3. 서버 실행
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --timeout-keep-alive 120
```

Swagger UI: http://localhost:8000/docs

### 프론트엔드

```bash
# 실기기 연결 (USB)
adb reverse tcp:8000 tcp:8000

# Android Studio에서 frontend/ 폴더 열고 빌드
```

---

## 환경변수 (.env)

```env
OPENAI_API_KEY=           # OpenAI GPT-4o
TMAP_API_KEY=             # SK TMAP
KAKAO_REST_API_KEY=       # Kakao Developers
TOUR_API_KEY=             # data.go.kr 한국관광공사
STORE_API_KEY=            # data.go.kr 소상공인
JUSO_API_KEY=             # business.juso.go.kr
VWORLD_API_KEY=           # VWorld WFS
VWORLD_DOMAIN=http://localhost
SEOUL_LOCKER_API_KEY=     # 서울시 물품보관함
SEOUL_RESTROOM_API_KEY=   # 서울시 공중화장실
```

---

## API 엔드포인트

| 엔드포인트 | 에이전트 | 설명 |
|---|---|---|
| `POST /ar/agent/chat` | **Orchestrator** | **단일 엔드포인트 → 4개 에이전트 자동 라우팅** |
| `POST /place/query` | Place Insight | 건물 인식 → AR 오버레이 + 도슨트 TTS |
| `POST /place/store` | Store Detail | 층별 매장 상세 (Kakao + Chroma 캐싱) |
| `POST /navigation/search` | Navigation | 자연어 → POI 후보 (LLM 의도 파악) |
| `POST /navigation/route` | Navigation | 확정 목적지 → 보행자 경로 + 턴별 TTS |
| `POST /convenience/query` | Convenience | 주변 편의시설 (15개 카테고리) |
| `POST /halal/query` | Halal | 기도시간 / 키블라 / 할랄식당 / 기도실 |

---

## LangGraph Orchestrator (`/ar/agent/chat`)

프론트엔드 AR 채팅 창의 단일 엔드포인트. GPT-4o가 메시지를 분류해 4개 에이전트 중 하나로 자동 라우팅합니다.

### 흐름

```
POST /ar/agent/chat
  └─ intent_classifier (GPT-4o, few-shot 18개)
       ├─ "place"       → place_insight_agent  → AR 오버레이 + 도슨트
       ├─ "navigation"  → navigation_agent     → POI 검색 결과
       ├─ "halal"       → halal_agent          → 기도시간 / 할랄식당 / 기도실
       └─ "convenience" → convenience_agent    → 주변 편의시설
            └─ response_synthesizer → { speech, source_agent, raw_data }
```

### 라우팅 기준

| 메시지 예시 | 분류 |
|---|---|
| "이 건물 뭐야?", "여기 운영시간?" | `place` |
| "명동역 어떻게 가?", "롯데백화점 경로" | `navigation` |
| "기도 시간 알려줘", "할랄 식당", "기도실 어디야?" | `halal` |
| "ATM", "화장실", "카페", "환전소", "약국" | `convenience` |

> **주의**: 할랄 식당은 `halal`로 분류 (일반 식당은 `convenience`)

### curl 테스트 예시

```bash
BASE="http://localhost:8000"

# 건물 정보 (place)
curl -s -X POST "$BASE/ar/agent/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"이 건물 뭐야?","lat":37.5636,"lng":126.9822,"heading":45.0,"language":"ko"}' | jq .

# 길 안내 (navigation)
curl -s -X POST "$BASE/ar/agent/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"명동역까지 어떻게 가?","lat":37.5636,"lng":126.9822}' | jq .

# 기도 시간 (halal)
curl -s -X POST "$BASE/ar/agent/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"지금 기도 시간 알려줘","lat":37.5636,"lng":126.9822,"language":"ar"}' | jq .

# 할랄 식당 (halal — convenience 아님)
curl -s -X POST "$BASE/ar/agent/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"근처 할랄 식당 추천해줘","lat":37.5636,"lng":126.9822}' | jq .

# ATM (convenience)
curl -s -X POST "$BASE/ar/agent/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"근처 ATM 찾아줘","lat":37.5636,"lng":126.9822}' | jq .

# 화장실 (convenience)
curl -s -X POST "$BASE/ar/agent/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"화장실 어디야?","lat":37.5636,"lng":126.9822}' | jq .
```

### 응답 형식

```json
{
  "speech":       "근처 할랄 식당으로 명동 할랄가든(120m)이 있습니다.",
  "source_agent": "halal",
  "raw_data":     { ... },
  "session_id":   "550e8400-e29b-41d4-a716-446655440000"
}
```

### 테스트 실행

```bash
source venv/bin/activate
python -m pytest tests/test_orchestrator.py -v
# 18개 케이스 전부 통과 (LLM / 서브에이전트 mock)
```

---

## 화면 흐름

```
앱 시작 → Splash → Onboarding (언어/이름/선호)
  ↓
홈 (기도시간, 키블라, 검색바)
  ├── 검색 → 검색 결과 → 식당/기도실 상세 → "길안내 시작" → AR 길안내
  ├── 할랄 식당 목록 → 상세 → "길안내 시작" → AR 길안내
  ├── 기도실 목록 → 상세 → "길안내 시작" → AR 길안내
  ├── 키블라 나침반 (5대 기도시간 + 방향)
  └── AR 탐색 (건물 조준 → 건물 인식 → 층별 정보 + 도슨트)

AR 탐색: PlaceAugmentingActivity (ARCore VPS + /place/query)
AR 길안내: ArNavigationActivity (ARCore + SceneView + /navigation/route)
```

---

## 다국어 지원

| language | 응답 언어 |
|---|---|
| `ko` | 한국어 |
| `en` | English |
| `ar` | العربية |
| `ja` | 日本語 |
| `zh` | 中文 |
