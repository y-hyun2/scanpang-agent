# ScanPang — AR 기반 외국인 관광 안내 플랫폼

> "건물 이름을 몰라도, 카메라를 들면 모든 정보가 보인다"

한국외국어대학교 산업경영공학과 캡스톤 프로젝트 (2026)  
협업 기업: 레인보우컴퍼니 · 테스트 환경: 서울 명동 일대

---

## 목차

1. [서비스 개요](#1-서비스-개요)
2. [핵심 기능](#2-핵심-기능)
3. [기존 서비스와의 차별성](#3-기존-서비스와의-차별성)
4. [시스템 아키텍처](#4-시스템-아키텍처)
5. [프로젝트 구조](#5-프로젝트-구조)
6. [기술 스택](#6-기술-스택)
7. [실행 방법](#7-실행-방법)
8. [API 엔드포인트](#8-api-엔드포인트)
9. [환경 변수](#9-환경-변수)
10. [다국어 지원](#10-다국어-지원)

---

## 1. 서비스 개요

한국을 방문한 외국인 관광객은 지도·번역·검색 앱을 번갈아 사용해야 하는 불편함과 한글 언어 장벽을 겪는다. ScanPang은 이 문제를 **스마트폰 카메라 하나**로 해결한다.

카메라로 건물을 조준하면 건물명·층별 매장·영업시간이 AR 오버레이로 표시되고, AI Agent가 음성으로 도슨트 해설을 제공한다. AR 길안내, 할랄 식당/기도실 검색, 주변 편의시설 안내까지 단일 앱에서 제공하는 **통합형 AR 관광 솔루션**이다.

무슬림 여행객(기도시간·키블라·할랄식당)을 핵심 타겟으로 시작하며, 향후 비건·힌두교 등 다양한 제약 조건의 여행객으로 확장한다.

---

## 2. 핵심 기능

### 2.1 공간 증강 (Spatial AR)

카메라가 향한 건물을 실시간으로 식별하고 AR 오버레이를 제공한다.

- **ARCore Geospatial VPS** 기반 정밀 위치 추적 (오차 1.5m 이내)
- **VWorld 건물 폴리곤 + Shapely STRtree Ray Casting** — 명동 2km 반경 29,831개 건물을 O(log n)으로 검색
- 화면 중앙 조준점 → 가장 가까운 건물 타겟 → AR 마커 렌더링
- Bottom Sheet로 층별 매장·영업시간·할랄 정보 표시
- TTS 도슨트 해설 (다국어)

### 2.2 AR Navigation

실외 보행 환경에 최적화된 ARCore 기반 도보 길안내.

- TMAP 보행자 경로 파싱 + T-map 미제공 꺾임 지점 직접 계산 (prevBearing/curBearing 각도 차 45° 이상 시 TURN_POINT 자동 추가)
- 회전 지점에 3D 화살표 모델(left_arrow.glb / right_arrow.glb) AR 배치
- **나침반/점선 HUD** — 고개를 30° 이상 숙이면 목적지 방향 점선 자동 표시 (안전 설계)
- GPS 정확도 게이트 (VPS 3m 이하 확보 후 경로 탐색 자동 시작)
- 실시간 미니맵 (Google Maps SDK)

### 2.3 AI Agent (LangGraph Orchestrator)

GPT-4o 기반 멀티 에이전트 시스템. `/ar/agent/chat` 단일 엔드포인트로 4개 에이전트를 자동 라우팅한다.

```
POST /ar/agent/chat
  └─ intent_classifier (GPT-4o, few-shot 18개 + Redis 세션 컨텍스트)
       ├─ place       → Place Insight Agent  → AR 오버레이 + 도슨트
       ├─ navigation  → Navigation Agent     → POI 검색 + 경로
       ├─ halal       → Halal Agent          → 기도시간 / 할랄식당 / 기도실
       └─ convenience → Convenience Agent    → ATM·약국·화장실 등 15개 카테고리
```

**일반 GPT 대비 9가지 프롬프트 엔지니어링 기법** 적용:
에이전트별 역할 고정, TTS용 응답 길이 강제, 6개 언어 Few-shot JSON 분류, 장소명 정규화(명동성당→명동대성당), TMAP 도메인 코드 자연어 변환, `is_estimated` 할루시네이션 투명성 플래그, 언어 일관성 2단계, Temperature 분리(분류 0.0 / 생성 0.3)

### 2.4 Redis 세션 메모리

대화 맥락을 유지해 "거기 어떻게 가?" 같은 지시어를 올바르게 해석한다.

- session_id 기반, 최근 5턴을 intent_classifier 프롬프트에 주입
- Redis LIST + HASH 구조, TTL 24시간, 최대 10턴, PII 자동 마스킹
- Redis 미연결 시 graceful degradation (단일 턴으로 정상 동작)

---

## 3. 기존 서비스와의 차별성

| 구분 | 기존 지도 서비스 | ScanPang |
|---|---|---|
| 정보의 깊이 | 주소·영업시간 단편 정보 | 층별 입점 매장, AI 도슨트 해설 |
| 인터페이스 | 평면 2D 맵 검색 | 실공간 객체 인식 AR 오버레이 |
| 언어 장벽 | 별도 번역기 필요 | 스캔 즉시 모국어 자동 응답 |
| 부가 가치 | 단순 위치 확인 | 할랄·기도시간·편의시설 통합 |
| AI | 범용 LLM | 도메인 특화 RAG + 멀티 에이전트 |

**RAG 데이터 직접 구축**: Kakao → TourAPI → TMAP → Juso API → 소상공인 API 5단계 파이프라인으로 층별 매장 정보를 생성. 네이버·카카오도 제공하지 않는 수준의 건물 상세 정보를 ChromaDB에 임베딩 보관.

---

## 4. 시스템 아키텍처

```
Android (Jetpack Compose + ARCore)
  │  GPS·heading·pitch
  ▼
FastAPI 백엔드
  ├── /ar/agent/chat  ← LangGraph Orchestrator
  │     ├── Redis (세션 조회/저장)
  │     └── intent_classifier → 4 Sub-Agents
  ├── /place/query    ← VWorld Ray Casting + ChromaDB RAG + GPT-4o 도슨트
  ├── /navigation/*   ← Kakao Local + TMAP 보행자 경로 + LLM TTS 생성
  ├── /halal/query    ← Aladhan API + 자체 JSON 데이터셋 (20개 식당, 10개 기도실)
  └── /convenience/*  ← Kakao 카테고리 + 서울시 Open API (화장실·물품보관함)
```

---

## 5. 프로젝트 구조

```
Scanpang_agent/
├── main.py                     # FastAPI 서버 (7개 엔드포인트)
├── docker-compose.yml          # Redis 7-alpine
├── requirements.txt
│
├── agents/
│   ├── orchestrator_agent.py   # LangGraph Orchestrator + 세션 컨텍스트 주입
│   ├── place_insight_agent.py  # 건물 인식 → AR 오버레이 + 도슨트
│   ├── navigation_agent.py     # 자연어 → POI 검색 → 보행자 경로
│   ├── convenience_agent.py    # 15개 카테고리 편의시설
│   └── halal_agent.py          # 기도시간·키블라·할랄식당·기도실
│
├── core/
│   └── session_store.py        # Redis 세션 저장소 (TTL 24h, PII 마스킹)
│
├── schemas/                    # Pydantic 모델
│   ├── session.py              # ConversationTurn, SessionContext
│   └── ...
│
├── tools/                      # 외부 API 래퍼
│   ├── building_raycast.py     # VWorld STRtree Ray Casting
│   ├── navigation_tools.py     # TMAP POI + 보행자 경로
│   ├── convenience_tools.py    # Kakao + 서울시 Open API
│   ├── halal_tools.py          # Aladhan + 자체 JSON
│   └── store_tools.py          # 매장 상세 + Chroma 캐싱
│
├── rag/
│   ├── build_place_db.py       # 5단계 API 파이프라인 → ChromaDB 구축
│   ├── build_vworld_buildings.py
│   └── data/
│       ├── vworld_buildings.json         # 명동 2km 29,831개 건물
│       ├── myeongdong_restaurants.json   # 할랄 식당 20개
│       └── prayer_rooms.json            # 기도실 10개
│
├── tests/
│   ├── test_orchestrator.py    # 라우팅 18케이스
│   └── test_session.py         # 세션 9케이스
│
└── frontend/                   # Android 앱
    └── app/src/main/java/
        ├── com/scanpang/app/   # Compose UI + API 연동
        │   ├── screens/ar/     # ArExploreScreen, ArNavigationMapScreen
        │   ├── ar/             # AgentService, TTS, STT
        │   └── data/remote/    # ScanPangApi, ScanPangViewModel
        └── com/hufs/arnavigation_com/  # ARCore + SceneView AR Navigation 엔진
```

---

## 6. 기술 스택

| 구분 | 기술 |
|---|---|
| 백엔드 | FastAPI, Python 3.11, Uvicorn |
| LLM | OpenAI GPT-4o |
| 오케스트레이션 | LangGraph StateGraph (intent_classifier → 4 sub-agents) |
| 세션 | Redis 7 (대화 맥락 유지, TTL 24h) |
| 건물 인식 | VWorld WFS 폴리곤 + Shapely STRtree Ray Casting |
| 길찾기 | TMAP 보행자 경로 + 꺾임 직접 계산 + LLM 턴별 TTS |
| 장소 정보 | Kakao Local, TourAPI, 소상공인 API, Juso API |
| 편의시설 | Kakao 카테고리/키워드, 서울시 Open API |
| 기도시간/키블라 | Aladhan API |
| 벡터 DB | ChromaDB (place_info, store_detail) |
| 프론트엔드 | Jetpack Compose + ARCore Geospatial + SceneView 2.3.3 |
| 테스트 기기 | Samsung Galaxy S23 Ultra |

---

## 7. 실행 방법

### Redis

```bash
docker compose up -d redis
adb reverse tcp:6379 tcp:6379  # 실기기 연결 시
```

### 백엔드

```bash
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt

# 최초 1회: ChromaDB + VWorld 폴리곤 DB 구축
python -m rag.build_place_db
python -m rag.build_vworld_buildings

# 서버 실행 (--reload: .py 파일 변경 시 자동 재시작 — git pull 즉시 반영)
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --timeout-keep-alive 120 --reload
```

Swagger UI: http://localhost:8000/docs

### 프론트엔드

```bash
adb reverse tcp:8000 tcp:8000
# Android Studio에서 frontend/ 폴더 열고 빌드
```

### 테스트

```bash
source venv/bin/activate
python -m pytest tests/ -v
# test_orchestrator.py: 18 passed
# test_session.py:      8 passed, 1 skipped (Redis 없는 환경)
```

---

## 8. API 엔드포인트

| 엔드포인트 | 설명 |
|---|---|
| `POST /ar/agent/chat` | **Orchestrator** — 단일 엔드포인트, 4개 에이전트 자동 라우팅 + 세션 유지 |
| `POST /place/query` | 건물 인식 → AR 오버레이 + 도슨트 TTS |
| `POST /place/store` | 층별 매장 상세 (Kakao + Chroma 캐싱) |
| `POST /navigation/search` | 자연어 → POI 후보 목록 |
| `POST /navigation/route` | 확정 목적지 → 보행자 경로 + 턴별 TTS |
| `POST /convenience/query` | 주변 편의시설 (15개 카테고리) |
| `POST /halal/query` | 기도시간 / 키블라 / 할랄식당 / 기도실 |

**세션 다중 턴 테스트**

```bash
SESSION=$(uuidgen | tr '[:upper:]' '[:lower:]')

# 1턴: 건물 질문 → place 라우팅
curl -s -X POST http://localhost:8000/ar/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"눈스퀘어 뭐야?\",\"lat\":37.5636,\"lng\":126.9822,\"session_id\":\"$SESSION\"}" | jq .

# 2턴: 지시어 → 이전 이력 참고해 navigation 라우팅
curl -s -X POST http://localhost:8000/ar/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"거기 어떻게 가?\",\"lat\":37.5636,\"lng\":126.9822,\"session_id\":\"$SESSION\"}" | jq .
```

---

## 9. 환경 변수

`.env` 파일에 설정:

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
REDIS_URL=redis://localhost:6379/0
```

---

## 10. 다국어 지원

| `language` | 응답 언어 |
|---|---|
| `ko` | 한국어 |
| `en` | English |
| `ar` | العربية |
| `ja` | 日本語 |
| `zh` | 中文 |
