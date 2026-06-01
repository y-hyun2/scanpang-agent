"""
run_speech_eval.py
TTS 음성 응답 품질 평가 — LLM-as-Judge.

평가 대상: search_agent, halal_agent, place_chat_agent
Judge 모델: gpt-4o (피평가 모델과 동일하나 별도 채점 프롬프트 사용)

사용:
    python eval/run_speech_eval.py
    python eval/run_speech_eval.py --verbose
"""

import argparse
import asyncio
import json
import os
import pathlib
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from openai import AsyncOpenAI
_client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))


# ── 테스트 케이스 정의 ────────────────────────────────────────────────────────

SEARCH_CASES = [
    {
        "id": "search_cafe_ko",
        "component": "search_agent",
        "category": "cafe",
        "language": "ko",
        "facilities": [
            {"name": "스타벅스 명동점", "distance_m": 120, "address": "서울 중구 명동2가",
             "open_hours": "07:00-22:00", "extra": {}},
            {"name": "투썸플레이스", "distance_m": 250, "address": "서울 중구 명동",
             "open_hours": "09:00-22:00", "extra": {}},
        ],
    },
    {
        "id": "search_pharmacy_en",
        "component": "search_agent",
        "category": "pharmacy",
        "language": "en",
        "facilities": [
            {"name": "명동 온누리약국", "distance_m": 80, "address": "서울 중구 명동",
             "open_hours": "09:00-21:00", "extra": {}},
        ],
    },
    {
        "id": "search_restroom_ar",
        "component": "search_agent",
        "category": "restroom",
        "language": "ar",
        "facilities": [
            {"name": "명동 공중화장실", "distance_m": 50, "address": "서울 중구 명동",
             "open_hours": "24시간", "extra": {"wheelchair": True}},
        ],
    },
    {
        "id": "search_halal_en",
        "component": "search_agent",
        "category": "halal_restaurant",
        "language": "en",
        "facilities": [
            {"name": "이슬람 식당 명동", "distance_m": 180, "address": "서울 중구",
             "open_hours": "11:00-21:00",
             "extra": {"halal_type": "certified", "cuisine_type": ["Korean", "Middle Eastern"]}},
        ],
    },
    {
        "id": "search_atm_ko",
        "component": "search_agent",
        "category": "atm",
        "language": "ko",
        "facilities": [
            {"name": "신한은행 ATM", "distance_m": 200, "address": "서울 중구 명동",
             "open_hours": "24시간", "extra": {}},
        ],
    },
    {
        "id": "search_empty_ko",
        "component": "search_agent",
        "category": "locker",
        "language": "ko",
        "facilities": [],
    },
]

HALAL_CASES = [
    {
        "id": "halal_prayer_ko",
        "component": "halal_agent",
        "category": "prayer_time",
        "language": "ko",
        "data": {
            "fajr": "04:12", "dhuhr": "12:15", "asr": "15:48",
            "maghrib": "19:32", "isha": "21:05",
            "hijri_date": "1446-11-20",
        },
        "current_time_hint": "14:30",  # 현재시각 힌트 (asr가 다음 기도)
    },
    {
        "id": "halal_prayer_en",
        "component": "halal_agent",
        "category": "prayer_time",
        "language": "en",
        "data": {
            "fajr": "04:12", "dhuhr": "12:15", "asr": "15:48",
            "maghrib": "19:32", "isha": "21:05",
            "hijri_date": "1446-11-20",
        },
        "current_time_hint": "14:30",
    },
    {
        "id": "halal_prayer_ar",
        "component": "halal_agent",
        "category": "prayer_time",
        "language": "ar",
        "data": {
            "fajr": "04:12", "dhuhr": "12:15", "asr": "15:48",
            "maghrib": "19:32", "isha": "21:05",
            "hijri_date": "1446-11-20",
        },
        "current_time_hint": "14:30",
    },
    {
        "id": "halal_qibla_ko",
        "component": "halal_agent",
        "category": "qibla",
        "language": "ko",
        "data": {"direction": 295.3},
    },
    {
        "id": "halal_qibla_en",
        "component": "halal_agent",
        "category": "qibla",
        "language": "en",
        "data": {"direction": 295.3},
    },
    {
        "id": "halal_qibla_ar",
        "component": "halal_agent",
        "category": "qibla",
        "language": "ar",
        "data": {"direction": 295.3},
    },
]

PLACE_CASES = [
    {
        "id": "place_cathedral_ko",
        "component": "place_chat",
        "message": "명동성당 역사 알려줘",
        "language": "ko",
        "lat": 37.5632, "lng": 126.9875,
    },
    {
        "id": "place_namsan_en",
        "component": "place_chat",
        "message": "Tell me about Namsan Tower",
        "language": "en",
        "lat": 37.5512, "lng": 126.9882,
    },
    {
        "id": "place_gyeongbok_ko",
        "component": "place_chat",
        "message": "경복궁 입장료 얼마야?",
        "language": "ko",
        "lat": 37.5796, "lng": 126.9770,
    },
    {
        "id": "place_bukchon_en",
        "component": "place_chat",
        "message": "What makes Bukchon Hanok Village special?",
        "language": "en",
        "lat": 37.5826, "lng": 126.9830,
    },
    {
        "id": "place_ddp_ko",
        "component": "place_chat",
        "message": "동대문 디자인 플라자가 뭐야?",
        "language": "ko",
        "lat": 37.5670, "lng": 127.0096,
    },
    {
        "id": "place_insadong_ar",
        "component": "place_chat",
        "message": "ما هي منطقة إنسادونج؟",
        "language": "ar",
        "lat": 37.5743, "lng": 126.9853,
    },
]

ALL_CASES = SEARCH_CASES + HALAL_CASES + PLACE_CASES


# ── 응답 수집 ─────────────────────────────────────────────────────────────────

async def _get_search_speech(case: dict) -> tuple[str, str]:
    """search_agent._generate_speech 직접 호출. (context, speech) 반환."""
    from agents.search_agent import _generate_speech
    facilities = case["facilities"]
    category   = case["category"]
    language   = case["language"]

    context_lines = [f"Category: {category}", f"Language: {language}"]
    if facilities:
        f = facilities[0]
        context_lines += [
            f"Nearest: {f['name']}",
            f"Distance: {f['distance_m']}m",
            f"Open hours: {f['open_hours']}",
            f"Extra: {f['extra']}",
            f"Total found: {len(facilities)}",
        ]
    else:
        context_lines.append("No facilities found.")

    speech = await _generate_speech(facilities, category, language)
    return "\n".join(context_lines), speech


async def _get_halal_speech(case: dict) -> tuple[str, str]:
    """halal_agent._generate_speech 직접 호출. (context, speech) 반환."""
    from agents.halal_agent import _generate_speech
    data     = case["data"]
    category = case["category"]
    language = case["language"]

    if category == "prayer_time":
        hint = case.get("current_time_hint", "14:30")
        context = (
            f"Current time (KST): {hint}\n"
            f"Fajr: {data['fajr']}, Dhuhr: {data['dhuhr']}, Asr: {data['asr']}, "
            f"Maghrib: {data['maghrib']}, Isha: {data['isha']}\n"
            f"Hijri: {data['hijri_date']}\nLanguage: {language}"
        )
    else:
        direction = data["direction"]
        context = f"Qibla direction: {direction:.1f}° (Northwest)\nLanguage: {language}"

    speech = await _generate_speech(data, category, language)
    return context, speech


async def _get_place_speech(case: dict) -> tuple[str, str]:
    """place_chat_agent 직접 호출. (context, speech) 반환."""
    from agents.place_insight_agent import run_place_chat_agent
    context = f"User question: {case['message']}\nLanguage: {case['language']}\nLocation: lat={case['lat']}, lng={case['lng']}"
    result  = await run_place_chat_agent(
        message=case["message"],
        lat=case["lat"],
        lng=case["lng"],
        language=case["language"],
    )
    return context, result.get("speech", "")


# ── Judge ─────────────────────────────────────────────────────────────────────

_JUDGE_SYSTEM = """\
You are an objective evaluator for a Korean AR tourism app's text-to-speech responses.
Score the assistant response on 5 criteria (1-5 scale each).

Criteria:
1. groundedness (1-5): Response stays within the given context data. No invented facts.
   5=only uses given data, 1=multiple invented facts
2. completeness (1-5): Key information (name, distance, hours, direction, etc.) is included.
   5=all key info present, 1=most key info missing
3. language_match (1-5): Response language matches the requested language.
   5=perfect match, 1=wrong language entirely
4. tts_naturalness (1-5): Suitable for text-to-speech. No markdown, bullet points, URLs.
   Natural spoken style, 1-3 sentences.
   5=perfect TTS style, 1=contains markdown/lists/unnatural text
5. conciseness (1-5): 1-3 sentences. Not too long, not too short.
   5=ideal length, 1=way too long or too short

Output JSON only:
{"groundedness": N, "completeness": N, "language_match": N, "tts_naturalness": N, "conciseness": N, "note": "one line"}
"""


async def _judge(context: str, speech: str, language: str) -> dict:
    user_content = f"[CONTEXT GIVEN TO SYSTEM]\n{context}\n\n[SYSTEM RESPONSE]\n{speech}\n\n[REQUESTED LANGUAGE] {language}"
    try:
        resp = await _client.chat.completions.create(
            model="gpt-4o",
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": _JUDGE_SYSTEM},
                {"role": "user",   "content": user_content},
            ],
            temperature=0,
            max_tokens=200,
        )
        return json.loads(resp.choices[0].message.content)
    except Exception as e:
        print(f"    [judge 오류] {e}")
        return {"groundedness": 0, "completeness": 0, "language_match": 0,
                "tts_naturalness": 0, "conciseness": 0, "note": f"ERROR: {e}"}


# ── 자동 포맷 검사 ─────────────────────────────────────────────────────────────

def _format_check(speech: str, language: str) -> dict:
    sentences = len([s for s in re.split(r'[.!?。！？\n]', speech) if s.strip()])
    return {
        "sentence_count": sentences,
        "in_range":       1 <= sentences <= 4,
        "no_markdown":    not bool(re.search(r"[*#\[\]`]|https?://", speech)),
        "char_count":     len(speech),
    }


# ── 리포트 ─────────────────────────────────────────────────────────────────────

def print_report(results: list[dict], verbose: bool) -> None:
    CRITERIA = ["groundedness", "completeness", "language_match", "tts_naturalness", "conciseness"]

    print("\n" + "=" * 60)
    print("  TTS SPEECH QUALITY EVALUATION REPORT (LLM-as-Judge)")
    print("=" * 60)

    # 전체 평균
    scored = [r for r in results if r.get("scores")]
    if scored:
        print(f"\n[전체 평균 점수 (1-5)]  n={len(scored)}")
        print(f"  {'기준':<20} {'평균':>6} {'min':>5} {'max':>5}")
        print(f"  {'-'*40}")
        for c in CRITERIA:
            vals = [r["scores"][c] for r in scored if r["scores"].get(c, 0) > 0]
            if vals:
                print(f"  {c:<20} {sum(vals)/len(vals):>6.2f} {min(vals):>5} {max(vals):>5}")
        overall = [sum(r["scores"][c] for c in CRITERIA) / len(CRITERIA)
                   for r in scored if r.get("scores")]
        print(f"  {'Overall avg':<20} {sum(overall)/len(overall):>6.2f}")

    # 컴포넌트별 평균
    from collections import defaultdict
    comp_scores: dict[str, list[float]] = defaultdict(list)
    for r in scored:
        avg = sum(r["scores"][c] for c in CRITERIA) / len(CRITERIA)
        comp_scores[r["component"]].append(avg)

    print(f"\n[컴포넌트별 Overall 평균]")
    for comp, vals in comp_scores.items():
        print(f"  {comp:<25} {sum(vals)/len(vals):.2f}  (n={len(vals)})")

    # 포맷 자동 검사
    fmt_ok = sum(1 for r in results if r.get("format", {}).get("in_range") and
                 r.get("format", {}).get("no_markdown"))
    print(f"\n[자동 포맷 검사]")
    print(f"  문장수 1-4 + 마크다운 없음: {fmt_ok}/{len(results)} ({fmt_ok/len(results):.1%})")

    if verbose:
        print(f"\n[케이스별 상세]")
        for r in results:
            sc = r.get("scores", {})
            avg = sum(sc.get(c, 0) for c in CRITERIA) / len(CRITERIA) if sc else 0
            fmt = r.get("format", {})
            print(f"\n  [{r['id']}] component={r['component']} lang={r['language']}")
            print(f"  응답: {r.get('speech', '')[:80]}{'...' if len(r.get('speech',''))>80 else ''}")
            print(f"  점수: G={sc.get('groundedness')} C={sc.get('completeness')} "
                  f"L={sc.get('language_match')} T={sc.get('tts_naturalness')} "
                  f"Cn={sc.get('conciseness')} → avg={avg:.1f}")
            print(f"  포맷: 문장수={fmt.get('sentence_count')} "
                  f"마크다운없음={fmt.get('no_markdown')} 글자수={fmt.get('char_count')}")
            if sc.get("note"):
                print(f"  Judge 메모: {sc['note']}")

    print()


# ── 메인 ──────────────────────────────────────────────────────────────────────

async def run_eval(verbose: bool) -> None:
    print(f"평가 대상: {len(ALL_CASES)}개 케이스")
    print("응답 수집 + Judge 채점 실행 중...")

    results = []
    for i, case in enumerate(ALL_CASES, 1):
        cid  = case["id"]
        comp = case["component"]
        lang = case["language"]
        print(f"  [{i:2d}/{len(ALL_CASES)}] {cid}", end="\r")

        try:
            if comp == "search_agent":
                context, speech = await _get_search_speech(case)
            elif comp == "halal_agent":
                context, speech = await _get_halal_speech(case)
            else:
                context, speech = await _get_place_speech(case)
        except Exception as e:
            print(f"\n  ERROR collecting [{cid}]: {e}")
            results.append({**case, "speech": "", "context": "", "scores": None, "format": {}})
            continue

        scores = await _judge(context, speech, lang)
        fmt    = _format_check(speech, lang)

        results.append({
            **case,
            "speech":  speech,
            "context": context,
            "scores":  scores,
            "format":  fmt,
        })

    print(f"  완료 {len(results)}개                    ")
    print_report(results, verbose)

    out_path = pathlib.Path("eval/logs/speech_eval_result.json")
    out_path.parent.mkdir(exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"결과 저장: {out_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true", help="케이스별 상세 출력")
    args = parser.parse_args()
    asyncio.run(run_eval(args.verbose))


if __name__ == "__main__":
    main()
