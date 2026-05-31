"""
run_token_compare.py
질문별 OpenAI(gpt-4o) vs Claude(claude-opus-4-5) 토큰/비용 비교.

TTS 응답 생성(gpt-4o) + Judge 채점(Claude) 각각의 토큰을 캡처해
케이스별 비용을 산출한다.

사용:
    python eval/run_token_compare.py
"""

import asyncio
import json
import os
import pathlib
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from openai import AsyncOpenAI
import anthropic as _anthropic

_openai  = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))
_claude  = _anthropic.AsyncAnthropic(api_key=os.getenv("ANTHROPIC_API_KEY", ""))

# ── 단가 (USD / 1M tokens) ────────────────────────────────────────────────────
PRICE = {
    "gpt-4o":           {"input": 2.50,  "output": 10.00},
    "gpt-4o-mini":      {"input": 0.15,  "output": 0.60},
    "claude-opus-4-5":  {"input": 15.00, "output": 75.00},
}

def calc_cost(model: str, input_tokens: int, output_tokens: int) -> float:
    p = PRICE.get(model, {"input": 0, "output": 0})
    return (input_tokens * p["input"] + output_tokens * p["output"]) / 1_000_000


# ── 시스템 프롬프트 (run_speech_eval.py에서 복사) ──────────────────────────────
from eval.run_speech_eval import (
    ALL_CASES, _JUDGE_SYSTEM,
    _get_search_speech, _get_halal_speech, _get_place_speech,
)

SPEECH_SYSTEM = {
    "search_agent": """You are a helpful AR navigation assistant.
Generate a concise spoken response (2-3 sentences) about the nearest facility.
Respond in the language specified by the 'language' field.
Include: facility name, distance, open hours (if available), and any notable info.
Keep it natural and friendly for TTS.""",
    "halal_agent": """You are a friendly Muslim travel assistant for tourists visiting Seoul, Korea.
Generate a concise 2-3 sentence response suitable for text-to-speech.
Be warm, helpful, and respectful of Islamic practices.""",
    "place_chat": """You are a friendly Seoul tour guide for foreign visitors.
Answer questions about tourist attractions, landmarks, neighborhoods,
historic sites, and famous places in Seoul using your general knowledge.
Respond in 2-3 short sentences suitable for text-to-speech.
Be warm, concise, and informative.""",
}


async def run_one(case: dict) -> dict:
    comp = case["component"]
    lang = case["language"]

    # ── 1. OpenAI: 응답 생성 ──────────────────────────────────────────────────
    if comp == "search_agent":
        context, speech = await _get_search_speech(case)
    elif comp == "halal_agent":
        context, speech = await _get_halal_speech(case)
    else:
        context, speech = await _get_place_speech(case)

    # 토큰 직접 측정: 동일 내용으로 usage 포함 재호출
    gen_model = "gpt-4o"
    system_prompt = SPEECH_SYSTEM.get(comp, "")
    gen_resp = await _openai.chat.completions.create(
        model=gen_model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": context},
        ],
        max_tokens=300,
        temperature=0,  # 토큰 측정용 — 동일 길이 유도
    )
    gen_input  = gen_resp.usage.prompt_tokens
    gen_output = gen_resp.usage.completion_tokens

    # ── 2. Claude: Judge 채점 ─────────────────────────────────────────────────
    judge_model = "claude-opus-4-5"
    user_content = (
        f"[CONTEXT GIVEN TO SYSTEM]\n{context}\n\n"
        f"[SYSTEM RESPONSE]\n{speech}\n\n"
        f"[REQUESTED LANGUAGE] {lang}"
    )
    judge_resp = await _claude.messages.create(
        model=judge_model,
        max_tokens=300,
        system=_JUDGE_SYSTEM,
        messages=[{"role": "user", "content": user_content}],
    )
    judge_input  = judge_resp.usage.input_tokens
    judge_output = judge_resp.usage.output_tokens

    # ── 비용 계산 ─────────────────────────────────────────────────────────────
    gen_cost   = calc_cost(gen_model,   gen_input,   gen_output)
    judge_cost = calc_cost(judge_model, judge_input, judge_output)

    return {
        "id":           case["id"],
        "component":    comp,
        "language":     lang,
        "openai": {
            "model":   gen_model,
            "input":   gen_input,
            "output":  gen_output,
            "cost_usd": gen_cost,
        },
        "claude": {
            "model":   judge_model,
            "input":   judge_input,
            "output":  judge_output,
            "cost_usd": judge_cost,
        },
        "total_cost_usd": gen_cost + judge_cost,
    }


def print_report(results: list[dict]) -> None:
    print("\n" + "=" * 80)
    print("  질문별 OpenAI vs Claude 토큰/비용 비교")
    print("=" * 80)

    print(f"\n{'ID':<28} {'OpenAI 입력':>10} {'OpenAI 출력':>10} {'OpenAI $':>10} "
          f"{'Claude 입력':>11} {'Claude 출력':>11} {'Claude $':>10} {'합계 $':>10}")
    print(f"{'─'*102}")

    for r in results:
        oa = r["openai"]
        cl = r["claude"]
        print(f"{r['id']:<28} {oa['input']:>10,} {oa['output']:>10,} {oa['cost_usd']:>10.5f} "
              f"{cl['input']:>11,} {cl['output']:>11,} {cl['cost_usd']:>10.5f} "
              f"{r['total_cost_usd']:>10.5f}")

    # 합계
    total_oa_in   = sum(r["openai"]["input"]    for r in results)
    total_oa_out  = sum(r["openai"]["output"]   for r in results)
    total_oa_cost = sum(r["openai"]["cost_usd"] for r in results)
    total_cl_in   = sum(r["claude"]["input"]    for r in results)
    total_cl_out  = sum(r["claude"]["output"]   for r in results)
    total_cl_cost = sum(r["claude"]["cost_usd"] for r in results)
    total_cost    = sum(r["total_cost_usd"]     for r in results)

    print(f"{'─'*102}")
    print(f"{'합계 (18개 케이스)':<28} {total_oa_in:>10,} {total_oa_out:>10,} {total_oa_cost:>10.5f} "
          f"{total_cl_in:>11,} {total_cl_out:>11,} {total_cl_cost:>10.5f} {total_cost:>10.5f}")

    print(f"\n[단가 기준]")
    print(f"  gpt-4o          : 입력 $2.50/1M  출력 $10.00/1M")
    print(f"  claude-opus-4-5 : 입력 $15.00/1M 출력 $75.00/1M")

    print(f"\n[비용 요약]")
    print(f"  OpenAI (gpt-4o) 응답 생성 18건 : ${total_oa_cost:.5f} ({total_oa_in+total_oa_out:,} 토큰)")
    print(f"  Claude Judge 채점 18건          : ${total_cl_cost:.5f} ({total_cl_in+total_cl_out:,} 토큰)")
    print(f"  전체 합계                        : ${total_cost:.5f}")
    print(f"\n  케이스당 평균 : ${total_cost/len(results):.5f}")

    # 컴포넌트별
    from collections import defaultdict
    comp_stats: dict[str, dict] = defaultdict(lambda: {"oa_cost": 0, "cl_cost": 0, "n": 0})
    for r in results:
        comp_stats[r["component"]]["oa_cost"] += r["openai"]["cost_usd"]
        comp_stats[r["component"]]["cl_cost"] += r["claude"]["cost_usd"]
        comp_stats[r["component"]]["n"] += 1

    print(f"\n[컴포넌트별 평균 비용]")
    print(f"  {'컴포넌트':<25} {'OpenAI/건':>12} {'Claude/건':>12} {'합계/건':>12}")
    print(f"  {'─'*64}")
    for comp, s in comp_stats.items():
        n = s["n"]
        print(f"  {comp:<25} ${s['oa_cost']/n:>10.5f} ${s['cl_cost']/n:>10.5f} ${(s['oa_cost']+s['cl_cost'])/n:>10.5f}")

    print()


async def main() -> None:
    print(f"18개 케이스 토큰 측정 중... (OpenAI + Claude 각각 호출)")
    results = []
    for i, case in enumerate(ALL_CASES, 1):
        print(f"  [{i:2d}/18] {case['id']}", end="\r")
        r = await run_one(case)
        results.append(r)
    print(f"  완료 18개                    ")

    print_report(results)

    out = pathlib.Path("eval/logs/token_compare.json")
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"결과 저장: {out}")


if __name__ == "__main__":
    asyncio.run(main())
