"""
run_latency_compare.py
역할별 API 응답 시간 비교 (OpenAI / Claude / Gemini).
각 역할당 5개 샘플, 총 75회 호출.

사용:
    python eval/run_latency_compare.py
"""

import asyncio, json, os, pathlib, sys, time, re
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from openai import AsyncOpenAI
import anthropic as _anthropic
from google import genai as _genai

_openai = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))
_claude = _anthropic.AsyncAnthropic(api_key=os.getenv("ANTHROPIC_API_KEY", ""))
_gemini = _genai.Client(api_key=os.getenv("GEMINI_API_KEY", ""))

GEMINI_GAP = 15  # 초 (분당 4회 → 안전)


# ── 시간 측정 포함 LLM 호출 ────────────────────────────────────────────────────

async def timed_call(api: str, system: str, user: str,
                     openai_model: str = "gpt-4o") -> dict:
    t0 = time.perf_counter()
    try:
        if api == "openai":
            resp = await _openai.chat.completions.create(
                model=openai_model, temperature=0, max_tokens=500,
                messages=[{"role": "system", "content": system},
                          {"role": "user",   "content": user}],
            )
            text = resp.choices[0].message.content.strip()
            tokens = resp.usage.total_tokens

        elif api == "claude":
            resp = await _claude.messages.create(
                model="claude-opus-4-5", max_tokens=500,
                system=system,
                messages=[{"role": "user", "content": user}],
            )
            text   = resp.content[0].text.strip()
            tokens = resp.usage.input_tokens + resp.usage.output_tokens

        elif api == "gemini":
            resp = await asyncio.to_thread(
                _gemini.models.generate_content,
                model="gemini-2.5-flash", contents=user,
                config=_genai.types.GenerateContentConfig(
                    system_instruction=system,
                    max_output_tokens=500, temperature=0,
                ),
            )
            text   = resp.text.strip()
            tokens = (resp.usage_metadata.prompt_token_count +
                      resp.usage_metadata.candidates_token_count)

        elapsed_ms = (time.perf_counter() - t0) * 1000
        return {"ok": True, "ms": round(elapsed_ms), "tokens": tokens,
                "text": text[:80]}

    except Exception as e:
        elapsed_ms = (time.perf_counter() - t0) * 1000
        return {"ok": False, "ms": round(elapsed_ms), "tokens": 0,
                "text": f"ERROR: {e}"[:80]}


# ── 역할별 샘플 정의 ──────────────────────────────────────────────────────────

from agents.orchestrator_agent import _INTENT_SYSTEM, _FEW_SHOTS
from agents.search_agent import CATEGORY_EXTRACT_PROMPT, SPEECH_PROMPT
from rag.automation.llm_extractor import _HOMEPAGE_FLOOR_SYSTEM_PROMPT
from tools.open_hours_normalizer import _NORMALIZE_SYSTEM

# Few-shot 텍스트 변환
examples = []
for i in range(0, min(len(_FEW_SHOTS)-1, 30), 2):
    q = _FEW_SHOTS[i]["content"]
    a = _FEW_SHOTS[i+1]["content"]
    examples.append(f"입력: {q}\n출력: {a}")
INTENT_SYSTEM_WITH_SHOTS = _INTENT_SYSTEM + "\n\n예시:\n" + "\n\n".join(examples[:12])

ROLES = [
    {
        "role": "인텐트 분류",
        "openai_model": "gpt-4o",
        "system": INTENT_SYSTEM_WITH_SHOTS,
        "samples": [
            '메시지: "할랄 식당 어디 있어?"',
            '메시지: "남산타워 역사 알려줘"',
            '메시지: "카페 추천해줘"',
            '메시지: "기도 시간 알려줘"',
            '메시지: "명동역으로 길 안내해줘"',
        ],
    },
    {
        "role": "TTS 응답 생성",
        "openai_model": "gpt-4o",
        "system": SPEECH_PROMPT,
        "samples": [
            "Category: cafe\nNearest: 스타벅스 명동점\nDistance: 120m\nOpen hours: 07:00-22:00\nLanguage: ko",
            "Category: pharmacy\nNearest: 명동 온누리약국\nDistance: 80m\nOpen hours: 09:00-21:00\nLanguage: en",
            "Category: restroom\nNearest: 명동 공중화장실\nDistance: 50m\nOpen hours: 24시간\nLanguage: ar",
            "Category: halal_restaurant\nNearest: 이슬람 식당\nDistance: 180m\nOpen hours: 11:00-21:00\nLanguage: en",
            "Category: atm\nNearest: 신한은행 ATM\nDistance: 200m\nOpen hours: 24시간\nLanguage: ko",
        ],
    },
    {
        "role": "카테고리 추출",
        "openai_model": "gpt-4o",
        "system": CATEGORY_EXTRACT_PROMPT,
        "samples": [
            "무슬림 음식 추천해줘",
            "기도실 어디야?",
            "카페 추천해줘",
            "Where is the nearest toilet?",
            "약국 찾아줘",
        ],
    },
    {
        "role": "층별 정보 추출",
        "openai_model": "gpt-4o-mini",
        "system": _HOMEPAGE_FLOOR_SYSTEM_PROMPT,
        "samples": [
            "건물명: 테스트몰\n\n=== 홈페이지 텍스트 ===\nB2 식품관\n스시히로, 파리바게뜨\nB1 생활관\n올리브영, 다이소\n1F 명품관\n루이비통, 구찌",
            "건물명: City Tower\n\n=== 홈페이지 텍스트 ===\nB1 Food Court: McDonald's, Subway\n1F Lobby: KB Bank, Olive Young\n2F Fashion: Zara, Uniqlo",
            "건물명: 강남 쇼핑센터\n\n=== 홈페이지 텍스트 ===\n■ 지하 1층\n- 노브랜드버거\n- 공차\n■ 1층\n- 올리브영 강남점\n- 다이소",
            "건물명: 센트로폴리스\n\n=== 홈페이지 텍스트 ===\n1F: 어반밀커피, 아티제\n2F: 한와담블랙, 스시소라\n3F: 센트로폴리스 컨퍼런스",
            "건물명: 서울상공회의소\n\n=== 홈페이지 텍스트 ===\n1F: 스타벅스 대한상공회의소점, 하나은행\n2F: 최앤이치과의원, 꿈길\n3F: 한국회계기준원",
        ],
    },
    {
        "role": "영업시간 파싱",
        "openai_model": "gpt-4o-mini",
        "system": _NORMALIZE_SYSTEM,
        "samples": [
            "월 10:00-22:00 / 화 10:00-22:00 / 수 10:00-22:00 / 목 10:00-22:00 / 금 10:00-22:00 / 토 11:00-21:00 / 일 휴무",
            "24시간 영업연중무휴",
            "월-금 09:00-21:00 (13:00-14:00 점심 휴게)",
            "화 09:00-18:00 / 수 09:00-18:00 / 목 09:00-18:00 / 금 09:00-18:00",
            "월 07:20-21:00 / 화 07:20-21:00 / 수 07:20-21:00 / 목 07:20-21:00 / 금 07:20-21:00 / 토 07:20-22:00 / 일 11:30-19:00",
        ],
    },
]


# ── 리포트 ────────────────────────────────────────────────────────────────────

def stats(ms_list: list[float]) -> dict:
    if not ms_list:
        return {"avg": 0, "min": 0, "max": 0, "p50": 0}
    s = sorted(ms_list)
    return {
        "avg": round(sum(s) / len(s)),
        "min": round(min(s)),
        "max": round(max(s)),
        "p50": round(s[len(s) // 2]),
    }


def print_report(all_results: list[dict]) -> None:
    print("\n" + "=" * 80)
    print("  응답 시간 비교 (ms) — OpenAI vs Claude vs Gemini")
    print("=" * 80)

    apis   = ["openai", "claude", "gemini"]
    labels = {"openai": "OpenAI", "claude": "Claude", "gemini": "Gemini"}

    print(f"\n{'역할':<18} {'API':<10} {'평균ms':>8} {'최솟값':>8} {'최댓값':>8} {'중앙값':>8} {'성공률':>8}")
    print(f"{'─'*72}")

    role_stats: dict[str, dict] = {}

    for role_data in all_results:
        role = role_data["role"]
        role_stats[role] = {}
        first = True
        for api in apis:
            ms_list   = [s[api]["ms"]  for s in role_data["samples"] if s[api]["ok"]]
            ok_count  = sum(1 for s in role_data["samples"] if s[api]["ok"])
            total     = len(role_data["samples"])
            st        = stats(ms_list)
            role_stats[role][api] = st
            role_col  = role if first else ""
            print(f"  {role_col:<16} {labels[api]:<10} {st['avg']:>8,} {st['min']:>8,} "
                  f"{st['max']:>8,} {st['p50']:>8,} {ok_count/total:>8.0%}")
            first = False
        print()

    # 전체 요약
    print(f"{'─'*72}")
    print(f"\n[전체 평균 응답 시간]")
    for api in apis:
        all_avg = [role_stats[r][api]["avg"] for r in role_stats if role_stats[r][api]["avg"] > 0]
        overall = round(sum(all_avg) / len(all_avg)) if all_avg else 0
        print(f"  {labels[api]:<10}: {overall:,}ms")

    # 배수 비교
    oa_avgs = {r: role_stats[r]["openai"]["avg"] for r in role_stats}
    print(f"\n[OpenAI 대비 응답 시간 배수]")
    for api in ["claude", "gemini"]:
        ratios = []
        for r in role_stats:
            oa = oa_avgs[r]
            other = role_stats[r][api]["avg"]
            if oa > 0 and other > 0:
                ratios.append(other / oa)
        if ratios:
            avg_ratio = sum(ratios) / len(ratios)
            print(f"  {labels[api]:<10}: 평균 {avg_ratio:.1f}x ({'느림' if avg_ratio > 1 else '빠름'})")

    print()


# ── 메인 ─────────────────────────────────────────────────────────────────────

async def main() -> None:
    total = sum(len(r["samples"]) for r in ROLES)
    print(f"총 {total}개 샘플 × 3개 API = {total*3}회 호출")
    print(f"Gemini는 {GEMINI_GAP}초 간격 순차 실행\n")

    all_results = []

    for role_data in ROLES:
        role    = role_data["role"]
        system  = role_data["system"]
        samples = role_data["samples"]
        om      = role_data["openai_model"]

        print(f"  [{role}] 실행 중...")
        enriched = []

        for i, user in enumerate(samples):
            sample_res = {}

            # OpenAI + Claude 동시 호출
            oa_task = timed_call("openai", system, user, openai_model=om)
            cl_task = timed_call("claude", system, user)
            oa_res, cl_res = await asyncio.gather(oa_task, cl_task)
            sample_res["openai"] = oa_res
            sample_res["claude"] = cl_res

            # Gemini 순차 호출 (rate limit)
            if i > 0:
                await asyncio.sleep(GEMINI_GAP)
            gm_res = await timed_call("gemini", system, user)
            sample_res["gemini"] = gm_res

            print(f"    [{i+1}/{len(samples)}] OA={oa_res['ms']}ms "
                  f"CL={cl_res['ms']}ms GM={gm_res['ms']}ms")
            enriched.append(sample_res)

        all_results.append({**role_data, "samples": enriched})
        await asyncio.sleep(GEMINI_GAP)  # 역할 사이 추가 대기

    print_report(all_results)

    out = pathlib.Path("eval/logs/latency_compare.json")
    out.write_text(json.dumps(all_results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"결과 저장: {out}")


if __name__ == "__main__":
    asyncio.run(main())
