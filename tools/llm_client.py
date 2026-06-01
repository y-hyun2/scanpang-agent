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
from typing import Any

from dotenv import load_dotenv
from openai import AsyncOpenAI

from core.usage_tracker import get_tracker

load_dotenv()

_OPENAI_KEY = os.getenv("OPENAI_API_KEY", "")
_OPENROUTER_KEY = os.getenv("OPENROUTER_API_KEY", "")
_clients: dict[str, AsyncOpenAI] = {}


def _get_client(model: str) -> AsyncOpenAI:
    """모델 이름에 '/' 가 있으면 OpenRouter(vendor/model 형식), 아니면 OpenAI."""
    backend = "openrouter" if "/" in model else "openai"
    if backend not in _clients:
        if backend == "openrouter":
            _clients[backend] = AsyncOpenAI(
                api_key=_OPENROUTER_KEY,
                base_url="https://openrouter.ai/api/v1",
            )
        else:
            _clients[backend] = AsyncOpenAI(api_key=_OPENAI_KEY)
    return _clients[backend]


def normalize_model_kwargs(model: str, kwargs: dict) -> dict:
    """모델별 파라미터 차이를 보정한다.

    - gpt-5 / o-series: max_tokens 미지원 → max_completion_tokens 로 변환.
    - o-series(o1/o3/o4, 순수 reasoning)만 temperature 커스텀값 미지원 → 제거.
      (gpt-5.4-mini 등 gpt-5 계열은 temperature=0 정상 지원 → 결정성 위해 유지)
    """
    base = model.split("/")[-1]
    if base.startswith(("gpt-5", "o1", "o3", "o4")):
        kwargs = dict(kwargs)
        if "max_tokens" in kwargs:
            kwargs["max_completion_tokens"] = kwargs.pop("max_tokens")
        if base.startswith(("o1", "o3", "o4")):
            kwargs.pop("temperature", None)
    return kwargs


async def call_llm(
    user_id: str,
    purpose: str,
    messages: list[dict],
    model: str = "gpt-5.4-mini",
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
    client = _get_client(model)
    response = await client.chat.completions.create(
        model=model,
        messages=messages,
        **normalize_model_kwargs(model, openai_kwargs),
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
