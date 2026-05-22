"""
tests/test_orchestrator.py
orchestrator_agent의 intent 분류 + 라우팅 10개 케이스 검증.

mock 전략:
  - classify_intent 내부 LLM(_llm.ainvoke) 을 패치해서 네트워크 없이 실행
  - run_orchestrator는 sub-agent까지 모두 패치해 순수 라우팅 로직만 검증
"""

import json
import pytest
from unittest.mock import AsyncMock, MagicMock, patch


# ── classify_intent 단위 테스트 (10개 라우팅 케이스) ──────────────────────────

def _mock_llm_response(agent: str, resolved: str = "", sub_category: str = ""):
    """ChatOpenAI.ainvoke 반환값을 흉내내는 mock.
    classify_intent 가 {selected_agent, resolved_message, sub_category} 3-key 출력을
    파싱하므로 셋 다 채워준다."""
    msg = MagicMock()
    msg.content = json.dumps({
        "selected_agent":   agent,
        "resolved_message": resolved,
        "sub_category":     sub_category,
    })
    return msg


@pytest.mark.asyncio
@pytest.mark.parametrize("message,expected_agent", [
    # place — 관광지/랜드마크/지역 (매장 정보는 convenience)
    ("이 건물 뭐야?",             "place"),
    ("남산타워 언제 지어졌어?",    "place"),
    # navigation — 새 목적지 지정
    ("거기 어떻게 가?",           "navigation"),
    ("롯데백화점까지 길 안내해줘", "navigation"),
    # nav_guide — AR 길안내 중 보조 질문
    ("여기서 좌회전 맞아?",        "nav_guide"),
    # halal — 종교 정보(기도시간/키블라)만. 할랄식당/기도실은 convenience
    ("기도 시간 알려줘",          "halal"),
    ("키블라 방향 알려줘",         "halal"),
    # convenience — 모든 시설 검색 + 매장
    ("근처 ATM 찾아줘",           "convenience"),
    ("화장실 어디야?",            "convenience"),
    ("카페 추천해줘",             "convenience"),
    ("할랄 식당 어디 있어?",      "convenience"),
    ("기도실 어디야?",            "convenience"),
])
async def test_classify_intent(message: str, expected_agent: str):
    """LLM 응답을 mock해서 각 메시지가 올바른 에이전트로 분류되는지 확인."""
    from agents.orchestrator_agent import classify_intent

    with patch("agents.orchestrator_agent._llm") as mock_llm:
        mock_llm.ainvoke = AsyncMock(
            return_value=_mock_llm_response(expected_agent, resolved=message)
        )
        agent, _resolved, _sub = await classify_intent(message)

    assert agent == expected_agent, (
        f"메시지 '{message}' → 예상 '{expected_agent}', 실제 '{agent}'"
    )


# ── classify_intent 예외 처리 테스트 ──────────────────────────────────────

@pytest.mark.asyncio
async def test_classify_intent_fallback_on_llm_error():
    """LLM 예외 발생 시 convenience로 fallback."""
    from agents.orchestrator_agent import classify_intent

    with patch("agents.orchestrator_agent._llm") as mock_llm:
        mock_llm.ainvoke = AsyncMock(side_effect=Exception("API timeout"))
        agent, _resolved, _sub = await classify_intent("알 수 없는 요청")

    assert agent == "convenience"


@pytest.mark.asyncio
async def test_classify_intent_fallback_on_unknown_agent():
    """LLM이 정의되지 않은 에이전트명을 반환하면 convenience로 fallback."""
    from agents.orchestrator_agent import classify_intent

    with patch("agents.orchestrator_agent._llm") as mock_llm:
        mock_llm.ainvoke = AsyncMock(
            return_value=_mock_llm_response("unknown_agent")
        )
        agent, _resolved, _sub = await classify_intent("이상한 메시지")

    assert agent == "convenience"


# ── run_orchestrator 통합 라우팅 테스트 ──────────────────────────────────────

def _sub_agent_mock(speech: str, extra: dict = None):
    """sub-agent가 반환하는 dict를 흉내낸다."""
    return {"speech": speech, **(extra or {})}


@pytest.mark.asyncio
async def test_orchestrator_routes_to_place():
    """place 에이전트로 라우팅되고 speech가 올바르게 반환되는지 확인."""
    from agents.orchestrator_agent import run_orchestrator

    with (
        patch("agents.orchestrator_agent.classify_intent",
              AsyncMock(return_value=("place", "", ""))),
        patch("agents.orchestrator_agent.run_place_chat_agent",
              AsyncMock(return_value=_sub_agent_mock("눈스퀘어입니다."))),
    ):
        result = await run_orchestrator("이 건물 뭐야?", 37.56, 126.98)

    assert result["source_agent"] == "place"
    assert result["speech"] == "눈스퀘어입니다."
    assert "session_id" in result


@pytest.mark.asyncio
async def test_orchestrator_routes_to_navigation():
    """navigation 에이전트로 라우팅되는지 확인."""
    from agents.orchestrator_agent import run_orchestrator

    with (
        patch("agents.orchestrator_agent.classify_intent",
              AsyncMock(return_value=("navigation", "", ""))),
        patch("agents.orchestrator_agent.run_route_search",
              AsyncMock(return_value=_sub_agent_mock("명동역 방향으로 안내합니다."))),
    ):
        result = await run_orchestrator("명동역 어떻게 가?", 37.56, 126.98)

    assert result["source_agent"] == "navigation"
    assert "명동역" in result["speech"]


@pytest.mark.asyncio
async def test_orchestrator_routes_to_halal():
    """halal 에이전트로 라우팅되는지 확인."""
    from agents.orchestrator_agent import run_orchestrator

    with (
        patch("agents.orchestrator_agent.classify_intent",
              AsyncMock(return_value=("halal", "", "prayer_time"))),
        patch("agents.orchestrator_agent.run_halal_agent",
              AsyncMock(return_value=_sub_agent_mock("기도 시간 안내입니다."))),
    ):
        result = await run_orchestrator("기도 시간 알려줘", 37.56, 126.98)

    assert result["source_agent"] == "halal"
    assert result["speech"] == "기도 시간 안내입니다."


@pytest.mark.asyncio
async def test_orchestrator_routes_to_convenience():
    """convenience 에이전트로 라우팅되는지 확인."""
    from agents.orchestrator_agent import run_orchestrator

    with (
        patch("agents.orchestrator_agent.classify_intent",
              AsyncMock(return_value=("convenience", "", ""))),
        patch("agents.orchestrator_agent.run_search_agent",
              AsyncMock(return_value=_sub_agent_mock("근처 ATM이 2곳 있습니다."))),
    ):
        result = await run_orchestrator("근처 ATM 찾아줘", 37.56, 126.98)

    assert result["source_agent"] == "convenience"
    assert "ATM" in result["speech"]


@pytest.mark.asyncio
async def test_orchestrator_place_chat_speech():
    """place chat 응답({speech, raw})에서 speech가 final_speech로 추출되는지 확인."""
    from agents.orchestrator_agent import run_orchestrator

    place_response = {"speech": "명동성당은 1898년에 건립된...", "raw": {}}
    with (
        patch("agents.orchestrator_agent.classify_intent",
              AsyncMock(return_value=("place", "", ""))),
        patch("agents.orchestrator_agent.run_place_chat_agent",
              AsyncMock(return_value=place_response)),
    ):
        result = await run_orchestrator("저 건물 설명해줘", 37.56, 126.98)

    assert "명동성당" in result["speech"]
    assert result["source_agent"] == "place"


@pytest.mark.asyncio
async def test_orchestrator_session_id_passthrough():
    """클라이언트가 session_id를 넘기지 않아도 UUID가 생성되는지 확인."""
    from agents.orchestrator_agent import run_orchestrator

    with (
        patch("agents.orchestrator_agent.classify_intent",
              AsyncMock(return_value=("convenience", "", ""))),
        patch("agents.orchestrator_agent.run_search_agent",
              AsyncMock(return_value=_sub_agent_mock("화장실 안내"))),
    ):
        result = await run_orchestrator("화장실 어디야?", 37.56, 126.98)

    assert isinstance(result["session_id"], str)
    assert len(result["session_id"]) == 36  # UUID4 형식
