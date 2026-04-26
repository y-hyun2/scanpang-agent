"""
core/session_store.py
Redis 기반 대화 세션 저장소.

구조: HASH  scanpang:session:{session_id}:meta   → {language, created_at}
      LIST  scanpang:session:{session_id}:turns  → [{role,content,agent,ts}, ...]
                                                   LPUSH → 최신이 index 0

TTL: 24시간 (마지막 활동 기준으로 자동 연장)
최대 보관: 최근 10턴
PII: 기본값 off (필요 시 subclass 에서 override)
"""

import json
import logging
import os
import re
import time
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

_TURN_KEY   = "scanpang:session:{sid}:turns"
_META_KEY   = "scanpang:session:{sid}:meta"
_TTL        = 60 * 60 * 24   # 24시간(초)
_MAX_TURNS  = 10


# ── PII 패턴 (선택적 마스킹) ───────────────────────────────────────────────
_PII_PATTERNS = [
    (re.compile(r"\d{2,3}-\d{3,4}-\d{4}"), "***-****-****"),   # 전화번호
    (re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"), "****@****.***"),  # 이메일
]


def _mask_pii(text: str) -> str:
    for pattern, replacement in _PII_PATTERNS:
        text = pattern.sub(replacement, text)
    return text


class SessionStore:
    """
    싱글턴 세션 저장소.
    Redis 미연결 시 graceful degradation: 모든 메서드가 빈 값을 반환하고 예외를 삼킨다.
    """

    _instance: Optional["SessionStore"] = None
    _client: Optional[aioredis.Redis] = None

    def __init__(self, redis_url: str):
        self._redis_url = redis_url

    # ── 연결 ────────────────────────────────────────────────────────────────

    async def connect(self) -> None:
        try:
            self._client = aioredis.from_url(
                self._redis_url,
                encoding="utf-8",
                decode_responses=True,
                socket_connect_timeout=2,
                socket_timeout=2,
            )
            await self._client.ping()
            logger.info("Redis 연결 성공: %s", self._redis_url)
        except Exception as e:
            logger.warning("Redis 연결 실패 (세션 비활성화): %s", e)
            self._client = None

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()

    @property
    def available(self) -> bool:
        return self._client is not None

    # ── 세션 저장 ─────────────────────────────────────────────────────────

    async def save_turn(
        self,
        session_id: str,
        role: str,            # "user" | "assistant"
        content: str,
        agent_name: str = "",
        mask_pii: bool = True,
    ) -> None:
        if not self.available:
            return
        try:
            if mask_pii:
                content = _mask_pii(content)

            turn = json.dumps(
                {"role": role, "content": content, "agent": agent_name, "ts": int(time.time())},
                ensure_ascii=False,
            )
            tkey = _TURN_KEY.format(sid=session_id)
            mkey = _META_KEY.format(sid=session_id)

            pipe = self._client.pipeline()
            pipe.lpush(tkey, turn)
            pipe.ltrim(tkey, 0, _MAX_TURNS - 1)
            pipe.expire(tkey, _TTL)
            pipe.hset(mkey, mapping={"last_active": int(time.time())})
            pipe.expire(mkey, _TTL)
            await pipe.execute()
        except Exception as e:
            logger.warning("save_turn 실패 (무시): %s", e)

    # ── 세션 조회 ─────────────────────────────────────────────────────────

    async def get_recent_turns(
        self,
        session_id: str,
        n: int = 5,
    ) -> list[dict]:
        """최근 n턴을 오래된 순으로 반환 (index 0 = 가장 오래된 것)."""
        if not self.available:
            return []
        try:
            tkey = _TURN_KEY.format(sid=session_id)
            raw_turns = await self._client.lrange(tkey, 0, n - 1)
            turns = [json.loads(t) for t in raw_turns]
            turns.reverse()   # LPUSH 저장이라 최신이 앞 → 뒤집어 오래된 순으로
            return turns
        except Exception as e:
            logger.warning("get_recent_turns 실패 (무시): %s", e)
            return []

    async def get_meta(self, session_id: str) -> dict:
        if not self.available:
            return {}
        try:
            mkey = _META_KEY.format(sid=session_id)
            return await self._client.hgetall(mkey) or {}
        except Exception as e:
            logger.warning("get_meta 실패 (무시): %s", e)
            return {}

    async def delete_session(self, session_id: str) -> None:
        if not self.available:
            return
        try:
            pipe = self._client.pipeline()
            pipe.delete(_TURN_KEY.format(sid=session_id))
            pipe.delete(_META_KEY.format(sid=session_id))
            await pipe.execute()
        except Exception as e:
            logger.warning("delete_session 실패 (무시): %s", e)


# ── 모듈 레벨 싱글턴 ────────────────────────────────────────────────────────

_session_store: Optional[SessionStore] = None


def get_session_store() -> SessionStore:
    global _session_store
    if _session_store is None:
        url = os.getenv("REDIS_URL", "redis://localhost:6379/0")
        _session_store = SessionStore(url)
    return _session_store
