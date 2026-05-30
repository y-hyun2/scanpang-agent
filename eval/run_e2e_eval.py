"""
run_e2e_eval.py
End-to-End 파이프라인 평가.

측정 항목:
  1. Task Completion Rate  — LLM-as-Judge: 사용자 목표 달성 여부
  2. E2E Latency           — run_orchestrator 호출 ~ final_speech 반환
  3. Routing Accuracy      — source_agent 가 예상 에이전트와 일치하는지
  4. Fallback Rate         — 의도치 않은 smalltalk fallback 비율

사용:
    python eval/run_e2e_eval.py
    python eval/run_e2e_eval.py --verbose
"""

import argparse
import asyncio
import json
import os
import pathlib
import sys
import time

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from openai import AsyncOpenAI
_client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))

# 명동 중심부 좌표
MYEONGDONG_LAT = 37.5636
MYEONGDONG_LNG = 126.9825

# ── 시나리오 정의 ──────────────────────────────────────────────────────────────

SCENARIOS = [
    {
        "id": "e2e_001",
        "message": "근처 할랄 식당 알려줘",
        "language": "ko",
        "expected_agent": "convenience",
        "user_goal": "할랄 식당 이름 또는 거리 정보를 얻는다",
        "success_hint": "식당명 또는 거리(m) 또는 없다는 안내가 포함되어야 함",
    },
    {
        "id": "e2e_002",
        "message": "명동성당까지 길 안내해줘",
        "language": "ko",
        "expected_agent": "navigation",
        "user_goal": "명동성당으로 가는 경로 안내를 시작한다",
        "success_hint": "경로 또는 방향 안내 내용이 포함되어야 함",
    },
    {
        "id": "e2e_003",
        "message": "지금 기도 시간이야?",
        "language": "ko",
        "expected_agent": "halal",
        "user_goal": "현재 기도 시간 또는 다음 기도 시간을 알아낸다",
        "success_hint": "기도 이름(파즈르/아스르 등) 또는 시간이 포함되어야 함",
    },
    {
        "id": "e2e_004",
        "message": "카페 어디 있어?",
        "language": "ko",
        "expected_agent": "convenience",
        "user_goal": "근처 카페 이름 또는 위치를 얻는다",
        "success_hint": "카페명 또는 거리 정보 또는 없다는 안내가 포함되어야 함",
    },
    {
        "id": "e2e_005",
        "message": "남산타워 알려줘",
        "language": "ko",
        "expected_agent": "place",
        "user_goal": "남산타워에 대한 정보를 얻는다",
        "success_hint": "남산타워 설명 또는 관련 정보가 포함되어야 함",
    },
    {
        "id": "e2e_006",
        "message": "약국 찾아줘",
        "language": "ko",
        "expected_agent": "convenience",
        "user_goal": "근처 약국 이름 또는 위치를 얻는다",
        "success_hint": "약국명 또는 거리 정보 또는 없다는 안내가 포함되어야 함",
    },
    {
        "id": "e2e_007",
        "message": "키블라 방향이 어디야?",
        "language": "ko",
        "expected_agent": "halal",
        "user_goal": "키블라 방향(각도 또는 방위)을 알아낸다",
        "success_hint": "각도(도) 또는 방위(북쪽/동쪽 등)가 포함되어야 함",
    },
    {
        "id": "e2e_008",
        "message": "경복궁까지 어떻게 가?",
        "language": "ko",
        "expected_agent": "navigation",
        "user_goal": "경복궁으로 가는 경로 안내를 시작한다",
        "success_hint": "경로 또는 방향 안내 내용이 포함되어야 함",
    },
    {
        "id": "e2e_009",
        "message": "Where is the nearest convenience store?",
        "language": "en",
        "expected_agent": "convenience",
        "user_goal": "Find the name or distance of a nearby convenience store",
        "success_hint": "Store name or distance or 'not found' message must be present",
    },
    {
        "id": "e2e_010",
        "message": "Tell me about Gyeongbokgung Palace",
        "language": "en",
        "expected_agent": "place",
        "user_goal": "Get information about Gyeongbokgung Palace",
        "success_hint": "Description or historical information about the palace must be present",
    },
    {
        "id": "e2e_011",
        "message": "안녕",
        "language": "ko",
        "expected_agent": "smalltalk",
        "user_goal": "친근한 인사 응답을 받는다",
        "success_hint": "짧고 친근한 응답이 있어야 함",
    },
    {
        "id": "e2e_012",
        "message": "أين أقرب مطعم حلال؟",
        "language": "ar",
        "expected_agent": "convenience",
        "user_goal": "الحصول على اسم أو مسافة أقرب مطعم حلال",
        "success_hint": "يجب أن يحتوي على اسم المطعم أو المسافة أو رسالة 'غير موجود'",
    },
]

# 다턴 시나리오 (session_id 공유)
MULTI_TURN_SCENARIOS = [
    {
        "id": "e2e_multi_001_turn1",
        "message": "눈스퀘어가 뭐야?",
        "language": "ko",
        "expected_agent": "place",
        "session_group": "multi_001",
        "turn": 1,
        "user_goal": "눈스퀘어에 대한 정보를 얻는다",
        "success_hint": "눈스퀘어 설명이 포함되어야 함",
    },
    {
        "id": "e2e_multi_001_turn2",
        "message": "거기 어떻게 가?",
        "language": "ko",
        "expected_agent": "navigation",
        "session_group": "multi_001",
        "turn": 2,
        "user_goal": "눈스퀘어로 가는 경로 안내를 시작한다 (지시어 해소 필요)",
        "success_hint": "눈스퀘어 또는 경로 관련 내용이 포함되어야 함",
    },
]


# ── Judge ─────────────────────────────────────────────────────────────────────

_JUDGE_SYSTEM = """\
You are evaluating whether an AR tourism app's response successfully fulfilled the user's goal.

Score:
1. completed (true/false): Did the response achieve the user's goal?
2. quality (1-5): How well did it fulfill the goal?
   5=perfectly, 4=mostly, 3=partially, 2=barely, 1=not at all
3. note: one short sentence explaining your judgment

Output JSON only:
{"completed": true/false, "quality": N, "note": "..."}
"""

async def _judge_completion(user_goal: str, success_hint: str, speech: str, source_agent: str) -> dict:
    user_content = (
        f"USER GOAL: {user_goal}\n"
        f"SUCCESS CRITERIA: {success_hint}\n"
        f"AGENT USED: {source_agent}\n"
        f"RESPONSE: {speech}"
    )
    try:
        resp = await _client.chat.completions.create(
            model="gpt-4o",
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": _JUDGE_SYSTEM},
                {"role": "user",   "content": user_content},
            ],
            temperature=0,
            max_tokens=150,
        )
        return json.loads(resp.choices[0].message.content)
    except Exception as e:
        return {"completed": False, "quality": 0, "note": f"Judge error: {e}"}


# ── 실행 ──────────────────────────────────────────────────────────────────────

async def run_scenario(scenario: dict, session_id: str | None = None) -> dict:
    from agents.orchestrator_agent import run_orchestrator

    t_start = time.perf_counter()
    try:
        result = await run_orchestrator(
            message=scenario["message"],
            lat=MYEONGDONG_LAT,
            lng=MYEONGDONG_LNG,
            language=scenario["language"],
            session_id=session_id,
        )
        latency_ms = (time.perf_counter() - t_start) * 1000
        speech       = result.get("speech", "")
        source_agent = result.get("source_agent", "")
        error        = None
    except Exception as e:
        latency_ms   = (time.perf_counter() - t_start) * 1000
        speech       = ""
        source_agent = "error"
        error        = str(e)

    routing_correct = (source_agent == scenario["expected_agent"])
    unexpected_fallback = (
        source_agent == "smalltalk"
        and scenario["expected_agent"] != "smalltalk"
    )

    judge = await _judge_completion(
        scenario["user_goal"], scenario["success_hint"], speech, source_agent
    )

    return {
        **scenario,
        "speech":               speech,
        "source_agent":         source_agent,
        "latency_ms":           round(latency_ms),
        "routing_correct":      routing_correct,
        "unexpected_fallback":  unexpected_fallback,
        "judge":                judge,
        "error":                error,
    }


# ── 리포트 ────────────────────────────────────────────────────────────────────

def print_report(results: list[dict], verbose: bool) -> None:
    total = len(results)
    errored = [r for r in results if r.get("error")]

    completed = [r for r in results if r["judge"].get("completed")]
    routing_ok = [r for r in results if r.get("routing_correct")]
    fallbacks  = [r for r in results if r.get("unexpected_fallback")]

    latencies = [r["latency_ms"] for r in results if not r.get("error")]
    latencies_sorted = sorted(latencies)
    p50 = latencies_sorted[len(latencies_sorted)//2] if latencies_sorted else 0
    p95 = latencies_sorted[int(len(latencies_sorted)*0.95)] if latencies_sorted else 0

    qualities = [r["judge"]["quality"] for r in results if r["judge"].get("quality", 0) > 0]

    print("\n" + "=" * 60)
    print("  END-TO-END PIPELINE EVALUATION REPORT")
    print("=" * 60)

    print(f"\n[전체 지표]")
    print(f"  Task Completion Rate  : {len(completed)}/{total}  ({len(completed)/total:.1%})")
    print(f"  Completion Quality    : {sum(qualities)/len(qualities):.2f}/5.00  (avg)") if qualities else None
    print(f"  Routing Accuracy      : {len(routing_ok)}/{total}  ({len(routing_ok)/total:.1%})")
    print(f"  Unexpected Fallback   : {len(fallbacks)}/{total}  ({len(fallbacks)/total:.1%})")
    print(f"  Error Rate            : {len(errored)}/{total}  ({len(errored)/total:.1%})")

    print(f"\n[지연시간]")
    print(f"  P50  : {p50:,.0f} ms")
    print(f"  P95  : {p95:,.0f} ms")
    print(f"  평균  : {sum(latencies)/len(latencies):,.0f} ms") if latencies else None
    print(f"  최소  : {min(latencies):,.0f} ms  최대: {max(latencies):,.0f} ms") if latencies else None

    # 에이전트별 요약
    from collections import defaultdict
    agent_stats: dict[str, dict] = defaultdict(lambda: {"total":0,"completed":0,"latencies":[]})
    for r in results:
        ag = r["expected_agent"]
        agent_stats[ag]["total"] += 1
        if r["judge"].get("completed"):
            agent_stats[ag]["completed"] += 1
        if not r.get("error"):
            agent_stats[ag]["latencies"].append(r["latency_ms"])

    print(f"\n[에이전트별 완료율 / 평균 지연]")
    print(f"  {'Agent':<18} {'완료율':>8} {'평균ms':>8}")
    print(f"  {'-'*38}")
    for ag, s in agent_stats.items():
        rate = f"{s['completed']}/{s['total']} ({s['completed']/s['total']:.1%})"
        avg_lat = f"{sum(s['latencies'])/len(s['latencies']):.0f}" if s["latencies"] else "-"
        print(f"  {ag:<18} {rate:>14} {avg_lat:>8}")

    if verbose:
        print(f"\n[케이스별 상세]")
        for r in results:
            j = r["judge"]
            status = "✅" if j.get("completed") else "❌"
            route  = "✅" if r.get("routing_correct") else f"❌→{r['source_agent']}"
            print(f"\n  {status} [{r['id']}]  route={route}  {r['latency_ms']}ms")
            print(f"  입력: \"{r['message'][:50]}\"")
            print(f"  응답: {r.get('speech','')[:80]}{'...' if len(r.get('speech',''))>80 else ''}")
            print(f"  Judge: quality={j.get('quality')}  {j.get('note','')}")
            if r.get("error"):
                print(f"  ERROR: {r['error']}")

    # 실패 케이스 요약
    failed = [r for r in results if not r["judge"].get("completed")]
    if failed:
        print(f"\n[미완료 케이스 {len(failed)}개]")
        for r in failed:
            print(f"  [{r['id']}] agent={r['source_agent']} | {r['judge'].get('note','')}")

    print()


# ── 메인 ──────────────────────────────────────────────────────────────────────

async def run_eval(verbose: bool) -> None:
    import uuid
    all_scenarios = list(SCENARIOS)

    # 다턴 시나리오: 같은 session_id 공유
    session_map: dict[str, str] = {}
    for s in MULTI_TURN_SCENARIOS:
        grp = s["session_group"]
        if grp not in session_map:
            session_map[grp] = str(uuid.uuid4())
    all_scenarios.extend(MULTI_TURN_SCENARIOS)

    total = len(all_scenarios)
    print(f"시나리오: {total}개 ({len(SCENARIOS)}개 단일턴 + {len(MULTI_TURN_SCENARIOS)}개 다턴)")
    print("실행 중... (외부 API 호출 포함)\n")

    results = []
    for i, scenario in enumerate(all_scenarios, 1):
        print(f"  [{i:2d}/{total}] {scenario['id']}", end="\r")
        sid = session_map.get(scenario.get("session_group"))
        r = await run_scenario(scenario, session_id=sid)
        results.append(r)
        # 다턴 케이스는 순서 보장을 위해 짧은 대기
        if scenario.get("session_group"):
            await asyncio.sleep(0.5)

    print(f"  완료 {len(results)}개                    ")
    print_report(results, verbose)

    out_path = pathlib.Path("eval/logs/e2e_eval_result.json")
    out_path.parent.mkdir(exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"결과 저장: {out_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true", help="케이스별 상세 출력")
    args = parser.parse_args()
    asyncio.run(run_eval(args.verbose))


if __name__ == "__main__":
    main()
