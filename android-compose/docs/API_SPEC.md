# ScanPang 프론트(Android) 연동용 API 명세 가이드

이 문서는 **현재 앱에 하드코딩·로컬 저장·더미 함수로만 처리된 부분**을 기준으로, 백엔드(또는 외부 API)와 무엇을 맞춰 연동하면 되는지 정리한 **초안 명세**입니다. 실제 배포 전에 팀에서 필드명·인증 방식·페이지네이션 규칙을 확정하면 됩니다.

---

## 1. 연동이 필요한 이유 (현재 상태 요약)

| 구분 | 현재 구현 | 연동 시 기대 |
|------|-----------|--------------|
| 장소·검색 결과 | `SearchResultsScreen`, `NearbyHalalRestaurantsScreen` 등에 **고정 리스트** | 서버 검색/목록 API로 대체 |
| 상세 화면 | `RestaurantDetailScreen`, `PrayerRoomDetailScreen` 등 **문구·이미지 URL 고정** | `placeId` 기준 상세 API |
| 저장(북마크) | `SavedPlacesStore` → **SharedPreferences + JSON** | 로그인 시 **서버 동기화** 권장 (비로그인은 로컬만 유지 가능) |
| 최근 검색 | `SearchHistoryPreferences` → **로컬만** | 선택: 서버에 검색 이력 동기화 또는 로컬 유지 |
| 키블라·기도시간 | `QiblaDataProviders.kt` **더미** + 기기 센서/위치만 실제 | **외부 기도시간 API** 또는 자체 계산 서버 |
| AR 채팅 | `ArExploreAgent.kt` **고정 응답** | LLM/에이전트 API |
| AR·내비 UI | POI 이름·설명·길안내 문구 **하드코딩** | POI/경로 API (또는 지도 SDK + 서버 POI) |
| 홈 | 인사말·**“명동역 6번 출구”** 등 고정 | 사용자명·역지오코딩 결과 API |

**센서·카메라·나침반**은 보통 API가 아니라 **OS(SensorManager, CameraX, FusedLocation)** 그대로 쓰고, 서버에는 **좌표·장소 ID**만 보내는 형태가 일반적입니다.

---

## 2. 공통 규칙 (제안)

- **Base URL**: `https://api.example.com/v1` (예시)
- **인증 (선택)**  
  - `Authorization: Bearer <access_token>`  
  - 로그인 전에는 저장·검색 이력 동기화 생략 가능
- **에러 응답 (제안)**  
  - JSON: `{ "code": "STRING", "message": "사용자용 메시지" }`
- **페이지네이션 (제안)**  
  - `?page=1&size=20` 또는 `?cursor=...&limit=20`
- **위치 기반 조회**  
  - `lat`, `lng` 쿼리 또는 헤더 (할랄/기도실/검색 근처 추천에 공통 사용)

---

## 3. 도메인별 API 초안

### 3.1 검색 (SearchDefault / SearchResults)

**현재**: `SearchResultsScreen`에 `resultItems` 고정 리스트. 검색어는 네비게이션 인자로만 전달됨.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/search/places` | 키워드·카테고리·위치 기반 장소 검색 |

**Query (예시)**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `q` | string | Y | 검색어 |
| `lat` | number | N | 위도 |
| `lng` | number | N | 경도 |
| `radius_m` | int | N | 반경(m), 기본값 합의 |
| `page` | int | N | 페이지 |
| `size` | int | N | 페이지 크기 |

**응답 (예시)**

```json
{
  "items": [
    {
      "id": "place_abc",
      "name": "할랄가든 명동점",
      "badge_kind": "HALAL_MEAT",
      "badge_label": "HALAL MEAT",
      "cuisine_label": "한식",
      "distance_m": 120,
      "is_open": true,
      "trust_tags": [
        { "label": "할랄 인증", "type": "verified" },
        { "label": "방문자 추천", "type": "star" }
      ]
    }
  ],
  "total_count": 12,
  "page": 1,
  "size": 20
}
```

앱의 `SearchResultPlaceCard`와 맞추려면 `badge_kind`를 `HALAL_MEAT | SEAFOOD | VEGGIE | SALAM_SEOUL` 등 **앱 enum과 동일한 코드**로 통일하는 것이 좋습니다.

---

### 3.2 주변 할랄 식당 목록

**현재**: `NearbyHalalRestaurantsScreen`의 `allPlaces` 고정 + 칩으로 클라이언트 필터.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/places/halal/nearby` | 주변 할랄 식당 목록 |

**Query (예시)**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `lat` | number | Y | 위도 |
| `lng` | number | Y | 경도 |
| `category` | string | N | `HALAL_MEAT`, `SEAFOOD`, `VEGGIE`, `SALAM_SEOUL` (미전송 시 전체) |
| `radius_m` | int | N | 반경 |

**응답**: 3.1의 `items[]`와 동일 스키마를 재사용해도 됨.

---

### 3.3 주변 기도실

**현재**: `NearbyPrayerRoomsScreen` 등에 목록/필터 하드코딩 가능성 → 동일 패턴으로 `GET /places/prayer-rooms/nearby` 제안.

**Query**: `lat`, `lng`, `filter` (예: `gender_separated` 등 UI 칩과 매핑)

**응답 필드 (예시)**: `id`, `name`, `distance_m`, `address`, `tags[]`, `is_open`

---

### 3.4 장소 상세 (식당 / 기도실)

**현재**: `RestaurantDetailScreen` — 이름·주소·메뉴·이미지 `ScanPangFigmaAssets` 등 고정.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/places/{placeId}` | 식당·상점 등 상세 |
| GET | `/prayer-rooms/{roomId}` | 기도실 상세 (분리할지 단일 `places`로 통합할지는 백엔드 설계 선택) |

**응답 (식당 예시)**

```json
{
  "id": "place_halal_garden_myeongdong",
  "name": "할랄가든 명동점",
  "category": "할랄 식당",
  "distance_line": "명동 · 도보 2분",
  "meta_line": "한식 · 도보 2분",
  "tags": ["할랄 인증", "방문자 추천"],
  "hero_images": ["https://..."],
  "address": "서울특별시 중구 명동길 26",
  "phone": "02-1234-5678",
  "hours_label": "11:00 – 22:00 (연중무휴)",
  "is_open": true,
  "intro": "…",
  "menus": [{ "name": "한우 불고기 정식", "price": "15,000원" }]
}
```

북마크 저장 시 서버에 보낼 `id`는 위 `id`와 동일하게 쓰면 `RestaurantDetailScreen`의 `DetailPlaceId`를 API id로 교체하기 쉽습니다.

---

### 3.5 저장(북마크) 동기화

**현재**: `SavedPlacesStore` — 로컬 JSON만.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/me/saved-places` | 내 저장 목록 |
| PUT | `/me/saved-places` | 전체 치환 동기화 (간단한 MVP) |
| POST | `/me/saved-places` | 한 건 추가 `{ "place_id": "...", "target": "RESTAURANT" }` |
| DELETE | `/me/saved-places/{placeId}` | 삭제 |

**저장 항목 스키마 (서버)**

```json
{
  "place_id": "place_abc",
  "name": "할랄가든 명동점",
  "category": "할랄 식당",
  "distance_line": "명동 · 도보 2분",
  "tags": ["할랄 인증"],
  "target": "RESTAURANT",
  "saved_at": "2026-04-13T12:00:00Z"
}
```

비로그인 사용자는 기존처럼 **로컬만** 유지하고, 로그인 시 `GET` 후 병합하는 방식이 흔합니다.

---

### 3.6 최근 검색

**현재**: `SearchHistoryPreferences` — 로컬 전용.

- **옵션 A**: 계속 로컬만 (API 없음)  
- **옵션 B**: `GET/POST /me/search-history` 로 기기 간 동기화

---

### 3.7 홈 / 사용자 요약

**현재**: `HomeScreen` — "아미나님", "명동역 6번 출구 근처" 고정.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/me/home` | 인사용 닉네임, 추천 카피, 근처 요약 등 |

**응답 (예시)**

```json
{
  "display_name": "아미나",
  "greeting_subtitle": "오늘 명동을 탐험해볼까요?",
  "location_summary": "현재 위치: 명동역 6번 출구 근처",
  "featured_sections": []
}
```

또는 닉네임만 서버에서 받고, **위치 문구는** 클라이언트가 `FusedLocation` + **역지오코딩 API**(Google Maps Geocoding, Kakao Local 등)로 만들 수 있습니다.

---

### 3.8 키블라 방위 · 기도 시간 · 메카 거리

**현재**: `getQiblaDirection()`, `getPrayerTimes()`, `getMeccaDistanceKm()` 더미.

- **방위·거리**: 수식으로 클라이언트만 계산해도 되고, `GET /qibla?lat=&lng=` 로 서버에 맡겨도 됨.
- **기도 시간**: 보통 **Aladhan API**, **Islamic Network** 등 외부 API를 쓰거나, 백엔드가 위도·경도·날짜를 받아 위임합니다.

**자체 API로 감쌀 경우 (예시)**

`GET /prayer/today?lat=37.56&lng=126.98&method=2`

```json
{
  "next_prayer": { "key": "DHUHR", "time_local": "12:15", "remaining_seconds": 9204 },
  "qibla_bearing_deg": 292.4,
  "distance_to_kaaba_km": 8565.2
}
```

---

### 3.9 AR 탐색 — 검색·필터·POI·채팅

**현재**: `ArExploreScreen` — 카테고리/정렬/최근검색/검색결과 리스트·채팅 응답 모두 하드코딩 또는 `sendMessageToAgent()` 더미.

| 구분 | 제안 API |
|------|-----------|
| 카테고리·정렬 옵션 | `GET /ar/explore/meta` → 칩 라벨·정렬 키 목록 |
| 주변 POI (AR 핀) | `GET /ar/pois?lat=&lng=&radius_m=&categories=` |
| POI 상세(건물/층/AI가이드 탭) | `GET /ar/pois/{poiId}/detail` → 탭별 텍스트 또는 구조화 필드 |
| AI 채팅 | `POST /ar/agent/chat` (SSE/스트리밍은 선택) |

**채팅 요청/응답 (예시)**

```http
POST /ar/agent/chat
Content-Type: application/json
```

```json
{
  "session_id": "optional-uuid",
  "message": "눈스퀘어가 뭐야?",
  "context": { "lat": 37.56, "lng": 126.98, "locale": "ko" }
}
```

```json
{
  "reply": "눈스퀘어는 명동의 쇼핑·문화 복합 시설입니다.",
  "session_id": "uuid"
}
```

앱의 `sendMessageToAgent(message: String)`는 위 `POST` 호출로 바꾸면 됩니다.

---

### 3.10 AR 길안내(내비) 화면

**현재**: `ArNavigationMapScreen` — 안내 문구·POI 위치·하단 시트 내용 고정.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/navigation/active` | 현재 안내 세션 (목적지, 다음 턴, 거리 등) |
| GET | `/navigation/pois` | 지도 위 POI 마커 |
| WS (선택) | `/navigation/stream` | 실시간 경로 업데이트 |

실제 경로 계산은 **Google Directions / OSRM / 자체 엔진**과 연동한 뒤, 앱에는 **이미 계산된 스텝 배열**만 내려주는 패턴이 많습니다.

---

### 3.11 프로필

**현재**: `ProfileScreen` 등 UI 위주 — 설정 항목이 서버와 연결될 경우 `GET/PATCH /me/profile` 정도로 확장.

---

## 4. 이미지·정적 에셋

**현재**: `ScanPangFigmaAssets.kt`에 Figma MCP URL 하드코딩.

- 상세/홈 카드 이미지는 장소 상세 API의 `hero_images[]` 또는 CDN URL로 **완전히 대체**하는 것을 권장합니다.
- 만료되는 임시 URL은 운영에서 제거해야 합니다.

---

## 5. 우선순위 제안 (MVP → 확장)

1. **MVP**  
   - `GET /search/places`, `GET /places/{id}`, `GET /places/halal/nearby`  
   - (선택) `GET /me/saved-places` + POST/DELETE
2. **2단계**  
   - 기도실 목록/상세, 홈 요약 또는 역지오코딩 연동
3. **3단계**  
   - AR POI + `POST /ar/agent/chat`, 내비 세션 API

---

## 6. 프론트(Android)에서 바꿀 파일 힌트

| 연동 대상 | 대표 파일 / 심볼 |
|-----------|------------------|
| 검색 결과 | `SearchResultsScreen.kt` |
| 할랄 근처 | `NearbyHalalRestaurantsScreen.kt` |
| 기도실 | `NearbyPrayerRoomsScreen.kt`, `PrayerRoomDetailScreen.kt` |
| 상세·북마크 | `RestaurantDetailScreen.kt`, `SavedPlacesStore.kt` |
| 키블라 더미 | `qibla/QiblaDataProviders.kt` |
| AR 채팅 더미 | `ar/ArExploreAgent.kt` |
| AR 목 데이터 | `screens/ar/ArExploreScreen.kt`, `components/ar/ArPoiFloatingPanel.kt` |
| 홈 문구 | `HomeScreen.kt` |

---

이 문서는 **캡스톤/기획 단계에서 백엔드 팀과 맞출 체크리스트**로 쓰고, 확정된 스펙은 OpenAPI(Swagger)나 Postman Collection으로 옮기면 됩니다.
