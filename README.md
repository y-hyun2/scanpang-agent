# ScanPang — 무슬림 관광객을 위한 AR 관광 플랫폼

서울(명동)을 방문하는 무슬림 관광객을 위한 AR 기반 관광 안내 서비스.
건물 인식, AR 길찾기, 할랄 식당/기도실 검색, 기도 시간/키블라 방향 안내를 하나의 백엔드에서 제공합니다.

---

## 프로젝트 구조

```
Scanpang_agent/
├── main.py                            # FastAPI 진입점 (6개 엔드포인트)
├── agents/                            # AI 에이전트
│   ├── place_insight_agent.py         # 건물 인식 + AR 오버레이 + 도슨트
│   ├── navigation_agent.py            # AR 길찾기 (검색 + 경로)
│   ├── convenience_agent.py           # 주변 편의시설
│   └── halal_agent.py                 # 할랄 식당 / 기도실 / 기도시간 / 키블라
├── tools/                             # 외부 API 래퍼 + 데이터 도구
│   ├── building_raycast.py            # VWorld 3D 폴리곤 레이캐스팅
│   ├── halal_tools.py                 # Aladhan API + 할랄 JSON 검색
│   ├── navigation_tools.py            # TMAP 보행자 경로
│   ├── convenience_tools.py           # Kakao/서울시 편의시설
│   ├── place_tools.py                 # Kakao Local
│   └── store_tools.py                 # 매장 상세 + Chroma 캐싱
├── schemas/                           # Pydantic 요청/응답 모델
│   ├── place.py / navigation.py / convenience.py / halal.py / store.py
├── rag/                               # 데이터 구축 + 정적 데이터
│   ├── build_place_db.py              # 명동 10개 건물 Chroma 구축
│   ├── build_vworld_buildings.py      # VWorld 건물 폴리곤 사전 적재
│   └── data/
│       ├── vworld_buildings.json      # 명동 2km 건물 29,831건 (12.6MB)
│       ├── myeongdong_restaurants.json # 할랄 식당 20개
│       ├── prayer_rooms.json          # 기도실 10개
│       └── places_manual.json         # 건물 수동 보완 데이터
├── frontend/                          # PlaceAugmenting (건물 인식 AR, Android Kotlin)
├── ar-navigation/                     # AR Navigation (길찾기 AR, Android Kotlin)
├── .env                               # API 키 (.gitignore)
└── requirements.txt
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
| 기도 시간/키블라 | Aladhan API (API key 불필요) |
| 벡터 DB | ChromaDB |
| AR 프론트 | ARCore Geospatial + SceneView (Android Kotlin) |

---

## 실행 방법

```bash
# 1. 가상환경 + 패키지
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt

# 2. DB 구축 (최초 1회)
python -m rag.build_place_db
python -m rag.build_vworld_buildings    # ~2분

# 3. 서버 실행
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --timeout-keep-alive 120
```

Swagger UI: http://localhost:8000/docs

### 실기기 연결 (USB)
```bash
adb reverse tcp:8000 tcp:8000
# Android 앱의 SERVER_URL = http://localhost:8000/
```

---

## 환경변수 (.env)

```env
OPENAI_API_KEY=           # OpenAI GPT-4o
TMAP_API_KEY=             # SK TMAP
KAKAO_REST_API_KEY=       # Kakao Developers
TOUR_API_KEY=             # data.go.kr 한국관광공사 (디코딩 키)
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
| `POST /place/query` | Place Insight | 건물 인식 → AR 오버레이 + 도슨트 TTS |
| `POST /place/store` | Store Detail | 층별 매장 상세 (Kakao + Chroma 캐싱) |
| `POST /navigation/search` | Navigation | 자연어 → POI 후보 (LLM 의도 파악) |
| `POST /navigation/route` | Navigation | 확정 목적지 → 보행자 경로 + 턴별 TTS |
| `POST /convenience/query` | Convenience | 주변 편의시설 (약국, ATM, 카페 등) |
| `POST /halal/query` | Halal | 기도시간 / 키블라 / 할랄식당 / 기도실 |

---

## Halal Agent

무슬림 관광객 전용 4가지 카테고리:

| category | 데이터 소스 | 설명 |
|---|---|---|
| `prayer_time` | Aladhan API | 5회 기도 시간 + Hijri 날짜 + 다음 기도 안내 |
| `qibla` | Aladhan API | 메카 방향 나침반 각도 |
| `restaurant` | JSON (20개) | 할랄 식당 (HALAL_MEAT/SEAFOOD/VEGGIE 필터) |
| `prayer_room` | JSON (10개) | 기도실 (시설/이용 상태) |

---

## Place Insight Agent

VWorld 건물 폴리곤(29,831건) + Shapely STRtree 레이캐스팅으로 건물 인식.

```
사용자 GPS + heading → STRtree 레이캐스팅 (3ms) → 건물 매칭 → GPT-4o 도슨트
```

지원 건물 (상세 정보): 롯데백화점, 신세계백화점, 명동성당, 눈스퀘어, 명동예술극장, N서울타워, 롯데시티호텔, 유네스코회관, 포스트타워, 대신파이낸스센터

---

## Navigation Agent

```
1단계: 자연어 → LLM 의도 파악 → TMAP POI 검색 → 후보 반환
2단계: 확정 목적지 → TMAP 보행자 경로 → LLM 턴별 TTS (한/영/아랍어)
```

---

## 프론트엔드

| 프로젝트 | 역할 | API |
|---|---|---|
| `frontend/` | 건물 인식 AR | `/place/query` |
| `ar-navigation/` | AR 길찾기 | `/navigation/search`, `/navigation/route` |

API 키는 각 프로젝트의 `local.properties`에서 관리 (Git 추적 안 됨).

---

## 다국어 지원

| language | 응답 언어 |
|---|---|
| `ko` | 한국어 |
| `en` | English |
| `ar` | العربية |
| `ja` | 日本語 |
| `zh` | 中文 |
