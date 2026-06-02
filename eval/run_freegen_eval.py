"""
run_freegen_eval.py
미평가 자유생성 3종(docent / nav_guide / nav speech) LLM-as-Judge 평가.

production 함수/프롬프트를 그대로 호출하고, Judge(gpt-5.2)는 run_speech_eval 재사용.
nav speech는 가장 비싼 호출 → --model 로 저가 모델(qwen-235b 등) 비교 목적.

사용:
    python eval/run_freegen_eval.py                          # 기본 gpt-5.4-mini
    python eval/run_freegen_eval.py --model qwen/qwen3-235b-a22b-2507   # nav speech 저가 비교
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
from eval.run_speech_eval import _judge, _strip_md
from agents.place_insight_agent import llm_generate_docent
from agents.navigation_agent import run_nav_guide_agent, SPEECH_PROMPT


# ── 케이스 ────────────────────────────────────────────────────────────────────

DOCENT_CASES = [
    {"id": "docent_lotte_ko", "language": "ko",
     "context": "건물명: 롯데백화점 본점\n카테고리: 백화점\n층별정보: B1 식품관(롯데마트), 1F 화장품·명품, 6F 식당가\nhalal_info: 6F에 할랄 인증 터키 레스토랑 있음\n영업시간: 10:30-20:00"},
    {"id": "docent_noon_en", "language": "en",
     "context": "Building: NoonSquare\nCategory: shopping mall\nFloors: 1F fashion, 2F cafe, B1 food court\nhalal_info: none\nHours: 11:00-22:00"},
    {"id": "docent_myeongdong_ar", "language": "ar",
     "context": "Building: Myeongdong Cathedral\nCategory: historic site / cathedral\nFloors: main hall\nhalal_info: none\nNote: Catholic cathedral, landmark"},
    {"id": "docent_nodata_ko", "language": "ko",
     "context": "건물명: 명동 상가건물\n카테고리: (미상)\n층별정보: 없음\nhalal_info: 없음"},
]

NAVGUIDE_CASES = [
    {"id": "navg_left_ko", "language": "ko", "message": "여기서 좌회전 맞아?",
     "ctx": {"is_routing": True, "destination_name": "명동역", "direction": "좌회전",
             "current_distance_m": 80, "current_speech": "80m 앞 좌회전",
             "next_direction": "직진", "next_distance_m": 200,
             "remaining_distance_m": 350, "remaining_time_min": 5}},
    {"id": "navg_howfar_ko", "language": "ko", "message": "얼마나 남았어?",
     "ctx": {"is_routing": True, "destination_name": "경복궁", "direction": "직진",
             "current_distance_m": 120, "current_speech": "120m 직진",
             "next_direction": "우회전", "next_distance_m": 300,
             "remaining_distance_m": 600, "remaining_time_min": 8}},
    {"id": "navg_howfar_en", "language": "en", "message": "How much further?",
     "ctx": {"is_routing": True, "destination_name": "Myeongdong Station", "direction": "straight",
             "current_distance_m": 50, "current_speech": "go straight 50m",
             "next_direction": "left", "next_distance_m": 150,
             "remaining_distance_m": 200, "remaining_time_min": 3}},
    {"id": "navg_dir_ar", "language": "ar", "message": "هل أنا في الاتجاه الصحيح؟",
     "ctx": {"is_routing": True, "destination_name": "Gyeongbokgung", "direction": "right",
             "current_distance_m": 90, "current_speech": "turn right in 90m",
             "next_direction": "straight", "next_distance_m": 250,
             "remaining_distance_m": 500, "remaining_time_min": 7}},
]

NAVSPEECH_CASES = [
    {"id": "navspeech_ko", "language": "ko", "destination": "명동역", "total_distance_m": 350, "total_time_min": 5,
     "turn_points": [
        {"index": 0, "pointType": "SP", "turnType": 200, "description": "출발", "nearPoiName": "", "intersectionName": "", "facilityType": "", "segment_distance_m": 0},
        {"index": 1, "pointType": "GP", "turnType": 12, "description": "좌회전", "nearPoiName": "신한은행", "intersectionName": "명동길 교차로", "facilityType": "", "segment_distance_m": 120},
        {"index": 2, "pointType": "GP", "turnType": 125, "description": "육교 건너기", "nearPoiName": "", "intersectionName": "", "facilityType": "125", "segment_distance_m": 80},
        {"index": 3, "pointType": "EP", "turnType": 201, "description": "도착", "nearPoiName": "명동역 1번출구", "intersectionName": "", "facilityType": "", "segment_distance_m": 150}]},
    {"id": "navspeech_en", "language": "en", "destination": "Gyeongbokgung", "total_distance_m": 600, "total_time_min": 8,
     "turn_points": [
        {"index": 0, "pointType": "SP", "turnType": 200, "description": "출발", "nearPoiName": "", "intersectionName": "", "facilityType": "", "segment_distance_m": 0},
        {"index": 1, "pointType": "GP", "turnType": 13, "description": "우회전", "nearPoiName": "교보문고", "intersectionName": "", "facilityType": "", "segment_distance_m": 300},
        {"index": 2, "pointType": "EP", "turnType": 201, "description": "도착", "nearPoiName": "경복궁 정문", "intersectionName": "", "facilityType": "", "segment_distance_m": 300}]},
    {"id": "navspeech_ar", "language": "ar", "destination": "Myeongdong Station", "total_distance_m": 200, "total_time_min": 3,
     "turn_points": [
        {"index": 0, "pointType": "SP", "turnType": 200, "description": "출발", "nearPoiName": "", "intersectionName": "", "facilityType": "", "segment_distance_m": 0},
        {"index": 1, "pointType": "GP", "turnType": 127, "description": "계단 이용", "nearPoiName": "", "intersectionName": "", "facilityType": "127", "segment_distance_m": 50},
        {"index": 2, "pointType": "EP", "turnType": 201, "description": "도착", "nearPoiName": "", "intersectionName": "", "facilityType": "", "segment_distance_m": 150}]},
]


# ── 응답 수집 ─────────────────────────────────────────────────────────────────

async def _docent(case, model):
    speech = await llm_generate_docent(case["context"], case["language"], model=model)
    return case["context"], speech

async def _navguide(case, model):
    res = await run_nav_guide_agent(case["message"], case["ctx"], case["language"], model=model)
    ctx = f"질문: {case['message']}\n경로상태: {json.dumps(case['ctx'], ensure_ascii=False)}"
    return ctx, res.get("speech", "")

async def _navspeech(case, model):
    content = await call_llm(
        user_id="", purpose="nav_speech", model=model, record=False, temperature=0,
        messages=[{"role": "user", "content": SPEECH_PROMPT.format(
            language=case["language"],
            turn_points_json=json.dumps(case["turn_points"], ensure_ascii=False, indent=2),
            destination_name=case["destination"],
            total_distance_m=case["total_distance_m"],
            total_time_min=case["total_time_min"])}],
    )
    try:
        arr = json.loads(re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip()))
        speech = " ".join(arr) if isinstance(arr, list) else str(arr)
    except Exception:
        speech = content
    ctx = f"목적지 {case['destination']}, {case['total_distance_m']}m / turn_points: " + \
          json.dumps([t["description"] for t in case["turn_points"]], ensure_ascii=False)
    return ctx, speech


GROUPS = [("docent", DOCENT_CASES, _docent),
          ("nav_guide", NAVGUIDE_CASES, _navguide),
          ("nav_speech", NAVSPEECH_CASES, _navspeech)]


async def run(model: str, judge_model: str) -> None:
    print(f"후보 모델: {model}  |  Judge: {judge_model}\n")
    overall = {}
    for name, cases, gen in GROUPS:
        scores = []
        for c in cases:
            try:
                ctx, speech = await gen(c, model)
                sc = await _judge(ctx, _strip_md(speech), c["language"], judge_model)
                vals = [sc.get(k, 0) for k in
                        ["groundedness", "completeness", "language_match", "tts_naturalness", "conciseness"]]
                ov = sum(vals) / len(vals)
                scores.append(ov)
            except Exception as e:
                print(f"  ERR [{c['id']}]: {e}")
        if scores:
            avg = sum(scores) / len(scores)
            overall[name] = avg
            print(f"  {name:<12} Overall {avg:.2f}  (n={len(scores)})")
    if overall:
        print(f"\n  전체 평균: {sum(overall.values())/len(overall):.2f}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model", default="gpt-5.4-mini")
    p.add_argument("--judge-model", default="gpt-5.2")
    a = p.parse_args()
    asyncio.run(run(a.model, a.judge_model))


if __name__ == "__main__":
    main()
