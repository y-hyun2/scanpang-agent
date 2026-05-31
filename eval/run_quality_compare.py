"""
run_quality_compare.py
OpenAI / Claude / Gemini 3개 API로 동일 작업 수행 후 품질 비교.

평가 항목:
  1. 인텐트 분류    — Accuracy, Macro F1        (골든셋 90개 → 샘플 30개)
  2. 카테고리 추출  — Classification Accuracy   (골든셋 10개)
  3. 영업시간 파싱  — Schema Validity, Exact Match (골든셋 30개)
  4. 층별 정보 추출 — F1, Schema Validity        (골든셋 7개)
  5. TTS 응답 생성 — 자동 포맷 검사             (기존 18개)

사용:
    python eval/run_quality_compare.py
"""

import asyncio, json, os, pathlib, sys, re, math
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

GEMINI_RPM_WAIT = 20  # 무료 티어 분당 5회 → 20초 간격


# ── 공통 LLM 호출 ─────────────────────────────────────────────────────────────

async def llm(api: str, system: str, user: str, model_override: str = "") -> str:
    if api == "openai":
        model = model_override or "gpt-4o"
        resp = await _openai.chat.completions.create(
            model=model, temperature=0, max_tokens=600,
            response_format={"type": "json_object"},
            messages=[{"role": "system", "content": system},
                      {"role": "user",   "content": user}],
        )
        return resp.choices[0].message.content.strip()

    elif api == "claude":
        resp = await _claude.messages.create(
            model="claude-opus-4-5", max_tokens=600,
            system=system + "\n\nOutput JSON only.",
            messages=[{"role": "user", "content": user}],
        )
        text = resp.content[0].text.strip()
        m = re.search(r'\{.*\}', text, re.DOTALL)
        return m.group() if m else text

    elif api == "gemini":
        for attempt in range(8):
            try:
                resp = await asyncio.to_thread(
                    _gemini.models.generate_content,
                    model="gemini-2.5-flash", contents=user,
                    config=_genai.types.GenerateContentConfig(
                        system_instruction=system + "\n\nOutput JSON only.",
                        max_output_tokens=600, temperature=0,
                    ),
                )
                text = resp.text.strip()
                m = re.search(r'\{.*\}', text, re.DOTALL)
                return m.group() if m else text
            except Exception as e:
                if "429" in str(e) or "RESOURCE_EXHAUSTED" in str(e):
                    wait = 70
                    print(f"\n    [Gemini] 한도 초과 — {wait}초 대기 ({attempt+1})")
                    await asyncio.sleep(wait)
                else:
                    raise
        return "{}"
    return "{}"


# ── 1. 인텐트 분류 ─────────────────────────────────────────────────────────────

async def eval_intent(api: str) -> dict:
    from agents.orchestrator_agent import _INTENT_SYSTEM, _FEW_SHOTS

    # Few-shot을 텍스트 예시로 변환 (모든 API 호환)
    examples = []
    for i in range(0, len(_FEW_SHOTS)-1, 2):
        q = _FEW_SHOTS[i]["content"].replace('메시지: ', '')
        a = _FEW_SHOTS[i+1]["content"]
        examples.append(f"입력: {q}\n출력: {a}")
    few_shot_text = "\n\n".join(examples[:15])  # 15개만 사용 (토큰 절약)
    system = _INTENT_SYSTEM + f"\n\n예시:\n{few_shot_text}"

    # 골든셋에서 30개 샘플 (6개 클래스 × 5개)
    golden = []
    with open("eval/datasets/intent_golden.jsonl", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                golden.append(json.loads(line))

    # 클래스별 5개 균등 샘플
    from collections import defaultdict
    by_class: dict[str, list] = defaultdict(list)
    for c in golden:
        by_class[c["expected_agent"]].append(c)
    sampled = []
    for cls in ["place", "navigation", "nav_guide", "halal", "convenience", "smalltalk"]:
        sampled.extend(by_class[cls][:5])

    correct = 0
    predictions = []
    labels = []
    agents = ["place", "navigation", "nav_guide", "halal", "convenience", "smalltalk"]

    for i, case in enumerate(sampled):
        if api == "gemini" and i > 0:
            await asyncio.sleep(GEMINI_RPM_WAIT)
        user = f'메시지: "{case["message"]}"'
        if case.get("session_context"):
            user = f"이전 대화:\n{case['session_context']}\n\n{user}"
        try:
            raw = await llm(api, system, user)
            data = json.loads(raw)
            pred = data.get("selected_agent", "smalltalk")
        except Exception:
            pred = "smalltalk"
        if pred not in agents:
            pred = "smalltalk"
        predictions.append(pred)
        labels.append(case["expected_agent"])
        if pred == case["expected_agent"]:
            correct += 1

    accuracy = correct / len(sampled)

    # Macro F1
    f1s = []
    for agent in agents:
        tp = sum(1 for p, l in zip(predictions, labels) if p == agent and l == agent)
        fp = sum(1 for p, l in zip(predictions, labels) if p == agent and l != agent)
        fn = sum(1 for p, l in zip(predictions, labels) if p != agent and l == agent)
        prec = tp / (tp + fp) if (tp + fp) > 0 else 0
        rec  = tp / (tp + fn) if (tp + fn) > 0 else 0
        f1   = 2 * prec * rec / (prec + rec) if (prec + rec) > 0 else 0
        f1s.append(f1)
    macro_f1 = sum(f1s) / len(f1s)

    return {"accuracy": accuracy, "macro_f1": macro_f1, "n": len(sampled)}


# ── 2. 카테고리 추출 ─────────────────────────────────────────────────────────

CATEGORY_CASES = [
    {"user": "무슬림 음식 추천해줘",       "expected": "halal_restaurant"},
    {"user": "기도실 어디야?",             "expected": "prayer_room"},
    {"user": "카페 추천해줘",              "expected": "cafe"},
    {"user": "약국 찾아줘",               "expected": "pharmacy"},
    {"user": "화장실 어디야?",             "expected": "restroom"},
    {"user": "Where is the nearest toilet?", "expected": "restroom"},
    {"user": "환전소 어디 있어?",           "expected": "exchange"},
    {"user": "편의점 어디야?",             "expected": "convenience_store"},
    {"user": "주차장 어디야?",             "expected": "parking"},
    {"user": "병원 어디야?",              "expected": "hospital"},
]

async def eval_category(api: str) -> dict:
    from agents.search_agent import CATEGORY_EXTRACT_PROMPT
    correct = 0
    for i, case in enumerate(CATEGORY_CASES):
        if api == "gemini" and i > 0:
            await asyncio.sleep(GEMINI_RPM_WAIT)
        try:
            raw = await llm(api, CATEGORY_EXTRACT_PROMPT, case["user"])
            data = json.loads(raw)
            pred = data.get("category", "")
        except Exception:
            pred = ""
        if pred == case["expected"]:
            correct += 1
    return {"accuracy": correct / len(CATEGORY_CASES), "n": len(CATEGORY_CASES)}


# ── 3. 영업시간 파싱 ─────────────────────────────────────────────────────────

async def eval_hours(api: str) -> dict:
    from tools.open_hours_normalizer import _NORMALIZE_SYSTEM, _looks_valid

    cases = []
    with open("eval/datasets/open_hours_golden.jsonl", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                cases.append(json.loads(line))

    schema_ok = 0
    exact_ok  = 0
    scored    = 0
    DAYS      = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]

    for i, case in enumerate(cases):
        if api == "gemini" and i > 0:
            await asyncio.sleep(GEMINI_RPM_WAIT)
        try:
            raw = await llm(api, _NORMALIZE_SYSTEM, case["input"],
                            model_override="gpt-4o-mini" if api == "openai" else "")
            parsed = json.loads(raw)
            if not parsed.get("always_open") and parsed.get("weekly") is None:
                parsed["weekly"] = {}
            for d in DAYS:
                parsed.setdefault("weekly", {}).setdefault(d, [])
        except Exception:
            parsed = {}

        if _looks_valid(parsed):
            schema_ok += 1

        if case.get("schema_only"):
            continue

        scored += 1
        expected_always = case.get("expected_always_open", False)
        if expected_always:
            if parsed.get("always_open") or all(
                parsed.get("weekly", {}).get(d) == [["00:00","24:00"]] for d in DAYS
            ):
                exact_ok += 1
        else:
            expected_weekly = case.get("expected_weekly", {})
            alt_weekly = case.get("alt_weekly") or case.get("flexible_weekly")
            match = True
            for d in DAYS:
                actual   = sorted(tuple(r) for r in parsed.get("weekly", {}).get(d, []))
                expected = sorted(tuple(r) for r in expected_weekly.get(d, []))
                alt_exp  = sorted(tuple(r) for r in (alt_weekly or {}).get(d, [])) if alt_weekly else []
                if actual != expected and actual != alt_exp:
                    match = False
                    break
            if match:
                exact_ok += 1

    return {
        "schema_validity": schema_ok / len(cases),
        "exact_match":     exact_ok / scored if scored > 0 else 0,
        "n_total": len(cases), "n_scored": scored,
    }


# ── 4. 층별 정보 추출 ──────────────────────────────────────────────────────────

async def eval_extractor(api: str) -> dict:
    from rag.automation.llm_extractor import _HOMEPAGE_FLOOR_SYSTEM_PROMPT, _validate_quote

    cases = []
    with open("eval/datasets/extractor_golden.jsonl", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                cases.append(json.loads(line))

    all_tp = all_fp = all_fn = 0
    schema_ok = 0
    hallucinated = 0
    trap_total   = 0

    def normalize(name: str) -> str:
        return " ".join(name.lower().split())

    for i, case in enumerate(cases):
        if api == "gemini" and i > 0:
            await asyncio.sleep(GEMINI_RPM_WAIT)
        html  = case["html_text"]
        trunc = html[:6000]
        user  = f"건물명: {case['building_name']}\n\n=== 홈페이지 텍스트 ===\n{trunc}"
        try:
            raw  = await llm(api, _HOMEPAGE_FLOOR_SYSTEM_PROMPT, user,
                             model_override="gpt-4o-mini" if api == "openai" else "")
            data = json.loads(raw)
            raw_floors = data.get("floor_info", [])
        except Exception:
            raw_floors = []

        # verbatim 검증 후 validated
        validated: dict[str, list[str]] = {}
        for fl in raw_floors:
            valid_stores = []
            for store in fl.get("stores", []):
                quote = store.get("verbatim_quote", "")
                if _validate_quote(quote, trunc):
                    valid_stores.append(normalize(store.get("name", "")))
            if valid_stores:
                validated[fl.get("floor", "?")] = valid_stores

        schema_ok += 1 if isinstance(raw_floors, list) else 0

        gt   = case.get("ground_truth", {})
        gt_set = {normalize(s) for stores in gt.values() for s in stores}
        ex_set = {s for stores in validated.values() for s in stores}

        if not gt:
            trap_total += 1
            if ex_set:
                hallucinated += 1
        else:
            tp = len(gt_set & ex_set)
            fp = len(ex_set - gt_set)
            fn = len(gt_set - ex_set)
            all_tp += tp; all_fp += fp; all_fn += fn

    prec = all_tp / (all_tp + all_fp) if (all_tp + all_fp) > 0 else 1.0
    rec  = all_tp / (all_tp + all_fn) if (all_tp + all_fn) > 0 else 1.0
    f1   = 2 * prec * rec / (prec + rec) if (prec + rec) > 0 else 0.0

    return {
        "schema_validity":    schema_ok / len(cases),
        "precision":          prec,
        "recall":             rec,
        "f1":                 f1,
        "hallucination_rate": hallucinated / trap_total if trap_total > 0 else 0,
        "n": len(cases),
    }


# ── 5. TTS 자동 포맷 검사 ─────────────────────────────────────────────────────

def eval_tts_format(api: str) -> dict:
    """기존 로그에서 TTS 응답을 읽어 자동 검사."""
    log_path = {
        "openai": "eval/logs/speech_eval_result.json",
        "claude": "eval/logs/speech_eval_claude_result.json",
    }.get(api)

    if not log_path or not pathlib.Path(log_path).exists():
        return {"n": 0, "note": "로그 없음"}

    results = json.loads(pathlib.Path(log_path).read_text(encoding="utf-8"))

    total = len(results)
    lang_ok = 0
    sent_ok = 0
    no_md   = 0
    avg_len = 0

    try:
        from langdetect import detect
        can_detect = True
    except ImportError:
        can_detect = False

    for r in results:
        speech = r.get("speech", "")
        lang   = r.get("language", "ko")
        avg_len += len(speech)

        sentences = len([s for s in re.split(r'[.!?。！？\n]', speech) if s.strip()])
        if 1 <= sentences <= 4:
            sent_ok += 1

        if not re.search(r"[*#\[\]`]|https?://", speech):
            no_md += 1

        if can_detect:
            try:
                expected_lang = {"ko": "ko", "en": "en", "ar": "ar", "ja": "ja", "zh": "zh"}.get(lang, lang)
                detected = detect(speech)
                if detected == expected_lang or (lang == "ar" and detected in ["ar","fa","ur"]):
                    lang_ok += 1
            except Exception:
                lang_ok += 1
        else:
            lang_ok += 1

    avg_len = avg_len / total if total > 0 else 0
    return {
        "n":              total,
        "lang_match":     lang_ok / total,
        "sent_in_range":  sent_ok / total,
        "no_markdown":    no_md  / total,
        "avg_char_len":   round(avg_len, 1),
        "format_pass":    sum(1 for r in results
                              if not re.search(r"[*#\[\]`]|https?://", r.get("speech",""))
                              and 1 <= len([s for s in re.split(r'[.!?。！？\n]',
                                           r.get("speech","")) if s.strip()]) <= 4) / total,
    }


# ── 메인 ─────────────────────────────────────────────────────────────────────

async def run_api(api: str) -> dict:
    print(f"\n  [{api.upper()}] 시작...")
    results = {}

    print(f"    인텐트 분류 (30개)...")
    results["intent"] = await eval_intent(api)

    print(f"    카테고리 추출 (10개)...")
    results["category"] = await eval_category(api)

    print(f"    영업시간 파싱 (30개)...")
    results["hours"] = await eval_hours(api)

    print(f"    층별 정보 추출 (7개)...")
    results["extractor"] = await eval_extractor(api)

    if api in ("openai", "claude"):
        print(f"    TTS 포맷 검사 (기존 로그)...")
        results["tts"] = eval_tts_format(api)

    return results


def print_report(all_res: dict[str, dict]) -> None:
    api_list = list(all_res.keys())
    api_labels = {"openai": "OpenAI", "claude": "Claude", "gemini": "Gemini"}

    print("\n" + "=" * 75)
    print(f"  품질 비교 — {' vs '.join(api_labels[a] for a in api_list)}")
    print("=" * 75)

    # 인텐트 분류
    print(f"\n[1] 인텐트 분류 (n=30, 6클래스 균등)")
    print(f"  {'API':<12} {'Accuracy':>10} {'Macro F1':>10}")
    print(f"  {'─'*34}")
    for api in api_list:
        r = all_res[api].get("intent", {})
        print(f"  {api_labels[api]:<12} {r.get('accuracy',0):>10.1%} {r.get('macro_f1',0):>10.3f}")

    # 카테고리 추출
    print(f"\n[2] 카테고리 추출 (n=10)")
    print(f"  {'API':<12} {'Accuracy':>10}")
    print(f"  {'─'*24}")
    for api in api_list:
        r = all_res[api].get("category", {})
        print(f"  {api_labels[api]:<12} {r.get('accuracy',0):>10.1%}")

    # 영업시간 파싱
    print(f"\n[3] 영업시간 파싱 (n=30)")
    print(f"  {'API':<12} {'Schema Valid':>13} {'Exact Match':>12}")
    print(f"  {'─'*39}")
    for api in api_list:
        r = all_res[api].get("hours", {})
        print(f"  {api_labels[api]:<12} {r.get('schema_validity',0):>13.1%} {r.get('exact_match',0):>12.1%}")

    # 층별 정보 추출
    print(f"\n[4] 층별 정보 추출 (n=7)")
    print(f"  {'API':<12} {'Precision':>10} {'Recall':>8} {'F1':>8} {'Halluc.':>9}")
    print(f"  {'─'*50}")
    for api in api_list:
        r = all_res[api].get("extractor", {})
        print(f"  {api_labels[api]:<12} {r.get('precision',0):>10.3f} {r.get('recall',0):>8.3f} "
              f"{r.get('f1',0):>8.3f} {r.get('hallucination_rate',0):>9.1%}")

    # TTS 포맷
    tts_apis = [a for a in api_list if all_res[a].get("tts", {}).get("n", 0) > 0]
    if tts_apis:
        print(f"\n[5] TTS 자동 포맷 검사 (n=18)")
        print(f"  {'API':<12} {'Lang Match':>11} {'Sent OK':>8} {'No MD':>7} {'포맷 Pass':>10} {'평균 글자':>10}")
        print(f"  {'─'*60}")
        for api in tts_apis:
            r = all_res[api].get("tts", {})
            print(f"  {api_labels[api]:<12} {r.get('lang_match',0):>11.1%} {r.get('sent_in_range',0):>8.1%} "
                  f"{r.get('no_markdown',0):>7.1%} {r.get('format_pass',0):>10.1%} "
                  f"{r.get('avg_char_len',0):>10.1f}")

    print()


async def main() -> None:
    print("품질 비교 시작 (OpenAI → Claude → Gemini 순서)")
    print("Gemini는 분당 5회 한도로 20초 간격 실행\n")

    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--apis", nargs="+", default=["openai", "claude"],
                        help="실행할 API 목록 (openai claude gemini)")
    args, _ = parser.parse_known_args()

    all_res = {}
    for api in args.apis:
        all_res[api] = await run_api(api)

    print_report(all_res)

    out = pathlib.Path("eval/logs/quality_compare.json")
    out.write_text(json.dumps(all_res, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"결과 저장: {out}")


if __name__ == "__main__":
    asyncio.run(main())
