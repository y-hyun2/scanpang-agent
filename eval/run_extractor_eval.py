"""
run_extractor_eval.py
llm_extractor (층별 매장 정보 추출) 평가 스크립트.

측정 항목:
  1. Parse Rate         — JSON 파싱 성공률
  2. Verbatim Pass Rate — verbatim_quote 검증 통과율 (환각 방어)
  3. Precision         — 추출 매장 중 정답 비율
  4. Recall            — 정답 매장 중 추출 비율
  5. F1                — Precision/Recall 조화평균
  6. Hallucination Rate — 정보 없는 텍스트에서 매장을 지어냈는지

사용:
    python eval/run_extractor_eval.py
    python eval/run_extractor_eval.py --verbose
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

from rag.automation.llm_extractor import extract_floor_info_from_homepage, _validate_quote


# ── 비교 유틸 ──────────────────────────────────────────────────────────────────

def _normalize(name: str) -> str:
    """매장명 정규화 (공백·대소문자 무관 비교)."""
    return " ".join(name.lower().split())


def _compute_pr(extracted: dict[str, list[str]], ground_truth: dict[str, list[str]]) -> dict:
    """Precision / Recall / F1 계산 (매장명 정규화 후 비교)."""
    gt_set = {_normalize(s) for stores in ground_truth.values() for s in stores}
    ex_set = {_normalize(s) for stores in extracted.values() for s in stores}

    if not ex_set and not gt_set:
        return {"precision": 1.0, "recall": 1.0, "f1": 1.0, "tp": 0, "fp": 0, "fn": 0,
                "extracted_stores": [], "gt_stores": []}

    tp = len(gt_set & ex_set)
    fp = len(ex_set - gt_set)
    fn = len(gt_set - ex_set)

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall    = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1        = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

    return {"precision": precision, "recall": recall, "f1": f1,
            "tp": tp, "fp": fp, "fn": fn,
            "extracted_stores": sorted(ex_set),
            "gt_stores": sorted(gt_set)}


def _verbatim_pass_rate(raw_response: list[dict], html_text: str) -> dict:
    """
    LLM 원본 응답(verbatim_quote 포함)의 통과율 계산.
    extract_floor_info_from_homepage 내부에서 이미 필터링하므로,
    여기서는 단순히 통과/실패 카운트만 한다.
    """
    total, passed = 0, 0
    for floor_item in raw_response:
        for store in floor_item.get("stores", []):
            total += 1
            quote = store.get("verbatim_quote", "")
            if _validate_quote(quote, html_text[:6000]):
                passed += 1
    return {"total": total, "passed": passed,
            "pass_rate": passed / total if total > 0 else None}


# ── 평가 ──────────────────────────────────────────────────────────────────────

async def eval_case(case: dict) -> dict:
    building_name = case["building_name"]
    html_text     = case["html_text"]
    ground_truth  = case["ground_truth"]

    # verbatim 통과율을 재계산하기 위해 LLM 원본 응답도 별도로 가져옴
    # (내부 함수를 직접 호출해 raw response 확보)
    from openai import AsyncOpenAI
    from dotenv import load_dotenv
    import json as _json
    load_dotenv()
    _client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))
    from rag.automation.llm_extractor import _HOMEPAGE_FLOOR_SYSTEM_PROMPT

    truncated    = html_text[:6000]
    user_content = f"건물명: {building_name}\n\n=== 홈페이지 텍스트 ===\n{truncated}"

    raw_response = []
    parse_ok = False
    try:
        resp = await _client.chat.completions.create(
            model="gpt-4o-mini",
            temperature=0,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": _HOMEPAGE_FLOOR_SYSTEM_PROMPT},
                {"role": "user",   "content": user_content},
            ],
            max_tokens=2000,
        )
        data = _json.loads(resp.choices[0].message.content)
        raw_response = data.get("floor_info", [])
        parse_ok = True
    except Exception as e:
        print(f"\n  [LLM 오류] {case['id']}: {e}")

    verbatim = _verbatim_pass_rate(raw_response, html_text)

    # 검증 후 최종 출력 (extract_floor_info_from_homepage 사용)
    validated = await extract_floor_info_from_homepage(building_name, html_text)

    # floor → store names 변환
    extracted: dict[str, list[str]] = {}
    for floor_item in validated:
        fl = floor_item.get("floor", "?")
        extracted[fl] = [s["name"] for s in floor_item.get("stores", [])]

    pr = _compute_pr(extracted, ground_truth)

    # 환각 감지: 정보 없는 케이스에서 매장이 추출됐는지
    hallucinated = (not ground_truth) and bool(extracted)

    return {
        **case,
        "parse_ok":        parse_ok,
        "verbatim":        verbatim,
        "extracted":       extracted,
        "pr":              pr,
        "hallucinated":    hallucinated,
        "exact_match":     pr["f1"] == 1.0,
    }


# ── 리포트 ────────────────────────────────────────────────────────────────────

def print_report(results: list[dict], verbose: bool) -> None:
    total = len(results)
    parse_ok = sum(1 for r in results if r["parse_ok"])

    vt_totals = sum(r["verbatim"]["total"] for r in results)
    vt_passed = sum(r["verbatim"]["passed"] for r in results)

    # Precision / Recall / F1 (정답이 있는 케이스만)
    scored = [r for r in results if r["pr"]["gt_stores"] or r["extracted"]]
    precisions = [r["pr"]["precision"] for r in scored]
    recalls    = [r["pr"]["recall"]    for r in scored]
    f1s        = [r["pr"]["f1"]        for r in scored]

    # 환각 감지
    trap_cases = [r for r in results if not r["ground_truth"]]
    hallucinated = [r for r in trap_cases if r["hallucinated"]]

    print("\n" + "=" * 60)
    print("  LLM EXTRACTOR EVALUATION REPORT")
    print("=" * 60)

    print(f"\n[전체 지표]")
    print(f"  Parse Rate           : {parse_ok}/{total}  ({parse_ok/total:.1%})")
    print(f"  Verbatim Pass Rate   : {vt_passed}/{vt_totals}  ({vt_passed/vt_totals:.1%})" if vt_totals else "  Verbatim: N/A")
    print(f"  Hallucination Rate   : {len(hallucinated)}/{len(trap_cases)}  ({len(hallucinated)/len(trap_cases):.1%})" if trap_cases else "")

    if scored:
        print(f"\n[추출 정확도]  (정답 있는 케이스 {len(scored)}개)")
        print(f"  {'케이스':<22} {'P':>6} {'R':>6} {'F1':>6} {'TP':>4} {'FP':>4} {'FN':>4}")
        print(f"  {'-'*56}")
        for r in scored:
            p  = r["pr"]
            flag = "  ❌" if p["f1"] < 1.0 else "  ✅"
            print(f"  {r['id']:<22} {p['precision']:>6.3f} {p['recall']:>6.3f} {p['f1']:>6.3f} "
                  f"{p['tp']:>4} {p['fp']:>4} {p['fn']:>4}{flag}")
        print(f"  {'-'*56}")
        print(f"  {'평균':<22} {sum(precisions)/len(precisions):>6.3f} "
              f"{sum(recalls)/len(recalls):>6.3f} {sum(f1s)/len(f1s):>6.3f}")

    # 타입별
    from collections import defaultdict
    type_f1: dict[str, list] = defaultdict(list)
    for r in results:
        type_f1[r["type"]].append(r["pr"]["f1"])

    print(f"\n[타입별 평균 F1]")
    for t, vals in type_f1.items():
        print(f"  {t:<25} {sum(vals)/len(vals):.3f}  (n={len(vals)})")

    if verbose:
        print(f"\n[케이스별 상세]")
        for r in results:
            p = r["pr"]
            print(f"\n  [{r['id']}] type={r['type']}")
            print(f"  건물: {r['building_name']}")
            print(f"  추출: {dict(r['extracted'])}")
            print(f"  정답: {r['ground_truth']}")
            if p["fp"] > 0:
                fp_stores = set(r["pr"]["extracted_stores"]) - set(r["pr"]["gt_stores"])
                print(f"  FP (지어낸 매장): {sorted(fp_stores)}")
            if p["fn"] > 0:
                fn_stores = set(r["pr"]["gt_stores"]) - set(r["pr"]["extracted_stores"])
                print(f"  FN (놓친 매장):   {sorted(fn_stores)}")
            if r["hallucinated"]:
                print(f"  ⚠️  환각 발생: 정보 없는 텍스트에서 매장 추출됨")

    print()


async def run_eval(dataset_path: str, verbose: bool) -> None:
    cases = []
    with open(dataset_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                cases.append(json.loads(line))

    print(f"데이터셋 로드: {len(cases)}개 케이스  ({dataset_path})")
    print("평가 실행 중... (LLM API 호출)\n")

    results = []
    for i, case in enumerate(cases, 1):
        print(f"  [{i:2d}/{len(cases)}] {case['id']}  ({case['type']})", end="\r")
        r = await eval_case(case)
        results.append(r)

    print(f"  완료 {len(results)}개                         ")
    print_report(results, verbose)

    out_path = pathlib.Path("eval/logs/extractor_eval_result.json")
    out_path.parent.mkdir(exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"결과 저장: {out_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="eval/datasets/extractor_golden.jsonl")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    asyncio.run(run_eval(args.dataset, args.verbose))


if __name__ == "__main__":
    main()
