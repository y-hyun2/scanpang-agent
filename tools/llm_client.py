"""
tools/llm_client.py
프로젝트 내 모든 LLM 호출의 단일 진입점.

사용 패턴:
    from tools.llm_client import call_llm

    content = await call_llm(
        user_id=user_id,
        purpose="docent",        # 통계 라벨
        messages=[{"role": "system", "content": ...}, ...],
        model="gpt-4o",          # 기본값
        # 그 외 OpenAI 옵션 (temperature, max_tokens, response_format 등) 그대로 전달
    )

설계 의도:
- 12군데 흩어진 LLM 호출을 한 함수로 통합 → 향후 토큰 카운팅·rate limit·모델 교체가 한 곳에서.
- 응답의 usage.{prompt,completion}_tokens 를 UsageTracker 가 Redis 에 기록.
- 백그라운드 enrichment 호출은 user_id="" 로 전달 → 통계 기록 스킵.
- record=False 옵션으로 통계 기록 명시 비활성화 가능 (테스트 등).
"""

from __future__ import annotations

import os
from typing import Any, Optional

from dotenv import load_dotenv
from openai import AsyncOpenAI

from core.usage_tracker import get_tracker

load_dotenv()

_OPENAI_KEY = os.getenv("OPENAI_API_KEY", "")
_client: Optional[AsyncOpenAI] = None


def _get_client() -> AsyncOpenAI:
    global _client
    if _client is None:
        _client = AsyncOpenAI(api_key=_OPENAI_KEY)
    return _client


async def call_llm(
    user_id: str,
    purpose: str,
    messages: list[dict],
    model: str = "gpt-4o",
    record: bool = True,
    **openai_kwargs: Any,
) -> str:
    """
    OpenAI chat completion 호출 + 사용량 자동 기록.

    Args:
        user_id: 호출한 사용자 식별자 (Supabase auth.users.id). 빈 문자열이면 기록 안 함.
        purpose: 통계 라벨 (예: "docent", "nav_speech", "intent_classify").
        messages: OpenAI chat messages 포맷.
        model: 기본 gpt-4o. mini 강등 후보면 gpt-4o-mini.
        record: False 면 Redis 기록 생략 (백그라운드/테스트용).
        **openai_kwargs: temperature, max_tokens, response_format 등 그대로 전달.

    Returns:
        응답 첫 choice 의 message.content (문자열).
    """
    client = _get_client()
    response = await client.chat.completions.create(
        model=model,
        messages=messages,
        **openai_kwargs,
    )

    if record and user_id:
        try:
            usage = response.usage
            await get_tracker().record(
                user_id=user_id,
                purpose=purpose,
                model=model,
                prompt_tokens=usage.prompt_tokens,
                completion_tokens=usage.completion_tokens,
            )
        except Exception as e:
            # 통계 기록 실패는 본 응답에 영향 X
            print(f"[llm_client] usage 기록 실패 (무시): {e}")

    return (response.choices[0].message.content or "").strip()
