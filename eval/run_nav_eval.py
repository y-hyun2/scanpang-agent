"""
run_nav_eval.py
navigation 의도추출(#6) + POI 선택(#7) 평가.

production 프롬프트(navigation_agent.INTENT_PROMPT / SELECT_POI_PROMPT)를 그대로 사용.
모델만 --model 로 교체해 같은 골든셋 비교 가능.

사용:
    python eval/run_nav_eval.py
    python eval/run_nav_eval.py --model gpt-4o --verbose
"""

import argparse
import asyncio
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from tools.llm_client import call_llm
from agents.navigation_agent import INTENT_PROMPT, SELECT_POI_PROMPT


def _norm(s: str) -> str:
    return "".join((s or "").split()).lower()


# ── #6 nav 의도추출 ───────────────────────────────────────────────────────────

async def eval_intent(path: str, model: str, verbose: bool) -> None:
    cases = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    INTENTS = ["specific_place", "category_search"]
    cm = {a: {b: 0 for b in INTENTS} for a in INTENTS}
    intent_ok = lang_ok = kw_ok = 0
    wrong = []

    for c in cases:
        try:
            content = await call_llm(
                user_id="", purpose="nav_intent", model=model, record=False,
                temperature=0,
                messages=[{"role": "system", "content": INTENT_PROMPT},
                          {"role": "user", "content": c["message"]}],
            )
            raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip())
            d = json.loads(raw)
        except Exception as e:
            d = {"keyword": "", "intent": "specific_place", "language": ""}
            if verbose:
                print(f"  ERR [{c['id']}]: {e}")

        pi, pl, pk = d.get("intent", ""), d.get("language", ""), d.get("keyword", "")
        ei, el, ek = c["expected_intent"], c["expected_language"], c["expected_keyword"]
        if pi in cm and ei in cm:
            cm[ei][pi] += 1
        i_ok, l_ok, k_ok = (pi == ei), (pl == el), (_norm(pk) == _norm(ek))
        intent_ok += i_ok; lang_ok += l_ok; kw_ok += k_ok
        if verbose and not (i_ok and l_ok and k_ok):
            wrong.append((c["id"], c["message"], (pi, pl, pk), (ei, el, ek)))

    n = len(cases)
    # intent macro F1 (2-class)
    f1s = []
    for a in INTENTS:
        tp = cm[a][a]
        fp = sum(cm[o][a] for o in INTENTS if o != a)
        fn = sum(cm[a][o] for o in INTENTS if o != a)
        prec = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn) if tp + fn else 0.0
        f1s.append(2 * prec * rec / (prec + rec) if prec + rec else 0.0)

    print(f"\n=== #6 nav 의도추출 (모델: {model}, n={n}) ===")
    print(f"  Intent Accuracy : {intent_ok/n*100:.1f}%  ({intent_ok}/{n})")
    print(f"  Intent Macro F1 : {sum(f1s)/len(f1s):.3f}")
    print(f"  Language Accuracy: {lang_ok/n*100:.1f}%  ({lang_ok}/{n})")
    print(f"  Keyword Match    : {kw_ok/n*100:.1f}%  ({kw_ok}/{n})  (정규화 일치, 확장/번역 변동 있음)")
    if verbose and wrong:
        print(f"\n  [틀린 케이스 {len(wrong)}]")
        for cid, msg, pred, exp in wrong:
            print(f"   [{cid}] {msg}")
            print(f"     예측 intent/lang/kw: {pred}")
            print(f"     정답 intent/lang/kw: {exp}")


# ── #7 POI 선택 ───────────────────────────────────────────────────────────────

async def eval_poi(path: str, model: str, verbose: bool) -> None:
    cases = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    ok = 0
    wrong = []

    for c in cases:
        poi_list = "\n".join(
            f"{i}. {p['name']} | {p['address']}"
            + (f" | {p['distance_m']}m" if p.get("distance_m") is not None else "")
            for i, p in enumerate(c["candidates"])
        )
        try:
            content = await call_llm(
                user_id="", purpose="nav_poi_select", model=model, record=False,
                temperature=0,
                messages=[{"role": "user", "content": SELECT_POI_PROMPT.format(
                    keyword=c["keyword"], lat=c["lat"], lng=c["lng"], poi_list=poi_list)}],
            )
            m = re.search(r"\d+", content)
            pred = int(m.group()) if m else -1
        except Exception as e:
            pred = -1
            if verbose:
                print(f"  ERR [{c['id']}]: {e}")
        exp = c["expected_index"]
        if pred == exp:
            ok += 1
        elif verbose:
            wrong.append((c["id"], c["keyword"], pred, exp, c.get("note", "")))

    n = len(cases)
    print(f"\n=== #7 POI 선택 (모델: {model}, n={n}) ===")
    print(f"  Accuracy (top-1): {ok/n*100:.1f}%  ({ok}/{n})")
    if verbose and wrong:
        print(f"\n  [틀린/불일치 케이스 {len(wrong)}]")
        for cid, kw, pred, exp, note in wrong:
            print(f"   [{cid}] '{kw}'  예측 idx={pred} / 정답 idx={exp}  ({note})")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="gpt-5.4-mini",
                        help="프로덕션 기본 gpt-5.4-mini; baseline은 --model gpt-4o")
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--intent-dataset", default="eval/datasets/nav_intent_golden.jsonl")
    parser.add_argument("--poi-dataset", default="eval/datasets/poi_golden.jsonl")
    args = parser.parse_args()

    async def run():
        await eval_intent(args.intent_dataset, args.model, args.verbose)
        await eval_poi(args.poi_dataset, args.model, args.verbose)

    asyncio.run(run())


if __name__ == "__main__":
    main()
