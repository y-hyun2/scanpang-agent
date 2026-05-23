"""
tools/open_hours_normalizer.py
store_details.open_hours 자유 텍스트 → 구조화 weekly schedule.

매장 1건당 1회 LLM 호출. 결과는 details JSONB 안 `schedule` 키에 저장되어,
이후 검색·상세 응답 시 [open_hours_parser.is_open_now_struct] 가
LLM 없이 즉시 영업중 여부를 판정한다.

스키마:
    {
        "weekly": {
            "mon": [["09:00", "22:00"]],          # 여러 구간 — 점심 휴게 등
            "tue": [["09:00", "13:00"], ["14:00", "22:00"]],
            ...
            "sun": []                              # 빈 배열 = 휴무
        },
        "always_open": false,                      # 24/7 매장이면 true (weekly 무시)
        "raw": "원문 그대로"                       # 디버깅용
    }
"""
from __future__ import annotations

import json
import os
import re
from typing import Optional

from openai import AsyncOpenAI
from dotenv import load_dotenv

load_dotenv()
_client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))

_DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]

# 메모리 캐시 — 같은 open_hours 문자열은 한 번만 LLM 호출.
# 프로세스 수명 동안만 유지(seed 스크립트는 영구 캐시 불필요).
_CACHE: dict[str, dict] = {}


_NORMALIZE_SYSTEM = """\
당신은 영업시간 텍스트를 구조화된 JSON 스케줄로 변환합니다.

규칙:
1. 입력 텍스트에 명시된 내용만 사용. 학습된 지식 사용 금지.
2. 시간은 항상 24시간 "HH:MM" 형식. (예: "오후 10시" → "22:00", "오전 9시반" → "09:30")
3. 휴게시간/브레이크가 있으면 같은 요일에 두 구간으로 분리. (예: [["11:30","15:00"], ["17:00","22:00"]])
4. 휴무일은 빈 배열 []. (예: 일요일 휴무면 "sun": [])
5. "매일"·"연중무휴"는 모든 요일에 동일 적용.
6. 24시간 영업 매장은 always_open=true 로 표시하고 weekly 는 빈 객체.
7. 정보가 불명확하면 그 요일은 누락하지 말고 빈 배열로 둠.
8. 모호한 다음날 자정 넘김(예: 마감 02:00)은 종료시간을 "26:00" 처럼 24+ 로 표기 가능.

출력 형식 (JSON only, 다른 설명 없이):
{
  "weekly": {
    "mon": [["09:00","22:00"]],
    "tue": [["09:00","22:00"]],
    "wed": [["09:00","22:00"]],
    "thu": [["09:00","22:00"]],
    "fri": [["09:00","22:00"]],
    "sat": [["09:00","22:00"]],
    "sun": []
  },
  "always_open": false
}
"""


_TIME_FMT_RE = re.compile(r"^\d{1,2}:\d{2}$")


def _looks_valid(schedule: dict) -> bool:
    """LLM 출력 형태가 우리 스키마를 따르는지 가벼운 검증."""
    if not isinstance(schedule, dict):
        return False
    if schedule.get("always_open") is True:
        return True
    weekly = schedule.get("weekly")
    if not isinstance(weekly, dict):
        return False
    for d in _DAYS:
        ranges = weekly.get(d, [])
        if not isinstance(ranges, list):
            return False
        for r in ranges:
            if not (isinstance(r, list) and len(r) == 2):
                return False
            if not all(isinstance(t, str) and _TIME_FMT_RE.match(t) for t in r):
                return False
    return True


async def normalize_open_hours(open_hours: str) -> Optional[dict]:
    """
    자유 텍스트 → schedule dict 또는 None.
    None 반환 조건:
        - 빈 입력
        - OPENAI_API_KEY 없음
        - LLM 응답 파싱 실패
    """
    text = (open_hours or "").strip()
    if not text:
        return None
    if text in _CACHE:
        return _CACHE[text]
    if not os.getenv("OPENAI_API_KEY"):
        return None

    try:
        resp = await _client.chat.completions.create(
            model="gpt-4o-mini",
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": _NORMALIZE_SYSTEM},
                {"role": "user", "content": text},
            ],
            temperature=0,
            max_tokens=400,
        )
        content = resp.choices[0].message.content or "{}"
        parsed = json.loads(content)
    except Exception as e:
        print(f"[open_hours_normalizer] LLM 실패: {type(e).__name__}: {e}")
        return None

    if not _looks_valid(parsed):
        print(f"[open_hours_normalizer] 스키마 검증 실패 — 원문: {text[:60]!r}")
        return None

    # weekly 누락 요일 채우기 + raw 보존
    if not parsed.get("always_open"):
        weekly = parsed.get("weekly", {})
        for d in _DAYS:
            weekly.setdefault(d, [])
        parsed["weekly"] = weekly
    else:
        parsed["weekly"] = {}
    parsed["raw"] = text

    _CACHE[text] = parsed
    return parsed
