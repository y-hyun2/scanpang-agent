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
from tools.open_hours_parser import is_open_now_combined as _is_open_now_combined
import json as _json
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


_OUTDOOR_CATEGORIES = {"restroom", "subway", "locker", "prayer_room"}


async def _outdoor_search(category_key: str, req: SearchRequest) -> SearchResponse:
    """건물 외 카테고리(화장실/지하철/물품보관/기도실)는 store_details 가 아닌
    각자 출처 테이블에서 거리 정렬로 반환.
    lat/lng 없으면 default(명동) 좌표 fallback."""
    from tools.convenience_tools import (
        public_restroom_search, seoul_locker_search, kakao_category_search,
        prayer_room_search,
    )
    lat = req.lat or 37.5636
    lng = req.lng or 126.9822

    if category_key == "restroom":
        rows = await public_restroom_search(lat, lng, radius=2000)
    elif category_key == "subway":
        rows = await kakao_category_search("subway", lat, lng, radius=1500)
    elif category_key == "locker":
        rows = await seoul_locker_search(lat, lng, radius=1500)
    elif category_key == "prayer_room":
        rows = prayer_room_search(lat, lng, radius=2000)
    else:
        rows = []

    rows_sorted = sorted(rows, key=lambda r: r.get("distance_m", 0))[:req.limit]
    results = [
        SearchResultItem(
            id=f"__outdoor__{category_key}__{r.get('name','')}",
            store_name=r.get("name", ""),
            category_key=category_key,
            addr=r.get("address", ""),
            phone=r.get("phone", ""),
            lat=r.get("lat"),
            lng=r.get("lng"),
            distance_m=r.get("distance_m"),
            is_open_now=_is_open_now_combined(r.get("open_hours") or "", None),
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
    async with pool.acquire() as conn:
        # store_details: 매장명 ILIKE OR 분류된 category_key 매칭.
        # 예) "카페" 검색 → ILIKE %카페% 는 거의 0건이지만 category_key='cafe' row 가 다 잡힘.
        rows = await conn.fetch(
            """
            SELECT id, store_name, category, category_key, addr, phone,
                   place_id, lat, lng, floor, image_urls, open_hours, details
            FROM store_details
            WHERE store_name ILIKE $1
               OR ($3 != 'other' AND category_key = $3)
            ORDER BY last_updated DESC NULLS LAST
            LIMIT $2
            """,
            f"%{q}%",
            req.limit,
            category_key,
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
                is_open_now=_is_open_now_combined(r["open_hours"], schedule),
            )
        )

    return SearchResponse(query=req.query, count=len(results), results=results)


@app.post("/place/detail", response_model=PlaceDetailResponse)
async def place_detail(req: PlaceDetailRequest):
    """
    PlaceDetailScreen 진입 시 호출 — store_details row 하나를 전부 펼쳐서 반환.
    검색 결과(SearchResultItem.id)를 그대로 넘기면 됨.
    """
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

    return PlaceDetailResponse(
        id=row["id"],
        store_name=row["store_name"],
        place_id=row["place_id"],
        lat=row["lat"],
        lng=row["lng"],
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
