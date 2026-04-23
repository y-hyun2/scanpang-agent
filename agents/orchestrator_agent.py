"""
orchestrator_agent.py
LangGraph 기반 멀티-에이전트 오케스트레이터.

흐름: intent_classifier → (place | navigation | halal | convenience) → response_synthesizer
"""

import json
import os
import uuid
from typing import Literal, Optional

from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langgraph.graph import StateGraph, START, END
from typing_extensions import TypedDict

from agents.convenience_agent import run_convenience_agent
from agents.halal_agent import run_halal_agent
from agents.navigation_agent import run_search_agent
from agents.place_insight_agent import run_place_insight_agent
from schemas.convenience import ConvenienceRequest
from schemas.halal import HalalRequest
from schemas.navigation import NavRequest
from schemas.place import PlaceRequest

load_dotenv()

# ── 상태 정의 ──────────────────────────────────────────────────────────────

class OrchestratorState(TypedDict):
    user_message: str
    user_lat: float
    user_lng: float
    user_heading: float
    language: str
    selected_agent: Literal["place", "navigation", "halal", "convenience"]
    sub_agent_response: dict
    final_speech: str
    source_agent: str


# ── Intent 분류 프롬프트 ────────────────────────────────────────────────────

_INTENT_SYSTEM = """당신은 AR 관광 앱의 요청 라우터입니다. 사용자 메시지를 분석해 아래 4개 에이전트 중 하나를 선택하세요.

에이전트 설명:
- place     : 현재 바라보는 건물/장소 정보 (건물명, 운영시간, 층별 매장, 도슨트 해설)
- navigation: 특정 목적지로 이동 (경로 안내, 길 찾기)
- halal     : 이슬람 관련 모든 것 (기도 시간, 키블라, 할랄 식당, 기도실/무슬라)
- convenience: 일반 편의시설 (ATM, 카페, 약국, 화장실, 환전소, 지하철, 주차장 등)

핵심 구분 규칙:
- 할랄 식당은 반드시 "halal" (convenience 아님)
- 기도실/무슬라는 반드시 "halal"
- 일반 식당/카페는 "convenience"
- 건물 자체에 대한 질문(이게 뭐야, 여기 몇 층이야)은 "place"
- 어디로 가고 싶다는 내용은 "navigation"

아래 예시를 참고하세요."""

_FEW_SHOTS = [
    {"role": "user",      "content": '메시지: "이 건물 뭐야?"'},
    {"role": "assistant", "content": '{"selected_agent": "place"}'},
    {"role": "user",      "content": '메시지: "저기 어떤 건물이야?"'},
    {"role": "assistant", "content": '{"selected_agent": "place"}'},
    {"role": "user",      "content": '메시지: "여기 운영시간이 어때?"'},
    {"role": "assistant", "content": '{"selected_agent": "place"}'},
    {"role": "user",      "content": '메시지: "명동성당에 대해 설명해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "place"}'},
    {"role": "user",      "content": '메시지: "거기 어떻게 가?"'},
    {"role": "assistant", "content": '{"selected_agent": "navigation"}'},
    {"role": "user",      "content": '메시지: "명동역으로 길 안내해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "navigation"}'},
    {"role": "user",      "content": '메시지: "롯데백화점까지 경로 알려줘"'},
    {"role": "assistant", "content": '{"selected_agent": "navigation"}'},
    {"role": "user",      "content": '메시지: "기도 시간 알려줘"'},
    {"role": "assistant", "content": '{"selected_agent": "halal"}'},
    {"role": "user",      "content": '메시지: "지금 아스르 기도 시간이야?"'},
    {"role": "assistant", "content": '{"selected_agent": "halal"}'},
    {"role": "user",      "content": '메시지: "키블라 방향이 어디야?"'},
    {"role": "assistant", "content": '{"selected_agent": "halal"}'},
    {"role": "user",      "content": '메시지: "할랄 식당 어디 있어?"'},
    {"role": "assistant", "content": '{"selected_agent": "halal"}'},
    {"role": "user",      "content": '메시지: "무슬림 음식 먹을 수 있는 곳 추천해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "halal"}'},
    {"role": "user",      "content": '메시지: "기도실 어디야?"'},
    {"role": "assistant", "content": '{"selected_agent": "halal"}'},
    {"role": "user",      "content": '메시지: "근처 ATM 찾아줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience"}'},
    {"role": "user",      "content": '메시지: "화장실 어디야?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience"}'},
    {"role": "user",      "content": '메시지: "카페 추천해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience"}'},
    {"role": "user",      "content": '메시지: "환전소 어디 있어?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience"}'},
    {"role": "user",      "content": '메시지: "약국 찾아줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience"}'},
]

_llm = ChatOpenAI(model="gpt-4o", temperature=0, response_format={"type": "json_object"})


# ── 독립 분류 함수 (테스트 가능) ─────────────────────────────────────────────

async def classify_intent(message: str) -> Literal["place", "navigation", "halal", "convenience"]:
    """사용자 메시지 → 에이전트 종류 분류. 실패 시 convenience 반환."""
    messages = [
        {"role": "system", "content": _INTENT_SYSTEM},
        *_FEW_SHOTS,
        {"role": "user", "content": f'메시지: "{message}"'},
    ]
    try:
        response = await _llm.ainvoke(messages)
        data = json.loads(response.content)
        agent = data.get("selected_agent", "convenience")
        if agent not in ("place", "navigation", "halal", "convenience"):
            return "convenience"
        return agent
    except Exception:
        return "convenience"


# ── LangGraph 노드 ────────────────────────────────────────────────────────

async def _intent_classifier_node(state: OrchestratorState) -> dict:
    selected = await classify_intent(state["user_message"])
    return {"selected_agent": selected, "source_agent": selected}


async def _call_place_node(state: OrchestratorState) -> dict:
    req = PlaceRequest(
        heading=state.get("user_heading", 0.0),
        user_lat=state["user_lat"],
        user_lng=state["user_lng"],
        user_message=state["user_message"],
        language=state.get("language", "ko"),
    )
    result = await run_place_insight_agent(req)
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


async def _call_navigation_node(state: OrchestratorState) -> dict:
    req = NavRequest(
        message=state["user_message"],
        lat=state["user_lat"],
        lng=state["user_lng"],
    )
    result = await run_search_agent(req)
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


async def _call_halal_node(state: OrchestratorState) -> dict:
    req = HalalRequest(
        message=state["user_message"],
        lat=state["user_lat"],
        lng=state["user_lng"],
        language=state.get("language", "ko"),
    )
    result = await run_halal_agent(req)
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


async def _call_convenience_node(state: OrchestratorState) -> dict:
    req = ConvenienceRequest(
        message=state["user_message"],
        lat=state["user_lat"],
        lng=state["user_lng"],
        language=state.get("language", "ko"),
    )
    result = await run_convenience_agent(req)
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


def _response_synthesizer_node(state: OrchestratorState) -> dict:
    """sub_agent_response에서 speech를 추출해 final_speech에 기록."""
    raw = state.get("sub_agent_response", {})
    speech = raw.get("speech") or raw.get("docent", {}).get("speech", "")
    return {"final_speech": speech}


def _route_after_classifier(state: OrchestratorState) -> str:
    return state["selected_agent"]


# ── 그래프 빌드 ───────────────────────────────────────────────────────────

def _build_graph() -> StateGraph:
    g = StateGraph(OrchestratorState)

    g.add_node("intent_classifier", _intent_classifier_node)
    g.add_node("call_place",        _call_place_node)
    g.add_node("call_navigation",   _call_navigation_node)
    g.add_node("call_halal",        _call_halal_node)
    g.add_node("call_convenience",  _call_convenience_node)
    g.add_node("response_synthesizer", _response_synthesizer_node)

    g.add_edge(START, "intent_classifier")
    g.add_conditional_edges(
        "intent_classifier",
        _route_after_classifier,
        {
            "place":        "call_place",
            "navigation":   "call_navigation",
            "halal":        "call_halal",
            "convenience":  "call_convenience",
        },
    )
    g.add_edge("call_place",       "response_synthesizer")
    g.add_edge("call_navigation",  "response_synthesizer")
    g.add_edge("call_halal",       "response_synthesizer")
    g.add_edge("call_convenience", "response_synthesizer")
    g.add_edge("response_synthesizer", END)

    return g.compile()


_graph = _build_graph()


# ── 퍼블릭 진입점 ─────────────────────────────────────────────────────────

async def run_orchestrator(
    message: str,
    lat: float,
    lng: float,
    heading: float = 0.0,
    language: str = "ko",
) -> dict:
    """
    단일 진입점: 메시지를 받아 적절한 에이전트로 라우팅하고 결과를 반환한다.

    Returns:
        {
            "speech": str,
            "source_agent": str,
            "raw_data": dict,
            "session_id": str,
        }
    """
    initial_state: OrchestratorState = {
        "user_message":   message,
        "user_lat":       lat,
        "user_lng":       lng,
        "user_heading":   heading,
        "language":       language,
        "selected_agent": "convenience",   # 초기값 (classifier가 덮어씀)
        "sub_agent_response": {},
        "final_speech":   "",
        "source_agent":   "",
    }

    result = await _graph.ainvoke(initial_state)

    return {
        "speech":       result["final_speech"],
        "source_agent": result["source_agent"],
        "raw_data":     result["sub_agent_response"],
        "session_id":   str(uuid.uuid4()),
    }
