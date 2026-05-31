"""
run_api_compare.py
프로젝트 내 모든 LLM 역할을 OpenAI / Claude / Gemini 3개 API로 수행하고
토큰 수와 비용을 비교한다.

비교 대상 역할:
  1. 인텐트 분류    (orchestrator_agent)   — gpt-4o
  2. TTS 응답 생성  (search/halal/place)   — gpt-4o
  3. 카테고리 추출  (search_agent)         — gpt-4o
  4. 층별 정보 추출 (llm_extractor)        — gpt-4o-mini
  5. 영업시간 파싱  (open_hours_normalizer) — gpt-4o-mini

사용:
    python eval/run_api_compare.py
"""

import asyncio, json, os, pathlib, sys, re
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from openai import AsyncOpenAI
import anthropic as _anthropic
from google import genai as _genai

_openai  = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))
_claude  = _anthropic.AsyncAnthropic(api_key=os.getenv("ANTHROPIC_API_KEY", ""))
_gemini  = _genai.Client(api_key=os.getenv("GEMINI_API_KEY", ""))

# ── 단가 (USD / 1M tokens) ────────────────────────────────────────────────────
PRICE = {
    "gpt-4o":              {"input": 2.50,  "output": 10.00},
    "gpt-4o-mini":         {"input": 0.15,  "output": 0.60},
    "claude-opus-4-5":     {"input": 15.00, "output": 75.00},
    "gemini-2.5-flash":    {"input": 0.075, "output": 0.30},
}

def cost(model: str, inp: int, out: int) -> float:
    p = PRICE.get(model, {"input": 0, "output": 0})
    return (inp * p["input"] + out * p["output"]) / 1_000_000


# ── API 호출 ──────────────────────────────────────────────────────────────────

async def call_openai(model: str, system: str, user: str) -> dict:
    resp = await _openai.chat.completions.create(
        model=model,
        messages=[{"role": "system", "content": system},
                  {"role": "user",   "content": user}],
        max_tokens=500, temperature=0,
    )
    inp, out = resp.usage.prompt_tokens, resp.usage.completion_tokens
    return {"model": model, "input": inp, "output": out,
            "total": inp+out, "cost": cost(model, inp, out),
            "text": resp.choices[0].message.content.strip()[:120]}


async def call_claude(system: str, user: str) -> dict:
    model = "claude-opus-4-5"
    resp = await _claude.messages.create(
        model=model, max_tokens=500, system=system,
        messages=[{"role": "user", "content": user}],
    )
    inp, out = resp.usage.input_tokens, resp.usage.output_tokens
    return {"model": model, "input": inp, "output": out,
            "total": inp+out, "cost": cost(model, inp, out),
            "text": resp.content[0].text.strip()[:120]}


async def call_gemini(system: str, user: str) -> dict:
    model_name = "gemini-2.5-flash"
    for attempt in range(5):
        try:
            resp = await asyncio.to_thread(
                _gemini.models.generate_content,
                model=model_name,
                contents=user,
                config=_genai.types.GenerateContentConfig(
                    system_instruction=system,
                    max_output_tokens=500,
                    temperature=0,
                ),
            )
            inp = resp.usage_metadata.prompt_token_count
            out = resp.usage_metadata.candidates_token_count
            return {"model": model_name, "input": inp, "output": out,
                    "total": inp+out, "cost": cost(model_name, inp, out),
                    "text": resp.text.strip()[:120]}
        except Exception as e:
            if "429" in str(e) or "RESOURCE_EXHAUSTED" in str(e):
                wait = 70
                print(f"\n    [Gemini] 한도 초과 — {wait}초 대기 후 재시도 ({attempt+1})")
                await asyncio.sleep(wait)
            else:
                raise
    # 재시도 모두 소진 — 더 길게 기다리고 마지막 시도
    print(f"\n    [Gemini] 최종 재시도 — 120초 대기")
    await asyncio.sleep(120)
    resp = await asyncio.to_thread(
        _gemini.models.generate_content,
        model=model_name, contents=user,
        config=_genai.types.GenerateContentConfig(
            system_instruction=system, max_output_tokens=500, temperature=0,
        ),
    )
    inp = resp.usage_metadata.prompt_token_count
    out = resp.usage_metadata.candidates_token_count
    return {"model": model_name, "input": inp, "output": out,
            "total": inp+out, "cost": cost(model_name, inp, out),
            "text": resp.text.strip()[:120]}


async def run_all(system: str, user: str, openai_model: str = "gpt-4o",
                  gemini_result: dict | None = None) -> dict:
    """OpenAI + Claude 동시 호출. Gemini는 별도 전달 (rate limit 관리용)."""
    oa, cl = await asyncio.gather(
        call_openai(openai_model, system, user),
        call_claude(system, user),
    )
    return {"openai": oa, "claude": cl, "gemini": gemini_result or {}}


# ── 역할별 샘플 케이스 ────────────────────────────────────────────────────────

from agents.orchestrator_agent import _INTENT_SYSTEM, _FEW_SHOTS
from agents.search_agent import CATEGORY_EXTRACT_PROMPT, SPEECH_PROMPT
from rag.automation.llm_extractor import _HOMEPAGE_FLOOR_SYSTEM_PROMPT
from tools.open_hours_normalizer import _NORMALIZE_SYSTEM

ROLES = [
    # ── 1. 인텐트 분류 ──────────────────────────────────────────────────────
    {
        "role": "인텐트 분류",
        "file": "orchestrator_agent.py",
        "current_model": "gpt-4o",
        "samples": [
            {
                "id": "intent_001",
                "system": _INTENT_SYSTEM,
                "user": '메시지: "할랄 식당 어디 있어?"',
            },
            {
                "id": "intent_002",
                "system": _INTENT_SYSTEM,
                "user": '이전 대화:\nuser: 눈스퀘어 뭐야?\nassistant: 눈스퀘어는 명동의 쇼핑몰입니다.\n\n메시지: "거기 어떻게 가?"',
            },
            {
                "id": "intent_003",
                "system": _INTENT_SYSTEM,
                "user": '메시지: "기도 시간 알려줘"',
            },
        ],
    },
    # ── 2. TTS 응답 생성 ────────────────────────────────────────────────────
    {
        "role": "TTS 응답 생성",
        "file": "search_agent.py / halal_agent.py / place_insight_agent.py",
        "current_model": "gpt-4o",
        "samples": [
            {
                "id": "tts_search",
                "system": SPEECH_PROMPT,
                "user": "Category: cafe\nNearest facility: 스타벅스 명동점\nDistance: 120m\nOpen hours: 07:00-22:00\nLanguage: ko",
            },
            {
                "id": "tts_halal",
                "system": "You are a friendly Muslim travel assistant for tourists visiting Seoul.",
                "user": "Qibla direction: 295.3° (Northwest). Respond in Korean.",
            },
            {
                "id": "tts_place",
                "system": "You are a friendly Seoul tour guide. Respond in 2-3 short sentences for TTS.",
                "user": "User question: 명동성당 역사 알려줘\nLanguage: Korean\nLocation: 명동",
            },
        ],
    },
    # ── 3. 카테고리 추출 ────────────────────────────────────────────────────
    {
        "role": "카테고리 추출",
        "file": "search_agent.py",
        "current_model": "gpt-4o",
        "samples": [
            {
                "id": "cat_001",
                "system": CATEGORY_EXTRACT_PROMPT,
                "user": "무슬림 음식 추천해줘",
            },
            {
                "id": "cat_002",
                "system": CATEGORY_EXTRACT_PROMPT,
                "user": "Where is the nearest toilet?",
            },
            {
                "id": "cat_003",
                "system": CATEGORY_EXTRACT_PROMPT,
                "user": "약국 찾아줘",
            },
        ],
    },
    # ── 4. 층별 정보 추출 ───────────────────────────────────────────────────
    {
        "role": "층별 정보 추출",
        "file": "rag/automation/llm_extractor.py",
        "current_model": "gpt-4o-mini",
        "samples": [
            {
                "id": "floor_001",
                "system": _HOMEPAGE_FLOOR_SYSTEM_PROMPT,
                "user": "건물명: 테스트몰\n\n=== 홈페이지 텍스트 ===\nB2 식품관\n스시히로, 파리바게뜨, 투썸플레이스\nB1 생활관\n올리브영, 다이소\n1F 명품관\n루이비통, 구찌",
            },
            {
                "id": "floor_002",
                "system": _HOMEPAGE_FLOOR_SYSTEM_PROMPT,
                "user": "건물명: 강남 쇼핑센터\n\n=== 홈페이지 텍스트 ===\n■ 지하 1층\n- 노브랜드버거\n- 공차\n■ 1층\n- 올리브영 강남점\n- 다이소",
            },
        ],
    },
    # ── 5. 영업시간 파싱 ────────────────────────────────────────────────────
    {
        "role": "영업시간 파싱",
        "file": "tools/open_hours_normalizer.py",
        "current_model": "gpt-4o-mini",
        "samples": [
            {
                "id": "hours_001",
                "system": _NORMALIZE_SYSTEM,
                "user": "월 10:00-22:00 / 화 10:00-22:00 / 수 10:00-22:00 / 목 10:00-22:00 / 금 10:00-22:00 / 토 11:00-21:00 / 일 휴무",
            },
            {
                "id": "hours_002",
                "system": _NORMALIZE_SYSTEM,
                "user": "24시간 영업연중무휴",
            },
            {
                "id": "hours_003",
                "system": _NORMALIZE_SYSTEM,
                "user": "월-금 09:00-21:00 (13:00-14:00 점심 휴게)",
            },
        ],
    },
]


# ── 리포트 ────────────────────────────────────────────────────────────────────

def print_report(all_results: list[dict]) -> None:
    apis    = ["openai", "claude", "gemini"]
    api_labels = {"openai": "OpenAI", "claude": "Claude", "gemini": "Gemini"}

    print("\n" + "=" * 90)
    print("  프로젝트 LLM 역할별 × API별 토큰/비용 비교")
    print("=" * 90)

    role_totals: dict[str, dict] = {}

    for role_data in all_results:
        role    = role_data["role"]
        model   = role_data["current_model"]
        samples = role_data["samples"]

        print(f"\n── {role}  (현재 모델: {model}) ──")
        print(f"  {'케이스':<15} {'API':<14} {'입력':>7} {'출력':>7} {'합계':>7} {'비용($)':>10}")
        print(f"  {'─'*60}")

        role_sums = {api: {"input": 0, "output": 0, "cost": 0.0} for api in apis}

        for s in samples:
            first = True
            for api in apis:
                r = s[api]
                sid = s["id"] if first else ""
                alabel = f"[{api_labels[api]}] {r['model']}"
                print(f"  {sid:<15} {alabel:<20} {r['input']:>7,} {r['output']:>7,} "
                      f"{r['total']:>7,} {r['cost']:>10.5f}")
                role_sums[api]["input"]  += r["input"]
                role_sums[api]["output"] += r["output"]
                role_sums[api]["cost"]   += r["cost"]
                first = False
            print()

        # 역할별 합계
        n = len(samples)
        print(f"  [역할 합계 / 케이스당 평균]")
        for api in apis:
            s = role_sums[api]
            print(f"    {api_labels[api]:<8}: 합계 ${s['cost']:.5f}  케이스당 ${s['cost']/n:.5f}  ({s['input']+s['output']:,} 토큰)")

        role_totals[role] = role_sums

    # ── 비용 요약 ─────────────────────────────────────────────────────────────
    print(f"\n{'='*100}")
    print(f"  [비용 비교] 역할별 합산")
    print(f"{'='*100}")
    print(f"\n  {'역할':<20} {'현재모델':<14} {'OpenAI $':>12} {'Claude $':>12} {'Gemini $':>12} {'Claude/OA':>10} {'Gemini/OA':>10}")
    print(f"  {'─'*95}")

    grand_cost  = {api: 0.0 for api in apis}
    grand_tok   = {api: {"input": 0, "output": 0} for api in apis}
    for role_data in all_results:
        role  = role_data["role"]
        model = role_data["current_model"]
        sums  = role_totals[role]
        oa    = sums["openai"]["cost"]
        cl    = sums["claude"]["cost"]
        gm    = sums["gemini"]["cost"]
        grand_cost["openai"] += oa
        grand_cost["claude"] += cl
        grand_cost["gemini"] += gm
        for api in apis:
            grand_tok[api]["input"]  += role_totals[role][api]["input"]
            grand_tok[api]["output"] += role_totals[role][api]["output"]
        cl_ratio = cl/oa if oa > 0 else 0
        gm_ratio = gm/oa if oa > 0 else 0
        print(f"  {role:<20} {model:<14} {oa:>12.5f} {cl:>12.5f} {gm:>12.5f} "
              f"{cl_ratio:>9.1f}x {gm_ratio:>9.1f}x")

    print(f"  {'─'*95}")
    oa = grand_cost["openai"]; cl = grand_cost["claude"]; gm = grand_cost["gemini"]
    print(f"  {'합계':<20} {'':14} {oa:>12.5f} {cl:>12.5f} {gm:>12.5f} "
          f"{cl/oa:>9.1f}x {gm/oa:>9.1f}x")

    # ── 토큰 비교 ─────────────────────────────────────────────────────────────
    print(f"\n{'='*100}")
    print(f"  [토큰 비교] 역할별 합산 (입력 / 출력 / 합계)")
    print(f"{'='*100}")
    print(f"\n  {'역할':<20} {'':4} {'OA 입력':>9} {'OA 출력':>9} {'OA 합계':>9} "
          f"{'CL 입력':>9} {'CL 출력':>9} {'CL 합계':>9} "
          f"{'GM 입력':>9} {'GM 출력':>9} {'GM 합계':>9}")
    print(f"  {'─'*105}")

    for role_data in all_results:
        role = role_data["role"]
        sums = role_totals[role]
        oa_i = sums["openai"]["input"]; oa_o = sums["openai"]["output"]
        cl_i = sums["claude"]["input"]; cl_o = sums["claude"]["output"]
        gm_i = sums["gemini"]["input"]; gm_o = sums["gemini"]["output"]
        print(f"  {role:<24} {oa_i:>9,} {oa_o:>9,} {oa_i+oa_o:>9,} "
              f"{cl_i:>9,} {cl_o:>9,} {cl_i+cl_o:>9,} "
              f"{gm_i:>9,} {gm_o:>9,} {gm_i+gm_o:>9,}")

    print(f"  {'─'*105}")
    for api, label in zip(apis, ["OpenAI", "Claude", "Gemini"]):
        ti = grand_tok[api]["input"]; to = grand_tok[api]["output"]
        print(f"  {label} 합계: 입력 {ti:,}  출력 {to:,}  합계 {ti+to:,} 토큰")

    print(f"\n[단가 기준]")
    for model, p in PRICE.items():
        print(f"  {model:<22}: 입력 ${p['input']:>5.3f}/1M  출력 ${p['output']:>5.2f}/1M")
    print()


# ── 메인 ─────────────────────────────────────────────────────────────────────

async def main() -> None:
    total_cases = sum(len(r["samples"]) for r in ROLES)
    print(f"총 {total_cases}개 케이스 × 3개 API = {total_cases*3}회 호출")
    print("실행 중... (각 케이스당 3개 API 동시 호출)\n")

    # Gemini 먼저 순차 실행 (분당 5회 한도 준수, 케이스 사이 13초 대기)
    print("  [1단계] Gemini 순차 실행 중 (분당 5회 한도)...")
    all_samples_flat = [(role_data, s) for role_data in ROLES for s in role_data["samples"]]
    gemini_results: list[dict] = []
    for i, (_, s) in enumerate(all_samples_flat, 1):
        print(f"    Gemini [{i:2d}/{total_cases}] {s['id']}", end="\r")
        gm = await call_gemini(s["system"], s["user"])
        gemini_results.append(gm)
        if i < total_cases:
            await asyncio.sleep(20)  # 분당 5회 → 20초 간격 (여유있게)

    print(f"\n  [2단계] OpenAI + Claude 병렬 실행 중...")
    all_results = []
    case_num = 0
    gm_idx = 0
    for role_data in ROLES:
        enriched_samples = []
        for s in role_data["samples"]:
            case_num += 1
            print(f"    OA+Claude [{case_num:2d}/{total_cases}] {s['id']}", end="\r")
            res = await run_all(s["system"], s["user"], role_data["current_model"],
                                gemini_result=gemini_results[gm_idx])
            gm_idx += 1
            enriched_samples.append({**s, **res})
        all_results.append({**role_data, "samples": enriched_samples})

    print(f"  완료 {total_cases}개                    ")
    print_report(all_results)

    out = pathlib.Path("eval/logs/api_compare.json")
    out.write_text(json.dumps(all_results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"결과 저장: {out}")


if __name__ == "__main__":
    asyncio.run(main())
