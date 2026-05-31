from typing import Optional

from fastapi import Depends, FastAPI, HTTPException
from core.auth import get_current_user_id
from fastapi.encoders import jsonable_encoder
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from schemas.navigation import NavRequest, RouteRequest
from agents.navigation_agent import run_route_search, run_route_agent
from schemas.place import PlaceRequest
from agents.place_insight_agent import run_place_insight_agent
from schemas.store import StoreRequest
from tools.store_tools import get_store_detail
from schemas.convenience import ConvenienceRequest
from agents.search_agent import run_search_agent
from schemas.halal import HalalRequest
from agents.halal_agent import run_halal_agent
from agents.orchestrator_agent import run_orchestrator
from core.session_store import get_session_store
from core.usage_tracker import get_tracker
from schemas.search import SearchRequest, SearchResponse, SearchResultItem, AutocompleteRequest, AutocompleteResponse
from schemas.place_detail import PlaceDetailRequest, PlaceDetailResponse
from schemas.user import (
    UserPreferencesUpsertRequest, UserPreferencesResponse,
    SavedPlacesUpdateRequest, SearchHistoryUpdateRequest,
    RecentlyViewedUpdateRequest,
    InquirySubmitRequest, InquirySubmitResponse,
)
from tools.open_hours_parser import is_open_now_combined as _is_open_now_combined
from tools.translation import translate_fields
import asyncio
import json as _json
import re as _re
from datetime import datetime, timedelta, timezone


def _extract_url(text: str) -> str:
    """'<a href="URL">...</a>' 형태에서 URL만 추출. 일반 URL이면 그대로 반환."""
    if not text:
        return text
    m = _re.search(r'href=["\']([^"\']+)["\']', text)
    return m.group(1) if m else _re.sub(r"<[^>]+>", "", text).strip()


def _strip_html(text: str) -> str:
    """Tour API 텍스트 필드의 HTML 태그를 제거한다. <br>은 줄바꿈으로 변환, 줄 구조 유지."""
    if not text:
        return text
    cleaned = _re.sub(r"<br\s*/?>", "\n", text, flags=_re.IGNORECASE)
    cleaned = _re.sub(r"<[^>]+>", "", cleaned)
    lines = [" ".join(line.split()) for line in cleaned.split("\n")]
    return "\n".join(lines).strip()


def _haversine_m(user_lat: float, user_lng: float, place_lat: float, place_lng: float) -> float:
    import math as _math
    R = 6371000.0
    lat1 = _math.radians(user_lat)
    lat2 = _math.radians(place_lat)
    dlat = lat2 - lat1
    dlng = _math.radians(place_lng - user_lng)
    a = _math.sin(dlat / 2) ** 2 + _math.cos(lat1) * _math.cos(lat2) * _math.sin(dlng / 2) ** 2
    return round(2 * R * _math.asin(_math.sqrt(a)), 1)


async def _ensure_translations(
    pool,
    table: str,
    pk_col: str,
    pk_val: str,
    fields: dict,
    lat: float,
    lng: float,
    language: str,
    existing: dict = None,
    no_translate: bool = False,
) -> dict:
    if language == "ko":
        return {}
    trans = existing or {}
    cached = trans.get(language, {})
    # no_translate: DeepL 호출 없이 이미 캐시된 번역만 반환 (없으면 빈 dict → 원본 폴백).
    if no_translate:
        return cached
    # 이미 번역된 필드는 제외하고 빠진 것만 번역
    missing = {k: v for k, v in fields.items() if k not in cached and v and str(v).strip()}
    if not missing:
        return cached
    new_fields = await translate_fields(missing, lat=lat, lng=lng, lang=language)
    if new_fields:
        merged = {**cached, **new_fields}
        async with pool.acquire() as conn:
            await conn.execute(
                f"UPDATE {table} SET translations = COALESCE(translations, '{{}}'::jsonb) || $1::jsonb WHERE {pk_col} = $2",
                {language: merged}, pk_val,
            )
        return merged
    return cached


def _parse_jsonb_list(val) -> list:
    """JSONB 컬럼 → Python list 변환. single/double encoded 및 asyncpg 반환 타입 모두 처리."""
    if val is None:
        return []
    if isinstance(val, list):
        return val
    try:
        result = _json.loads(val)
        if isinstance(result, list):
            return result
        if isinstance(result, str):
            inner = _json.loads(result)
            return inner if isinstance(inner, list) else []
        return []
    except (ValueError, TypeError):
        return []
from rag.automation.worker import start_worker, stop_worker
from core.db import get_pool, close_pool
from api.h3_buildings import router as h3_buildings_router

app = FastAPI(title="ScanPang Navigation API")

app.include_router(h3_buildings_router)


@app.on_event("startup")
async def _startup():
    await get_session_store().connect()
    await get_tracker().connect()
    await get_pool()
    await start_worker()


@app.on_event("shutdown")
async def _shutdown():
    await stop_worker()
    await close_pool()
    await get_session_store().close()


# ── Orchestrator 스키마 ───────────────────────────────────────────────────

class NavContext(BaseModel):
    """AR 길안내 중 채팅 호출에 frontend 가 같이 전송하는 현재 상태.
    None 이면 일반 모드(검색/장소 정보 등), 값 있으면 nav_guide 라우팅 후보."""
    is_routing: bool = False
    destination_name: str = ""
    direction: str = ""             # "LEFT" | "RIGHT" | "STRAIGHT" | "DESTINATION"
    current_speech: str = ""        # 현재 턴 LLM 안내 ("스타벅스에서 좌회전")
    current_distance_m: int = 0     # 다음 턴까지 거리
    next_direction: str = ""
    next_distance_m: int = 0
    remaining_distance_m: int = 0   # 목적지까지 남은 총 거리
    remaining_time_min: int = 0     # ETA


class AgentChatRequest(BaseModel):
    message: str
    lat: float
    lng: float
    heading: float = 0.0
    language: str = "ko"
    session_id: Optional[str] = None
    nav_context: Optional[NavContext] = None


class AgentChatResponse(BaseModel):
    speech: str
    source_agent: str
    raw_data: dict
    session_id: str


@app.post("/navigation/search")
async def navigation_search(req: NavRequest, user_id: str = Depends(get_current_user_id)):
    """
    1단계: 자연어 메시지 → POI 후보 목록 반환
    앱에서 사용자에게 목적지 확인/선택 후 /navigation/route 호출
    """
    return await run_route_search(req, user_id=user_id)


@app.post("/navigation/route")
async def navigation_route(req: RouteRequest, user_id: str = Depends(get_current_user_id)):
    """
    2단계: 확정된 목적지 → 보행자 경로 계산 + 턴별 TTS 안내 반환
    """
    return await run_route_agent(req, user_id=user_id)


def _sse_chunk(event: str, data) -> str:
    """SSE 이벤트 한 청크를 텍스트로 직렬화한다."""
    if not isinstance(data, str):
        # jsonable_encoder: datetime/UUID/Decimal 등 비직렬화 타입을 JSON 호환으로 변환
        data_str = _json.dumps(jsonable_encoder(data), ensure_ascii=False)
    else:
        data_str = data
    return f"event: {event}\ndata: {data_str}\n\n"


# Railway/nginx 같은 reverse-proxy 환경에서 SSE 가 buffer 되어 끊기는 문제 방지.
# `X-Accel-Buffering: no` 는 nginx 가 응답을 버퍼링하지 않도록 명시 (Railway 의 edge
# proxy 가 이 헤더 인식). `Cache-Control: no-cache, no-transform` 으로 CDN/프록시의
# 캐싱·변형도 차단. 로컬 + ngrok 환경에선 영향 없지만 prod 에선 필수.
_SSE_HEADERS = {
    "Cache-Control": "no-cache, no-transform",
    "X-Accel-Buffering": "no",
    "Connection": "keep-alive",
}


@app.post("/place/query")
async def place_query(req: PlaceRequest, user_id: str = Depends(get_current_user_id)):
    """
    ARCore가 인식한 건물 place_id → AR 오버레이 데이터 + TTS 도슨트 해설 반환
    """
    return await run_place_insight_agent(req, user_id=user_id)


@app.post("/place/query/stream")
async def place_query_stream(req: PlaceRequest, user_id: str = Depends(get_current_user_id)):
    """
    /place/query 의 SSE 스트리밍 버전.
    progress 이벤트로 실제 처리 단계별 진행률을 push 하고, 완료 시 complete 이벤트로 결과 전달.
    """
    async def generate():
        try:
            import asyncio
            queue: asyncio.Queue = asyncio.Queue()

            async def cb(pct: int, msg: str):
                await queue.put((pct, msg))

            async def run():
                try:
                    result = await run_place_insight_agent(req, progress_cb=cb, user_id=user_id)
                    return result
                finally:
                    await queue.put(None)  # 예외 발생 시에도 sentinel 보장

            task = asyncio.ensure_future(run())

            while True:
                item = await queue.get()
                if item is None:
                    break
                pct, msg = item
                yield _sse_chunk("progress", {"progress": pct, "message": msg})

            result = await task
            yield _sse_chunk("complete", {"result": result})
        except Exception as e:
            yield _sse_chunk("error", str(e))

    return StreamingResponse(generate(), media_type="text/event-stream", headers=_SSE_HEADERS)


@app.post("/place/store")
async def place_store(req: StoreRequest, user_id: str = Depends(get_current_user_id)):
    """
    사용자가 층별 매장 탭 → 매장 상세 정보 반환 (Kakao on-demand + Chroma 캐싱)
    """
    detail = await get_store_detail(
        req.place_id, req.store_name,
        lat=req.lat, lng=req.lng,
        language=req.language,
    )
    if isinstance(detail, dict):
        details = detail.get("details") or {}
        schedule = details.get("schedule") if isinstance(details, dict) else None
        detail["is_open_now"] = _is_open_now_combined(
            detail.get("open_hours") or "", schedule,
        )
    return detail


@app.post("/place/store/stream")
async def place_store_stream(req: StoreRequest, user_id: str = Depends(get_current_user_id)):
    """
    /place/store 의 SSE 스트리밍 버전.
    """
    async def generate():
        try:
            import asyncio
            queue: asyncio.Queue = asyncio.Queue()

            async def cb(pct: int, msg: str):
                await queue.put((pct, msg))

            async def run():
                try:
                    detail = await get_store_detail(
                        req.place_id, req.store_name,
                        lat=req.lat, lng=req.lng,
                        language=req.language,
                        progress_cb=cb,
                    )
                    if isinstance(detail, dict):
                        details = detail.get("details") or {}
                        schedule = details.get("schedule") if isinstance(details, dict) else None
                        detail["is_open_now"] = _is_open_now_combined(
                            detail.get("open_hours") or "", schedule,
                        )
                    return detail
                finally:
                    await queue.put(None)  # 예외 발생 시에도 sentinel 보장

            task = asyncio.ensure_future(run())

            while True:
                item = await queue.get()
                if item is None:
                    break
                pct, msg = item
                yield _sse_chunk("progress", {"progress": pct, "message": msg})

            result = await task
            yield _sse_chunk("complete", {"result": result})
        except Exception as e:
            yield _sse_chunk("error", str(e))

    return StreamingResponse(generate(), media_type="text/event-stream", headers=_SSE_HEADERS)



@app.post("/user/preferences", response_model=UserPreferencesResponse)
async def user_preferences_upsert(req: UserPreferencesUpsertRequest):
    """
    온보딩 완료/프로필 수정 시 frontend 호출.
    user_id 는 frontend 가 발급한 device UUID (장기적으론 Supabase Auth uid).
    NULL/빈 필드는 기존 값 유지 — COALESCE 패턴.
    """
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            INSERT INTO user_preferences
                (user_id, display_name, language, value_added, saved_places,
                 search_history, recently_viewed_places, created_at, updated_at)
            VALUES ($1, $2, $3, $4, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                display_name = COALESCE(EXCLUDED.display_name, user_preferences.display_name),
                language     = COALESCE(EXCLUDED.language,     user_preferences.language),
                value_added  = COALESCE(EXCLUDED.value_added,  user_preferences.value_added),
                updated_at   = NOW()
            RETURNING user_id, display_name, language, value_added,
                      saved_places::text AS saved_places,
                      search_history::text AS search_history,
                      recently_viewed_places::text AS recently_viewed_places
            """,
            req.user_id, req.display_name, req.language, req.value_added,
        )
    return UserPreferencesResponse(
        user_id=row["user_id"],
        display_name=row["display_name"],
        language=row["language"],
        value_added=row["value_added"],
        saved_places=_parse_jsonb_list(row["saved_places"]),
        search_history=_parse_jsonb_list(row["search_history"]),
        recently_viewed_places=_parse_jsonb_list(row["recently_viewed_places"]),
    )


@app.get("/user/preferences/{user_id}", response_model=UserPreferencesResponse)
async def user_preferences_get(user_id: str):
    """user_id 로 user_preferences 조회 — 앱 실행 시 첫 sync 용."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT user_id, display_name, language, value_added,
                   saved_places::text AS saved_places,
                   search_history::text AS search_history,
                   recently_viewed_places::text AS recently_viewed_places
            FROM user_preferences WHERE user_id = $1
            """,
            user_id,
        )
    if row is None:
        raise HTTPException(status_code=404, detail="user_preferences not found")
    return UserPreferencesResponse(
        user_id=row["user_id"],
        display_name=row["display_name"],
        language=row["language"],
        value_added=row["value_added"],
        saved_places=_parse_jsonb_list(row["saved_places"]),
        search_history=_parse_jsonb_list(row["search_history"]),
        recently_viewed_places=_parse_jsonb_list(row["recently_viewed_places"]),
    )


@app.put("/user/preferences/{user_id}/saved-places")
async def user_saved_places_update(user_id: str, req: SavedPlacesUpdateRequest):
    """SavedPlacesStore 변경 시 호출. 전체 list 교체 (delta sync 가 아닌 full replace)."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        # core/db.py 의 jsonb codec 이 list 를 자동 직렬화. _json.dumps + ::jsonb
        # 하면 더블 인코딩(jsonb_typeof=string)돼서 GET 시 list 가 아닌 string 으로 디코드.
        await conn.execute(
            """
            INSERT INTO user_preferences (user_id, saved_places, created_at, updated_at)
            VALUES ($1, $2, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                saved_places = EXCLUDED.saved_places,
                updated_at   = NOW()
            """,
            user_id, req.items,
        )
    return {"user_id": user_id, "count": len(req.items)}


@app.put("/user/preferences/{user_id}/search-history")
async def user_search_history_update(user_id: str, req: SearchHistoryUpdateRequest):
    """SearchHistoryPreferences 변경 시 호출. 최근 검색어 list 전체 교체."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO user_preferences (user_id, search_history, created_at, updated_at)
            VALUES ($1, $2, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                search_history = EXCLUDED.search_history,
                updated_at     = NOW()
            """,
            user_id, req.items,
        )
    return {"user_id": user_id, "count": len(req.items)}


@app.put("/user/preferences/{user_id}/recently-viewed")
async def user_recently_viewed_update(user_id: str, req: RecentlyViewedUpdateRequest):
    """RecentlyViewedStore 변경 시 호출. '최근 본 장소' list 전체 교체.
    saved_places 와 동일 — jsonb codec 이 list 직렬화 담당, json.dumps 금지(더블 인코딩)."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO user_preferences (user_id, recently_viewed_places, created_at, updated_at)
            VALUES ($1, $2, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                recently_viewed_places = EXCLUDED.recently_viewed_places,
                updated_at             = NOW()
            """,
            user_id, req.items,
        )
    return {"user_id": user_id, "count": len(req.items)}


@app.post("/user/inquiry", response_model=InquirySubmitResponse)
async def user_inquiry_submit(req: InquirySubmitRequest):
    """
    1:1 문의 접수 — ContactScreen 의 제출 버튼이 호출.
    inquiries 테이블에 row 생성하고 id+status 반환. 추후 status 변경/응답은
    어드민 도구에서 처리. 이메일/Slack 알림은 이 핸들러에 후속 추가 가능.
    """
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            INSERT INTO inquiries (user_id, category, title, content)
            VALUES ($1, $2, $3, $4)
            RETURNING id, user_id, status
            """,
            req.user_id, req.category, req.title, req.content,
        )
    return InquirySubmitResponse(
        id=row["id"],
        user_id=row["user_id"],
        status=row["status"],
    )


@app.post("/convenience/query")
async def convenience_query(req: ConvenienceRequest, user_id: str = Depends(get_current_user_id)):
    """
    카테고리 탭 or 텍스트 검색 → 주변 편의시설 목록 반환
    category 있으면 LLM 없이 바로 검색, message만 있으면 LLM으로 카테고리 추출
    """
    return await run_search_agent(req, user_id=user_id)


@app.post("/halal/query")
async def halal_query(req: HalalRequest, user_id: str = Depends(get_current_user_id)):
    """
    Halal Agent: 기도 시간, 키블라 방향, 할랄 식당, 기도실
    category: prayer_time | qibla | restaurant | prayer_room
    """
    return await run_halal_agent(req, user_id=user_id)


@app.post("/ar/agent/chat", response_model=AgentChatResponse)
async def ar_agent_chat(req: AgentChatRequest, user_id: str = Depends(get_current_user_id)):
    """
    LangGraph Orchestrator: 단일 엔드포인트에서 5개 에이전트를 자동 라우팅.
    intent_classifier(GPT-4o) → place | navigation | nav_guide | halal | convenience → 통합 응답.
    nav_context 가 있으면 길안내 중 사용자 발화로 간주, nav_guide 후보로 분류.
    """
    result = await run_orchestrator(
        message=req.message,
        lat=req.lat,
        lng=req.lng,
        heading=req.heading,
        language=req.language,
        session_id=req.session_id,
        nav_context=req.nav_context.model_dump() if req.nav_context else None,
        user_id=user_id,
    )
    return result



_OUTDOOR_CATEGORIES = {"restroom", "subway", "locker", "prayer_room", "halal_restaurant", "vegan_restaurant", "vegan_cafe", "accommodation", "tourist", "cultural"}


def _vegan_category_label(vegan_level: str, category_key: str, language: str = "ko") -> str:
    """'채식전문 · 음식점' 또는 'Vegan Only · Restaurant' 형식의 카테고리 레이블 반환."""
    if language == "en":
        type_label = "Cafe" if category_key == "vegan_cafe" else "Restaurant"
        level_label = {
            "채식전문": "Vegan Only",
            "채식가능": "Vegetarian-Friendly",
        }.get(vegan_level, vegan_level)
    else:
        type_label = "카페" if category_key == "vegan_cafe" else "음식점"
        level_label = vegan_level
    return f"{level_label} · {type_label}" if level_label else type_label


def _norm_subway_station(s: str) -> str:
    """'강남역' / '강남' / '서울 역' → '강남' 등 비교용 정규화 (끝 '역' 제거 + 공백 제거)."""
    s = (s or "").strip()
    if s.endswith("역"):
        s = s[:-1]
    return s.replace(" ", "")


async def _subway_en_name_map() -> tuple[dict, dict]:
    """subway_exits.translations[en] → 영문 역명 룩업 2종 반환.
    - full:       {(norm_station, line): {name, line}}  — 역+호선 정확 매칭
    - by_station: {norm_station: {name, line}}           — 호선 불명 시 역 단위 fallback
    영문 번역이 채워진 행만 사용. 없으면 호출처가 원본(한국어) 유지.
    """
    pool = await get_pool()
    async with pool.acquire() as conn:
        erows = await conn.fetch(
            "SELECT DISTINCT station_name, line, translations FROM subway_exits "
            "WHERE translations::text <> '{}'",
        )
    full: dict = {}
    by_station: dict = {}
    for er in erows:
        t = (_json.loads(er["translations"]).get("en") or {})
        if t.get("name"):
            k = _norm_subway_station(er["station_name"])
            full[(k, er["line"])] = t
            by_station.setdefault(k, t)
    return full, by_station


async def _outdoor_search(category_key: str, req: SearchRequest) -> SearchResponse:
    """건물 외 카테고리(화장실/지하철/물품보관/기도실/할랄식당)는 store_details 가 아닌
    각자 출처 테이블·JSON 에서 거리 정렬로 반환.
    lat/lng 없으면 default(명동) 좌표 fallback."""
    from tools.convenience_tools import (
        public_restroom_search, seoul_locker_search, kakao_category_search,
        prayer_room_search,
    )
    from tools.halal_tools import halal_restaurant_search
    from tools.vegan_tools import vegan_restaurant_search
    lat = req.lat or 37.5636
    lng = req.lng or 126.9822

    if category_key == "restroom":
        rows = await public_restroom_search(lat, lng, radius=2000)
        # 화장실 검색은 JSON 캐시 출처라 translations 가 없다. 영어 모드면 public_restrooms
        # DB 에서 mng_no 로 캐시된 번역을 배치 조회해 붙여준다(상세화면과 동일 영어명 노출).
        if req.language != "ko" and rows:
            mng_nos = [r["mng_no"] for r in rows if r.get("mng_no")]
            if mng_nos:
                pool = await get_pool()
                async with pool.acquire() as conn:
                    trows = await conn.fetch(
                        "SELECT mng_no, COALESCE(translations::text, '{}') AS translations "
                        "FROM public_restrooms WHERE mng_no = ANY($1::text[])",
                        mng_nos,
                    )
                tmap = {tr["mng_no"]: tr["translations"] for tr in trows}
                for r in rows:
                    r["translations"] = tmap.get(r.get("mng_no"), "{}")
    elif category_key == "subway":
        rows = await kakao_category_search("subway", lat, lng, radius=1500)
        # Kakao 는 한국어 역명("강남역 2호선")만 준다. 영어 모드면 subway_exits.translations[en]
        # 와 (역명, 호선)으로 매칭해 "Gangnam Station Line 2" 로 교체.
        if req.language == "en" and rows:
            full, by_station = await _subway_en_name_map()
            for r in rows:
                toks = (r.get("name") or "").split()
                if not toks:
                    continue
                skey = _norm_subway_station(toks[0])
                line = toks[-1] if len(toks) >= 2 else ""
                t = full.get((skey, line)) or by_station.get(skey)
                if t:
                    r["name"] = f"{t.get('name','')} {t.get('line','')}".strip()
    elif category_key == "locker":
        rows = await seoul_locker_search(lat, lng, radius=1500)
        # 물품보관함 이름은 "명동역 물품보관함" 형식 → 역명을 영문으로 바꿔 "Myeongdong Station Luggage Locker".
        if req.language == "en" and rows:
            _, by_station = await _subway_en_name_map()
            for r in rows:
                base = (r.get("name") or "").replace("물품보관함", "").strip()
                t = by_station.get(_norm_subway_station(base))
                if t:
                    r["name"] = f"{t.get('name','')} Luggage Locker".strip()
    elif category_key == "prayer_room":
        rows = await prayer_room_search(lat, lng, radius=2000)
    elif category_key == "halal_restaurant":
        # halal_tools 는 name_ko / address / opening_hours 키를 쓰므로 표준 키로 변환
        raw = await halal_restaurant_search(lat, lng, radius=0)
        rows = [
            {
                "name":         (r.get("name_en") or r.get("name_ko") or "") if req.language == "en" else (r.get("name_ko") or r.get("name_en") or ""),
                "address":      r.get("address", ""),
                "phone":        r.get("phone", ""),
                "lat":          r.get("lat"),
                "lng":          r.get("lng"),
                "distance_m":   r.get("distance_m", 0),
                "open_hours":   r.get("opening_hours") or "",
                "halal_id":     r.get("restaurant_id", ""),
                "halal_type":   r.get("halal_type", ""),
            }
            for r in raw
        ]
    elif category_key in ("vegan_restaurant", "vegan_cafe"):
        src_key = "restaurant" if category_key == "vegan_restaurant" else "cafe"
        raw = await vegan_restaurant_search(lat, lng, radius=0, category_key=src_key)
        rows = [
            {
                "name":         r.get("name", ""),
                "address":      r.get("address", ""),
                "phone":        r.get("phone", ""),
                "lat":          r.get("lat"),
                "lng":          r.get("lng"),
                "distance_m":   r.get("distance_m", 0),
                "open_hours":   r.get("open_hours") or "",
                "vegan_id":     r.get("vegan_id", ""),
                "vegan_level":  r.get("vegan_level", ""),
                "vegan_menu":   r.get("vegan_menu", ""),
                "translations": r.get("translations", "{}"),
            }
            for r in raw
        ]
    elif category_key == "accommodation":
        return await _accommodation_search(req, lat, lng, language=req.language)
    elif category_key in ("tourist", "cultural"):
        return await _tourist_search(req, lat, lng, language=req.language)
    else:
        rows = []

    rows_sorted = sorted(rows, key=lambda r: r.get("distance_m", 0))[:req.limit]
    # id 패턴: {category_key}__{원천 PK}
    # restroom = mng_no, halal_restaurant = restaurant_id, 그 외는 이름 fallback
    def _outdoor_id(category: str, r: dict) -> str:
        if category == "restroom" and r.get("mng_no"):
            return f"restroom__{r['mng_no']}"
        if category == "halal_restaurant" and r.get("halal_id"):
            return f"halal__{r['halal_id']}"
        if category in ("vegan_restaurant", "vegan_cafe") and r.get("vegan_id"):
            return f"vegan__{r['vegan_id']}"
        if category == "prayer_room" and r.get("room_id"):
            return f"prayer__{r['room_id']}"
        # subway/locker: 매장명 = "역명 호선" 또는 보관함 이름. _subway_detail / _locker_detail 가 query 로 사용.
        if category == "subway":
            return f"subway__{r.get('name','')}"
        if category == "locker":
            return f"locker__{r.get('name','')}"
        return f"__outdoor__{category}__{r.get('name','')}"

    # 화면 표시용 카테고리 라벨
    _LABEL_KO = {
        "restroom": "화장실", "subway": "지하철역", "locker": "물품보관함",
        "prayer_room": "기도실", "halal_restaurant": "할랄 식당",
        "vegan_restaurant": "비건 식당", "vegan_cafe": "비건 카페",
    }
    _LABEL_EN = {
        "restroom": "Restroom", "subway": "Subway Station", "locker": "Luggage Locker",
        "prayer_room": "Prayer Room", "halal_restaurant": "Halal Restaurant",
        "vegan_restaurant": "Vegan Restaurant", "vegan_cafe": "Vegan Cafe",
    }
    LABEL = _LABEL_EN if req.language == "en" else _LABEL_KO

    # 캐시된 언어별 번역 적용 — 매장 상세화면과 동일하게 검색 결과도 영어 매장명/주소 노출.
    # translations 컬럼이 없는 출처(subway/locker 등)는 원본(한국어) 그대로.
    def _outdoor_tl(r: dict, field: str) -> str:
        if req.language == "ko":
            return ""
        raw = r.get("translations")
        if not raw:
            return ""
        try:
            t = raw if isinstance(raw, dict) else _json.loads(raw)
        except (ValueError, TypeError):
            return ""
        if not isinstance(t, dict):
            return ""
        return (t.get(req.language, {}) or {}).get(field, "") or ""

    results = [
        SearchResultItem(
            id=_outdoor_id(category_key, r),
            store_name=_outdoor_tl(r, "name") or r.get("name", ""),
            category=(_vegan_category_label(r.get("vegan_level", ""), category_key, req.language)
                      if category_key in ("vegan_restaurant", "vegan_cafe")
                      else LABEL.get(category_key, category_key)),
            category_key=category_key,
            addr=_outdoor_tl(r, "addr") or r.get("address", ""),
            phone=r.get("phone", ""),
            lat=r.get("lat"),
            lng=r.get("lng"),
            distance_m=r.get("distance_m"),
            is_open_now=_is_open_now_combined(r.get("open_hours") or "", None),
            halal_type=r.get("halal_type") or None,
        )
        for r in rows_sorted
    ]
    return SearchResponse(query=req.query, count=len(results), results=results)


@app.post("/place/autocomplete", response_model=AutocompleteResponse)
async def place_autocomplete(req: AutocompleteRequest):
    """
    타이핑 중 debounce 후 호출. store_name prefix match + pg_trgm similarity 로 제안 반환.
    """
    q = (req.q or "").strip()
    if not q:
        return AutocompleteResponse(suggestions=[])

    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            WITH matches AS (
                SELECT DISTINCT store_name
                FROM store_details
                WHERE store_name ILIKE $1
                   OR similarity(
                        regexp_replace(store_name, '\\s+', '', 'g'),
                        regexp_replace($2, '\\s+', '', 'g')
                      ) >= 0.25
            )
            SELECT store_name
            FROM matches
            ORDER BY
              CASE WHEN store_name ILIKE $1 THEN 0 ELSE 1 END,
              similarity(
                regexp_replace(store_name, '\\s+', '', 'g'),
                regexp_replace($2, '\\s+', '', 'g')
              ) DESC
            LIMIT $3
            """,
            f"%{q}%",
            q,
            req.limit,
        )
    return AutocompleteResponse(suggestions=[r["store_name"] for r in rows])


@app.post("/place/search", response_model=SearchResponse)
async def place_search(req: SearchRequest):
    """
    통합 검색 — 쿼리 분류 후 카테고리별 출처로 자동 라우팅:
      - 건물 외 카테고리(화장실/지하철역/물품보관함/기도실): 각자 출처 테이블 거리 검색
      - 건물 내 매장(카페/식당/...): store_details ILIKE 매칭

    SearchDefaultScreen 호출 시 lat/lng 같이 보내야 outdoor 거리 정렬 가능.
    """
    q = (req.query or "").strip()
    if not q:
        return SearchResponse(query=req.query, count=0, results=[])

    # 방안 1: 노이즈 단어 제거 — "근처 스타벅스" → "스타벅스", "근처 카페" → "카페"
    # trgm 유사도 계산과 category 분류 모두 clean_q 기준으로 수행.
    _NOISE_RE = _re.compile(r'\s*(근처|주변에|주변|가까운|nearby|near)\s*', _re.IGNORECASE)
    clean_q = _NOISE_RE.sub(' ', q).strip() or q

    # 쿼리 → category_key. 노이즈 제거 후 clean_q 기준으로 분류.
    from tools.category_classifier import classify_query
    category_key = classify_query(clean_q)
    if category_key in _OUTDOOR_CATEGORIES:
        return await _outdoor_search(category_key, req)

    pool = await get_pool()
    # 사용자 위치 — 없으면 명동 fallback. 거리 정렬 위해 필수.
    user_lat = req.lat if req.lat is not None else 37.5636
    user_lng = req.lng if req.lng is not None else 126.9822
    used_fallback = req.lat is None or req.lng is None
    print(f"[place_search] q={q!r} clean_q={clean_q!r} category_key={category_key!r} "
          f"user=({user_lat:.4f},{user_lng:.4f}){' [FALLBACK 명동]' if used_fallback else ''}")
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT * FROM (
              SELECT id, store_name, category, category_key, addr, phone,
                     place_id, lat, lng, floor, image_urls, open_hours, details,
                     COALESCE(translations::text, '{}') AS translations,
                     last_updated,
                     CASE
                       WHEN lat IS NOT NULL AND lng IS NOT NULL THEN
                         ST_Distance(
                           ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography,
                           ST_SetSRID(ST_MakePoint($4::float, $5::float), 4326)::geography
                         )
                       ELSE NULL
                     END AS dist_m
              FROM store_details
              WHERE store_name ILIKE $1
                 OR category ILIKE $1
                 -- 영어 등 비-한국어 검색어가 한국어 매장 데이터와 안 맞는 문제 보완:
                 -- 캐시된 현지화 매장명/카테고리(translations[$7])도 매칭. 예: "chicken" → "BBQ Chicken".
                 OR translations -> $7 ->> 'name' ILIKE $1
                 OR translations -> $7 ->> 'category' ILIKE $1
                 OR similarity(
                      regexp_replace(store_name, '\\s+', '', 'g'),
                      regexp_replace($6, '\\s+', '', 'g')
                    ) >= 0.3
                 OR ($3 != 'other' AND category_key = $3)
            ) sub
            ORDER BY
              CASE
                WHEN $3 = 'other' AND store_name ILIKE $1 THEN 0
                WHEN $3 = 'other' AND similarity(
                       regexp_replace(store_name, '\\s+', '', 'g'),
                       regexp_replace($6, '\\s+', '', 'g')
                     ) >= 0.6 THEN 1
                WHEN $3 = 'other' AND similarity(
                       regexp_replace(store_name, '\\s+', '', 'g'),
                       regexp_replace($6, '\\s+', '', 'g')
                     ) >= 0.3 THEN 2
                WHEN category ILIKE $1 THEN 3
                ELSE 4
              END,
              dist_m NULLS LAST
            LIMIT $2
            """,
            f"%{clean_q}%",  # ILIKE도 clean_q 기준
            req.limit,
            category_key,
            user_lng, user_lat,
            clean_q,          # trgm도 clean_q 기준
            req.language,     # $7 — translations[language] 매칭용
        )

    results: list[SearchResultItem] = []
    for r in rows:
        raw_imgs = r["image_urls"]
        first_img: Optional[str] = None
        if raw_imgs:
            try:
                imgs = raw_imgs if isinstance(raw_imgs, list) else _json.loads(raw_imgs)
                if imgs:
                    first_img = imgs[0]
            except (ValueError, TypeError):
                first_img = None

        # details JSONB → schedule 추출 (정규화된 구조 있으면 그걸로 is_open_now 정확 판정)
        raw_details = r["details"]
        schedule = None
        if raw_details:
            try:
                d = raw_details if isinstance(raw_details, dict) else _json.loads(raw_details)
                if isinstance(d, dict):
                    schedule = d.get("schedule")
            except (ValueError, TypeError):
                schedule = None

        _rt = r["translations"]
        if isinstance(_rt, str):
            try: _rt = _json.loads(_rt)
            except Exception: _rt = {}
        if not isinstance(_rt, dict):
            _rt = {}
        _tl = _rt.get(req.language, {})
        # 캐시 없으면 백그라운드 번역 트리거 (현재 요청은 한국어로 즉시 반환, 다음 요청부터 영어)
        if req.language != "ko" and not _tl and r["lat"] is not None and r["lng"] is not None:
            asyncio.ensure_future(_ensure_translations(
                pool, "store_details", "id", r["id"],
                {
                    "name":     r["store_name"] or "",
                    "addr":     r["addr"] or "",
                    "category": r["category"] or "",
                },
                lat=float(r["lat"]), lng=float(r["lng"]),
                language=req.language, existing=_rt,
            ))
        results.append(
            SearchResultItem(
                id=r["id"],
                store_name=_tl.get("name") or r["store_name"],
                category=_tl.get("category") or r["category"],
                category_key=r["category_key"],
                addr=_tl.get("addr") or r["addr"],
                phone=r["phone"],
                place_id=r["place_id"],
                lat=r["lat"],
                lng=r["lng"],
                floor=r["floor"],
                image_url=first_img,
                distance_m=round(r["dist_m"], 1) if r["dist_m"] is not None else None,
                is_open_now=_is_open_now_combined(r["open_hours"], schedule),
            )
        )

    return SearchResponse(query=req.query, count=len(results), results=results)


async def _restroom_detail(mng_no: str, language: str = "ko", no_translate: bool = False) -> PlaceDetailResponse:
    """public_restrooms 테이블 1건 → PlaceDetailResponse.
    details 키는 store_details schema 와 동일 — male_toilt_cnt, has_disabled 등."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT mng_no, name, type, addr_road, addr_lot, phone, open_hours, "
            "       lat, lng, raw, COALESCE(translations::text, '{}') AS translations "
            "FROM public_restrooms WHERE mng_no = $1",
            mng_no,
        )
    if row is None:
        raise HTTPException(status_code=404, detail=f"화장실 '{mng_no}' 없음")
    raw = row["raw"]
    if isinstance(raw, str):
        try: raw = _json.loads(raw)
        except Exception: raw = {}
    if not isinstance(raw, dict):
        raw = {}

    def yn(v): return (str(v) or "").upper() == "Y"
    def pos_int(v):
        try: return int(v or 0) > 0
        except (ValueError, TypeError): return False

    details = {
        "name":              raw.get("RSTRM_NM") or row["name"] or "",
        "type":              raw.get("SE_NM") or row["type"] or "",
        "mng_inst":          raw.get("MNG_INST_NM") or "",
        "male_toilt_cnt":    raw.get("MALE_TOILT_CNT") or "",
        "female_toilt_cnt":  raw.get("FEMALE_TOILT_CNT") or "",
        "has_disabled":      pos_int(raw.get("MALE_FRDBL_TOILT_CNT")) or pos_int(raw.get("FEMALE_FRDBL_TOILT_CNT")),
        "has_child":         pos_int(raw.get("MALE_CHLD_TOILT_CNT")) or pos_int(raw.get("FEMALE_CHLD_TOILT_CNT")),
        "has_diaper_table":  yn(raw.get("DIAP_EXCHCON_EN")),
        "has_emergency_bell":yn(raw.get("EMRGNCBLL_INSTL_YN")),
        "has_cctv":          yn(raw.get("RSTRM_ENTRAN_CCTV_INSTL_EN")),
        "waste_method":      raw.get("WSTE_PRCS_MTH_NM") or "",
    }
    open_hours = row["open_hours"] or ""
    _rt = row["translations"]
    if isinstance(_rt, str):
        try: _rt = _json.loads(_rt)
        except Exception: _rt = {}
    if not isinstance(_rt, dict):
        _rt = {}
    if language != "ko":
        _t = await _ensure_translations(
            pool, "public_restrooms", "mng_no", mng_no,
            {"name": row["name"] or "", "addr": row["addr_road"] or row["addr_lot"] or "", "open_hours": open_hours},
            lat=float(row["lat"] or 0), lng=float(row["lng"] or 0), language=language, existing=_rt,
            no_translate=no_translate,
        )
    else:
        _t = {}
    return PlaceDetailResponse(
        id=f"restroom__{row['mng_no']}",
        store_name=_t.get("name") or row["name"] or "",
        place_id=None,
        lat=row["lat"], lng=row["lng"],
        category=details["type"],
        category_key="restroom",
        addr=_t.get("addr") or row["addr_road"] or row["addr_lot"] or "",
        phone=row["phone"] or "",
        floor=None,
        homepage=None,
        place_url=None,
        open_hours=_t.get("open_hours") or open_hours,
        closed_days=None,
        is_open_now=_is_open_now_combined(open_hours, None),
        image_urls=[],
        details=details,
        source="public_restroom",
        last_updated=None,
    )


async def _halal_detail(restaurant_id: str, language: str = "ko", no_translate: bool = False) -> PlaceDetailResponse:
    """halal_restaurants 테이블 1건 → PlaceDetailResponse."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT restaurant_id, name_ko, name_en, halal_type,
                   muslim_cooks_available, no_alcohol_sales,
                   cuisine_type::text AS cuisine_type,
                   menu_examples::text AS menu_examples,
                   short_description_ko, address, phone,
                   opening_hours::text AS opening_hours,
                   break_time::text AS break_time,
                   last_order::text AS last_order,
                   image_urls::text AS image_urls,
                   lat, lng,
                   COALESCE(translations::text, '{}') AS translations
            FROM halal_restaurants WHERE restaurant_id = $1
            """,
            restaurant_id,
        )
    if row is None:
        raise HTTPException(status_code=404, detail=f"할랄 식당 '{restaurant_id}' 없음")

    def _j(s):
        if not s: return None
        try: return _json.loads(s)
        except Exception: return None

    oh_raw = _j(row["opening_hours"]) or {}
    kst = timezone(timedelta(hours=9))
    days = ["mon","tue","wed","thu","fri","sat","sun"]
    today_idx = datetime.now(kst).weekday()
    oh_today = oh_raw.get(days[today_idx], "") if isinstance(oh_raw, dict) else ""
    open_hours_str = oh_today or (str(oh_raw) if not isinstance(oh_raw, dict) else "")

    details = {
        "halal_type":             row["halal_type"] or "",
        "muslim_cooks_available": row["muslim_cooks_available"],
        "no_alcohol_sales":       row["no_alcohol_sales"],
        "cuisine_type":           _j(row["cuisine_type"]) or [],
        "menu_examples":          _j(row["menu_examples"]) or [],
        "short_description_ko":   row["short_description_ko"] or "",
        "break_time":             _j(row["break_time"]) or {},
        "last_order":             _j(row["last_order"]) or {},
        "weekly_open_hours":      oh_raw if isinstance(oh_raw, dict) else {},
    }
    _lat_h = float(row["lat"]) if row["lat"] is not None else None
    _lng_h = float(row["lng"]) if row["lng"] is not None else None
    _rt = row["translations"]
    if isinstance(_rt, str):
        try: _rt = _json.loads(_rt)
        except Exception: _rt = {}
    if not isinstance(_rt, dict):
        _rt = {}
    if language != "ko":
        _t = await _ensure_translations(
            pool, "halal_restaurants", "restaurant_id", restaurant_id,
            {
                "name":                 row["name_ko"] or row["name_en"] or "",
                "addr":                 row["address"] or "",
                "open_hours":           open_hours_str,
                "short_description_ko": row["short_description_ko"] or "",
            },
            lat=_lat_h or 0, lng=_lng_h or 0, language=language, existing=_rt,
            no_translate=no_translate,
        )
        if _t.get("short_description_ko"):
            details["short_description_ko"] = _t["short_description_ko"]
    else:
        _t = {}
    return PlaceDetailResponse(
        id=f"halal__{row['restaurant_id']}",
        store_name=_t.get("name") or row["name_ko"] or row["name_en"] or "",
        place_id=None,
        lat=_lat_h,
        lng=_lng_h,
        category="Halal Restaurant" if language == "en" else "할랄 식당",
        category_key="halal_restaurant",
        addr=_t.get("addr") or row["address"] or "",
        phone=row["phone"] or "",
        floor=None,
        homepage=None,
        place_url=None,
        open_hours=_t.get("open_hours") or open_hours_str,
        closed_days=None,
        is_open_now=_is_open_now_combined(open_hours_str, None),
        image_urls=(_j(row["image_urls"]) or []),
        details=details,
        source="halal_restaurants",
        last_updated=None,
    )


async def _vegan_detail(vegan_id: str, language: str = "ko", no_translate: bool = False) -> PlaceDetailResponse:
    """vegan_restaurants 테이블 1건 → PlaceDetailResponse."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, store_name, category, category_key, addr, phone,
                   details::text AS details,
                   open_hours, closed_days, homepage,
                   image_urls::text AS image_urls,
                   floor, place_url, lat, lng, last_updated,
                   COALESCE(translations::text, '{}') AS translations
            FROM vegan_restaurants WHERE id = $1
            """,
            vegan_id,
        )
    if row is None:
        raise HTTPException(status_code=404, detail=f"비건 식당 '{vegan_id}' 없음")

    def _j(s):
        if not s: return None
        try: return _json.loads(s)
        except Exception: return None

    d = _j(row["details"]) or {}
    src_category_key = row["category_key"] or "restaurant"
    resp_category_key = "vegan_cafe" if src_category_key == "cafe" else "vegan_restaurant"
    vegan_level = d.get("vegan_level", "")
    resp_category = _vegan_category_label(vegan_level, resp_category_key, language)
    open_hours = row["open_hours"] or ""
    _lat_v = float(row["lat"]) if row["lat"] is not None else None
    _lng_v = float(row["lng"]) if row["lng"] is not None else None
    _rt = row["translations"]
    if isinstance(_rt, str):
        try: _rt = _json.loads(_rt)
        except Exception: _rt = {}
    if not isinstance(_rt, dict):
        _rt = {}
    if language != "ko":
        _t = await _ensure_translations(
            pool, "vegan_restaurants", "id", vegan_id,
            {
                "name":        row["store_name"] or "",
                "addr":        row["addr"] or "",
                "open_hours":  open_hours,
                "closed_days": row["closed_days"] or "",
                "vegan_level": d.get("vegan_level", ""),
                "vegan_menu":  d.get("vegan_menu", ""),
            },
            lat=_lat_v or 0, lng=_lng_v or 0, language=language, existing=_rt,
            no_translate=no_translate,
        )
    else:
        _t = {}
    return PlaceDetailResponse(
        id=f"vegan__{row['id']}",
        store_name=_t.get("name") or row["store_name"] or "",
        place_id=None,
        lat=_lat_v,
        lng=_lng_v,
        category=resp_category,
        category_key=resp_category_key,
        addr=_t.get("addr") or row["addr"] or "",
        phone=row["phone"] or "",
        floor=row["floor"] or None,
        homepage=row["homepage"] or None,
        place_url=row["place_url"] or None,
        open_hours=_t.get("open_hours") or open_hours,
        closed_days=_t.get("closed_days") or row["closed_days"] or None,
        is_open_now=_is_open_now_combined(open_hours, None),
        image_urls=(_j(row["image_urls"]) or []),
        details={
            "vegan_level":     _t.get("vegan_level") or d.get("vegan_level", ""),
            "vegan_menu":      _t.get("vegan_menu") or d.get("vegan_menu", ""),
            "restaurant_type": d.get("restaurant_type", ""),
        },
        source="vegan_restaurants",
        last_updated=row["last_updated"].isoformat() if row["last_updated"] else None,
    )


async def _prayer_detail(room_id: str, language: str = "ko", no_translate: bool = False) -> PlaceDetailResponse:
    """prayer_rooms 테이블 1건 → PlaceDetailResponse."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT room_id, name, name_en, address, phone, open_hours,
                   floor, lat, lng,
                   facilities::text AS facilities,
                   availability_status, capacity, notes,
                   image_urls::text AS image_urls,
                   COALESCE(translations::text, '{}') AS translations
            FROM prayer_rooms WHERE room_id = $1
            """,
            room_id,
        )
    if row is None:
        raise HTTPException(status_code=404, detail=f"기도실 '{room_id}' 없음")

    def _j(s):
        if not s: return None
        try: return _json.loads(s)
        except Exception: return None

    facilities = _j(row["facilities"]) or {}
    details = {
        "name_en":              row["name_en"] or "",
        "facilities":           facilities,
        "wudu":                 bool(facilities.get("wudu")),
        "gender_separation":    bool(facilities.get("gender_separation")),
        "prayer_mat":           bool(facilities.get("prayer_mat")),
        "quran_available":      bool(facilities.get("quran_available")),
        "availability_status":  row["availability_status"] or "",
        "capacity":             row["capacity"] or "",
        "notes":                row["notes"] or "",
    }
    _lat_p = float(row["lat"]) if row["lat"] is not None else None
    _lng_p = float(row["lng"]) if row["lng"] is not None else None
    _rt = row["translations"]
    if isinstance(_rt, str):
        try: _rt = _json.loads(_rt)
        except Exception: _rt = {}
    if not isinstance(_rt, dict):
        _rt = {}
    if language != "ko":
        _t = await _ensure_translations(
            pool, "prayer_rooms", "room_id", room_id,
            {
                "name":       row["name"] or "",
                "addr":       row["address"] or "",
                "open_hours": row["open_hours"] or "",
                "notes":      row["notes"] or "",
            },
            lat=_lat_p or 0, lng=_lng_p or 0, language=language, existing=_rt,
            no_translate=no_translate,
        )
        if _t.get("notes"):
            details["notes"] = _t["notes"]
    else:
        _t = {}
    return PlaceDetailResponse(
        id=f"prayer__{row['room_id']}",
        store_name=_t.get("name") or row["name"] or "",
        place_id=None,
        lat=_lat_p,
        lng=_lng_p,
        category="Prayer Room" if language == "en" else "기도실",
        category_key="prayer_room",
        addr=_t.get("addr") or row["address"] or "",
        phone=row["phone"] or "",
        floor=row["floor"] or None,
        homepage=None,
        place_url=None,
        open_hours=_t.get("open_hours") or row["open_hours"] or "",
        closed_days=None,
        is_open_now=_is_open_now_combined(row["open_hours"] or "", None),
        image_urls=(_j(row["image_urls"]) or []),
        details=details,
        source="prayer_rooms",
        last_updated=None,
    )


async def _accommodation_search(req: SearchRequest, lat: float, lng: float, language: str = "ko") -> SearchResponse:
    """accommodation_places 테이블 거리 정렬 검색."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT id, name_ko, category, addr, phone, lat, lng, open_hours,
                   (image_urls->>0) AS image_url,
                   COALESCE(translations::text, '{}') AS translations,
                   CASE
                     WHEN lat IS NOT NULL AND lng IS NOT NULL THEN
                       ST_Distance(
                         ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography,
                         ST_SetSRID(ST_MakePoint($2::float, $3::float), 4326)::geography
                       )
                     ELSE NULL
                   END AS dist_m
            FROM accommodation_places
            ORDER BY dist_m NULLS LAST, last_updated DESC NULLS LAST
            LIMIT $1
            """,
            req.limit, lng, lat,
        )
    results = [
        SearchResultItem(
            id=r["id"],
            store_name=(_json.loads(r["translations"]).get(language, {}).get("name") or r["name_ko"] or ""),
            category=r["category"] or ("Accommodation" if language == "en" else "숙박"),
            category_key="accommodation",
            addr=(_json.loads(r["translations"]).get(language, {}).get("addr") or r["addr"] or ""),
            phone=r["phone"] or "",
            lat=r["lat"],
            lng=r["lng"],
            image_url=r["image_url"] or None,
            distance_m=round(float(r["dist_m"]), 1) if r["dist_m"] is not None else None,
            is_open_now=_is_open_now_combined(r["open_hours"] or "", None),
        )
        for r in rows
    ]
    return SearchResponse(query=req.query, count=len(results), results=results)


async def _tourist_search(req: SearchRequest, lat: float, lng: float, language: str = "ko") -> SearchResponse:
    """tourist_places 테이블 거리 정렬 검색 (관광지 + 문화시설 통합)."""
    from tools.category_classifier import classify_category
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT id, name_ko, category, addr, phone, lat, lng, open_hours,
                   (image_urls->>0) AS image_url,
                   COALESCE(translations::text, '{}') AS translations,
                   CASE
                     WHEN lat IS NOT NULL AND lng IS NOT NULL THEN
                       ST_Distance(
                         ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography,
                         ST_SetSRID(ST_MakePoint($2::float, $3::float), 4326)::geography
                       )
                     ELSE NULL
                   END AS dist_m
            FROM tourist_places
            ORDER BY dist_m NULLS LAST, last_updated DESC NULLS LAST
            LIMIT $1
            """,
            req.limit, lng, lat,
        )
    results = []
    for r in rows:
        key = classify_category(r["category"] or "", r["name_ko"] or "")
        if key not in ("tourist", "cultural"):
            key = "tourist"
        _tl = _json.loads(r["translations"]).get(language, {})
        results.append(
            SearchResultItem(
                id=r["id"],
                store_name=_tl.get("name") or r["name_ko"] or "",
                category=r["category"] or ("Tourist Spot" if language == "en" else "관광지"),
                category_key=key,
                addr=_tl.get("addr") or r["addr"] or "",
                phone=r["phone"] or "",
                lat=r["lat"],
                lng=r["lng"],
                image_url=r["image_url"] or None,
                distance_m=round(float(r["dist_m"]), 1) if r["dist_m"] is not None else None,
                is_open_now=_is_open_now_combined(r["open_hours"] or "", None),
            )
        )
    return SearchResponse(query=req.query, count=len(results), results=results)


async def _accommodation_detail(acc_id: str, language: str = "ko", user_lat: float | None = None, user_lng: float | None = None, no_translate: bool = False) -> PlaceDetailResponse:
    """accommodation_places 테이블 1건 → PlaceDetailResponse."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, content_id, name_ko, lat, lng, addr, phone, category,
                   image_urls::text AS image_urls, homepage, open_hours,
                   checkintime, checkouttime, infocenterlodging, parkinglodging,
                   reservationlodging, reservationurl,
                   conveniences, source, last_updated,
                   COALESCE(translations::text, '{}') AS translations
            FROM accommodation_places WHERE id = $1
            """,
            acc_id,
        )
    if row is None:
        raise HTTPException(status_code=404, detail=f"숙박업소 '{acc_id}' 없음")
    convs = row["conveniences"]
    if isinstance(convs, str):
        import json as _json
        try:
            convs = _json.loads(convs)
        except Exception:
            convs = []
    open_hours_str = row["open_hours"] or ""
    is_open = _is_open_now_combined(open_hours_str, None) if open_hours_str else None
    _lat_a = float(row["lat"]) if row["lat"] is not None else None
    _lng_a = float(row["lng"]) if row["lng"] is not None else None
    _rt = row["translations"]
    if isinstance(_rt, str):
        try: _rt = _json.loads(_rt)
        except Exception: _rt = {}
    if not isinstance(_rt, dict):
        _rt = {}
    _info   = _strip_html(row["infocenterlodging"] or "")
    _park   = _strip_html(row["parkinglodging"] or "")
    _resv   = _strip_html(row["reservationlodging"] or "")
    if language != "ko":
        _t = await _ensure_translations(
            pool, "accommodation_places", "id", acc_id,
            {
                "name":               row["name_ko"] or "",
                "addr":               row["addr"] or "",
                "open_hours":         open_hours_str,
                "infocenterlodging":  _info,
                "parkinglodging":     _park,
                "reservationlodging": _resv,
            },
            lat=_lat_a or 0, lng=_lng_a or 0, language=language, existing=_rt,
            no_translate=no_translate,
        )
    else:
        _t = {}
    dist_a = _haversine_m(user_lat, user_lng, _lat_a, _lng_a) if (user_lat and user_lng and _lat_a and _lng_a) else None
    return PlaceDetailResponse(
        id=row["id"],
        store_name=_t.get("name") or row["name_ko"] or "",
        place_id=row["content_id"] or None,
        lat=_lat_a,
        lng=_lng_a,
        distance_m=dist_a,
        category=row["category"] or ("Accommodation" if language == "en" else "숙박"),
        category_key="accommodation",
        addr=_t.get("addr") or row["addr"] or "",
        phone=row["phone"] or "",
        floor=None,
        homepage=_extract_url(row["homepage"] or "") or None,
        place_url=None,
        open_hours=_t.get("open_hours") or open_hours_str or None,
        closed_days=None,
        is_open_now=is_open,
        image_urls=_parse_jsonb_list(row["image_urls"]),
        details={
            "checkintime":        _strip_html(row["checkintime"] or ""),
            "checkouttime":       _strip_html(row["checkouttime"] or ""),
            "infocenterlodging":  _t.get("infocenterlodging") or _info,
            "parkinglodging":     _t.get("parkinglodging") or _park,
            "reservationlodging": _t.get("reservationlodging") or _resv,
            "reservationurl":     _extract_url(row["reservationurl"] or ""),
            "conveniences":       convs or [],
        },
        source=row["source"] or "accommodation_places",
        last_updated=row["last_updated"].isoformat() if row["last_updated"] else None,
    )


async def _tourist_detail(tourist_id: str, language: str = "ko", user_lat: float | None = None, user_lng: float | None = None, no_translate: bool = False) -> PlaceDetailResponse:
    """tourist_places 테이블 1건 → PlaceDetailResponse."""
    from tools.category_classifier import classify_category
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, content_id, content_type_id, name_ko, lat, lng, addr, phone, category,
                   overview, image_urls::text AS image_urls, homepage, open_hours,
                   infocenter, parking, restdate, usetime,
                   infocenterculture, parkingculture, parkingfee,
                   restdateculture, usefee, usetimeculture,
                   conveniences, source, last_updated,
                   COALESCE(translations::text, '{}') AS translations
            FROM tourist_places WHERE id = $1
            """,
            tourist_id,
        )
    if row is None:
        raise HTTPException(status_code=404, detail=f"관광지/문화시설 '{tourist_id}' 없음")
    key = classify_category(row["category"] or "", row["name_ko"] or "")
    if key not in ("tourist", "cultural"):
        key = "tourist"
    convs = row["conveniences"]
    if isinstance(convs, str):
        import json as _json
        try:
            convs = _json.loads(convs)
        except Exception:
            convs = []
    ctype = row["content_type_id"]
    if ctype == 14:
        type_details = {
            "infocenterculture": _strip_html(row["infocenterculture"] or ""),
            "parkingculture":    _strip_html(row["parkingculture"] or ""),
            "parkingfee":        _strip_html(row["parkingfee"] or ""),
            "restdateculture":   _strip_html(row["restdateculture"] or ""),
            "usefee":            _strip_html(row["usefee"] or ""),
            "usetimeculture":    _strip_html(row["usetimeculture"] or ""),
        }
    else:
        type_details = {
            "infocenter": _strip_html(row["infocenter"] or ""),
                        "parking":    _strip_html(row["parking"] or ""),
            "restdate":   _strip_html(row["restdate"] or ""),
            "usetime":    _strip_html(row["usetime"] or ""),
        }
    open_hours_str = row["open_hours"] or ""
    is_open = _is_open_now_combined(open_hours_str, None) if open_hours_str else None
    overview_raw = row["overview"] or ""
    overview = _re.sub(r"<[^>]+>", "", overview_raw).strip()
    _lat_t = float(row["lat"]) if row["lat"] is not None else None
    _lng_t = float(row["lng"]) if row["lng"] is not None else None
    _rt = row["translations"]
    if isinstance(_rt, str):
        try: _rt = _json.loads(_rt)
        except Exception: _rt = {}
    if not isinstance(_rt, dict):
        _rt = {}
    if language != "ko":
        _t = await _ensure_translations(
            pool, "tourist_places", "id", tourist_id,
            {
                "name":        row["name_ko"] or "",
                "addr":        row["addr"] or "",
                "open_hours":  open_hours_str,
                "description": overview,
                **{k: v for k, v in type_details.items() if v},
            },
            lat=_lat_t or 0, lng=_lng_t or 0, language=language, existing=_rt,
            no_translate=no_translate,
        )
        type_details = {k: _t.get(k) or v for k, v in type_details.items()}
    else:
        _t = {}
    dist_t = _haversine_m(user_lat, user_lng, _lat_t, _lng_t) if (user_lat and user_lng and _lat_t and _lng_t) else None
    return PlaceDetailResponse(
        id=row["id"],
        store_name=_t.get("name") or row["name_ko"] or "",
        place_id=row["content_id"] or None,
        lat=_lat_t,
        lng=_lng_t,
        distance_m=dist_t,
        category=row["category"] or ("Tourist Spot" if language == "en" else "관광지"),
        category_key=key,
        addr=_t.get("addr") or row["addr"] or "",
        phone=row["phone"] or "",
        floor=None,
        homepage=_extract_url(row["homepage"] or "") or None,
        place_url=None,
        open_hours=_t.get("open_hours") or open_hours_str or None,
        closed_days=None,
        is_open_now=is_open,
        image_urls=_parse_jsonb_list(row["image_urls"]),
        details={
            "overview":     _t.get("description") or overview,
            "conveniences": convs or [],
            **type_details,
        },
        source=row["source"] or "tourist_places",
        last_updated=row["last_updated"].isoformat() if row["last_updated"] else None,
    )


async def _subway_detail(station_query: str, language: str = "ko") -> PlaceDetailResponse:
    """지하철역 detail — subway_exits 테이블 + seoul_metro fetcher (TAGO 시간표/빠른하차).

    `station_query`: search API 에서 받은 "역명 호선" 텍스트. 예: "명동역 4호선" / "을지로입구역 2호선".
    seoul_metro.fetch() 가 _parse_query() 로 역명+호선 분리 → subway_exits 조회 + TAGO API 호출.
    details 키: station_name, line, station_id, exits[], schedule{}, exit_count, fast_alights[]
    """
    from tools.details_fetchers import seoul_metro
    del language  # TODO: TAGO 응답은 한국어 고정. 추후 다국어 시 사용.
    if not station_query:
        raise HTTPException(status_code=400, detail="station query 필요")
    fetched = await seoul_metro.fetch(
        store_name=station_query, lat=37.5636, lng=126.9822,
        building_ufid="", category_name="지하철역",
    )
    if not fetched or not fetched.get("details", {}).get("station_name"):
        raise HTTPException(status_code=404, detail=f"지하철역 '{station_query}' 없음")
    details = fetched.get("details", {}) or {}
    # 출구 평균 좌표 (대표 좌표 — 카드 거리 계산용)
    exits = details.get("exits", []) or []
    lat = lng = None
    valid_coords = [(e["lat"], e["lng"]) for e in exits if e.get("lat") and e.get("lng")]
    if valid_coords:
        lat = sum(c[0] for c in valid_coords) / len(valid_coords)
        lng = sum(c[1] for c in valid_coords) / len(valid_coords)
    open_hours = fetched.get("open_hours", "") or ""
    return PlaceDetailResponse(
        id=f"subway__{station_query}",
        store_name=f"{details.get('station_name','')} {details.get('line','')}".strip(),
        place_id=details.get("station_id") or None,
        lat=lat, lng=lng,
        category="지하철역",
        category_key="subway",
        addr="",
        phone=fetched.get("phone", "") or "",
        floor=None,
        homepage=None,
        place_url=None,
        open_hours=open_hours or None,
        closed_days=None,
        is_open_now=_is_open_now_combined(open_hours, None),
        image_urls=[],
        details=details,
        source=fetched.get("source", "tago_subway"),
        last_updated=None,
    )


async def _locker_detail(query: str, language: str = "ko") -> PlaceDetailResponse:
    """물품보관함 detail — 서울 OpenAPI 실시간 (seoul_openapi fetcher).

    `query`: search API 에서 받은 매장명 (예: 보관함 위치 명). seoul_openapi.fetch 가 좌표 근처
    locker 데이터 가져옴. 별도 테이블 없이 매번 외부 API 호출.
    """
    from tools.details_fetchers import seoul_openapi
    del language  # TODO: 서울 OpenAPI 한국어 고정. 추후 다국어 시 사용.
    if not query:
        raise HTTPException(status_code=400, detail="locker query 필요")
    fetched = await seoul_openapi.fetch(
        store_name=query, lat=37.5636, lng=126.9822,
        building_ufid="", category_name="물품보관함",
    )
    if not fetched:
        raise HTTPException(status_code=404, detail=f"보관함 '{query}' 없음")
    details = fetched.get("details", {}) or {}
    return PlaceDetailResponse(
        id=f"locker__{query}",
        store_name=query,
        place_id=None,
        lat=details.get("lat"),
        lng=details.get("lng"),
        category="물품보관함",
        category_key="locker",
        addr=fetched.get("addr", "") or "",
        phone=fetched.get("phone", "") or "",
        floor=None,
        homepage=None,
        place_url=None,
        open_hours=fetched.get("open_hours", "") or None,
        closed_days=None,
        is_open_now=None,
        image_urls=[],
        details=details,
        source=fetched.get("source", "seoul_openapi"),
        last_updated=None,
    )


@app.post("/place/detail", response_model=PlaceDetailResponse)
async def place_detail(req: PlaceDetailRequest):
    """
    PlaceDetailScreen 진입 시 호출. id prefix 로 출처 분기:
    - `restroom__{mng_no}` → public_restrooms 테이블
    - `halal__{restaurant_id}` → halal_restaurants 테이블
    - `prayer__{room_id}` → prayer_rooms 테이블
    - `accommodation__{content_id}` → accommodation_places 테이블
    - `tourist__{content_id}` → tourist_places 테이블
    - 그 외 → store_details 테이블 (건물 내 매장 캐시)
    """
    # outdoor 라우팅
    if req.id.startswith("restroom__"):
        return await _restroom_detail(req.id[len("restroom__"):], language=req.language, no_translate=req.no_translate)
    if req.id.startswith("halal__"):
        return await _halal_detail(req.id[len("halal__"):], language=req.language, no_translate=req.no_translate)
    if req.id.startswith("vegan__"):
        return await _vegan_detail(req.id[len("vegan__"):], language=req.language, no_translate=req.no_translate)
    if req.id.startswith("prayer__"):
        return await _prayer_detail(req.id[len("prayer__"):], language=req.language, no_translate=req.no_translate)
    if req.id.startswith("accommodation__"):
        return await _accommodation_detail(req.id, language=req.language, user_lat=req.user_lat, user_lng=req.user_lng, no_translate=req.no_translate)
    if req.id.startswith("tourist__"):
        return await _tourist_detail(req.id, language=req.language, user_lat=req.user_lat, user_lng=req.user_lng, no_translate=req.no_translate)
    if req.id.startswith("subway__"):
        return await _subway_detail(req.id[len("subway__"):], language=req.language)
    if req.id.startswith("locker__"):
        return await _locker_detail(req.id[len("locker__"):], language=req.language)

    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, store_name, place_id, lat, lng,
                   category, category_key, addr, phone, floor,
                   homepage, place_url,
                   open_hours, closed_days,
                   image_urls, details, source, last_updated,
                   COALESCE(translations::text, '{}') AS translations
            FROM store_details
            WHERE id = $1
            """,
            req.id,
        )

    if row is None:
        # 404 디버깅 — 어떤 id 가 store_details 에 없는지 콘솔에 노출.
        # frontend(SavedPlace/Recent/Search) 가 보낸 id 가 store_details.id 와
        # 매칭 안 되는 패턴인지 추적용.
        print(f"[place_detail] 404 — store_details 에 매칭 없음: id={req.id!r}")
        raise HTTPException(status_code=404, detail=f"매장 '{req.id}' 정보를 찾을 수 없습니다.")

    # floor_info_seed 인 lightweight row → 카드 탭한 지금이 풀필드 fetch 타이밍.
    # get_store_detail 이 fetcher 디스패치(kakao_scraper + naver_place 등) + UPSERT
    # 수행. cache_id 는 '{place_id}__{store_name}' 패턴이라 req.id 그대로 재사용.
    if row["source"] == "floor_info_seed":
        sep = "__"
        idx = req.id.find(sep)
        place_id_part = req.id[:idx] if idx >= 0 else (row["place_id"] or "")
        store_name_part = req.id[idx + len(sep):] if idx >= 0 else row["store_name"]
        try:
            await get_store_detail(place_id_part, store_name_part, language=req.language)
            async with pool.acquire() as conn:
                row = await conn.fetchrow(
                    """
                    SELECT id, store_name, place_id, lat, lng,
                           category, category_key, addr, phone, floor,
                           homepage, place_url,
                           open_hours, closed_days,
                           image_urls, details, source, last_updated,
                           COALESCE(translations::text, '{}') AS translations
                    FROM store_details WHERE id = $1
                    """,
                    req.id,
                )
        except Exception as e:
            print(f"[place_detail] floor_info_seed lazy fetch 실패: {e}")

    # image_urls / details 는 JSONB — asyncpg 가 list/dict 로 디코드하지만
    # 안전을 위해 문자열 케이스도 방어적으로 처리.
    raw_imgs = row["image_urls"]
    image_urls: list[str]
    if isinstance(raw_imgs, list):
        image_urls = [str(x) for x in raw_imgs]
    elif isinstance(raw_imgs, str) and raw_imgs:
        try:
            parsed = _json.loads(raw_imgs)
            image_urls = [str(x) for x in parsed] if isinstance(parsed, list) else []
        except (ValueError, TypeError):
            image_urls = []
    else:
        image_urls = []

    raw_details = row["details"]
    details: dict
    if isinstance(raw_details, dict):
        details = raw_details
    elif isinstance(raw_details, str) and raw_details:
        try:
            parsed = _json.loads(raw_details)
            details = parsed if isinstance(parsed, dict) else {}
        except (ValueError, TypeError):
            details = {}
    else:
        details = {}

    last_updated = row["last_updated"]
    last_updated_iso = last_updated.isoformat() if last_updated else None

    # 사용자-매장 거리(m) — Haversine. user_lat/lng 와 row.lat/lng 모두 있을 때만.
    distance_m: float | None = None
    if (req.user_lat is not None and req.user_lng is not None
            and row["lat"] is not None and row["lng"] is not None):
        distance_m = _haversine_m(req.user_lat, req.user_lng, float(row["lat"]), float(row["lng"]))

    raw_trans = row["translations"]
    if isinstance(raw_trans, str):
        try: raw_trans = _json.loads(raw_trans)
        except Exception: raw_trans = {}
    if not isinstance(raw_trans, dict):
        raw_trans = {}
    t = raw_trans.get(req.language, {})
    # 번역 캐시 없으면 트리거 — 어느 경로로 진입해도 최초 1회만 실행.
    # no_translate 면 DeepL 호출 스킵: 캐시된 번역만 쓰고 없으면 원본 반환.
    if req.language != "ko" and not t and not req.no_translate \
            and row["lat"] is not None and row["lng"] is not None:
        t = await _ensure_translations(
            pool, "store_details", "id", row["id"],
            {
                "name":        row["store_name"] or "",
                "addr":        row["addr"] or "",
                "open_hours":  row["open_hours"] or "",
                "closed_days": row["closed_days"] or "",
                "category":    row["category"] or "",
            },
            lat=float(row["lat"]), lng=float(row["lng"]),
            language=req.language, existing=raw_trans,
        )
    return PlaceDetailResponse(
        id=row["id"],
        store_name=t.get("name") or row["store_name"],
        place_id=row["place_id"],
        lat=row["lat"],
        lng=row["lng"],
        distance_m=distance_m,
        category=t.get("category") or row["category"],
        category_key=row["category_key"],
        addr=t.get("addr") or row["addr"],
        phone=row["phone"],
        floor=row["floor"],
        homepage=row["homepage"],
        place_url=row["place_url"],
        open_hours=t.get("open_hours") or row["open_hours"],
        closed_days=t.get("closed_days") or row["closed_days"],
        is_open_now=_is_open_now_combined(row["open_hours"], details.get("schedule")),
        image_urls=image_urls,
        details=details,
        source=row["source"],
        last_updated=last_updated_iso,
    )


@app.post("/place/detail/stream")
async def place_detail_stream(req: PlaceDetailRequest):
    """
    /place/detail 의 SSE 스트리밍 버전.
    """
    async def generate():
        try:
            async def run():
                yield _sse_chunk("progress", {"progress": 10, "message": "데이터 조회 중..."})
                result: PlaceDetailResponse = await place_detail(req)
                yield _sse_chunk("progress", {"progress": 90, "message": "완료 처리 중..."})
                yield _sse_chunk("complete", {"result": result.model_dump()})

            async for chunk in run():
                yield chunk
        except HTTPException as e:
            yield _sse_chunk("error", f"HTTP_{e.status_code}: {e.detail}")
        except Exception as e:
            yield _sse_chunk("error", str(e))

    return StreamingResponse(generate(), media_type="text/event-stream", headers=_SSE_HEADERS)
