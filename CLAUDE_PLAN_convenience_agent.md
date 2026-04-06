# Convenience Agent — Claude Code Plan

## 역할

사용자가 AR 화면에서 카테고리 탭 or 텍스트/음성 검색 → 주변 편의시설 즉시 반환.

두 가지 입력 방식:
1. **필터 탭 선택**: `category` 파라미터 직접 전달 → LLM 없이 바로 검색
2. **텍스트/음성 검색**: `message` 파라미터 → LLM이 category + language 추출 후 검색

---

## 전체 흐름

```
POST /convenience/query
        ↓
[category 있음?]
  Yes → LLM 스킵, 바로 Step 2
  No  → Step 1: LLM이 message에서 category + language 추출
        ↓
Step 2: category별 라우팅
  ├─ Kakao categories    → kakao_category_search(code, lat, lng, radius)
  ├─ "exchange"          → kakao_keyword_search("환전", lat, lng, radius)
  ├─ "restroom"          → seoul_restroom_api(lat, lng, radius)
  ├─ "locker"            → seoul_locker_api(lat, lng, radius)
  └─ "prayer_room"       → load prayer_rooms.json → haversine 필터
        ↓
Step 3: distance_m 오름차순 정렬 → 상위 5개
        ↓
Step 4: LLM으로 speech 생성 (language 기준, 가장 가까운 1개 중심)
        ↓
Response: { speech, category, facilities[], language }
```

---

## 카테고리 전체 목록 & 반경 기본값

| category key | 처리 방식 | Kakao code | 반경 기본값 |
|---|---|---|---|
| `convenience_store` | Kakao | CS2 | 300m |
| `cafe` | Kakao | CE7 | 300m |
| `restaurant` | Kakao | FD6 | 300m |
| `pharmacy` | Kakao | PM9 | 500m |
| `hospital` | Kakao | HP8 | 500m |
| `bank` | Kakao | BK9 | 500m |
| `atm` | Kakao | BK9 | 300m |
| `shopping` | Kakao | MT1 | 500m |
| `parking` | Kakao | PK6 | 300m |
| `subway` | Kakao | SW8 | 1000m |
| `tourist_info` | Kakao | AT4 | 1000m |
| `exchange` | Kakao keyword "환전" | — | 500m |
| `restroom` | 서울시 Open API OA-22586 | — | 300m |
| `locker` | 서울시 Open API OA-22731 | — | 1000m |
| `prayer_room` | rag/data/prayer_rooms.json | — | 1000m |

---

## 추가할 파일 구조

```
scanpang-navigation-agent/
├── main.py                         ← /convenience/query 엔드포인트 추가
├── agents/
│   └── convenience_agent.py        ← 새로 추가
├── tools/
│   └── convenience_tools.py        ← 새로 추가
├── schemas/
│   └── convenience.py              ← 새로 추가
└── rag/
    └── data/
        └── prayer_rooms.json       ← 새로 추가 (수동 관리)
```

기존 navigation / place 파일 수정 없음.

---

## 서울시 Open API

표준 URL:
```
http://openapi.seoul.go.kr:8088/{API_KEY}/json/{SERVICE}/1/1000/
```

**API 키 발급 (데이터셋별 각각 신청):**
- 공중화장실 OA-22586: https://data.seoul.go.kr/dataList/OA-22586/S/1/datasetView.do → `SEOUL_RESTROOM_API_KEY`
- 물품보관함 OA-22731: https://data.seoul.go.kr/dataList/OA-22731/A/1/datasetView.do → `SEOUL_LOCKER_API_KEY`

### 공중화장실 (OA-22586)
```
서비스명: SearchPublicToiletPOIService
파싱 필드:
  POI_NM           → name
  REFINE_WGS84_LAT → lat
  REFINE_WGS84_LOGT → lng
  ANAM_OPENTIME    → open_hours
  WHEELCHAIR_YN    → extra.wheelchair
  REFINE_ROADNM_ADDR → address
```

### 물품보관함 (OA-22731)
```
서비스명: subwayLockerInfo
파싱 필드:
  역명      → name
  설치위치  → extra.location_detail
  위도      → lat
  경도      → lng
  소형      → extra.small
  중형      → extra.medium
  대형      → extra.large
```

전체 목록 수신 → haversine으로 거리 계산 → 반경 내 결과만 반환.

---

## 기도실 JSON (rag/data/prayer_rooms.json)

수동으로 관리. ChromaDB 불필요.

```json
[
  {
    "name": "이슬람 서울 중앙성원",
    "address": "서울 용산구 우사단로10길 39",
    "lat": 37.5348,
    "lng": 126.9924,
    "open_hours": "05:00~22:00",
    "phone": "02-793-6908"
  }
]
```

---

## Request / Response

### Request
```json
{
  "message": "가장 가까운 화장실 어디야?",
  "category": "",
  "lat": 37.5636,
  "lng": 126.9822,
  "language": "ko",
  "radius": 0
}
```
- `category` 있으면 LLM 스킵
- `radius` 0이면 카테고리별 기본값 사용

### Response
```json
{
  "speech": "200m 앞에 명동 공중화장실이 있습니다. 24시간 운영하며 장애인 화장실도 있습니다.",
  "category": "restroom",
  "facilities": [
    {
      "name": "명동 공중화장실",
      "distance_m": 200,
      "lat": 37.561,
      "lng": 126.983,
      "address": "서울 중구 명동길 14",
      "phone": "",
      "open_hours": "24시간",
      "extra": { "wheelchair": "Y" }
    }
  ],
  "language": "ko"
}
```

---

## LLM 프롬프트 — 카테고리 추출

```
카테고리 목록:
convenience_store, cafe, restaurant, pharmacy, hospital,
bank, atm, shopping, parking, subway, tourist_info,
exchange, restroom, locker, prayer_room

사용자 메시지에서 찾는 시설 카테고리와 언어를 JSON으로 반환.
예: { "category": "restroom", "language": "ko" }
```

---

## LLM 프롬프트 — speech 생성

- 가장 가까운 1개 시설 중심으로 2-3문장
- language 파라미터 언어로 응답
- 거리, 운영시간, 특이사항(wheelchair 등) 포함

---

## 환경변수 추가 (.env)

```
SEOUL_RESTROOM_API_KEY=    # data.seoul.go.kr OA-22586 공중화장실
SEOUL_LOCKER_API_KEY=      # data.seoul.go.kr OA-22731 물품보관함
```

---

## Postman 테스트 케이스

```
POST http://localhost:8000/convenience/query

# 필터 탭 (LLM 없음)
{ "category": "restroom", "lat": 37.5636, "lng": 126.9822, "language": "ko" }
{ "category": "locker", "lat": 37.5636, "lng": 126.9822, "language": "en" }
{ "category": "prayer_room", "lat": 37.5636, "lng": 126.9822, "language": "ar" }

# 텍스트 검색 (LLM 카테고리 추출)
{ "message": "가장 가까운 편의점 어디야?", "lat": 37.5636, "lng": 126.9822 }
{ "message": "Where can I store my luggage?", "lat": 37.5636, "lng": 126.9822 }
{ "message": "근처 ATM 어디야?", "lat": 37.5636, "lng": 126.9822 }
```

---

## 주의사항

1. 기존 navigation / place / store 관련 파일 건드리지 말 것
2. 서울 Open API 키는 데이터셋별로 각각 신청 — 하나의 키가 아님
3. `build_convenience_db.py` 불필요 — 실시간 API 호출 방식
4. prayer_rooms.json은 수동 업데이트 — 자동화 없음
5. haversine 거리 계산은 convenience_tools.py에 유틸 함수로 분리
