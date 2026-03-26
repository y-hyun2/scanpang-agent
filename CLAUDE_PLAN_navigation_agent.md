# Navigation Agent — Claude Code Plan 요청

## 지시사항
아래 전체 컨텍스트를 읽고, **plan 모드**로 Navigation Agent 전체 코드를 설계해줘.
코드 작성 전에 반드시 계획만 먼저 보여줘. 승인 후 코드 작성.

---

## 프로젝트 개요

- **서비스**: 나홀로 할랄 외국인 여행객을 위한 AR 솔루션 (ScanPang)
- **앱**: React Native (Android/iOS) + ARCore 기반 AR 길찾기
- **백엔드**: FastAPI (Python)
- **지금 만들 것**: Navigation Agent (Sub-agent 4개 중 첫 번째)

---

## 아키텍처 위치

```
[React Native AR 앱]
       ↓ HTTP POST
[FastAPI 서버]
       ↓
[Navigation Agent]  ← 지금 만들 것
   ├── Tool 1: TMAP POI 검색 (목적지 이름 → 좌표)
   └── Tool 2: TMAP 보행자 경로 (좌표 → GeoJSON 경로)
```

- LangGraph 아직 사용 안 함 (Orchestrator는 나중에)
- RAG 없음 (Navigation은 실시간 API만으로 충분)

---

## 파일 구조 (이렇게 만들어줘)

```
project/
├── main.py                      # FastAPI 앱 진입점
├── agents/
│   └── navigation_agent.py      # Agent 핵심 로직
├── tools/
│   └── navigation_tools.py      # TMAP Tool 함수 2개
├── schemas/
│   └── navigation.py            # Pydantic Request/Response 모델
└── .env                         # API 키 (파일만 생성, 값은 비워둠)
```

---

## API 1: TMAP 장소 통합 검색 (POI)

### 목적지 이름으로 검색

```
GET https://apis.openapi.sk.com/tmap/pois
  ?version=1
  &searchKeyword={목적지이름}
  &searchType=all
  &count=5
  &appKey={TMAP_API_KEY}
```

### 현재 위치 기준 거리순 검색 ("주변 할랄 식당" 쿼리용)

```
GET https://apis.openapi.sk.com/tmap/pois
  ?version=1
  &searchKeyword={키워드}
  &searchtypCd=R
  &centerLon={현재경도}
  &centerLat={현재위도}
  &radius=1
  &count=10
  &appKey={TMAP_API_KEY}
```

### Response에서 파싱할 키값

```json
{
  "searchPoiInfo": {
    "pois": {
      "poi": [{
        "id": "374469",           // ★ endPoiId로 경로 API에 직접 사용
        "name": "캄풍쿠",
        "noorLat": "37.55831",    // 중심점 위도 (fallback용)
        "noorLon": "126.98501",   // 중심점 경도 (fallback용)
        "pnsLat": "37.55820",     // ★ 보행자 입구점 위도 (경로 계산에 더 정확)
        "pnsLon": "126.98490",    // ★ 보행자 입구점 경도
        "upperAddrName": "서울",
        "middleAddrName": "중구",
        "lowerAddrName": "남산동2가",
        "detailAddrName": "16-4"
      }]
    }
  }
}
```

### 파싱 우선순위

| 키 | 용도 | 우선순위 |
|---|---|---|
| `id` | endPoiId로 경로 API에 직접 사용 | ★★★ |
| `pnsLat/pnsLon` | 보행자 입구점 좌표 | ★★★ |
| `noorLat/noorLon` | 중심점 좌표 (pns 없을 때 fallback) | ★★ |
| `name` | 장소명 | ★★★ |
| `upperAddrName ~ detailAddrName` | 전체 주소 조합 | ★★ |

> 경로 계산 우선순위: `endPoiId(id)` → `pnsLat/pnsLon` → `noorLat/noorLon`

---

## API 2: TMAP 보행자 경로 안내 (GeoJSON)

### Request Body

```json
{
  "startX": "126.9822",
  "startY": "37.5636",
  "endPoiId": "374469",    // POI id 있으면 이걸 우선 사용
  "endX": "126.98490",     // pnsLon (endPoiId 없을 때)
  "endY": "37.55820",      // pnsLat (endPoiId 없을 때)
  "reqCoordType": "WGS84GEO",
  "resCoordType": "WGS84GEO",
  "startName": "현재위치",
  "endName": "캄풍쿠",
  "searchOption": 0
}
```

### searchOption

| 값 | 의미 | 언제 |
|---|---|---|
| 0 | 추천 (기본값) | 일반 |
| 4 | 추천 + 대로우선 | 길치 여행자 |
| 10 | 최단거리 | 시간 촉박 |
| 30 | 최단거리 + 계단제외 | 캐리어, 기도 시간 촉박 |

### GeoJSON Response — Point feature (안내 지점)

```json
{
  "type": "Feature",
  "geometry": {
    "type": "Point",
    "coordinates": [126.985, 37.558]
  },
  "properties": {
    "index": "1",
    "pointIndex": 1,
    "pointType": "GP",
    "name": "명동길",
    "description": "우회전 후 직진",
    "intersectionName": "명동사거리",
    "nearPoiName": "GS25 명동점",
    "nearPoiX": "126.985",
    "nearPoiY": "37.558",
    "turnType": 13,
    "facilityType": "11",
    "totalDistance": 350,
    "totalTime": 420
  }
}
```

### Point feature 파싱할 키값 전부

| 키 | 용도 |
|---|---|
| `turnType` | AR 화살표 방향 결정 |
| `description` | LLM에 넘길 안내 문구 |
| `nearPoiName` | ★ "GS25에서 우회전" 랜드마크 안내에 직접 사용 |
| `intersectionName` | 교차로 이름 |
| `pointType` | SP/EP/GP 구분 → AR 오버레이 종류 결정 |
| `facilityType` | 육교/지하보도/계단 여부 |
| `totalDistance` | SP 지점에서만 나옴 |
| `totalTime` | SP 지점에서만 나옴 |

### GeoJSON Response — LineString feature (경로선)

```json
{
  "type": "Feature",
  "geometry": {
    "type": "LineString",
    "coordinates": [
      [126.982, 37.563],
      [126.983, 37.561],
      [126.985, 37.558]
    ]
  },
  "properties": {
    "index": 1,
    "lineIndex": 1,
    "name": "명동길",
    "distance": 80,
    "time": 60,
    "roadType": 21
  }
}
```

| 키 | 용도 |
|---|---|
| `coordinates` | ★ AR 경로선 렌더링 핵심 |
| `distance` | 구간 거리(m) |
| `time` | 구간 소요시간(초) |

### turnType 전체 코드

| 코드 | 의미 | AR 처리 |
|---|---|---|
| 11 | 직진 | 직진 화살표 |
| 12 | 좌회전 | 좌 화살표 |
| 13 | 우회전 | 우 화살표 |
| 14 | U-turn | U턴 표시 |
| 125 | 육교 | ⚠️ "육교 이용" 경고 |
| 126 | 지하보도 | ⚠️ 안내 |
| 127 | 계단 진입 | ⚠️ "계단 있음" 경고 |
| 200 | 출발지 | 출발 마커 |
| 201 | 목적지 | 도착 마커 |
| 211~217 | 횡단보도 | 🚶 안내 |
| 218 | 엘리베이터 | 엘리베이터 안내 |

### pointType 코드

| 코드 | 의미 |
|---|---|
| SP | 출발지 (totalDistance, totalTime 여기서만 나옴) |
| EP | 도착지 |
| GP | 일반 안내점 |

---

## FastAPI Request / Response 형태

### Request (React Native → FastAPI)

```json
{
  "message": "캄풍쿠 어떻게 가?",
  "lat": 37.5636,
  "lng": 126.9822
}
```

### Response (FastAPI → React Native)

```json
{
  "speech": "200m 직진 후 GS25 명동점에서 우회전하세요. 3분 거리입니다.",
  "ar_command": {
    "type": "start_navigation",
    "route_line": [
      {"lat": 37.563, "lng": 126.982},
      {"lat": 37.561, "lng": 126.983},
      {"lat": 37.558, "lng": 126.985}
    ],
    "turn_points": [
      {
        "lat": 37.561,
        "lng": 126.983,
        "turnType": 13,
        "description": "우회전",
        "nearPoiName": "GS25 명동점",
        "pointType": "GP",
        "facilityType": ""
      }
    ],
    "destination": {
      "lat": 37.5582,
      "lng": 126.9849,
      "name": "캄풍쿠"
    },
    "total_distance_m": 350,
    "total_time_min": 7
  }
}
```

- `route_line` → LineString coordinates → AR 경로선
- `turn_points` → Point features → AR 화살표 오버레이

---

## LLM 프롬프트 조건

- 모델: `gpt-4o-mini`
- 1-2문장으로 짧게 (TTS로 읽힘)
- `nearPoiName` 있으면 반드시 활용
  - ✅ "Turn right at GS25 Myeongdong"
  - ❌ "Turn right in 30 meters"
- `facilityType` 125/126/127이면 "계단/육교/지하보도 있음" 추가 멘트
- 사용자 언어 자동 감지 (한국어 → 한국어, 영어 → 영어, 아랍어 → 영어)

---

## 기술 스택

```bash
pip install fastapi uvicorn langchain langchain-openai httpx pydantic python-dotenv
```

## 환경변수 (.env)

```
OPENAI_API_KEY=
TMAP_API_KEY=
```

---

## Postman 테스트 케이스

```
POST http://localhost:8000/navigation/query
Content-Type: application/json

{
  "message": "캄풍쿠 어떻게 가?",
  "lat": 37.5636,
  "lng": 126.9822
}
```

```
POST http://localhost:8000/navigation/query
Content-Type: application/json

{
  "message": "How do I get to Kampungku?",
  "lat": 37.5636,
  "lng": 126.9822
}
```

---

## 주의사항

1. LangGraph 사용 안 함 (나중에 Orchestrator 만들 때 추가)
2. RAG 없음 (Navigation은 Tool만)
3. `endPoiId` 있으면 경로 API에서 endX/endY 대신 우선 사용
4. `pnsLat/pnsLon` → `noorLat/noorLon` fallback 로직 필요
5. SP pointType의 totalDistance/totalTime으로 전체 거리/시간 파싱
6. LineString만 route_line으로, Point만 turn_points로 분리해서 반환
