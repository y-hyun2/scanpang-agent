"""
run_category_eval.py
search category extract (편의시설 검색용 18종 분류) 평가.
사용자 메시지 → (category, language, brand_keyword) 분류 정확도.

사용:
    python eval/run_category_eval.py
    python eval/run_category_eval.py --model gpt-4o --verbose
"""

import argparse
import asyncio
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from agents.search_agent import _extract_category_and_language


def _norm(s: str) -> str:
    return "".join((s or "").split()).lower()


async def run(path: str, model: str, verbose: bool) -> None:
    cases = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    cat_ok = lang_ok = brand_ok = 0
    cm: dict = {}   # category confusion: cm[expected][pred]
    wrong = []

    for c in cases:
        try:
            cat, lang, brand = await _extract_category_and_language(c["message"], model=model)
        except Exception as e:
            cat, lang, brand = "", "", ""
            if verbose:
                print(f"  ERR [{c['id']}]: {e}")
        ec, el, eb = c["expected_category"], c["expected_language"], c["expected_brand"]
        c_ok, l_ok, b_ok = (cat == ec), (lang == el), (_norm(brand) == _norm(eb))
        cat_ok += c_ok; lang_ok += l_ok; brand_ok += b_ok
        cm.setdefault(ec, {}).setdefault(cat, 0)
        cm[ec][cat] += 1
        if verbose and not (c_ok and l_ok and b_ok):
            wrong.append((c["id"], c["message"], (cat, lang, brand), (ec, el, eb)))

    n = len(cases)
    # Macro F1 over categories present in golden
    cats = sorted(set(c["expected_category"] for c in cases))
    f1s = []
    for a in cats:
        tp = cm.get(a, {}).get(a, 0)
        fp = sum(cm.get(o, {}).get(a, 0) for o in cm if o != a)
        fn = sum(v for p, v in cm.get(a, {}).items() if p != a)
        prec = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn) if tp + fn else 0.0
        f1s.append(2 * prec * rec / (prec + rec) if prec + rec else 0.0)

    print(f"\n=== search category extract (모델: {model}, n={n}) ===")
    print(f"  Category Accuracy : {cat_ok/n*100:.1f}%  ({cat_ok}/{n})")
    print(f"  Category Macro F1 : {sum(f1s)/len(f1s):.3f}  ({len(cats)} classes)")
    print(f"  Language Accuracy : {lang_ok/n*100:.1f}%  ({lang_ok}/{n})")
    print(f"  Brand Match       : {brand_ok/n*100:.1f}%  ({brand_ok}/{n})")
    if verbose and wrong:
        print(f"\n  [틀린 케이스 {len(wrong)}]")
        for cid, msg, pred, exp in wrong:
            print(f"   [{cid}] {msg}")
            print(f"     예측 cat/lang/brand: {pred}")
            print(f"     정답 cat/lang/brand: {exp}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--dataset", default="eval/datasets/category_golden.jsonl")
    p.add_argument("--model", default="gpt-5.4-mini")
    p.add_argument("--verbose", action="store_true")
    a = p.parse_args()
    print(f"모델: {a.model}")
    asyncio.run(run(a.dataset, a.model, a.verbose))


if __name__ == "__main__":
    main()
