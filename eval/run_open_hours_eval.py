"""
run_open_hours_eval.py
open_hours_normalizer 평가 스크립트.

사용:
    python eval/run_open_hours_eval.py
    python eval/run_open_hours_eval.py --verbose
"""

import argparse
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

from tools.open_hours_normalizer import normalize_open_hours, _looks_valid

DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]


# ── 비교 함수 ─────────────────────────────────────────────────────────────────

def _normalize_ranges(ranges: list) -> list[tuple]:
    """시간 구간 리스트를 정렬된 tuple 집합으로 변환 (순서 무관 비교)."""
    return sorted(tuple(r) for r in ranges)


def _day_match(actual: list, expected: list) -> bool:
    return _normalize_ranges(actual) == _normalize_ranges(expected)


def _weekly_match(actual_weekly: dict, expected_weekly: dict, alt_weekly: dict | None = None) -> dict[str, bool]:
    """요일별 일치 여부 반환. alt_weekly가 있으면 둘 중 하나라도 맞으면 OK."""
    result = {}
    for day in DAYS:
        a = actual_weekly.get(day, [])
        e = expected_weekly.get(day, [])
        match = _day_match(a, e)
        if not match and alt_weekly:
            alt_e = alt_weekly.get(day, [])
            match = _day_match(a, alt_e)
        result[day] = match
    return result


def _is_24h_equivalent(schedule: dict) -> bool:
    """always_open=true 또는 모든 요일 00:00-24:00 이면 True."""
    if schedule.get("always_open"):
        return True
    weekly = schedule.get("weekly", {})
    return all(
        _day_match(weekly.get(d, []), [["00:00", "24:00"]])
        for d in DAYS
    )


# ── 평가 ──────────────────────────────────────────────────────────────────────

async def eval_case(case: dict) -> dict:
    raw_input = case["input"]
    try:
        result = await normalize_open_hours(raw_input)
    except Exception as e:
        return {**case, "error": str(e), "schema_valid": False,
                "always_open_correct": False, "day_results": {}, "exact_match": False}

    if result is None:
        return {**case, "result": None, "schema_valid": False,
                "always_open_correct": False, "day_results": {}, "exact_match": False}

    schema_valid = _looks_valid(result)
    actual_weekly = result.get("weekly", {})
    actual_always_open = result.get("always_open", False)

    # schema_only 케이스 (AMBIGUOUS): 형식만 검사
    if case.get("schema_only"):
        return {
            **case, "result": result,
            "schema_valid": schema_valid,
            "always_open_correct": None,
            "day_results": None,
            "exact_match": None,
        }

    # always_open 검사
    expected_always_open = case.get("expected_always_open", False)
    if expected_always_open:
        # H24 케이스: always_open=true 또는 모든 요일 00:00-24:00 허용
        ao_correct = _is_24h_equivalent(result)
    else:
        ao_correct = (actual_always_open == expected_always_open)

    # 요일별 검사 (H24 케이스는 always_open으로 이미 평가)
    if expected_always_open:
        day_results = None
        exact_match = ao_correct
    else:
        expected_weekly = case.get("expected_weekly", {})
        alt_weekly = case.get("alt_weekly") or case.get("flexible_weekly")
        day_results = _weekly_match(actual_weekly, expected_weekly, alt_weekly)
        exact_match = all(day_results.values())

    return {
        **case, "result": result,
        "schema_valid": schema_valid,
        "always_open_correct": ao_correct,
        "day_results": day_results,
        "exact_match": exact_match,
    }


# ── 리포트 ────────────────────────────────────────────────────────────────────

def print_report(results: list[dict], verbose: bool) -> None:
    total = len(results)
    schema_ok = sum(1 for r in results if r.get("schema_valid"))

    # exact_match 계산 (schema_only 제외)
    scored = [r for r in results if r.get("exact_match") is not None]
    exact_ok = sum(1 for r in scored if r.get("exact_match"))

    # always_open 정확도
    ao_scored = [r for r in results if r.get("always_open_correct") is not None]
    ao_ok = sum(1 for r in ao_scored if r.get("always_open_correct"))

    # 요일별 정확도 (day_results 있는 케이스만)
    day_totals: dict[str, int] = {d: 0 for d in DAYS}
    day_correct: dict[str, int] = {d: 0 for d in DAYS}
    for r in results:
        dr = r.get("day_results")
        if dr:
            for d in DAYS:
                day_totals[d] += 1
                if dr.get(d):
                    day_correct[d] += 1

    # 타입별 정확도
    from collections import defaultdict
    type_total: dict[str, int] = defaultdict(int)
    type_exact: dict[str, int] = defaultdict(int)
    for r in results:
        t = r.get("type", "?")
        type_total[t] += 1
        if r.get("exact_match"):
            type_exact[t] += 1

    print("\n" + "=" * 60)
    print("  OPEN HOURS NORMALIZER EVALUATION REPORT")
    print("=" * 60)

    print(f"\n[전체 지표]")
    print(f"  Schema Validity Rate : {schema_ok}/{total}  ({schema_ok/total:.1%})")
    print(f"  Exact Match Rate     : {exact_ok}/{len(scored)}  ({exact_ok/len(scored):.1%})  (ambiguous 제외)")
    print(f"  Always-open Accuracy : {ao_ok}/{len(ao_scored)}  ({ao_ok/len(ao_scored):.1%})")

    print(f"\n[타입별 Exact Match]")
    print(f"  {'Type':<16} {'Exact':>8} {'Total':>6} {'Rate':>8}")
    print(f"  {'-'*42}")
    for t in ["FULL_WEEK", "WEEKDAYS_ONLY", "PARTIAL", "H24", "MIDNIGHT", "IRREGULAR", "AMBIGUOUS"]:
        tot = type_total.get(t, 0)
        ext = type_exact.get(t, 0)
        rate = f"{ext/tot:.1%}" if tot > 0 else "  -"
        ambig = " (schema only)" if t == "AMBIGUOUS" else ""
        print(f"  {t:<16} {ext:>8} {tot:>6} {rate:>8}{ambig}")

    print(f"\n[요일별 정확도]")
    day_names = ["mon(월)", "tue(화)", "wed(수)", "thu(목)", "fri(금)", "sat(토)", "sun(일)"]
    for d, name in zip(DAYS, day_names):
        tot = day_totals[d]
        cor = day_correct[d]
        rate = f"{cor/tot:.1%}" if tot > 0 else "  -"
        print(f"  {name:<10} {cor}/{tot}  {rate}")

    if verbose:
        wrong = [r for r in results if r.get("exact_match") is False]
        if wrong:
            print(f"\n[틀린 케이스 {len(wrong)}개]")
            for r in wrong:
                print(f"\n  [{r['id']}] type={r['type']}")
                print(f"  입력: {r['input'][:70]}")
                if r.get("result"):
                    dr = r.get("day_results") or {}
                    wrong_days = [d for d, ok in dr.items() if not ok]
                    if wrong_days:
                        print(f"  틀린 요일: {wrong_days}")
                        for d in wrong_days:
                            actual = r["result"].get("weekly", {}).get(d, "?")
                            expected = r.get("expected_weekly", {}).get(d, "?")
                            print(f"    {d}: 예측={actual}  정답={expected}")
                    if not r.get("always_open_correct") and r.get("expected_always_open"):
                        print(f"  always_open 오류: 예측={r['result'].get('always_open')}  정답=true")
                else:
                    print(f"  결과: None (파싱 실패)")

    # ambiguous 케이스 schema 결과 출력
    ambig = [r for r in results if r.get("schema_only")]
    if ambig and verbose:
        print(f"\n[AMBIGUOUS 케이스 Schema 결과]")
        for r in ambig:
            sv = "✅" if r.get("schema_valid") else "❌"
            print(f"  {sv} [{r['id']}] \"{r['input'][:50]}\"")

    print()


async def run_eval(dataset_path: str, verbose: bool) -> None:
    cases = []
    with open(dataset_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                cases.append(json.loads(line))

    print(f"데이터셋 로드: {len(cases)}개 케이스  ({dataset_path})")
    print("평가 실행 중... (LLM API 호출)")

    results = []
    for i, case in enumerate(cases, 1):
        print(f"  [{i:2d}/{len(cases)}] {case['id']}", end="\r")
        r = await eval_case(case)
        results.append(r)

    print(f"  완료 {len(results)}개                   ")
    print_report(results, verbose)

    out_path = pathlib.Path(dataset_path).parent.parent / "logs" / "open_hours_eval_result.json"
    out_path.parent.mkdir(exist_ok=True)

    # result 안의 dict는 json 직렬화 가능
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"결과 저장: {out_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="eval/datasets/open_hours_golden.jsonl")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    asyncio.run(run_eval(args.dataset, args.verbose))


if __name__ == "__main__":
    main()
