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
from agents.navigation_agent import KEYWORD_PROMPT, select_best_poi


def _norm(s: str) -> str:
    return "".join((s or "").split()).lower()


# ── #6 nav 키워드/언어 추출 ──────────────────────────────────────────────────

async def eval_keyword(path: str, model: str, verbose: bool) -> None:
    cases = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    lang_ok = kw_ok = 0
    wrong = []

    for c in cases:
        try:
            content = await call_llm(
                user_id="", purpose="nav_keyword", model=model, record=False,
                temperature=0,
                messages=[{"role": "system", "content": KEYWORD_PROMPT},
                          {"role": "user", "content": c["message"]}],
            )
            raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip())
            d = json.loads(raw)
        except Exception as e:
            d = {"keyword": "", "language": ""}
            if verbose:
                print(f"  ERR [{c['id']}]: {e}")

        pl, pk = d.get("language", ""), d.get("keyword", "")
        el, ek = c["expected_language"], c["expected_keyword"]
        l_ok, k_ok = (pl == el), (_norm(pk) == _norm(ek))
        lang_ok += l_ok; kw_ok += k_ok
        if verbose and not (l_ok and k_ok):
            wrong.append((c["id"], c["message"], (pl, pk), (el, ek)))

    n = len(cases)
    print(f"\n=== #6 nav 키워드/언어 추출 (모델: {model}, n={n}) ===")
    print(f"  Language Accuracy: {lang_ok/n*100:.1f}%  ({lang_ok}/{n})")
    print(f"  Keyword Match    : {kw_ok/n*100:.1f}%  ({kw_ok}/{n})  (정규화 일치, 확장/번역 변동 있음)")
    if verbose and wrong:
        print(f"\n  [틀린 케이스 {len(wrong)}]")
        for cid, msg, pred, exp in wrong:
            print(f"   [{cid}] {msg}")
            print(f"     예측 lang/kw: {pred}")
            print(f"     정답 lang/kw: {exp}")


# ── #7 POI 선택 ───────────────────────────────────────────────────────────────

def eval_poi(path: str, verbose: bool) -> None:
    """POI 선택은 코드 랭킹(select_best_poi) — 모델 무관, 결정적."""
    cases = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    ok = 0
    wrong = []

    for c in cases:
        pred = select_best_poi(c["candidates"], c["lat"], c["lng"], c["keyword"])
        exp = c["expected_index"]
        if pred == exp:
            ok += 1
        elif verbose:
            wrong.append((c["id"], c["keyword"], pred, exp, c.get("note", "")))

    n = len(cases)
    print(f"\n=== #7 POI 선택 (코드 랭킹, n={n}) ===")
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
        await eval_keyword(args.intent_dataset, args.model, args.verbose)
        eval_poi(args.poi_dataset, args.verbose)

    asyncio.run(run())


if __name__ == "__main__":
    main()
