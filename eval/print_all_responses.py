"""
print_all_responses.py
평가 로그에서 질문 → 실제 답변을 전부 출력한다.

사용:
    python eval/print_all_responses.py
    python eval/print_all_responses.py > eval/logs/all_responses.txt
"""

import json
import sys
import pathlib

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

LOG_DIR = pathlib.Path("eval/logs")


def section(title: str) -> None:
    print(f"\n{'='*70}")
    print(f"  {title}")
    print(f"{'='*70}")


def subsection(title: str) -> None:
    print(f"\n── {title} ──")


# ── 1. 인텐트 분류기 ──────────────────────────────────────────────────────────

def print_intent(path: pathlib.Path) -> None:
    section("1. 인텐트 분류기 (Intent Classifier) — 마지막 실행 결과")
    data = json.loads(path.read_text(encoding="utf-8"))
    results = data["all_results"][-1]  # 마지막 run

    correct = [r for r in results if r["agent_correct"]]
    wrong   = [r for r in results if not r["agent_correct"]]

    print(f"\n총 {len(results)}개  |  정답 {len(correct)}개  |  오답 {len(wrong)}개\n")
    print(f"{'ID':<22} {'입력 메시지':<38} {'예측':>12} {'정답':>12} {'O/X':>4}")
    print(f"{'─'*92}")

    for r in results:
        ox     = "✅" if r["agent_correct"] else "❌"
        pred   = r["predicted_agent"]
        if r["predicted_sub_category"]:
            pred += f"/{r['predicted_sub_category']}"
        exp    = r["expected_agent"]
        if r["expected_sub_category"]:
            exp += f"/{r['expected_sub_category']}"
        msg    = r["message"][:36]
        print(f"{r['id']:<22} {msg:<38} {pred:>12} {exp:>12} {ox:>4}")

    if wrong:
        subsection("오답 케이스 상세")
        for r in wrong:
            print(f"\n  [{r['id']}]")
            print(f"  질문:   \"{r['message']}\"")
            print(f"  예측:   {r['predicted_agent']} / sub={r['predicted_sub_category'] or '-'}")
            print(f"  정답:   {r['expected_agent']} / sub={r['expected_sub_category'] or '-'}")
            if r.get("predicted_resolved") and r.get("expected_resolved"):
                print(f"  resolve 예측: \"{r['predicted_resolved']}\"")
                print(f"  resolve 정답: \"{r['expected_resolved']}\"")


# ── 2. Open Hours Normalizer ─────────────────────────────────────────────────

def print_open_hours(path: pathlib.Path) -> None:
    section("2. Open Hours Normalizer")
    results = json.loads(path.read_text(encoding="utf-8"))

    print(f"\n총 {len(results)}개\n")
    print(f"{'ID':<18} {'타입':<20} {'입력 텍스트':<40} {'Exact':>6} {'Schema':>7}")
    print(f"{'─'*96}")

    for r in results:
        ox     = "✅" if r.get("exact_match") else ("—" if r.get("exact_match") is None else "❌")
        schema = "✅" if r.get("schema_valid") else "❌"
        inp    = r["input"][:38]
        print(f"{r['id']:<18} {r['type']:<20} {inp:<40} {ox:>6} {schema:>7}")

    print()
    wrong = [r for r in results if r.get("exact_match") is False]
    if wrong:
        subsection("Exact Match 실패 케이스")
        for r in wrong:
            print(f"\n  [{r['id']}] type={r['type']}")
            print(f"  입력:   \"{r['input']}\"")
            print(f"  LLM 출력: {json.dumps(r.get('result', {}), ensure_ascii=False)[:120]}")
            dr = r.get("day_results") or {}
            bad_days = [d for d, ok in dr.items() if not ok]
            if bad_days:
                for d in bad_days:
                    actual   = r.get("result", {}).get("weekly", {}).get(d, "?")
                    expected = r.get("expected_weekly", {}).get(d, "?")
                    print(f"  {d}: LLM={actual}  정답={expected}")


# ── 3. TTS 음성 응답 품질 ────────────────────────────────────────────────────

def print_speech(path: pathlib.Path) -> None:
    section("3. TTS 음성 응답 품질 (LLM-as-Judge)")
    results = json.loads(path.read_text(encoding="utf-8"))

    CRITERIA = ["groundedness", "completeness", "language_match", "tts_naturalness", "conciseness"]

    print(f"\n총 {len(results)}개\n")

    for r in results:
        sc  = r.get("scores", {})
        avg = sum(sc.get(c, 0) for c in CRITERIA) / len(CRITERIA) if sc else 0
        fmt = r.get("format", {})
        print(f"[{r['id']}]  component={r['component']}  lang={r['language']}")
        print(f"  질문/컨텍스트: {r.get('context','')[:60]}")
        print(f"  LLM 응답: {r.get('speech','')[:100]}{'...' if len(r.get('speech',''))>100 else ''}")
        print(f"  점수: G={sc.get('groundedness')} C={sc.get('completeness')} "
              f"L={sc.get('language_match')} T={sc.get('tts_naturalness')} "
              f"Cn={sc.get('conciseness')}  avg={avg:.1f}")
        print(f"  Judge 메모: {sc.get('note','')}")
        print(f"  포맷: 문장수={fmt.get('sentence_count')} 글자수={fmt.get('char_count')} "
              f"마크다운없음={fmt.get('no_markdown')}")
        print()


# ── 4. E2E 파이프라인 ─────────────────────────────────────────────────────────

def print_e2e(path: pathlib.Path) -> None:
    section("4. End-to-End 파이프라인")
    results = json.loads(path.read_text(encoding="utf-8"))

    print(f"\n총 {len(results)}개\n")
    print(f"{'ID':<28} {'입력':<36} {'에이전트':>12} {'완료':>5} {'지연ms':>7} {'품질':>5}")
    print(f"{'─'*100}")

    for r in results:
        j      = r.get("judge", {})
        ok     = "✅" if j.get("completed") else "❌"
        route  = "✅" if r.get("routing_correct") else f"❌→{r.get('source_agent')}"
        msg    = r["message"][:34]
        print(f"{r['id']:<28} {msg:<36} {route:>12} {ok:>5} {r.get('latency_ms',0):>7} {j.get('quality',0):>5}")

    print()
    subsection("전체 응답 상세")
    for r in results:
        j = r.get("judge", {})
        print(f"\n  [{r['id']}]")
        print(f"  입력:   \"{r['message']}\"")
        print(f"  에이전트: {r.get('source_agent')}  라우팅={'✅' if r.get('routing_correct') else '❌'}")
        print(f"  응답:   {r.get('speech','')[:120]}{'...' if len(r.get('speech',''))>120 else ''}")
        print(f"  Judge: 완료={'✅' if j.get('completed') else '❌'}  품질={j.get('quality')}  {j.get('note','')}")
        print(f"  지연:   {r.get('latency_ms')}ms")


# ── 5. 층별 정보 추출기 ──────────────────────────────────────────────────────

def print_extractor(path: pathlib.Path) -> None:
    section("5. 층별 정보 추출기 (LLM Extractor)")
    results = json.loads(path.read_text(encoding="utf-8"))

    print(f"\n총 {len(results)}개\n")
    print(f"{'ID':<16} {'타입':<22} {'P':>6} {'R':>6} {'F1':>6} {'TP':>4} {'FP':>4} {'FN':>4}")
    print(f"{'─'*74}")

    for r in results:
        p = r.get("pr", {})
        ox = "✅" if p.get("f1", 0) >= 1.0 else "❌"
        print(f"{r['id']:<16} {r['type']:<22} "
              f"{p.get('precision',0):>6.3f} {p.get('recall',0):>6.3f} {p.get('f1',0):>6.3f} "
              f"{p.get('tp',0):>4} {p.get('fp',0):>4} {p.get('fn',0):>4}  {ox}")

    print()
    subsection("케이스별 상세")
    for r in results:
        p = r.get("pr", {})
        inp = r.get('html_text') or r.get('input', '')
        print(f"\n  [{r['id']}] type={r['type']}  건물={r['building_name']}")
        print(f"  입력 텍스트: \"{inp[:80]}{'...' if len(inp)>80 else ''}\"")
        print(f"  LLM 추출: {r.get('extracted', {})}")
        print(f"  정답:     {r.get('ground_truth', {})}")
        if p.get("fp", 0) > 0:
            fp_stores = set(p.get("extracted_stores", [])) - set(p.get("gt_stores", []))
            print(f"  지어낸 매장 (FP): {sorted(fp_stores)}")
        if p.get("fn", 0) > 0:
            fn_stores = set(p.get("gt_stores", [])) - set(p.get("extracted_stores", []))
            print(f"  놓친 매장 (FN):   {sorted(fn_stores)}")
        if r.get("hallucinated"):
            print(f"  ⚠️  환각 발생!")


# ── 메인 ─────────────────────────────────────────────────────────────────────

def main() -> None:
    print("Youscan AR 앱 — LLM 평가 전체 응답 리포트")
    print("평가 로그 디렉토리:", LOG_DIR.resolve())

    files = {
        "intent":    LOG_DIR / "intent_eval_result.json",
        "hours":     LOG_DIR / "open_hours_eval_result.json",
        "speech":    LOG_DIR / "speech_eval_result.json",
        "e2e":       LOG_DIR / "e2e_eval_result.json",
        "extractor": LOG_DIR / "extractor_eval_result.json",
    }

    for key, path in files.items():
        if not path.exists():
            print(f"\n⚠️  {path} 없음 — 건너뜀")
            continue
        if key == "intent":    print_intent(path)
        elif key == "hours":   print_open_hours(path)
        elif key == "speech":  print_speech(path)
        elif key == "e2e":     print_e2e(path)
        elif key == "extractor": print_extractor(path)

    print(f"\n{'='*70}")
    print("  리포트 끝")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
