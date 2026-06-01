"""
run_intent_eval.py
인텐트 분류기 평가 스크립트.

사용:
    python eval/run_intent_eval.py
    python eval/run_intent_eval.py --dataset eval/datasets/intent_golden.jsonl
    python eval/run_intent_eval.py --verbose   # 틀린 케이스 상세 출력
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

from agents.orchestrator_agent import classify_intent

# ── 지표 계산 ─────────────────────────────────────────────────────────────────

def _compute_metrics(results: list[dict]) -> dict:
    """Accuracy, Per-class Precision/Recall/F1, Macro F1 계산."""
    agents = ["place", "navigation", "nav_guide", "halal", "convenience", "smalltalk"]

    # Confusion matrix: cm[actual][predicted]
    cm: dict[str, dict[str, int]] = {a: {b: 0 for b in agents} for a in agents}
    for r in results:
        actual    = r["expected_agent"]
        predicted = r["predicted_agent"]
        if actual in cm and predicted in cm:
            cm[actual][predicted] += 1

    # Per-class precision, recall, F1
    per_class: dict[str, dict] = {}
    for agent in agents:
        tp = cm[agent][agent]
        fp = sum(cm[other][agent] for other in agents if other != agent)
        fn = sum(cm[agent][other] for other in agents if other != agent)
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall    = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1        = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
        support   = sum(cm[agent].values())
        per_class[agent] = {"precision": precision, "recall": recall, "f1": f1, "support": support}

    accuracy   = sum(r["agent_correct"] for r in results) / len(results) if results else 0.0
    macro_f1   = sum(v["f1"] for v in per_class.values()) / len(agents)
    total_sup  = sum(v["support"] for v in per_class.values())
    weighted_f1 = (
        sum(v["f1"] * v["support"] for v in per_class.values()) / total_sup
        if total_sup > 0 else 0.0
    )

    return {
        "accuracy":     accuracy,
        "macro_f1":     macro_f1,
        "weighted_f1":  weighted_f1,
        "per_class":    per_class,
        "confusion_matrix": cm,
    }


def _compute_sub_metrics(results: list[dict]) -> dict:
    """convenience/halal 케이스의 sub_category 정확도."""
    sub_results = [r for r in results if r.get("expected_sub_category")]
    if not sub_results:
        return {"sub_accuracy": None, "sub_total": 0}
    correct = sum(r["sub_correct"] for r in sub_results)
    return {
        "sub_accuracy": correct / len(sub_results),
        "sub_total":    len(sub_results),
        "sub_correct":  correct,
    }


def _compute_anaphora_metrics(results: list[dict]) -> dict:
    """다턴 지시어 해소 정확도."""
    anaphora = [r for r in results if r.get("expected_resolved")]
    if not anaphora:
        return {"anaphora_accuracy": None, "anaphora_total": 0}
    correct = sum(r["resolved_correct"] for r in anaphora)
    return {
        "anaphora_accuracy": correct / len(anaphora),
        "anaphora_total":    len(anaphora),
        "anaphora_correct":  correct,
    }


# ── 출력 ──────────────────────────────────────────────────────────────────────

def _print_report(metrics: dict, sub: dict, anaphora: dict, results: list[dict], verbose: bool) -> None:
    print("\n" + "=" * 60)
    print("  INTENT CLASSIFIER EVALUATION REPORT")
    print("=" * 60)

    print(f"\n[전체 지표]")
    print(f"  Accuracy    : {metrics['accuracy']:.1%}  ({sum(r['agent_correct'] for r in results)}/{len(results)})")
    print(f"  Macro F1    : {metrics['macro_f1']:.3f}")
    print(f"  Weighted F1 : {metrics['weighted_f1']:.3f}")

    print(f"\n[에이전트별 Precision / Recall / F1]")
    print(f"  {'Agent':<14} {'Prec':>6} {'Rec':>6} {'F1':>6} {'Support':>7}")
    print(f"  {'-'*46}")
    for agent, v in metrics["per_class"].items():
        print(
            f"  {agent:<14} {v['precision']:>6.3f} {v['recall']:>6.3f} {v['f1']:>6.3f} {v['support']:>7}"
        )

    print(f"\n[Sub-category 정확도]")
    if sub["sub_total"] > 0:
        print(f"  {sub['sub_accuracy']:.1%}  ({sub['sub_correct']}/{sub['sub_total']})")
    else:
        print("  해당 케이스 없음")

    print(f"\n[지시어 해소(Anaphora) 정확도]")
    if anaphora["anaphora_total"] > 0:
        print(f"  {anaphora['anaphora_accuracy']:.1%}  ({anaphora['anaphora_correct']}/{anaphora['anaphora_total']})")
    else:
        print("  해당 케이스 없음")

    print(f"\n[Confusion Matrix]  (행=실제, 열=예측)")
    agents = ["place", "navigation", "nav_guide", "halal", "convenience", "smalltalk"]
    short   = ["plac", "navi", "ngui", "hala", "conv", "smal"]
    cm = metrics["confusion_matrix"]
    header = "              " + "  ".join(f"{s:>4}" for s in short)
    print(f"  {header}")
    for agent, s in zip(agents, short):
        row = "  ".join(f"{cm[agent][b]:>4}" for b in agents)
        print(f"  {s:>4} ({agent[:4]}):  {row}")

    if verbose:
        wrong = [r for r in results if not r["agent_correct"]]
        if wrong:
            print(f"\n[틀린 케이스 {len(wrong)}개]")
            for r in wrong:
                msg = r['message'][:40].encode('utf-8', errors='replace').decode('utf-8')
                print(f"  [{r['id']}] \"{msg}\"")
                print(f"    expected : {r['expected_agent']} / {r['expected_sub_category'] or '-'}")
                print(f"    predicted: {r['predicted_agent']} / {r['predicted_sub_category'] or '-'}")

        wrong_sub = [r for r in results if r.get("expected_sub_category") and not r["sub_correct"]]
        if wrong_sub:
            print(f"\n[Sub-category 틀린 케이스 {len(wrong_sub)}개]")
            for r in wrong_sub:
                print(f"  [{r['id']}] \"{r['message'][:40]}\"")
                print(f"    expected_sub : {r['expected_sub_category']}")
                print(f"    predicted_sub: {r['predicted_sub_category']}")

    print()


# ── 메인 ──────────────────────────────────────────────────────────────────────

async def _run_once(cases: list[dict], run_idx: int, total_runs: int) -> list[dict]:
    results = []
    for i, case in enumerate(cases, 1):
        print(f"  run {run_idx}/{total_runs}  [{i:3d}/{len(cases)}] {case['id']}", end="\r")
        try:
            agent, resolved, sub_category = await classify_intent(
                message=case["message"],
                session_context=case.get("session_context", ""),
            )
        except Exception as e:
            print(f"\n  ERROR [{case['id']}]: {e}")
            agent, resolved, sub_category = "smalltalk", case["message"], ""

        agent_correct    = agent == case["expected_agent"]
        sub_correct      = sub_category == case.get("expected_sub_category", "")
        resolved_correct = None
        if case.get("expected_resolved"):
            resolved_correct = resolved.strip() == case["expected_resolved"].strip()

        results.append({
            "id":                     case["id"],
            "message":                case["message"],
            "expected_agent":         case["expected_agent"],
            "predicted_agent":        agent,
            "expected_sub_category":  case.get("expected_sub_category", ""),
            "predicted_sub_category": sub_category,
            "expected_resolved":      case.get("expected_resolved"),
            "predicted_resolved":     resolved,
            "agent_correct":          agent_correct,
            "sub_correct":            sub_correct,
            "resolved_correct":       resolved_correct,
        })
    return results


def _print_avg_report(all_runs: list[list[dict]], all_metrics: list[dict]) -> None:
    agents = ["place", "navigation", "nav_guide", "halal", "convenience", "smalltalk"]
    n = len(all_runs)

    print(f"\n{'='*60}")
    print(f"  {n}회 실행 평균 결과")
    print(f"{'='*60}")

    accs      = [m["accuracy"]    for m in all_metrics]
    mf1s      = [m["macro_f1"]    for m in all_metrics]
    wf1s      = [m["weighted_f1"] for m in all_metrics]

    print(f"\n[전체 지표]         평균    min    max")
    print(f"  Accuracy    :  {sum(accs)/n:.1%}  {min(accs):.1%}  {max(accs):.1%}")
    print(f"  Macro F1    :  {sum(mf1s)/n:.3f}  {min(mf1s):.3f}  {max(mf1s):.3f}")
    print(f"  Weighted F1 :  {sum(wf1s)/n:.3f}  {min(wf1s):.3f}  {max(wf1s):.3f}")

    print(f"\n[에이전트별 평균 F1]")
    print(f"  {'Agent':<14} {'F1 avg':>8} {'F1 min':>8} {'F1 max':>8}")
    print(f"  {'-'*42}")
    for agent in agents:
        f1s = [m["per_class"][agent]["f1"] for m in all_metrics]
        print(f"  {agent:<14} {sum(f1s)/n:>8.3f} {min(f1s):>8.3f} {max(f1s):>8.3f}")

    # 케이스별 오답 빈도 (n회 중 몇 번 틀렸나)
    case_errors: dict[str, int] = {}
    for results in all_runs:
        for r in results:
            if not r["agent_correct"]:
                case_errors[r["id"]] = case_errors.get(r["id"], 0) + 1

    persistent = {k: v for k, v in case_errors.items() if v >= (n // 2 + 1)}
    if persistent:
        print(f"\n[{n}회 중 과반 실패한 케이스 — 실제 취약점]")
        all_cases = {r["id"]: r for r in all_runs[0]}
        for cid, cnt in sorted(persistent.items(), key=lambda x: -x[1]):
            r = all_cases[cid]
            msg = r["message"][:40]
            print(f"  [{cid}] ({cnt}/{n}회 실패) \"{msg}\"")
            print(f"    expected : {r['expected_agent']} / {r['expected_sub_category'] or '-'}")
    print()


async def run_eval(dataset_path: str, verbose: bool, runs: int) -> None:
    cases = []
    with open(dataset_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                cases.append(json.loads(line))

    print(f"데이터셋 로드: {len(cases)}개 케이스  ({dataset_path})")
    print(f"총 {runs}회 실행 예정")

    all_runs: list[list[dict]] = []
    all_metrics: list[dict] = []

    for run_idx in range(1, runs + 1):
        results = await _run_once(cases, run_idx, runs)
        print(f"  run {run_idx} 완료 {len(results)}개                    ")
        metrics = _compute_metrics(results)
        sub     = _compute_sub_metrics(results)
        anaphora = _compute_anaphora_metrics(results)

        if runs == 1 or verbose:
            _print_report(metrics, sub, anaphora, results, verbose)

        all_runs.append(results)
        all_metrics.append(metrics)

    if runs > 1:
        _print_avg_report(all_runs, all_metrics)

    # 마지막 실행 결과 저장
    out_path = pathlib.Path(dataset_path).parent.parent / "logs" / "intent_eval_result.json"
    out_path.parent.mkdir(exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({
            "runs":       runs,
            "metrics":    all_metrics,
            "all_results": all_runs,
        }, f, ensure_ascii=False, indent=2)
    print(f"결과 저장: {out_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dataset", default="eval/datasets/intent_golden.jsonl",
        help="골든 데이터셋 경로",
    )
    parser.add_argument(
        "--verbose", action="store_true",
        help="틀린 케이스 상세 출력",
    )
    parser.add_argument(
        "--runs", type=int, default=1,
        help="반복 실행 횟수 (기본 1, 신뢰도 측정시 3 권장)",
    )
    args = parser.parse_args()
    asyncio.run(run_eval(args.dataset, args.verbose, args.runs))


if __name__ == "__main__":
    main()
