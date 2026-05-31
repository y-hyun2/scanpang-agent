"""
run_full_compare.py
OpenAI / Claude / Gemini 3개 API를 동일 작업으로 비교:
  - 품질 (Accuracy / F1 / Exact Match / Schema Validity)
  - 응답 시간 (평균·최소·최대·중앙값 ms)
  - 비용 (USD / 1M tokens 기준)

사용:
    python eval/run_full_compare.py
"""

import asyncio, json, os, pathlib, sys, time, re
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

GEMINI_GAP = 15   # 초 (분당 ~4회)

PRICE = {
    "gpt-4o":          {"input": 2.50,  "output": 10.00},
    "gpt-4o-mini":     {"input": 0.15,  "output": 0.60},
    "claude-opus-4-5": {"input": 15.00, "output": 75.00},
    "gemini-2.5-flash":{"input": 0.075, "output": 0.30},
}

def calc_cost(model, inp, out):
    p = PRICE.get(model, {"input":0,"output":0})
    return (inp * p["input"] + out * p["output"]) / 1_000_000


# ── 공통 호출 (시간·토큰 측정) ───────────────────────────────────────────────

async def call_api(api: str, system: str, user: str,
                   openai_model: str = "gpt-4o") -> dict:
    t0 = time.perf_counter()
    try:
        JSON_HINT = "\n\nOutput valid JSON only. No explanation, no markdown."

        if api == "openai":
            resp = await _openai.chat.completions.create(
                model=openai_model, temperature=0, max_tokens=600,
                response_format={"type": "json_object"},
                messages=[{"role":"system","content":system + JSON_HINT},
                          {"role":"user","content":user}],
            )
            text  = resp.choices[0].message.content or ""
            inp   = resp.usage.prompt_tokens
            out   = resp.usage.completion_tokens
            model = openai_model

        elif api == "claude":
            resp = await _claude.messages.create(
                model="claude-opus-4-5", max_tokens=600,
                system=system + JSON_HINT,
                messages=[{"role":"user","content":user}],
            )
            text  = resp.content[0].text if resp.content else ""
            inp   = resp.usage.input_tokens
            out   = resp.usage.output_tokens
            model = "claude-opus-4-5"

        elif api == "gemini":
            resp = await asyncio.to_thread(
                _gemini.models.generate_content,
                model="gemini-2.5-flash", contents=user,
                config=_genai.types.GenerateContentConfig(
                    system_instruction=system + JSON_HINT,
                    max_output_tokens=600, temperature=0,
                    response_mime_type="application/json",
                ),
            )
            text  = resp.text if hasattr(resp, "text") and resp.text else ""
            um    = resp.usage_metadata
            inp   = getattr(um, "prompt_token_count", 0) or 0
            out   = getattr(um, "candidates_token_count", 0) or 0
            model = "gemini-2.5-flash"

        # JSON 추출 — 설명 텍스트가 섞인 경우 { } 안쪽만 추출
        stripped = text.strip()
        if stripped and not stripped.startswith("{"):
            m = re.search(r'\{.*\}', stripped, re.DOTALL)
            stripped = m.group() if m else stripped
        text = stripped

        ms = round((time.perf_counter() - t0) * 1000)
        return {"ok":True, "ms":ms, "inp":inp, "out":out,
                "cost":calc_cost(model,inp,out), "text":text, "model":model}

    except Exception as e:
        ms = round((time.perf_counter() - t0) * 1000)
        return {"ok":False, "ms":ms, "inp":0, "out":0,
                "cost":0, "text":f"ERR:{e}"[:80], "model":""}


# ── 프롬프트 로드 ─────────────────────────────────────────────────────────────

from agents.orchestrator_agent import _INTENT_SYSTEM, _FEW_SHOTS
from agents.search_agent        import CATEGORY_EXTRACT_PROMPT, SPEECH_PROMPT
from rag.automation.llm_extractor import _HOMEPAGE_FLOOR_SYSTEM_PROMPT
from tools.open_hours_normalizer  import _NORMALIZE_SYSTEM
from rag.automation.llm_extractor import _validate_quote
from tools.open_hours_normalizer  import _looks_valid

shots_text = "\n\n".join(
    f"입력: {_FEW_SHOTS[i]['content']}\n출력: {_FEW_SHOTS[i+1]['content']}"
    for i in range(0, min(len(_FEW_SHOTS)-1,28),2)
)[:12]  # 12개만
INTENT_SYS = _INTENT_SYSTEM + "\n\n예시:\n" + "\n\n".join(
    f"입력: {_FEW_SHOTS[i]['content']}\n출력: {_FEW_SHOTS[i+1]['content']}"
    for i in range(0, min(len(_FEW_SHOTS)-1,28),2)
)

DAYS = ["mon","tue","wed","thu","fri","sat","sun"]

# ── 역할별 케이스 ─────────────────────────────────────────────────────────────

INTENT_CASES = [
    {"user":'메시지: "할랄 식당 어디 있어?"',          "expected":"convenience"},
    {"user":'메시지: "남산타워 역사 알려줘"',            "expected":"place"},
    {"user":'메시지: "카페 추천해줘"',                  "expected":"convenience"},
    {"user":'메시지: "기도 시간 알려줘"',               "expected":"halal"},
    {"user":'메시지: "명동역으로 길 안내해줘"',          "expected":"navigation"},
    {"user":'메시지: "여기서 좌회전 맞아?"',             "expected":"nav_guide"},
    {"user":'메시지: "안녕"',                           "expected":"smalltalk"},
    {"user":'메시지: "약국 찾아줘"',                    "expected":"convenience"},
    {"user":'메시지: "키블라 방향이 어디야?"',           "expected":"halal"},
    {"user":'메시지: "경복궁까지 어떻게 가?"',          "expected":"navigation"},
    {"user":'메시지: "거의 다 왔어?"',                  "expected":"nav_guide"},
    {"user":'메시지: "화장실 어디야?"',                  "expected":"convenience"},
]

CAT_CASES = [
    {"user":"무슬림 음식 추천해줘",           "expected":"halal_restaurant"},
    {"user":"기도실 어디야?",                "expected":"prayer_room"},
    {"user":"카페 추천해줘",                 "expected":"cafe"},
    {"user":"Where is the nearest toilet?","expected":"restroom"},
    {"user":"약국 찾아줘",                  "expected":"pharmacy"},
]

HOURS_CASES = [
    {"input":"월 10:00-22:00 / 화 10:00-22:00 / 수 10:00-22:00 / 목 10:00-22:00 / 금 10:00-22:00 / 토 11:00-21:00 / 일 휴무",
     "expected_weekly":{"mon":[["10:00","22:00"]],"tue":[["10:00","22:00"]],"wed":[["10:00","22:00"]],"thu":[["10:00","22:00"]],"fri":[["10:00","22:00"]],"sat":[["11:00","21:00"]],"sun":[]},"always_open":False},
    {"input":"24시간 영업연중무휴",           "expected_weekly":{},"always_open":True},
    {"input":"월-금 09:00-21:00 (13:00-14:00 점심 휴게)",
     "expected_weekly":{"mon":[["09:00","13:00"],["14:00","21:00"]],"tue":[["09:00","13:00"],["14:00","21:00"]],"wed":[["09:00","13:00"],["14:00","21:00"]],"thu":[["09:00","13:00"],["14:00","21:00"]],"fri":[["09:00","13:00"],["14:00","21:00"]],"sat":[],"sun":[]},"always_open":False},
    {"input":"화 09:00-18:00 / 수 09:00-18:00 / 목 09:00-18:00 / 금 09:00-18:00",
     "expected_weekly":{"mon":[],"tue":[["09:00","18:00"]],"wed":[["09:00","18:00"]],"thu":[["09:00","18:00"]],"fri":[["09:00","18:00"]],"sat":[],"sun":[]},"always_open":False},
    {"input":"월 07:20-21:00 / 화 07:20-21:00 / 수 07:20-21:00 / 목 07:20-21:00 / 금 07:20-21:00 / 토 07:20-22:00 / 일 11:30-19:00",
     "expected_weekly":{"mon":[["07:20","21:00"]],"tue":[["07:20","21:00"]],"wed":[["07:20","21:00"]],"thu":[["07:20","21:00"]],"fri":[["07:20","21:00"]],"sat":[["07:20","22:00"]],"sun":[["11:30","19:00"]]},"always_open":False},
]

FLOOR_CASES = [
    {"building":"테스트몰",
     "text":"B2 식품관\n스시히로, 파리바게뜨, 투썸플레이스\nB1 생활관\n올리브영, 다이소\n1F 명품관\n루이비통, 구찌",
     "gt":{"B2":["스시히로","파리바게뜨","투썸플레이스"],"B1":["올리브영","다이소"],"1F":["루이비통","구찌"]}},
    {"building":"강남 쇼핑센터",
     "text":"■ 지하 1층\n- 노브랜드버거\n- 공차\n■ 1층\n- 올리브영 강남점\n- 다이소\n■ 2층\n- 자라 강남점\n- COS",
     "gt":{"B1":["노브랜드버거","공차"],"1F":["올리브영 강남점","다이소"],"2F":["자라 강남점","COS"]}},
    {"building":"일반 오피스",
     "text":"회사 소개. IT 기업. 직원 500명.",
     "gt":{}},
]

LATENCY_CASES = {
    "인텐트 분류":   [c["user"] for c in INTENT_CASES[:5]],
    "TTS 응답 생성": [
        "Category: cafe\nNearest: 스타벅스 명동점\nDistance: 120m\nOpen hours: 07:00-22:00\nLanguage: ko",
        "Category: pharmacy\nNearest: 명동약국\nDistance: 80m\nOpen hours: 09:00-21:00\nLanguage: en",
        "Category: restroom\nNearest: 공중화장실\nDistance: 50m\nOpen hours: 24시간\nLanguage: ar",
        "Category: halal_restaurant\nNearest: 이슬람 식당\nDistance: 180m\nOpen hours: 11:00-21:00\nLanguage: en",
        "Category: atm\nNearest: 신한은행 ATM\nDistance: 200m\nOpen hours: 24시간\nLanguage: ko",
    ],
    "카테고리 추출": [c["user"] for c in CAT_CASES],
    "층별 정보 추출":[f"건물명: {c['building']}\n\n=== 홈페이지 텍스트 ===\n{c['text']}" for c in FLOOR_CASES[:3]],
    "영업시간 파싱": [c["input"] for c in HOURS_CASES[:3]],
}

LATENCY_SYSTEMS = {
    "인텐트 분류":   INTENT_SYS,
    "TTS 응답 생성": SPEECH_PROMPT,
    "카테고리 추출": CATEGORY_EXTRACT_PROMPT,
    "층별 정보 추출":_HOMEPAGE_FLOOR_SYSTEM_PROMPT,
    "영업시간 파싱": _NORMALIZE_SYSTEM,
}

LATENCY_MODELS = {
    "인텐트 분류":   "gpt-4o",
    "TTS 응답 생성": "gpt-4o",
    "카테고리 추출": "gpt-4o",
    "층별 정보 추출":"gpt-4o-mini",
    "영업시간 파싱": "gpt-4o-mini",
}


# ── 품질 계산 ─────────────────────────────────────────────────────────────────

def compute_intent_quality(results: list[dict], cases: list[dict]) -> dict:
    agents = ["place","navigation","nav_guide","halal","convenience","smalltalk"]
    preds, labels = [], []
    for r, c in zip(results, cases):
        try:
            d = json.loads(r["text"])
            pred = d.get("selected_agent","smalltalk")
        except:
            pred = "smalltalk"
        if pred not in agents: pred = "smalltalk"
        preds.append(pred); labels.append(c["expected"])

    acc = sum(p==l for p,l in zip(preds,labels)) / len(preds)
    f1s = []
    for ag in agents:
        tp = sum(1 for p,l in zip(preds,labels) if p==ag and l==ag)
        fp = sum(1 for p,l in zip(preds,labels) if p==ag and l!=ag)
        fn = sum(1 for p,l in zip(preds,labels) if p!=ag and l==ag)
        pr = tp/(tp+fp) if tp+fp>0 else 0
        rc = tp/(tp+fn) if tp+fn>0 else 0
        f1s.append(2*pr*rc/(pr+rc) if pr+rc>0 else 0)
    return {"accuracy":acc,"macro_f1":sum(f1s)/len(f1s)}


def compute_category_quality(results: list[dict], cases: list[dict]) -> dict:
    correct = 0
    for r, c in zip(results, cases):
        try:
            d = json.loads(r["text"])
            if d.get("category") == c["expected"]: correct += 1
        except: pass
    return {"accuracy": correct/len(cases)}


def compute_hours_quality(results: list[dict], cases: list[dict]) -> dict:
    schema_ok = exact_ok = total = 0
    for r, c in zip(results, cases):
        try:
            parsed = json.loads(r["text"])
            for d in DAYS:
                parsed.setdefault("weekly", {}).setdefault(d, [])
        except:
            parsed = {}

        if _looks_valid(parsed): schema_ok += 1
        total += 1
        if c["always_open"]:
            if parsed.get("always_open") or all(
                parsed.get("weekly",{}).get(d)==[["00:00","24:00"]] for d in DAYS
            ): exact_ok += 1
        else:
            ew = c["expected_weekly"]
            match = all(
                sorted(tuple(x) for x in parsed.get("weekly",{}).get(d,[])) ==
                sorted(tuple(x) for x in ew.get(d,[]))
                for d in DAYS
            )
            if match: exact_ok += 1
    return {"schema_validity":schema_ok/total,"exact_match":exact_ok/total}


def compute_floor_quality(results: list[dict], cases: list[dict]) -> dict:
    tp=fp=fn=0
    halluc=trap=0
    def norm(s): return " ".join(s.lower().split())
    for r, c in zip(results, cases):
        trunc = c["text"][:6000]
        try:
            data = json.loads(r["text"])
            raw_floors = data.get("floor_info",[]) if isinstance(data,dict) else []
        except:
            raw_floors = []
        extracted = set()
        for fl in raw_floors:
            for st in fl.get("stores",[]):
                if _validate_quote(st.get("verbatim_quote",""), trunc):
                    extracted.add(norm(st.get("name","")))
        gt_set = {norm(s) for stores in c["gt"].values() for s in stores}
        if not c["gt"]:
            trap += 1
            if extracted: halluc += 1
        else:
            tp += len(gt_set & extracted)
            fp += len(extracted - gt_set)
            fn += len(gt_set - extracted)
    prec = tp/(tp+fp) if tp+fp>0 else 1.0
    rec  = tp/(tp+fn) if tp+fn>0 else 1.0
    f1   = 2*prec*rec/(prec+rec) if prec+rec>0 else 0
    return {"precision":prec,"recall":rec,"f1":f1,
            "hallucination_rate":halluc/trap if trap>0 else 0}


# ── 실행 ─────────────────────────────────────────────────────────────────────

async def run_quality(api: str) -> dict:
    om = "gpt-4o-mini" if api=="openai" else "gpt-4o"

    print(f"    [{api}] 인텐트 ({len(INTENT_CASES)})", end=" ")
    intent_res = []
    for i,c in enumerate(INTENT_CASES):
        if api=="gemini" and i>0: await asyncio.sleep(GEMINI_GAP)
        intent_res.append(await call_api(api, INTENT_SYS, c["user"]))
    print("✓")

    print(f"    [{api}] 카테고리 ({len(CAT_CASES)})", end=" ")
    cat_res = []
    for i,c in enumerate(CAT_CASES):
        if api=="gemini" and i>0: await asyncio.sleep(GEMINI_GAP)
        cat_res.append(await call_api(api, CATEGORY_EXTRACT_PROMPT, c["user"]))
    print("✓")

    print(f"    [{api}] 영업시간 ({len(HOURS_CASES)})", end=" ")
    hours_res = []
    for i,c in enumerate(HOURS_CASES):
        if api=="gemini" and i>0: await asyncio.sleep(GEMINI_GAP)
        hours_res.append(await call_api(api, _NORMALIZE_SYSTEM, c["input"],
                                        openai_model="gpt-4o-mini" if api=="openai" else "gpt-4o"))
    print("✓")

    print(f"    [{api}] 층별 추출 ({len(FLOOR_CASES)})", end=" ")
    floor_res = []
    for i,c in enumerate(FLOOR_CASES):
        if api=="gemini" and i>0: await asyncio.sleep(GEMINI_GAP)
        user = f"건물명: {c['building']}\n\n=== 홈페이지 텍스트 ===\n{c['text']}"
        floor_res.append(await call_api(api, _HOMEPAGE_FLOOR_SYSTEM_PROMPT, user,
                                        openai_model="gpt-4o-mini" if api=="openai" else "gpt-4o"))
    print("✓")

    return {
        "intent":   compute_intent_quality(intent_res, INTENT_CASES),
        "category": compute_category_quality(cat_res, CAT_CASES),
        "hours":    compute_hours_quality(hours_res, HOURS_CASES),
        "extractor":compute_floor_quality(
            [{**r,"text":r["text"]} for r in floor_res],
            [{"text":c["text"],"gt":c["gt"]} for c in FLOOR_CASES]
        ),
        "cost": sum(r["cost"] for r in intent_res+cat_res+hours_res+floor_res),
    }


async def run_latency(api: str) -> dict:
    role_stats = {}
    for role, users in LATENCY_CASES.items():
        system = LATENCY_SYSTEMS[role]
        om     = LATENCY_MODELS[role]
        ms_list = []
        for i, user in enumerate(users):
            if api=="gemini" and i>0: await asyncio.sleep(GEMINI_GAP)
            r = await call_api(api, system, user, openai_model=om)
            if r["ok"]: ms_list.append(r["ms"])
        s = sorted(ms_list) if ms_list else [0]
        role_stats[role] = {
            "avg": round(sum(s)/len(s)), "min": s[0],
            "max": s[-1], "p50": s[len(s)//2],
            "success": len(ms_list), "total": len(users),
        }
    return role_stats


# ── 리포트 ────────────────────────────────────────────────────────────────────

def print_report(quality: dict, latency: dict) -> None:
    apis   = [a for a in ["openai","claude","gemini"] if a in quality]
    labels = {"openai":"OpenAI","claude":"Claude","gemini":"Gemini"}

    print("\n" + "="*80)
    print("  최종 비교 결과 — 품질 / 응답 시간 / 비용")
    print("="*80)

    # ── 품질 ─────────────────────────────────────────────────────────────────
    print(f"\n▶ 품질 비교\n")
    print(f"  {'역할':<18} {'지표':<14} " + "  ".join(f"{labels[a]:>12}" for a in apis))
    print(f"  {'─'*60}")

    rows = [
        ("인텐트 분류",   "Accuracy",       lambda r: f"{r['intent']['accuracy']:.1%}"),
        ("인텐트 분류",   "Macro F1",        lambda r: f"{r['intent']['macro_f1']:.3f}"),
        ("카테고리 추출", "Accuracy",        lambda r: f"{r['category']['accuracy']:.1%}"),
        ("영업시간 파싱", "Schema Valid",    lambda r: f"{r['hours']['schema_validity']:.1%}"),
        ("영업시간 파싱", "Exact Match",     lambda r: f"{r['hours']['exact_match']:.1%}"),
        ("층별 정보 추출","F1",              lambda r: f"{r['extractor']['f1']:.3f}"),
        ("층별 정보 추출","Hallucination",   lambda r: f"{r['extractor']['hallucination_rate']:.1%}"),
    ]
    prev_role = ""
    for role, metric, fn in rows:
        role_col = role if role != prev_role else ""
        prev_role = role
        vals = "  ".join(f"{fn(quality[a]):>12}" for a in apis)
        print(f"  {role_col:<18} {metric:<14} {vals}")

    # ── 응답 시간 ──────────────────────────────────────────────────────────────
    print(f"\n▶ 응답 시간 비교 (ms)\n")
    print(f"  {'역할':<18} " + "  ".join(f"{'['+labels[a]+'] avg':>14}" for a in apis))
    print(f"  {'─'*60}")
    for role in LATENCY_CASES:
        vals = []
        for a in apis:
            st = latency[a].get(role, {})
            avg = st.get("avg", 0)
            ok  = st.get("success", 0)
            tot = st.get("total", 0)
            s   = "✓" if ok == tot else f"({ok}/{tot})"
            vals.append(f"{avg:,}ms {s}")
        print(f"  {role:<18} " + "  ".join(f"{v:>16}" for v in vals))

    # ── 비용 ───────────────────────────────────────────────────────────────────
    print(f"\n▶ 비용 비교 (품질 평가 {len(INTENT_CASES)+len(CAT_CASES)+len(HOURS_CASES)+len(FLOOR_CASES)}개 호출 기준)\n")
    oa_cost = quality.get("openai",{}).get("cost",1) or 1
    for a in apis:
        c = quality[a].get("cost", 0)
        ratio = c / oa_cost
        print(f"  {labels[a]:<10}: ${c:.5f}  ({ratio:.1f}x)")

    print(f"\n[단가]")
    for model, p in PRICE.items():
        print(f"  {model:<22}: ${ p['input']:.3f}/1M 입력  ${p['output']:.2f}/1M 출력")

    print()


# ── 메인 ─────────────────────────────────────────────────────────────────────

async def main() -> None:
    total_q = len(INTENT_CASES)+len(CAT_CASES)+len(HOURS_CASES)+len(FLOOR_CASES)
    total_l = sum(len(v) for v in LATENCY_CASES.values())
    gemini_total = total_q + total_l
    print(f"품질 {total_q}개 + 지연시간 {total_l}개 = Gemini {gemini_total}회 ({gemini_total*GEMINI_GAP//60}분 예상)")
    print(f"OpenAI·Claude는 병렬, Gemini는 {GEMINI_GAP}초 간격\n")

    # 1단계: 품질 평가
    print("── 1단계: 품질 평가 ──")
    q_openai, q_claude = await asyncio.gather(
        run_quality("openai"), run_quality("claude")
    )
    print("  [Gemini] 품질 평가 시작...")
    q_gemini = await run_quality("gemini")

    # 2단계: 응답 시간 평가
    print("\n── 2단계: 응답 시간 평가 ──")
    print("  OpenAI + Claude 병렬...")
    l_openai, l_claude = await asyncio.gather(
        run_latency("openai"), run_latency("claude")
    )
    print("  [Gemini] 응답 시간 평가 시작...")
    l_gemini = await run_latency("gemini")

    quality = {"openai": q_openai, "claude": q_claude, "gemini": q_gemini}
    latency = {"openai": l_openai, "claude": l_claude, "gemini": l_gemini}

    print_report(quality, latency)

    out = pathlib.Path("eval/logs/full_compare.json")
    out.write_text(json.dumps({"quality":quality,"latency":latency},
                               ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"결과 저장: {out}")


if __name__ == "__main__":
    asyncio.run(main())
