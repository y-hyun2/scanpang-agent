"""
orchestrator_agent.py
LangGraph 기반 멀티-에이전트 오케스트레이터.

흐름: intent_classifier → (place | navigation | halal | convenience) → response_synthesizer

세션 처리:
  - 요청 전: Redis에서 최근 5턴 조회 → intent_classifier 프롬프트에 주입
  - 요청 후: user 발화 + assistant 응답을 Redis에 저장 (TTL 24h, 최대 10턴)
  - Redis 미연결: graceful degradation (세션 없이 단일 턴으로 동작)
"""

import json
import uuid
from typing import Literal, Optional

from dotenv import load_dotenv
from langgraph.graph import StateGraph, START, END
from typing_extensions import TypedDict

from agents.halal_agent import run_halal_agent
from agents.navigation_agent import run_route_search, run_nav_guide_agent
from agents.search_agent import run_search_agent
from agents.place_insight_agent import run_place_chat_agent
from core.session_store import get_session_store
from schemas.convenience import ConvenienceRequest
from schemas.halal import HalalRequest
from schemas.navigation import NavRequest
from tools.llm_client import call_llm
from schemas.session import ConversationTurn, SessionContext

load_dotenv()

# ── 상태 정의 ──────────────────────────────────────────────────────────────

class OrchestratorState(TypedDict):
    user_message: str             # 원본 — Redis 세션 저장용 (사용자 그대로 보존)
    resolved_message: str         # 지시어 풀어쓴 버전 — sub-agent 호출용
    user_lat: float
    user_lng: float
    user_heading: float
    user_id: str                  # Supabase auth.users.id — LLM 사용량 통계 라벨
    language: str
    session_id: str
    session_context: str          # 프롬프트에 삽입할 이전 대화 이력 텍스트
    selected_agent: Literal["place", "navigation", "nav_guide", "halal", "convenience", "smalltalk"]
    sub_category: str             # halal/convenience 세부 카테고리 — sub-agent _extract 우회용
    nav_context: dict             # AR 길안내 중 frontend 가 전달하는 현재 상태 (있으면 nav_guide 라우팅 후보)
    sub_agent_response: dict
    final_speech: str
    source_agent: str


# ── Intent 분류 프롬프트 ────────────────────────────────────────────────────

_INTENT_SYSTEM = """당신은 AR 관광 앱의 요청 라우터입니다. 사용자 메시지를 분석해 세 가지를 동시에 출력하세요:
1) 4개 에이전트 중 하나 선택 (selected_agent)
2) 메시지 안의 지시어를 이전 대화 맥락으로 풀어쓰기 (resolved_message)
3) halal/convenience 의 세부 카테고리 (sub_category, place/navigation 은 "")

응답은 반드시 JSON 형식
`{"selected_agent": "<agent>", "resolved_message": "<텍스트>", "sub_category": "<카테고리|빈문자열>"}`
한 줄로만 출력하세요.

에이전트 설명:
- place     : 관광지 / 랜드마크 / 지역·동네 일반 정보. 일반지식 기반 해설 응답.
              예) "남산타워 언제 지어졌어?", "경복궁 설명해줘", "여기 어디야?(동네)", "명동은 어떤 곳이야?"
- navigation: 특정 목적지로 이동 (경로 안내, 길 찾기) — "X로 가는 길", "X 어떻게 가"
- nav_guide : AR 길안내 중 현재 경로/방향에 대한 질문 — "여기서 좌회전 맞아?", "다음 갈림길은?",
              "거의 다 왔어?", "남은 거리는?" 등. 사용자가 이미 길안내 중일 때만 선택 가능.
- halal     : 무슬림 종교 정보 — 기도 시간(prayer_time), 키블라 방향(qibla) 만.
              할랄 식당/기도실 검색은 convenience 로 라우팅한다.
- convenience: 매장(상호명 포함) 및 모든 시설 검색 — 카페, 식당, ATM, 약국, 화장실,
              환전소, 지하철, 주차장, 상점, 할랄 식당, 기도실 등.
              (예: "이 매장 뭐 파는 곳이야?", "스타벅스 영업시간", "할랄 식당", "기도실 어디")
- smalltalk : 순수 인사 / 잡담 / 단순 감탄사 ("?", "음...", "안녕", "고마워", "심심해",
              "ㅋㅋ" 등). 아래 경우는 절대 smalltalk이 아님:
              · 장소·시설 이름이나 "어디야/찾아줘/알려줘/있어?"가 포함된 문장 → convenience 또는 navigation
              · 거리·방향·남은 경로에 대한 질문 → nav_guide
              · 기도 시간·키블라·살라트 관련 → halal
              · 단어 하나라도 아랍어(قبلة 등) 또는 이슬람 용어면 → halal 또는 convenience

핵심 구분 규칙:
- 기도 시간 / 키블라 / 살라트 관련 질문 → "halal"
- 할랄 식당, 무슬림 식당 → "convenience" (sub_category="halal_restaurant")
- 기도실, 무슬라(Musalla), 예배실 → "convenience" (sub_category="prayer_room")
- 일반 식당/카페/매장 검색 및 특정 매장(상호명) 정보 질문 → "convenience"
- 관광지/랜드마크/지역·동네에 대한 질문 → "place"
  (단, 매장/상점/특정 상호명이 들어가면 "convenience")
- 새 목적지를 정하는 길 찾기는 "navigation"
- 이미 진행 중인 길안내에 대한 질문(좌회전 맞아? 다음은? 얼마나 남았어?)은 "nav_guide"
- 인사/잡담/의미 불명 발화("?", "음...", "안녕", "ㅋㅋ", "고마워")는 "smalltalk"
- 단순 긍정 응답("ㅇㅇ", "응", "네", "예", "yes", "ok", "okay", "그래") 만 단독으로
  들어온 경우 이전 대화 맥락을 본다:
  · 직전 assistant 발화가 특정 장소 안내 제안("X 안내할까요?", "이 곳으로
    안내할까요?", "Y로 가시겠어요?") 이면 → "navigation",
    resolved_message = 그 장소 이름 + "(으)로 안내해줘" (예: "명동역(으)로 안내해줘")
  · 직전 assistant 가 다른 종류의 제안/질문이면 → "smalltalk" (확장 가능)
  · 이전 맥락이 없으면 → "smalltalk"
- 단순 부정 응답("아니", "아냐", "노", "no") + 직전이 안내 제안이면 → "smalltalk"
  (안내 취소 의사로 보고 도메인 에이전트 호출 X)

sub_category 값 (selected_agent 별):
- halal       : "prayer_time" | "qibla"
- convenience : "cafe" | "restaurant" | "convenience_store" | "pharmacy" | "hospital" |
                "bank" | "atm" | "shopping" | "parking" | "subway" | "tourist" |
                "accommodation" | "cultural" | "exchange" | "restroom" | "locker" |
                "halal_restaurant" | "prayer_room"
- place / navigation / nav_guide / smalltalk : "" (빈 문자열)

resolved_message 규칙:
- 지시어("거기", "그곳", "저기", "여기", "방금 말한 곳", "그 건물" 등)가 있고 이전 대화에서 가리키는 장소가 명확하면 그 장소 이름으로 치환.
  예: ctx="user: 눈스퀘어 뭐야?  assistant: ..." + 현재="거기 어떻게 가?"
      → resolved_message = "눈스퀘어 어떻게 가?"
- 지시어 없거나 가리키는 대상이 모호하면 원문 그대로.
- 사용자가 명시적으로 장소를 말했으면(예: "명동성당까지 가") 원문 그대로.

아래 예시를 참고하세요."""

_FEW_SHOTS = [
    {"role": "user",      "content": '메시지: "명동성당에 대해 설명해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "place", "resolved_message": "명동성당에 대해 설명해줘", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "남산타워 언제 지어졌어?"'},
    {"role": "assistant", "content": '{"selected_agent": "place", "resolved_message": "남산타워 언제 지어졌어?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "여기 무슨 동네야?"'},
    {"role": "assistant", "content": '{"selected_agent": "place", "resolved_message": "여기 무슨 동네야?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "경복궁 역사 알려줘"'},
    {"role": "assistant", "content": '{"selected_agent": "place", "resolved_message": "경복궁 역사 알려줘", "sub_category": ""}'},
    # 매장(상호명) 발화 — place 아니라 convenience
    {"role": "user",      "content": '메시지: "이 매장 뭐 파는 곳이야?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "이 매장 뭐 파는 곳이야?", "sub_category": "shopping"}'},
    {"role": "user",      "content": '메시지: "스타벅스 영업시간 알려줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "스타벅스 영업시간 알려줘", "sub_category": "cafe"}'},
    {"role": "user",      "content": '메시지: "올리브영 어디 있어?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "올리브영 어디 있어?", "sub_category": "shopping"}'},
    {"role": "user",      "content": '이전 대화:\nuser: 눈스퀘어 뭐야?\nassistant: 눈스퀘어는 명동 쇼핑몰입니다.\n\n메시지: "거기 어떻게 가?"'},
    {"role": "assistant", "content": '{"selected_agent": "navigation", "resolved_message": "눈스퀘어 어떻게 가?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "명동역으로 길 안내해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "navigation", "resolved_message": "명동역으로 길 안내해줘", "sub_category": ""}'},
    # nav_guide 예시 — AR 길안내 진행 중 사용자가 현재 경로에 대해 묻는 케이스
    {"role": "user",      "content": '메시지: "여기서 좌회전 맞아?"'},
    {"role": "assistant", "content": '{"selected_agent": "nav_guide", "resolved_message": "여기서 좌회전 맞아?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "이 갈림길에서 어디로 가?"'},
    {"role": "assistant", "content": '{"selected_agent": "nav_guide", "resolved_message": "이 갈림길에서 어디로 가?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "거의 다 왔어?"'},
    {"role": "assistant", "content": '{"selected_agent": "nav_guide", "resolved_message": "거의 다 왔어?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "얼마나 남았어?"'},
    {"role": "assistant", "content": '{"selected_agent": "nav_guide", "resolved_message": "얼마나 남았어?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "기도 시간 알려줘"'},
    {"role": "assistant", "content": '{"selected_agent": "halal", "resolved_message": "기도 시간 알려줘", "sub_category": "prayer_time"}'},
    {"role": "user",      "content": '메시지: "지금 아스르 기도 시간이야?"'},
    {"role": "assistant", "content": '{"selected_agent": "halal", "resolved_message": "지금 아스르 기도 시간이야?", "sub_category": "prayer_time"}'},
    {"role": "user",      "content": '메시지: "키블라 방향이 어디야?"'},
    {"role": "assistant", "content": '{"selected_agent": "halal", "resolved_message": "키블라 방향이 어디야?", "sub_category": "qibla"}'},
    {"role": "user",      "content": '메시지: "할랄 식당 어디 있어?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "할랄 식당 어디 있어?", "sub_category": "halal_restaurant"}'},
    {"role": "user",      "content": '메시지: "무슬림 음식 추천해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "무슬림 음식 추천해줘", "sub_category": "halal_restaurant"}'},
    {"role": "user",      "content": '메시지: "기도실 어디야?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "기도실 어디야?", "sub_category": "prayer_room"}'},
    {"role": "user",      "content": '메시지: "무슬라 가까운 데 있나?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "무슬라 가까운 데 있나?", "sub_category": "prayer_room"}'},
    {"role": "user",      "content": '메시지: "근처 ATM 찾아줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "근처 ATM 찾아줘", "sub_category": "atm"}'},
    {"role": "user",      "content": '메시지: "카페 추천해줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "카페 추천해줘", "sub_category": "cafe"}'},
    {"role": "user",      "content": '메시지: "환전소 어디 있어?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "환전소 어디 있어?", "sub_category": "exchange"}'},
    {"role": "user",      "content": '메시지: "약국 찾아줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "약국 찾아줘", "sub_category": "pharmacy"}'},
    {"role": "user",      "content": '메시지: "화장실 어디야?"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "화장실 어디야?", "sub_category": "restroom"}'},
    {"role": "user",      "content": '메시지: "지하철역 알려줘"'},
    {"role": "assistant", "content": '{"selected_agent": "convenience", "resolved_message": "지하철역 알려줘", "sub_category": "subway"}'},
    # smalltalk — 도메인 모호 발화: 도메인 에이전트 거치지 않고 범용 응답
    {"role": "user",      "content": '메시지: "?"'},
    {"role": "assistant", "content": '{"selected_agent": "smalltalk", "resolved_message": "?", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "음..."'},
    {"role": "assistant", "content": '{"selected_agent": "smalltalk", "resolved_message": "음...", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "안녕"'},
    {"role": "assistant", "content": '{"selected_agent": "smalltalk", "resolved_message": "안녕", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "고마워!"'},
    {"role": "assistant", "content": '{"selected_agent": "smalltalk", "resolved_message": "고마워!", "sub_category": ""}'},
    {"role": "user",      "content": '메시지: "ㅋㅋ"'},
    {"role": "assistant", "content": '{"selected_agent": "smalltalk", "resolved_message": "ㅋㅋ", "sub_category": ""}'},
    # 안내 제안 후 단순 긍정 응답 — navigation 으로 분기 + 원래 목적지 복원
    {"role": "user",      "content": '이전 대화:\nuser: 명동역까지 길안내좀\nassistant: \'명동역 4호선\' 찾았어요. 서울 중구 퇴계로 지하 126. 이 곳으로 안내할까요?\n\n메시지: "ㅇㅇ"'},
    {"role": "assistant", "content": '{"selected_agent": "navigation", "resolved_message": "명동역(으)로 안내해줘", "sub_category": ""}'},
    {"role": "user",      "content": '이전 대화:\nuser: 스타벅스\nassistant: \'스타벅스 청계크리스탈스퀘어점\'은 여기서 203미터 거리에 있습니다. 이 곳으로 안내할까요?\n\n메시지: "응"'},
    {"role": "assistant", "content": '{"selected_agent": "navigation", "resolved_message": "스타벅스 청계크리스탈스퀘어점(으)로 안내해줘", "sub_category": ""}'},
    {"role": "user",      "content": '이전 대화:\nuser: 명동역 길안내\nassistant: 명동역 찾았어요. 이 곳으로 안내할까요?\n\n메시지: "아니"'},
    {"role": "assistant", "content": '{"selected_agent": "smalltalk", "resolved_message": "아니", "sub_category": ""}'},
]

# ── 독립 분류 함수 (테스트 가능) ─────────────────────────────────────────────

_VALID_AGENTS = ("place", "navigation", "nav_guide", "halal", "convenience", "smalltalk")


async def classify_intent(
    message: str,
    session_context: str = "",
    user_id: str = "",
    model: str = "gpt-5.4-mini",  # 베이스라인 평가 결과 4o(F1 0.882) 대비 5.4-mini(1.000)가 더 정확+저렴
) -> tuple[Literal["place", "navigation", "nav_guide", "halal", "convenience", "smalltalk"], str, str]:
    """
    사용자 메시지 → (에이전트, resolved_message, sub_category) 튜플.
    sub_category 는 halal/convenience 일 때만 값 있음, 나머지는 빈 문자열.
    실패 시 (smalltalk, message, "") 반환 — 검색 도구 호출 없이 범용 응답.
    """
    user_content = f'메시지: "{message}"'
    if session_context:
        user_content = f"{session_context}\n\n{user_content}"

    messages = [
        {"role": "system", "content": _INTENT_SYSTEM},
        *_FEW_SHOTS,
        {"role": "user", "content": user_content},
    ]
    try:
        content = await call_llm(
            user_id=user_id,
            purpose="intent_classify",
            messages=messages,
            model=model,
            temperature=0,
            response_format={"type": "json_object"},
        )
        data = json.loads(content)
        agent = data.get("selected_agent", "smalltalk")
        if agent not in _VALID_AGENTS:
            agent = "smalltalk"
        resolved = (data.get("resolved_message") or message).strip() or message
        sub_category = (data.get("sub_category") or "").strip()
        return agent, resolved, sub_category
    except Exception:
        return "smalltalk", message, ""


# ── LangGraph 노드 ────────────────────────────────────────────────────────

async def _intent_classifier_node(state: OrchestratorState) -> dict:
    selected, resolved, sub_category = await classify_intent(
        state["user_message"],
        session_context=state.get("session_context", ""),
        user_id=state.get("user_id", ""),
    )
    if resolved != state["user_message"]:
        print(f"[orchestrator] 지시어 resolve: {state['user_message']!r} → {resolved!r}")
    has_nav_ctx = bool(state.get("nav_context"))
    print(f"[orchestrator] route: agent={selected}, sub={sub_category or '-'}, nav_context={has_nav_ctx}")
    return {
        "selected_agent":   selected,
        "source_agent":     selected,
        "resolved_message": resolved,
        "sub_category":     sub_category,
    }


def _sub_message(state: OrchestratorState) -> str:
    """sub-agent 에 전달할 메시지 — 지시어 resolve 된 버전 우선, 없으면 원본."""
    return state.get("resolved_message") or state["user_message"]


async def _call_place_node(state: OrchestratorState) -> dict:
    # 채팅 흐름은 ufid 가 없으므로 LLM 일반지식 기반 chat 진입점 사용.
    # 매장(상호명) 발화는 intent_classifier 가 convenience 로 라우팅한다.
    result = await run_place_chat_agent(
        message=_sub_message(state),
        lat=state["user_lat"],
        lng=state["user_lng"],
        language=state.get("language", "ko"),
        user_id=state.get("user_id", ""),
    )
    return {"sub_agent_response": result}


async def _call_navigation_node(state: OrchestratorState) -> dict:
    req = NavRequest(
        message=_sub_message(state),
        lat=state["user_lat"],
        lng=state["user_lng"],
    )
    result = await run_route_search(req, user_id=state.get("user_id", ""), language_override=state.get("language"))
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


async def _call_nav_guide_node(state: OrchestratorState) -> dict:
    # AR 길안내 중 어시스턴트 — frontend 가 보낸 nav_context (현재 turn/거리/목적지)
    # 를 그대로 LLM 프롬프트에 주입해서 응답 생성.
    nav_ctx = state.get("nav_context", {}) or {}
    print(f"[nav_guide] msg={_sub_message(state)!r}, nav_context.keys={list(nav_ctx.keys())}, is_routing={nav_ctx.get('is_routing')}")
    result = await run_nav_guide_agent(
        message=_sub_message(state),
        nav_context=nav_ctx,
        language=state.get("language", "ko"),
        user_id=state.get("user_id", ""),
    )
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


async def _call_halal_node(state: OrchestratorState) -> dict:
    # category 가 채워져 있으면 halal_agent 의 _extract_category(LLM #2) 호출 스킵.
    # orchestrator 의 intent_classifier 가 이미 sub_category 까지 결정했음.
    req = HalalRequest(
        category=state.get("sub_category", ""),
        message=_sub_message(state),
        lat=state["user_lat"],
        lng=state["user_lng"],
        language=state.get("language", "ko"),
    )
    result = await run_halal_agent(req, user_id=state.get("user_id", ""))
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


async def _call_convenience_node(state: OrchestratorState) -> dict:
    # category 가 채워져 있으면 search_agent 의 _extract_category_and_language
    # (LLM #2) 호출 스킵.
    req = ConvenienceRequest(
        category=state.get("sub_category", ""),
        message=_sub_message(state),
        lat=state["user_lat"],
        lng=state["user_lng"],
        language=state.get("language", "ko"),
    )
    result = await run_search_agent(req, user_id=state.get("user_id", ""))
    return {"sub_agent_response": result if isinstance(result, dict) else result.model_dump()}


_SMALLTALK_SYSTEM = (
    "You are a friendly AR travel assistant for Seoul. The user said something vague, "
    "ambiguous, or off-topic (a greeting, filler, single punctuation, etc.). "
    "Respond in 1-2 short, warm sentences suitable for text-to-speech. "
    "Do NOT invent facts about places. If the user seems lost, "
    "briefly suggest they can ask about nearby cafes, restrooms, landmarks, or directions."
)


async def _call_smalltalk_node(state: OrchestratorState) -> dict:
    # 도메인 에이전트 호출 없이 범용 응답만 생성. 비용 최소화 위해 max_tokens 짧게.
    lang_label = {
        "ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese",
    }.get(state.get("language", "ko"), "Korean")
    try:
        speech = await call_llm(
            user_id=state.get("user_id", ""),
            purpose="smalltalk",
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": _SMALLTALK_SYSTEM + f" Respond in {lang_label}."},
                {"role": "user",   "content": state["user_message"]},
            ],
            max_tokens=120,
            temperature=0.7,
        )
    except Exception:
        speech = "How can I help? Ask me about nearby cafes, restrooms, landmarks, or directions." if lang_label == "English" else "무엇을 도와드릴까요? 주변 카페·화장실·관광지나 길안내를 물어봐 주세요."
    return {"sub_agent_response": {"speech": speech, "raw": {}}}


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
    g.add_node("call_nav_guide",    _call_nav_guide_node)
    g.add_node("call_halal",        _call_halal_node)
    g.add_node("call_convenience",  _call_convenience_node)
    g.add_node("call_smalltalk",    _call_smalltalk_node)
    g.add_node("response_synthesizer", _response_synthesizer_node)

    g.add_edge(START, "intent_classifier")
    g.add_conditional_edges(
        "intent_classifier",
        _route_after_classifier,
        {
            "place":        "call_place",
            "navigation":   "call_navigation",
            "nav_guide":    "call_nav_guide",
            "halal":        "call_halal",
            "convenience":  "call_convenience",
            "smalltalk":    "call_smalltalk",
        },
    )
    g.add_edge("call_place",       "response_synthesizer")
    g.add_edge("call_navigation",  "response_synthesizer")
    g.add_edge("call_nav_guide",   "response_synthesizer")
    g.add_edge("call_halal",       "response_synthesizer")
    g.add_edge("call_convenience", "response_synthesizer")
    g.add_edge("call_smalltalk",   "response_synthesizer")
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
    session_id: Optional[str] = None,
    nav_context: Optional[dict] = None,
    user_id: str = "",
) -> dict:
    """
    단일 진입점: 메시지를 받아 적절한 에이전트로 라우팅하고 결과를 반환한다.
    session_id가 있으면 Redis에서 이전 대화 이력을 조회해 intent_classifier에 주입한다.

    Returns:
        {
            "speech": str,
            "source_agent": str,
            "raw_data": dict,
            "session_id": str,
        }
    """
    sid = session_id or str(uuid.uuid4())
    store = get_session_store()

    # ── 세션 컨텍스트 조회 ────────────────────────────────────────────────
    # get_recent_turns 자체가 _ensure_connected 로 lazy reconnect 하므로
    # available 가드는 session_id 유무만 본다.
    session_context = ""
    if session_id:
        raw_turns = await store.get_recent_turns(sid, n=5)
        turns = [ConversationTurn(**t) for t in raw_turns]
        ctx = SessionContext(session_id=sid, turns=turns)
        session_context = ctx.to_prompt_text()

    # ── LangGraph 실행 ────────────────────────────────────────────────────
    initial_state: OrchestratorState = {
        "user_message":    message,
        "user_lat":        lat,
        "user_lng":        lng,
        "user_heading":    heading,
        "user_id":         user_id,
        "language":        language,
        "session_id":      sid,
        "session_context": session_context,
        "selected_agent":  "smalltalk",
        "resolved_message": message,
        "sub_category":    "",
        "nav_context":     nav_context or {},
        "sub_agent_response": {},
        "final_speech":    "",
        "source_agent":    "",
    }

    result = await _graph.ainvoke(initial_state)

    speech       = result["final_speech"]
    source_agent = result["source_agent"]
    raw_data     = result["sub_agent_response"]

    # ── 세션 저장 (user + assistant 턴) ──────────────────────────────────
    await store.save_turn(sid, "user",      message, mask_pii=True)
    await store.save_turn(sid, "assistant", speech,  agent_name=source_agent, mask_pii=False)

    return {
        "speech":       speech,
        "source_agent": source_agent,
        "raw_data":     raw_data,
        "session_id":   sid,
    }
