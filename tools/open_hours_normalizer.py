"""
tools/open_hours_normalizer.py
open_hours + closed_days 자유 텍스트 → LLM 정규화 → store_hours 테이블 저장.

LLM 출력 스키마:
    {
        "weekly": {
            "mon": [["09:00", "22:00"]],
            "tue": [["09:00", "13:00"], ["14:00", "22:00"]],  # 브레이크타임
            "sun": []                                           # 빈 배열 = 휴무
        },
        "always_open": false,   # 24/7이면 true
        "holiday_note": "명절 당일 휴무"  # 비정기 휴무 안내 (없으면 "")
    }

store_hours 테이블 저장 규칙:
    - 요일별 구간마다 1 row (store_id, day_of_week, open_time, close_time)
    - 휴무일(빈 배열)은 row 없음
    - always_open은 모든 요일에 00:00-24:00 으로 저장
"""
from __future__ import annotations

import json
import re
from datetime import time as dt_time
from typing import Optional

from dotenv import load_dotenv

from tools.llm_client import call_llm

load_dotenv()

_DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
_DAY_INDEX = {d: i for i, d in enumerate(_DAYS)}

# 메모리 캐시 — 같은 (open_hours, closed_days) 조합은 한 번만 LLM 호출
_CACHE: dict[str, dict] = {}


_NORMALIZE_SYSTEM = """\
당신은 영업시간 텍스트를 구조화된 JSON 스케줄로 변환합니다.

입력: open_hours(영업시간)와 closed_days(휴무일) 두 텍스트가 함께 주어집니다.

규칙:
1. 입력 텍스트에 명시된 내용만 사용. 학습된 지식 사용 금지.
2. 시간은 항상 24시간 "HH:MM" 형식. (예: "오후 10시" → "22:00", "오전 9시반" → "09:30")
3. 휴게시간/브레이크가 있으면 같은 요일에 두 구간으로 분리. (예: [["11:30","15:00"], ["17:00","22:00"]])
4. 휴무일은 빈 배열 []. closed_days에 명시된 요일도 빈 배열로 처리.
5. "매일"·"연중무휴"는 모든 요일에 동일 적용.
6. 24시간 영업 매장은 always_open=true 로 표시하고 weekly 는 빈 객체.
7. 정보가 불명확하면 그 요일은 누락하지 말고 빈 배열로 둠.
8. 모호한 다음날 자정 넘김(예: 마감 02:00)은 종료시간을 "26:00" 처럼 24+ 로 표기 가능.
9. holiday_note: 명절·공휴일·선거일 등 비정기 휴무 안내 텍스트만 추출. 없으면 빈 문자열.
   (예: "석가탄신일·추석·설날 당일 휴무", "공휴일 휴무")
   정기 요일 휴무(예: "매주 일요일 휴무")는 holiday_note에 넣지 말고 weekly에 반영.

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
  "always_open": false,
  "holiday_note": ""
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


async def normalize_open_hours(
    open_hours: str,
    closed_days: str = "",
    model: str = "qwen/qwen3-235b-a22b-2507",  # 크로스벤더 평가: Exact Match 100%(>4o-mini 96.2%) + 더 쌈. OpenRouter 경유
) -> Optional[dict]:
    """자유 텍스트(open_hours[+closed_days]) → schedule dict 또는 None. (DB 저장 없음)

    반환: {"weekly": {...}, "always_open": bool, "holiday_note": str}
    DB 적재는 normalize_and_save 가, 평가는 이 함수를 직접 사용한다.
    model 인자로 모델 교체/비교 가능.
    """
    oh = (open_hours or "").strip()
    cd = (closed_days or "").strip()
    if not oh and not cd:
        return None

    cache_key = f"{model}||{oh}||{cd}"
    if cache_key in _CACHE:
        return _CACHE[cache_key]

    user_content = f"open_hours: {oh}\nclosed_days: {cd}" if cd else f"open_hours: {oh}"
    try:
        content = await call_llm(
            user_id="",
            purpose="open_hours_normalize",
            messages=[
                {"role": "system", "content": _NORMALIZE_SYSTEM},
                {"role": "user",   "content": user_content},
            ],
            model=model,
            record=False,
            response_format={"type": "json_object"},
            temperature=0,
            max_tokens=500,
        )
        parsed = json.loads(content or "{}")
    except Exception as e:
        print(f"[open_hours_normalizer] LLM 실패: {type(e).__name__}: {e}")
        return None

    if not _looks_valid(parsed):
        print(f"[open_hours_normalizer] 스키마 검증 실패: {user_content[:60]!r}")
        return None

    if not parsed.get("always_open"):
        weekly = parsed.get("weekly", {})
        for d in _DAYS:
            weekly.setdefault(d, [])
        parsed["weekly"] = weekly
    else:
        parsed["weekly"] = {}

    _CACHE[cache_key] = parsed
    return parsed


async def normalize_and_save(
    store_id: str,
    open_hours: str,
    closed_days: str,
    conn,
) -> Optional[str]:
    """
    open_hours + closed_days → LLM 정규화 → store_hours 테이블 저장.

    Args:
        store_id:    storedetails.id
        open_hours:  영업시간 원본 텍스트
        closed_days: 휴무일 원본 텍스트
        conn:        asyncpg 커넥션

    Returns:
        holiday_note 문자열 (storedetails.holiday_note 에 저장용). 실패 시 None.
    """
    schedule = await normalize_open_hours(open_hours, closed_days)
    if schedule is None:
        return None

    # ── store_hours 저장 ──────────────────────────────────────────────────────
    await conn.execute("DELETE FROM store_hours WHERE store_id = $1", store_id)

    rows = []
    if schedule.get("always_open"):
        for dow in range(7):
            rows.append((store_id, dow, "00:00", "24:00"))
    else:
        for day_name, ranges in schedule.get("weekly", {}).items():
            dow = _DAY_INDEX.get(day_name)
            if dow is None:
                continue
            for r in ranges:
                if len(r) == 2:
                    rows.append((store_id, dow, r[0], r[1]))

    if rows:
        def _t(s: str) -> dt_time:
            h, m = map(int, s.split(":"))
            return dt_time(h % 24, m)

        time_rows = [(sid, dow, _t(ot), _t(ct)) for sid, dow, ot, ct in rows]
        await conn.executemany(
            "INSERT INTO store_hours (store_id, day_of_week, open_time, close_time) "
            "VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING",
            time_rows,
        )

    return schedule.get("holiday_note") or None
