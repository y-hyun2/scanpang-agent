"""
core/usage_tracker.py
사용자별 LLM 토큰 사용량을 Redis 에 기록.

이번 단계는 **측정만** — 한도 차단(429) X. 1~2주 실측 데이터 모은 후 한도 조정.

키 스키마:
    user:{user_id}:tokens:day:{YYYY-MM-DD}        INT  TTL 자정까지
    user:{user_id}:tokens:month:{YYYY-MM}         INT  TTL 다음 달 1일
    user:{user_id}:purpose:{purpose}:day:{date}   INT  통계용
    global:cost_won:day:{date}                    INT  전체 비용(원, 단가 추정치)

Redis 미연결 시 graceful degradation: 기록 실패해도 LLM 호출은 정상 진행.
"""

from __future__ import annotations

import os
from datetime import datetime, timezone, timedelta
from typing import Optional

import redis.asyncio as aioredis
from dotenv import load_dotenv

load_dotenv()

# gpt-4o / gpt-4o-mini 단가 (2026.05 기준, USD per 1M tokens)
# input / output 비율로 정확히 분리해서 곱하고 ₩ 환산 (1 USD ≈ 1,360원)
_PRICE_USD_PER_M = {
    "gpt-4o":             {"input": 2.50, "output": 10.00},
    "gpt-4o-mini":        {"input": 0.15, "output": 0.60},
}
_USD_TO_KRW = 1360


def _kst_today() -> str:
    """KST 기준 YYYY-MM-DD."""
    return datetime.now(timezone(timedelta(hours=9))).strftime("%Y-%m-%d")


def _kst_month() -> str:
    """KST 기준 YYYY-MM."""
    return datetime.now(timezone(timedelta(hours=9))).strftime("%Y-%m")


def _seconds_until_midnight_kst() -> int:
    """다음 KST 자정까지 초."""
    kst = timezone(timedelta(hours=9))
    now = datetime.now(kst)
    tomorrow = (now + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    return int((tomorrow - now).total_seconds())


def _seconds_until_next_month_kst() -> int:
    """다음 KST 월 1일 자정까지 초."""
    kst = timezone(timedelta(hours=9))
    now = datetime.now(kst)
    if now.month == 12:
        next_month = now.replace(year=now.year + 1, month=1, day=1, hour=0, minute=0, second=0, microsecond=0)
    else:
        next_month = now.replace(month=now.month + 1, day=1, hour=0, minute=0, second=0, microsecond=0)
    return int((next_month - now).total_seconds())


def estimate_cost_won(model: str, prompt_tokens: int, completion_tokens: int) -> float:
    """USD 단가 기반 원화 추정. 모델 모르면 0."""
    price = _PRICE_USD_PER_M.get(model)
    if not price:
        return 0.0
    usd = (
        prompt_tokens     / 1_000_000 * price["input"] +
        completion_tokens / 1_000_000 * price["output"]
    )
    return usd * _USD_TO_KRW


class UsageTracker:
    """Redis 기반 사용량 누적. 싱글턴, graceful degradation."""

    _instance: Optional["UsageTracker"] = None
    _client: Optional[aioredis.Redis] = None

    def __init__(self, redis_url: str):
        self._redis_url = redis_url

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
            print(f"[UsageTracker] Redis 연결 성공: {self._redis_url}")
        except Exception as e:
            print(f"[UsageTracker] Redis 연결 실패: {e}")
            self._client = None

    async def _ensure_connected(self) -> bool:
        if self._client is not None:
            return True
        await self.connect()
        return self._client is not None

    async def record(
        self,
        user_id: str,
        purpose: str,
        model: str,
        prompt_tokens: int,
        completion_tokens: int,
    ) -> None:
        """
        한 LLM 호출의 사용량을 일/월/용도별 카운터에 누적.
        Redis 미연결 또는 user_id 빈 값이면 조용히 패스.
        """
        if not user_id or not await self._ensure_connected():
            return
        total = prompt_tokens + completion_tokens
        cost = estimate_cost_won(model, prompt_tokens, completion_tokens)
        day = _kst_today()
        month = _kst_month()

        try:
            pipe = self._client.pipeline()
            # 사용자별 일/월 누적 + TTL
            pipe.incrby(f"user:{user_id}:tokens:day:{day}", total)
            pipe.expire(f"user:{user_id}:tokens:day:{day}", _seconds_until_midnight_kst())
            pipe.incrby(f"user:{user_id}:tokens:month:{month}", total)
            pipe.expire(f"user:{user_id}:tokens:month:{month}", _seconds_until_next_month_kst())
            # 용도별 일별 누적 (어디서 토큰 많이 쓰는지 통계)
            pipe.incrby(f"user:{user_id}:purpose:{purpose}:day:{day}", total)
            pipe.expire(f"user:{user_id}:purpose:{purpose}:day:{day}", _seconds_until_midnight_kst())
            # 전역 비용 (원 단위 — 소수점 버리고 누적)
            pipe.incrby(f"global:cost_won:day:{day}", int(cost))
            pipe.expire(f"global:cost_won:day:{day}", _seconds_until_midnight_kst() + 86400 * 30)  # 30일 보관
            await pipe.execute()
        except Exception as e:
            print(f"[UsageTracker] record 실패 (무시): {e}")

    async def get_daily_usage(self, user_id: str) -> int:
        """오늘 누적 토큰. Redis 미연결이면 0."""
        if not user_id or not await self._ensure_connected():
            return 0
        try:
            val = await self._client.get(f"user:{user_id}:tokens:day:{_kst_today()}")
            return int(val) if val else 0
        except Exception:
            return 0


def get_tracker() -> UsageTracker:
    """싱글턴 헬퍼. main.py startup 에서 connect() 호출."""
    if UsageTracker._instance is None:
        url = os.getenv("REDIS_URL", "redis://localhost:6379/0")
        UsageTracker._instance = UsageTracker(url)
    return UsageTracker._instance
