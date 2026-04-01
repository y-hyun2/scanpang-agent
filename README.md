# ScanPang Agent

**ScanPang** AR 앱의 백엔드 서버
Navigation Agent (길안내)와 Place Insight Agent (건물 AR 오버레이 + 도슨트) 두 가지 기능을 제공합니다.

---

## 기술 스택

| 항목 | 내용 |
|---|---|
| 프레임워크 | FastAPI |
| LLM | OpenAI gpt-4o |
| 지도 API | TMAP (SK Telecom) |
| 장소 정보 | Kakao Local API, TourAPI, 소상공인 API, Juso API |
| 벡터 DB | ChromaDB + BAAI/bge-m3 임베딩 |
| 언어 | Python 3.10+ |

---

## 프로젝트 구조

```
scanpang-navigation-agent/
├── main.py                        # FastAPI 진입점 (엔드포인트 4개)
├── agents/
│   ├── navigation_agent.py        # 길안내 LLM 오케스트레이션
│   └── place_insight_agent.py     # 건물 AR 오버레이 + 도슨트 생성
├── tools/
│   ├── navigation_tools.py        # TMAP API 래퍼
│   ├── place_tools.py             # Kakao Local API 래퍼
│   └── store_tools.py             # 개별 매장 상세 조회 + Chroma 캐싱
├── schemas/
│   ├── navigation.py              # 길안내 요청/응답 모델
│   ├── place.py                   # 건물 조회 요청/응답 모델
│   └── store.py                   # 매장 상세 요청/응답 모델
├── rag/
│   ├── build_place_db.py          # DB 구축 스크립트 (서버 실행 전 1회)
│   └── data/
│       └── places_manual.json     # 수동 보완 데이터
├── chroma_db/                     # ChromaDB 저장소 (build 후 생성)
└── .env                           # API 키
```

---

## 환경변수 설정

`.env` 파일을 프로젝트 루트에 생성하세요.

```
OPENAI_API_KEY=        # OpenAI
TMAP_API_KEY=          # SK TMAP
KAKAO_REST_API_KEY=    # Kakao Developers
TOUR_API_KEY=          # data.go.kr 한국관광공사_국문 관광정보 서비스_GW (디코딩 키)
STORE_API_KEY=         # data.go.kr 소상공인시장진흥공단_상가(상권)정보
JUSO_API_KEY=          # business.juso.go.kr 도로명주소 검색
```

> TourAPI는 **디코딩 키** 사용. 

---

## 실행 방법

```bash
# 패키지 설치
pip install fastapi uvicorn langchain langchain-openai httpx pydantic python-dotenv chromadb sentence-transformers

# Place Insight DB 구축 (최초 1회)
python -m rag.build_place_db

# 서버 실행
uvicorn main:app --reload --port 8000
```

Swagger UI: http://localhost:8000/docs

---

## ChromaDB 구조

Place Insight Agent는 `./chroma_db` 폴더에 두 개의 컬렉션을 유지합니다.

```
chroma_db/
├── place_info     ← 건물 정보 (build_place_db.py 실행 시 생성)
└── store_detail   ← 개별 매장 상세 캐시 (on-demand 생성)
```

### `place_info` 컬렉션

| 구성 요소 | 내용 |
|---|---|
| `document` | `name_ko + category + description_en` — 시맨틱 검색용 텍스트 |
| `id` | `place_id` (예: `myeongdong_cathedral`) |
| `metadata` | 아래 표 참고 |

**metadata 컬럼:**

| 컬럼 | 출처 |
|---|---|
| `place_id`, `name_ko`, `category` | Kakao Local |
| `lat`, `lng`, `addr`, `phone` | Kakao Local |
| `description_en` | TourAPI overview → GPT 번역 (없으면 GPT 직접 생성) |
| `image_url`, `homepage` | TourAPI |
| `open_hours`, `closed_days` | TourAPI detailIntro2 |
| `parking_info`, `admission_fee` | TourAPI detailIntro2 |
| `floor_info` | 소상공인 API → JSON 직렬화 문자열 |

> `floor_info`는 Chroma가 list를 지원하지 않아 `json.dumps()`로 직렬화. 조회 시 `json.loads()`로 역직렬화.

### `store_detail` 컬렉션

사용자가 층별 매장을 탭할 때 on-demand로 Kakao API를 호출하고, 결과를 캐싱.
두 번째 요청부터는 Kakao 호출 없이 캐시에서 바로 반환.

| 구성 요소 | 내용 |
|---|---|
| `id` | `{place_id}__{store_name}` |
| `metadata` | Kakao Local 응답 (name_ko, category, addr, phone 등) |

---

## API 엔드포인트

### POST /navigation/search

자연어 메시지 → POI 후보 목록 반환

**Request**
```json
{ "message": "캄풍쿠 어떻게 가?", "lat": 37.5636, "lng": 126.9822 }
```

**Response**
```json
{
  "speech": "'캄풍쿠' 찾았어요. 서울 중구 남산동2가. 이 곳으로 안내할까요?",
  "candidates": [
    { "poi_id": "374469", "name": "캄풍쿠", "address": "서울 중구 남산동2가 16-4",
      "pns_lat": 37.55820, "pns_lon": 126.98490, "recommended": true }
  ],
  "intent": "specific_place",
  "language": "ko"
}
```

---

### POST /navigation/route

확정된 목적지 → 보행자 경로 + 턴별 TTS 안내

**Request**
```json
{
  "lat": 37.5636, "lng": 126.9822,
  "destination": { "poi_id": "374469", "pns_lat": 37.55820, "pns_lon": 126.98490, "name": "캄풍쿠" },
  "language": "ko"
}
```

**Response**
```json
{
  "speech": "출발합니다. 캄풍쿠까지 350m, 약 7분 소요됩니다.",
  "ar_command": {
    "type": "start_navigation",
    "route_line": [{ "lat": 37.563, "lng": 126.982 }],
    "turn_points": [
      { "lat": 37.563, "lng": 126.982, "pointType": "SP", "turnType": 200,
        "speech": "출발합니다. 캄풍쿠까지 350m, 약 7분 소요됩니다.", "segment_distance_m": 0,
        "description": "출발", "nearPoiName": "", "intersectionName": "", "facilityType": "" },
      { "lat": 37.561, "lng": 126.983, "pointType": "GP", "turnType": 13,
        "speech": "GS25 명동점에서 우회전하세요.", "segment_distance_m": 150,
        "description": "우회전", "nearPoiName": "GS25 명동점", "intersectionName": "명동사거리", "facilityType": "" },
      { "lat": 37.558, "lng": 126.985, "pointType": "EP", "turnType": 201,
        "speech": "목적지 캄풍쿠에 도착했습니다.", "segment_distance_m": 200,
        "description": "도착", "nearPoiName": "", "intersectionName": "", "facilityType": "" }
    ],
    "destination": { "lat": 37.5582, "lng": 126.9849, "name": "캄풍쿠" },
    "total_distance_m": 350,
    "total_time_min": 7
  }
}
```

---

### POST /place/query

ARCore가 인식한 건물 → AR 오버레이 데이터 + TTS 도슨트 반환

**Request**
```json
{
  "place_id": "myeongdong_cathedral",
  "user_message": "What is this building?",
  "user_lat": 37.5628,
  "user_lng": 126.9875,
  "language": "en"
}
```

**Response**
```json
{
  "ar_overlay": {
    "name": "명동성당",
    "category": "여행",
    "floor_info": [
      { "floor": "1F", "stores": ["Main cathedral hall"] },
      { "floor": "B1", "stores": ["Café", "Gift shop"] }
    ],
    "halal_info": "",
    "image_url": "https://...",
    "homepage": "https://www.mdsd.or.kr",
    "open_hours": "06:00~21:00",
    "closed_days": "",
    "parking_info": "",
    "admission_fee": "무료"
  },
  "docent": {
    "speech": "This is Myeongdong Cathedral, completed in 1898. Korea's first Gothic-style Catholic cathedral and one of Seoul's most iconic landmarks.",
    "follow_up_suggestions": [
      "What's on each floor?",
      "What's nearby to eat?",
      "Is there a prayer room nearby?"
    ]
  }
}
```

---

### POST /place/store

층별 매장 탭 → 매장 상세 정보 반환 (Kakao on-demand + Chroma 캐싱)

**Request**
```json
{
  "place_id": "lotte_dept_myeongdong",
  "store_name": "스타벅스",
  "user_lat": 37.5631,
  "user_lng": 126.9879
}
```

**Response**
```json
{
  "store_name": "스타벅스",
  "place_id": "lotte_dept_myeongdong",
  "name_ko": "스타벅스 롯데백화점 명동본점",
  "category": "음식점",
  "addr": "서울 중구 남대문로 81",
  "phone": "02-000-0000",
  "place_url": "https://place.map.kakao.com/..."
}
```

---

## Navigation Agent 동작 흐름

```
[1단계] POST /navigation/search
  사용자: "캄풍쿠 어떻게 가?"
        ↓
  Step 0: LLM → 키워드 추출 + 의도 분류 + 언어 감지
          { "keyword": "캄풍쿠", "intent": "specific_place", "language": "ko" }
        ↓
  Step 1: TMAP POI 검색 (5km 반경 → 없으면 전국 fallback)
        ↓
  Step 2: LLM → 최적 POI 선택 (specific_place만)
        ↓
  응답: speech + candidates[] → 앱에서 사용자 확인

[2단계] POST /navigation/route
  확정된 목적지 전달
        ↓
  Step 3: TMAP 보행자 경로 계산
        ↓
  Step 4: LLM → 턴포인트별 TTS 문구 일괄 생성
        ↓
  응답: ar_command { route_line, turn_points }
```

---

## Place Insight Agent 동작 흐름

```
[ARCore: 건물 인식 → place_id 결정]
        ↓
POST /place/query
        ↓
  Chroma place_info 컬렉션에서 place_id로 직접 조회
        ↓
  ar_overlay: RAG 데이터 그대로 반환 (LLM 없음)
  docent:     gpt-4o로 자연어 해설 생성 (language 파라미터 언어로)
        ↓
  응답: ar_overlay + docent

[사용자가 층별 매장 탭]
        ↓
POST /place/store
        ↓
  Chroma store_detail 캐시 확인
    hit  → 바로 반환
    miss → Kakao API 호출 → 캐시 저장 → 반환
```

---

## 다국어 지원

`language` 파라미터 기준으로 입력 언어와 동일하게 응답합니다.

| language | 입력 예시 | 응답 언어 |
|---|---|---|
| `ko` | "이 건물 뭐야?" | 한국어 |
| `en` | "What is this building?" | English |
| `ar` | "ما هذا المبنى؟" | اللغة العربية |

---

## turnType 코드표

| turnType | 의미 |
|---|---|
| 11 | 직진 |
| 12 | 좌회전 |
| 13 | 우회전 |
| 14 | 유턴 |
| 16 | 8시 방향 (좌측 사선) |
| 17 | 10시 방향 (우측 사선) |
| 125 | 육교 |
| 126 | 지하보도 |
| 127 | 계단 |
| 200 | 출발 (SP) |
| 201 | 도착 (EP) |
