from fastapi import FastAPI
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

app = FastAPI(title="ScanPang Navigation API")


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
    return await get_store_detail(req.place_id, req.store_name)


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
