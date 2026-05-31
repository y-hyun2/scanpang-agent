"""
print_responses_notion.py
평가 로그를 노션에 바로 붙여넣을 수 있는 마크다운으로 출력한다.

사용:
    python eval/print_responses_notion.py > eval/logs/all_responses_notion.md
"""

import json
import sys
import pathlib

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

LOG_DIR = pathlib.Path("eval/logs")


# ── 1. 인텐트 분류기 ──────────────────────────────────────────────────────────

def print_intent(path: pathlib.Path) -> None:
    data    = json.loads(path.read_text(encoding="utf-8"))
    results = data["all_results"][-1]
    wrong   = [r for r in results if not r["agent_correct"]]

    print("## 1. 인텐트 분류기 응답 결과\n")
    print(f"> 총 {len(results)}개 | 정답 {len(results)-len(wrong)}개 | 오답 {len(wrong)}개\n")

    print("### 전체 케이스\n")
    print("| ID | 입력 메시지 | 예측 에이전트 | 정답 에이전트 | 결과 |")
    print("|---|---|---|---|---|")
    for r in results:
        ox   = "✅" if r["agent_correct"] else "❌"
        pred = r["predicted_agent"]
        if r["predicted_sub_category"]:
            pred += f" / {r['predicted_sub_category']}"
        exp  = r["expected_agent"]
        if r["expected_sub_category"]:
            exp += f" / {r['expected_sub_category']}"
        print(f"| {r['id']} | {r['message']} | {pred} | {exp} | {ox} |")

    if wrong:
        print("\n### 오답 케이스 상세\n")
        for r in wrong:
            print(f"**[{r['id']}]** `{r['message']}`")
            print(f"- 예측: {r['predicted_agent']} / sub={r['predicted_sub_category'] or '-'}")
            print(f"- 정답: {r['expected_agent']} / sub={r['expected_sub_category'] or '-'}")
            if r.get("predicted_resolved") and r.get("expected_resolved"):
                print(f"- 지시어 resolve 예측: `{r['predicted_resolved']}`")
                print(f"- 지시어 resolve 정답: `{r['expected_resolved']}`")
            print()


# ── 2. Open Hours Normalizer ─────────────────────────────────────────────────

def print_open_hours(path: pathlib.Path) -> None:
    results = json.loads(path.read_text(encoding="utf-8"))
    wrong   = [r for r in results if r.get("exact_match") is False]

    print("## 2. Open Hours Normalizer 응답 결과\n")
    print(f"> 총 {len(results)}개 | Exact Match 실패 {len(wrong)}개\n")

    print("### 전체 케이스\n")
    print("| ID | 타입 | 입력 텍스트 | Exact Match | Schema Valid |")
    print("|---|---|---|---|---|")
    for r in results:
        exact  = "✅" if r.get("exact_match") else ("—" if r.get("exact_match") is None else "❌")
        schema = "✅" if r.get("schema_valid") else "❌"
        inp    = r["input"][:50] + ("..." if len(r["input"]) > 50 else "")
        print(f"| {r['id']} | {r['type']} | {inp} | {exact} | {schema} |")

    if wrong:
        print("\n### Exact Match 실패 케이스 상세\n")
        for r in wrong:
            print(f"**[{r['id']}]** 타입: {r['type']}")
            print(f"\n입력:")
            print(f"```\n{r['input']}\n```")
            result = r.get("result", {})
            print(f"\nLLM 출력:")
            print(f"```json\n{json.dumps(result, ensure_ascii=False, indent=2)}\n```")
            dr = r.get("day_results") or {}
            bad = [d for d, ok in dr.items() if not ok]
            if bad:
                print(f"\n틀린 요일:")
                for d in bad:
                    actual   = result.get("weekly", {}).get(d, "?")
                    expected = r.get("expected_weekly", {}).get(d, "?")
                    print(f"- {d}: LLM={actual} / 정답={expected}")
            print()


# ── 3. TTS 음성 응답 품질 ────────────────────────────────────────────────────

def print_speech(path: pathlib.Path) -> None:
    results = json.loads(path.read_text(encoding="utf-8"))

    print("## 3. TTS 음성 응답 품질 (LLM-as-Judge)\n")
    print(f"> 총 {len(results)}개\n")

    print("### 점수 요약\n")
    print("| ID | 컴포넌트 | 언어 | G | C | L | T | Cn | 평균 |")
    print("|---|---|---|---|---|---|---|---|---|")
    CRITERIA = ["groundedness", "completeness", "language_match", "tts_naturalness", "conciseness"]
    for r in results:
        sc  = r.get("scores", {})
        avg = sum(sc.get(c, 0) for c in CRITERIA) / len(CRITERIA) if sc else 0
        print(f"| {r['id']} | {r['component']} | {r['language']} | "
              f"{sc.get('groundedness')} | {sc.get('completeness')} | "
              f"{sc.get('language_match')} | {sc.get('tts_naturalness')} | "
              f"{sc.get('conciseness')} | {avg:.1f} |")

    print("\n### 케이스별 상세\n")
    for r in results:
        sc  = r.get("scores", {})
        avg = sum(sc.get(c, 0) for c in CRITERIA) / len(CRITERIA) if sc else 0
        fmt = r.get("format", {})
        print(f"**[{r['id']}]** 컴포넌트: {r['component']} | 언어: {r['language']}")
        print(f"\n컨텍스트:")
        print(f"```\n{r.get('context', '')}\n```")
        print(f"\nLLM 응답:")
        print(f"> {r.get('speech', '')}")
        print(f"\n점수: G={sc.get('groundedness')} C={sc.get('completeness')} "
              f"L={sc.get('language_match')} T={sc.get('tts_naturalness')} "
              f"Cn={sc.get('conciseness')} → **평균 {avg:.1f}**")
        print(f"\nJudge 메모: *{sc.get('note', '')}*")
        print(f"\n포맷 검사: 문장수={fmt.get('sentence_count')} | "
              f"글자수={fmt.get('char_count')} | "
              f"마크다운없음={fmt.get('no_markdown')}")
        print("\n---\n")


# ── 4. E2E 파이프라인 ─────────────────────────────────────────────────────────

def print_e2e(path: pathlib.Path) -> None:
    results = json.loads(path.read_text(encoding="utf-8"))

    print("## 4. End-to-End 파이프라인 응답 결과\n")
    print(f"> 총 {len(results)}개\n")

    print("### 결과 요약\n")
    print("| ID | 입력 | 에이전트 | 라우팅 | 완료 | 지연(ms) | 품질 |")
    print("|---|---|---|---|---|---|---|")
    for r in results:
        j      = r.get("judge", {})
        ok     = "✅" if j.get("completed") else "❌"
        route  = "✅" if r.get("routing_correct") else f"❌→{r.get('source_agent')}"
        print(f"| {r['id']} | {r['message']} | {r.get('source_agent')} | "
              f"{route} | {ok} | {r.get('latency_ms')} | {j.get('quality')} |")

    print("\n### 케이스별 상세\n")
    for r in results:
        j  = r.get("judge", {})
        ok = "✅" if j.get("completed") else "❌"
        print(f"**[{r['id']}]** {ok}")
        print(f"- 입력: `{r['message']}`")
        print(f"- 에이전트: {r.get('source_agent')} | 라우팅: {'✅' if r.get('routing_correct') else '❌'} | 지연: {r.get('latency_ms')}ms")
        print(f"- 응답: {r.get('speech', '')}")
        print(f"- Judge: 완료={ok} | 품질={j.get('quality')} | {j.get('note', '')}")
        print()


# ── 5. 층별 정보 추출기 ──────────────────────────────────────────────────────

def print_extractor(path: pathlib.Path) -> None:
    results = json.loads(path.read_text(encoding="utf-8"))

    print("## 5. 층별 정보 추출기 결과\n")
    print(f"> 총 {len(results)}개\n")

    print("### 정확도 요약\n")
    print("| ID | 타입 | Precision | Recall | F1 | TP | FP | FN | 결과 |")
    print("|---|---|---|---|---|---|---|---|---|")
    for r in results:
        p  = r.get("pr", {})
        ox = "✅" if p.get("f1", 0) >= 1.0 else "❌"
        print(f"| {r['id']} | {r['type']} | "
              f"{p.get('precision',0):.3f} | {p.get('recall',0):.3f} | {p.get('f1',0):.3f} | "
              f"{p.get('tp',0)} | {p.get('fp',0)} | {p.get('fn',0)} | {ox} |")

    print("\n### 케이스별 상세\n")
    for r in results:
        p   = r.get("pr", {})
        inp = r.get('html_text') or r.get('input', '')
        print(f"**[{r['id']}]** 타입: {r['type']} | 건물: {r['building_name']}")
        print(f"\n입력 텍스트:")
        print(f"```\n{inp}\n```")
        print(f"\nLLM 추출 결과:")
        print(f"```json\n{json.dumps(r.get('extracted', {}), ensure_ascii=False, indent=2)}\n```")
        print(f"\n정답:")
        print(f"```json\n{json.dumps(r.get('ground_truth', {}), ensure_ascii=False, indent=2)}\n```")
        if p.get("fp", 0) > 0:
            fp_stores = set(p.get("extracted_stores", [])) - set(p.get("gt_stores", []))
            print(f"\n지어낸 매장 (FP): {sorted(fp_stores)}")
        if p.get("fn", 0) > 0:
            fn_stores = set(p.get("gt_stores", [])) - set(p.get("extracted_stores", []))
            print(f"\n놓친 매장 (FN): {sorted(fn_stores)}")
        if r.get("hallucinated"):
            print(f"\n⚠️ 환각 발생!")
        print("\n---\n")


# ── 메인 ─────────────────────────────────────────────────────────────────────

def main() -> None:
    print("# Youscan AR 앱 — LLM 평가 전체 응답 리포트\n")

    files = {
        "intent":    LOG_DIR / "intent_eval_result.json",
        "hours":     LOG_DIR / "open_hours_eval_result.json",
        "speech":    LOG_DIR / "speech_eval_claude_result.json",
        "e2e":       LOG_DIR / "e2e_eval_result.json",
        "extractor": LOG_DIR / "extractor_eval_result.json",
    }

    for key, path in files.items():
        if not path.exists():
            print(f"> ⚠️ {path.name} 없음\n")
            continue
        if key == "intent":      print_intent(path)
        elif key == "hours":     print_open_hours(path)
        elif key == "speech":    print_speech(path)
        elif key == "e2e":       print_e2e(path)
        elif key == "extractor": print_extractor(path)
        print("\n---\n")


if __name__ == "__main__":
    main()
