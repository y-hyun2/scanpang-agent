from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from schemas.navigation import NavRequest, RouteRequest
from agents.navigation_agent import run_search_agent, run_route_agent
from schemas.place import PlaceRequest
from agents.place_insight_agent import run_place_insight_agent
from schemas.store import StoreRequest
from tools.store_tools import get_store_detail
from schemas.convenience import ConvenienceRequest
from agents.convenience_agent import run_convenience_agent
from schemas.halal import HalalRequest
from agents.halal_agent import run_halal_agent
from agents.orchestrator_agent import run_orchestrator
from core.session_store import get_session_store
from schemas.restaurant import RestaurantDetailRequest
from tools.restaurant_tools import get_restaurant_detail
from schemas.search import SearchRequest, SearchResponse, SearchResultItem
from schemas.place_detail import PlaceDetailRequest, PlaceDetailResponse
from schemas.user import (
    UserPreferencesUpsertRequest, UserPreferencesResponse,
    SavedPlacesUpdateRequest, SearchHistoryUpdateRequest,
)
from tools.open_hours_parser import is_open_now_combined as _is_open_now_combined
import json as _json
from datetime import datetime, timedelta, timezone
from rag.automation.worker import start_worker, stop_worker
from core.db import get_pool, close_pool
from api.h3_buildings import router as h3_buildings_router

app = FastAPI(title="ScanPang Navigation API")

app.include_router(h3_buildings_router)


@app.on_event("startup")
async def _startup():
    await get_session_store().connect()
    await get_pool()
    await start_worker()


@app.on_event("shutdown")
async def _shutdown():
    await stop_worker()
    await close_pool()
    await get_session_store().close()


# ── Orchestrator 스키마 ───────────────────────────────────────────────────

class AgentChatRequest(BaseModel):
    message: str
    lat: float
    lng: float
    heading: float = 0.0
    language: str = "ko"
    session_id: Optional[str] = None


class AgentChatResponse(BaseModel):
    speech: str
    source_agent: str
    raw_data: dict
    session_id: str


@app.post("/navigation/search")
async def navigation_search(req: NavRequest):
    """
    1단계: 자연어 메시지 → POI 후보 목록 반환
    앱에서 사용자에게 목적지 확인/선택 후 /navigation/route 호출
    """
    return await run_search_agent(req)


@app.post("/navigation/route")
async def navigation_route(req: RouteRequest):
    """
    2단계: 확정된 목적지 → 보행자 경로 계산 + 턴별 TTS 안내 반환
    """
    return await run_route_agent(req)


@app.post("/place/query")
async def place_query(req: PlaceRequest):
    """
    ARCore가 인식한 건물 place_id → AR 오버레이 데이터 + TTS 도슨트 해설 반환
    """
    return await run_place_insight_agent(req)


@app.post("/place/store")
async def place_store(req: StoreRequest):
    """
    사용자가 층별 매장 탭 → 매장 상세 정보 반환 (Kakao on-demand + Chroma 캐싱)
    """
    detail = await get_store_detail(req.place_id, req.store_name)
    if isinstance(detail, dict):
        details = detail.get("details") or {}
        schedule = details.get("schedule") if isinstance(details, dict) else None
        detail["is_open_now"] = _is_open_now_combined(
            detail.get("open_hours") or "", schedule,
        )
    return detail


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
                 search_history, created_at, updated_at)
            VALUES ($1, $2, $3, $4, '[]'::jsonb, '[]'::jsonb, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                display_name = COALESCE(EXCLUDED.display_name, user_preferences.display_name),
                language     = COALESCE(EXCLUDED.language,     user_preferences.language),
                value_added  = COALESCE(EXCLUDED.value_added,  user_preferences.value_added),
                updated_at   = NOW()
            RETURNING user_id, display_name, language, value_added,
                      saved_places::text AS saved_places,
                      search_history::text AS search_history
            """,
            req.user_id, req.display_name, req.language, req.value_added,
        )
    return UserPreferencesResponse(
        user_id=row["user_id"],
        display_name=row["display_name"],
        language=row["language"],
        value_added=row["value_added"],
        saved_places=_json.loads(row["saved_places"] or "[]"),
        search_history=_json.loads(row["search_history"] or "[]"),
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
                   search_history::text AS search_history
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
        saved_places=_json.loads(row["saved_places"] or "[]"),
        search_history=_json.loads(row["search_history"] or "[]"),
    )


@app.put("/user/preferences/{user_id}/saved-places")
async def user_saved_places_update(user_id: str, req: SavedPlacesUpdateRequest):
    """SavedPlacesStore 변경 시 호출. 전체 list 교체 (delta sync 가 아닌 full replace)."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO user_preferences (user_id, saved_places, created_at, updated_at)
            VALUES ($1, $2::jsonb, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                saved_places = EXCLUDED.saved_places,
                updated_at   = NOW()
            """,
            user_id, _json.dumps(req.items, ensure_ascii=False),
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
            VALUES ($1, $2::jsonb, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                search_history = EXCLUDED.search_history,
                updated_at     = NOW()
            """,
            user_id, _json.dumps(req.items, ensure_ascii=False),
        )
    return {"user_id": user_id, "count": len(req.items)}


@app.post("/convenience/query")
async def convenience_query(req: ConvenienceRequest):
    """
    카테고리 탭 or 텍스트 검색 → 주변 편의시설 목록 반환
    category 있으면 LLM 없이 바로 검색, message만 있으면 LLM으로 카테고리 추출
    """
    return await run_convenience_agent(req)


@app.post("/halal/query")
async def halal_query(req: HalalRequest):
    """
    Halal Agent: 기도 시간, 키블라 방향, 할랄 식당, 기도실
    category: prayer_time | qibla | restaurant | prayer_room
    """
    return await run_halal_agent(req)


@app.post("/ar/agent/chat", response_model=AgentChatResponse)
async def ar_agent_chat(req: AgentChatRequest):
    """
    LangGraph Orchestrator: 단일 엔드포인트에서 4개 에이전트를 자동 라우팅.
    intent_classifier(GPT-4o) → place | navigation | halal | convenience → 통합 응답
    """
    result = await run_orchestrator(
        message=req.message,
        lat=req.lat,
        lng=req.lng,
        heading=req.heading,
        language=req.language,
        session_id=req.session_id,
    )
    return result


@app.post("/restaurant/detail")
async def restaurant_detail(req: RestaurantDetailRequest):
    """
    식당 이름으로 상세 정보 조회 (일반 식당 + 할랄 식당 통합 검색)
    """
    result = get_restaurant_detail(req.name)
    if result is None:
        raise HTTPException(status_code=404, detail=f"식당 '{req.name}' 정보를 찾을 수 없습니다.")
    return result


_OUTDOOR_CATEGORIES = {"restroom", "subway", "locker", "prayer_room", "halal_restaurant", "vegan_restaurant", "vegan_cafe"}


def _vegan_category_label(vegan_level: str, category_key: str) -> str:
    """'채식전문 · 음식점' 또는 '채식가능 · 카페' 형식의 카테고리 레이블 반환."""
    type_label = "카페" if category_key == "vegan_cafe" else "음식점"
    return f"{vegan_level} · {type_label}" if vegan_level else type_label


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
    elif category_key == "subway":
        rows = await kakao_category_search("subway", lat, lng, radius=1500)
    elif category_key == "locker":
        rows = await seoul_locker_search(lat, lng, radius=1500)
    elif category_key == "prayer_room":
        rows = await prayer_room_search(lat, lng, radius=2000)
    elif category_key == "halal_restaurant":
        # halal_tools 는 name_ko / address / opening_hours 키를 쓰므로 표준 키로 변환
        raw = await halal_restaurant_search(lat, lng, radius=0)
        rows = [
            {
                "name":         r.get("name_ko") or r.get("name_en") or "",
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
            }
            for r in raw
        ]
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
        return f"__outdoor__{category}__{r.get('name','')}"

    # 화면 표시용 한국어 카테고리 라벨
    LABEL = {
        "restroom": "화장실", "subway": "지하철역", "locker": "물품보관함",
        "prayer_room": "기도실", "halal_restaurant": "할랄 식당",
        "vegan_restaurant": "비건 식당", "vegan_cafe": "비건 카페",
    }

    results = [
        SearchResultItem(
            id=_outdoor_id(category_key, r),
            store_name=r.get("name", ""),
            category=(_vegan_category_label(r.get("vegan_level", ""), category_key)
                      if category_key in ("vegan_restaurant", "vegan_cafe")
                      else LABEL.get(category_key, category_key)),
            category_key=category_key,
            addr=r.get("address", ""),
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

    # 쿼리 → category_key. 한국어 카테고리명("카페"/"화장실") 우선, 그 외엔 매장명 분류 fallback.
    from tools.category_classifier import classify_query
    category_key = classify_query(q)
    if category_key in _OUTDOOR_CATEGORIES:
        return await _outdoor_search(category_key, req)

    pool = await get_pool()
    # 사용자 위치 — 없으면 명동 fallback. 거리 정렬 위해 필수.
    user_lat = req.lat if req.lat is not None else 37.5636
    user_lng = req.lng if req.lng is not None else 126.9822
    used_fallback = req.lat is None or req.lng is None
    print(f"[place_search] q={q!r} category_key={category_key!r} "
          f"user=({user_lat:.4f},{user_lng:.4f}){' [FALLBACK 명동]' if used_fallback else ''}")
    async with pool.acquire() as conn:
        # store_details: 매장명 ILIKE OR 분류된 category_key 매칭.
        # ORDER BY 거리(사용자 좌표 기준) — '식당' 칩이 용인 위치에서 외대 까르보네를
        # 명동 멜팅소울보다 먼저 보여주는 게 자연스러움. 좌표 없는 row 는 last_updated fallback.
        rows = await conn.fetch(
            """
            SELECT id, store_name, category, category_key, addr, phone,
                   place_id, lat, lng, floor, image_urls, open_hours, details,
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
               OR ($3 != 'other' AND category_key = $3)
            ORDER BY dist_m NULLS LAST, last_updated DESC NULLS LAST
            LIMIT $2
            """,
            f"%{q}%",
            req.limit,
            category_key,
            user_lng, user_lat,
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

        results.append(
            SearchResultItem(
                id=r["id"],
                store_name=r["store_name"],
                category=r["category"],
                category_key=r["category_key"],
                addr=r["addr"],
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


async def _restroom_detail(mng_no: str) -> PlaceDetailResponse:
    """public_restrooms 테이블 1건 → PlaceDetailResponse.
    details 키는 store_details schema 와 동일 — male_toilt_cnt, has_disabled 등."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT mng_no, name, type, addr_road, addr_lot, phone, open_hours, "
            "       lat, lng, raw "
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
    return PlaceDetailResponse(
        id=f"restroom__{row['mng_no']}",
        store_name=row["name"] or "",
        place_id=None,
        lat=row["lat"], lng=row["lng"],
        category=details["type"],
        category_key="restroom",
        addr=row["addr_road"] or row["addr_lot"] or "",
        phone=row["phone"] or "",
        floor=None,
        homepage=None,
        place_url=None,
        open_hours=open_hours,
        closed_days=None,
        is_open_now=_is_open_now_combined(open_hours, None),
        image_urls=[],
        details=details,
        source="public_restroom",
        last_updated=None,
    )


async def _halal_detail(restaurant_id: str) -> PlaceDetailResponse:
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
                   lat, lng
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
    return PlaceDetailResponse(
        id=f"halal__{row['restaurant_id']}",
        store_name=row["name_ko"] or row["name_en"] or "",
        place_id=None,
        lat=float(row["lat"]) if row["lat"] is not None else None,
        lng=float(row["lng"]) if row["lng"] is not None else None,
        category="할랄 식당",
        category_key="halal_restaurant",
        addr=row["address"] or "",
        phone=row["phone"] or "",
        floor=None,
        homepage=None,
        place_url=None,
        open_hours=open_hours_str,
        closed_days=None,
        is_open_now=_is_open_now_combined(open_hours_str, None),
        image_urls=(_j(row["image_urls"]) or []),
        details=details,
        source="halal_restaurants",
        last_updated=None,
    )


async def _vegan_detail(vegan_id: str) -> PlaceDetailResponse:
    """vegan_restaurants 테이블 1건 → PlaceDetailResponse."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, store_name, category, category_key, addr, phone,
                   details::text AS details,
                   open_hours, closed_days, homepage,
                   image_urls::text AS image_urls,
                   floor, place_url, lat, lng, last_updated
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
    resp_category = _vegan_category_label(vegan_level, resp_category_key)
    open_hours = row["open_hours"] or ""
    return PlaceDetailResponse(
        id=f"vegan__{row['id']}",
        store_name=row["store_name"] or "",
        place_id=None,
        lat=float(row["lat"]) if row["lat"] is not None else None,
        lng=float(row["lng"]) if row["lng"] is not None else None,
        category=resp_category,
        category_key=resp_category_key,
        addr=row["addr"] or "",
        phone=row["phone"] or "",
        floor=row["floor"] or None,
        homepage=row["homepage"] or None,
        place_url=row["place_url"] or None,
        open_hours=open_hours,
        closed_days=row["closed_days"] or None,
        is_open_now=_is_open_now_combined(open_hours, None),
        image_urls=(_j(row["image_urls"]) or []),
        details={
            "vegan_level": d.get("vegan_level", ""),
            "vegan_menu":  d.get("vegan_menu", ""),
            "restaurant_type": d.get("restaurant_type", ""),
        },
        source="vegan_restaurants",
        last_updated=row["last_updated"].isoformat() if row["last_updated"] else None,
    )


async def _prayer_detail(room_id: str) -> PlaceDetailResponse:
    """prayer_rooms 테이블 1건 → PlaceDetailResponse."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT room_id, name, name_en, address, phone, open_hours,
                   floor, lat, lng,
                   facilities::text AS facilities,
                   availability_status, capacity, notes,
                   image_urls::text AS image_urls
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
    return PlaceDetailResponse(
        id=f"prayer__{row['room_id']}",
        store_name=row["name"] or "",
        place_id=None,
        lat=float(row["lat"]) if row["lat"] is not None else None,
        lng=float(row["lng"]) if row["lng"] is not None else None,
        category="기도실",
        category_key="prayer_room",
        addr=row["address"] or "",
        phone=row["phone"] or "",
        floor=row["floor"] or None,
        homepage=None,
        place_url=None,
        open_hours=row["open_hours"] or "",
        closed_days=None,
        is_open_now=_is_open_now_combined(row["open_hours"] or "", None),
        image_urls=(_j(row["image_urls"]) or []),
        details=details,
        source="prayer_rooms",
        last_updated=None,
    )


@app.post("/place/detail", response_model=PlaceDetailResponse)
async def place_detail(req: PlaceDetailRequest):
    """
    PlaceDetailScreen 진입 시 호출. id prefix 로 출처 분기:
    - `restroom__{mng_no}` → public_restrooms 테이블
    - `halal__{restaurant_id}` → halal_restaurants 테이블
    - `prayer__{room_id}` → prayer_rooms 테이블
    - `tourist__{ufid}` → store_details 테이블 (import_tourist_places.py 배치 적재)
    - 그 외 → store_details 테이블 (건물 내 매장 캐시)
    """
    # outdoor 라우팅
    if req.id.startswith("restroom__"):
        return await _restroom_detail(req.id[len("restroom__"):])
    if req.id.startswith("halal__"):
        return await _halal_detail(req.id[len("halal__"):])
    if req.id.startswith("vegan__"):
        return await _vegan_detail(req.id[len("vegan__"):])
    if req.id.startswith("prayer__"):
        return await _prayer_detail(req.id[len("prayer__"):])

    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT id, store_name, place_id, lat, lng,
                   category, category_key, addr, phone, floor,
                   homepage, place_url,
                   open_hours, closed_days,
                   image_urls, details, source, last_updated
            FROM store_details
            WHERE id = $1
            """,
            req.id,
        )

    if row is None:
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
            await get_store_detail(place_id_part, store_name_part)
            async with pool.acquire() as conn:
                row = await conn.fetchrow(
                    """
                    SELECT id, store_name, place_id, lat, lng,
                           category, category_key, addr, phone, floor,
                           homepage, place_url,
                           open_hours, closed_days,
                           image_urls, details, source, last_updated
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
        import math as _math
        R = 6371000.0  # 지구 반지름(m)
        lat1 = _math.radians(req.user_lat)
        lat2 = _math.radians(float(row["lat"]))
        dlat = lat2 - lat1
        dlng = _math.radians(float(row["lng"]) - req.user_lng)
        a = _math.sin(dlat/2)**2 + _math.cos(lat1) * _math.cos(lat2) * _math.sin(dlng/2)**2
        distance_m = round(2 * R * _math.asin(_math.sqrt(a)), 1)

    return PlaceDetailResponse(
        id=row["id"],
        store_name=row["store_name"],
        place_id=row["place_id"],
        lat=row["lat"],
        lng=row["lng"],
        distance_m=distance_m,
        category=row["category"],
        category_key=row["category_key"],
        addr=row["addr"],
        phone=row["phone"],
        floor=row["floor"],
        homepage=row["homepage"],
        place_url=row["place_url"],
        open_hours=row["open_hours"],
        closed_days=row["closed_days"],
        is_open_now=_is_open_now_combined(row["open_hours"], details.get("schedule")),
        image_urls=image_urls,
        details=details,
        source=row["source"],
        last_updated=last_updated_iso,
    )
