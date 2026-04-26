"""
tests/test_session.py
Redis 세션 저장소 + SessionContext 단위 테스트.
Redis가 없는 CI 환경에서도 graceful degradation 동작을 검증한다.
"""

import asyncio
import time
import pytest

from schemas.session import ConversationTurn, SessionContext
from core.session_store import SessionStore, _mask_pii


# ── PII 마스킹 ────────────────────────────────────────────────────────────

def test_mask_phone():
    assert "***-****-****" in _mask_pii("제 번호는 010-1234-5678입니다")

def test_mask_email():
    result = _mask_pii("연락처: user@example.com")
    assert "@example.com" not in result

def test_no_pii():
    text = "화장실 어디야?"
    assert _mask_pii(text) == text


# ── SessionContext.to_prompt_text ─────────────────────────────────────────

def test_empty_context():
    ctx = SessionContext(session_id="s1", turns=[])
    assert ctx.to_prompt_text() == ""

def test_context_text():
    turns = [
        ConversationTurn(role="user",      content="눈스퀘어 뭐야?", agent="",      ts=1),
        ConversationTurn(role="assistant", content="눈스퀘어는 명동의 복합문화공간입니다.", agent="place", ts=2),
    ]
    ctx = SessionContext(session_id="s1", turns=turns)
    text = ctx.to_prompt_text()
    assert "눈스퀘어 뭐야?" in text
    assert "사용자" in text
    assert "AI(place)" in text

def test_context_max_turns():
    turns = [
        ConversationTurn(role="user", content=f"메시지{i}", agent="", ts=i)
        for i in range(10)
    ]
    ctx = SessionContext(session_id="s1", turns=turns)
    text = ctx.to_prompt_text(max_turns=3)
    assert "메시지7" in text
    assert "메시지0" not in text


# ── SessionStore graceful degradation (Redis 없음) ────────────────────────

@pytest.mark.asyncio
async def test_store_no_redis():
    store = SessionStore("redis://localhost:19999/0")  # 없는 포트
    await store.connect()
    assert not store.available

    # 예외 없이 빈 값 반환
    await store.save_turn("s1", "user", "테스트")
    turns = await store.get_recent_turns("s1")
    assert turns == []

    meta = await store.get_meta("s1")
    assert meta == {}

    await store.delete_session("s1")  # 예외 없어야 함


# ── SessionStore with Redis (선택적 통합 테스트, REDIS 환경에서만) ──────────

@pytest.mark.asyncio
async def test_store_with_redis():
    store = SessionStore("redis://localhost:6379/15")  # DB 15 격리
    await store.connect()
    if not store.available:
        pytest.skip("Redis not available")

    sid = f"test_{int(time.time())}"
    try:
        await store.save_turn(sid, "user",      "눈스퀘어 뭐야?",                  mask_pii=False)
        await store.save_turn(sid, "assistant", "명동의 복합문화공간입니다.", agent_name="place", mask_pii=False)
        await store.save_turn(sid, "user",      "거기 어떻게 가?",                  mask_pii=False)

        turns = await store.get_recent_turns(sid, n=5)
        assert len(turns) == 3
        assert turns[0]["role"] == "user"
        assert turns[0]["content"] == "눈스퀘어 뭐야?"
        assert turns[-1]["content"] == "거기 어떻게 가?"
        assert turns[1]["agent"] == "place"
    finally:
        await store.delete_session(sid)
        await store.close()


# ── 지시어 해석 시나리오 (context text 생성 검증) ──────────────────────────

def test_referential_context_scenario():
    """
    턴1: "눈스퀘어 뭐야?" → place
    턴2: "거기 어떻게 가?" → navigation (이전 컨텍스트에 눈스퀘어 포함)
    """
    turns = [
        ConversationTurn(role="user",      content="눈스퀘어 뭐야?",          agent="",      ts=1),
        ConversationTurn(role="assistant", content="눈스퀘어 정보입니다.",     agent="place", ts=2),
    ]
    ctx = SessionContext(session_id="s1", turns=turns)
    prompt_text = ctx.to_prompt_text()

    assert "눈스퀘어" in prompt_text
    assert "AI(place)" in prompt_text
