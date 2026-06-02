"""
tools/open_hours_normalizer.py
open_hours + closed_days 자유 텍스트 → LLM 정규화 → store_hours 테이블 저장.

LLM 출력 스키마:
    {
        "weekly": {
            "mon": [["11:00", "15:00"], ["16:00", "20:00"]],  # 브레이크 → 2구간
            "tue": [["11:00", "20:00"]],
            "fri": [["17:00", "26:00"]],                       # 자정 넘김 → 24+ (26:00)
            "sun": []                                           # 빈 배열 = 휴무
        },
        "last_order": {                                         # 구간별 라스트오더(없으면 [])
            "mon": ["14:00", "19:00"],
            "fri": ["25:00"]                                    # 익일 01:00 → 25:00
        },
        "always_open": false,   # 24/7이면 true
        "holiday_note": "명절 당일 휴무"  # 비정기 휴무 안내 (없으면 "")
    }

store_hours 테이블 저장 규칙 (하루 1 row, 컬럼 전부 TEXT):
    - 단일 구간: open_time="11:00", close_time="20:00"
    - 브레이크 2구간: open_time="11:00-15:00", close_time="16:00-20:00"  (사이 갭=브레이크)
    - 자정 넘김: close_time="26:00" 처럼 24+ 텍스트
    - last_order: 구간별 콤마 나열 "14:00, 19:00" (없으면 NULL)
    - 휴무일(빈 배열): row 없음
    - always_open: 모든 요일 open="00:00", close="24:00"
"""
from __future__ import annotations

import json
import re
from typing import Optional

from dotenv import load_dotenv

from tools.llm_client import call_llm

load_dotenv()

_DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
_DAY_INDEX = {d: i for i, d in enumerate(_DAYS)}

# 메모리 캐시 — 같은 (open_hours, closed_days) 조합은 한 번만 LLM 호출
_CACHE: dict[str, dict] = {}


def _encode_open_close(intervals: list) -> tuple[str, str]:
    """영업 구간 리스트 → (open_time, close_time) 텍스트.

    단일 구간:    ("11:00", "20:00")
    2구간 이상:   첫 구간 → open_time, 마지막 구간 → close_time, 각각 "시작-끝".
                 (브레이크는 두 구간 사이 갭으로 표현. 3구간 이상이면 중간은 버림)
    """
    if len(intervals) == 1:
        return intervals[0][0], intervals[0][1]
    first, last = intervals[0], intervals[-1]
    return f"{first[0]}-{first[1]}", f"{last[0]}-{last[1]}"


_NORMALIZE_SYSTEM = """\
당신은 영업시간 텍스트를 구조화된 JSON 스케줄로 변환합니다.

입력: open_hours(영업시간)와 closed_days(휴무일) 두 텍스트가 함께 주어집니다.

규칙:
1. 입력 텍스트에 명시된 내용만 사용. 학습된 지식 사용 금지.
2. 시간은 항상 24시간 "HH:MM" 형식. (예: "오후 10시" → "22:00", "오전 9시반" → "09:30")
3. 휴게시간/브레이크가 있으면 같은 요일을 두 구간으로 분리하고 브레이크 시간대 자체는 제외.
   예: "11:00 - 18:30, 14:00 - 17:00 브레이크타임" (영업 11:00~18:30, 브레이크 14:00~17:00)
       → [["11:00","14:00"], ["17:00","18:30"]]  (브레이크 14:00~17:00은 두 구간 사이 갭)
4. 휴무일은 빈 배열 []. closed_days에 명시된 요일도 빈 배열로 처리.
5. "매일"·"연중무휴"는 모든 요일에 동일 적용.
6. 24시간 영업 매장은 always_open=true 로 표시하고 weekly 는 빈 객체.
7. 정보가 불명확하면 그 요일은 누락하지 말고 빈 배열로 둠.
8. 모호한 다음날 자정 넘김(예: 마감 02:00)은 종료시간을 "26:00" 처럼 24+ 로 표기 가능.
9. holiday_note: 명절·공휴일·선거일 등 비정기 휴무 안내 텍스트만 추출. 없으면 빈 문자열.
   (예: "석가탄신일·추석·설날 당일 휴무", "공휴일 휴무")
   정기 요일 휴무(예: "매주 일요일 휴무")는 holiday_note에 넣지 말고 weekly에 반영.
10. last_order: 입력에 "라스트오더"/"주문마감"/"L.O"로 **명시된** 시각만 추출.
    - 명시 없으면 반드시 빈 배열 []. 영업 마감시간을 라스트오더로 대체/추정 금지(절대 지어내지 말 것).
    - weekly 구간 순서대로 배열. 라스트오더 시각은 weekly 영업구간에 포함하지 말 것.
    - 자정을 넘긴 시각은 24+로 표기. 예: 영업이 17:00~다음날 02:00(=26:00)이고
      "01:00 라스트오더"면, 그 01:00은 다음날이므로 25:00으로 적는다.

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
  "last_order": {
    "mon": ["21:30"], "tue": ["21:30"], "wed": ["21:30"],
    "thu": ["21:30"], "fri": ["21:30"], "sat": ["21:30"], "sun": []
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

    oh = (open_hours or "").strip()  # 라스트오더 키워드 가드용

    # ── store_hours 저장 (하루 1 row, 컬럼 전부 TEXT) ─────────────────────────
    await conn.execute("DELETE FROM store_hours WHERE store_id = $1", store_id)

    rows: list[tuple] = []
    if schedule.get("always_open"):
        for dow in range(7):
            rows.append((store_id, dow, "00:00", "24:00", None))
    else:
        weekly = schedule.get("weekly", {})
        # LLM이 라스트오더를 지어내는 것 방지 — raw에 LO 키워드가 있을 때만 채택.
        _has_lo = any(k in oh.lower() for k in
                      ("라스트오더", "라스트 오더", "주문마감", "주문 마감", "l.o", "lo "))
        lo_map = (schedule.get("last_order", {}) or {}) if _has_lo else {}
        for day_name, dow in _DAY_INDEX.items():
            intervals = [iv for iv in (weekly.get(day_name) or [])
                         if isinstance(iv, list) and len(iv) == 2]
            if not intervals:
                continue  # 휴무 → row 없음
            open_t, close_t = _encode_open_close(intervals)
            los = [str(x).strip() for x in (lo_map.get(day_name) or []) if str(x).strip()]
            last_order = ", ".join(los) or None
            rows.append((store_id, dow, open_t, close_t, last_order))

    if rows:
        await conn.executemany(
            "INSERT INTO store_hours (store_id, day_of_week, open_time, close_time, last_order) "
            "VALUES ($1, $2, $3, $4, $5) "
            "ON CONFLICT (store_id, day_of_week) DO UPDATE SET "
            "open_time = EXCLUDED.open_time, close_time = EXCLUDED.close_time, "
            "last_order = EXCLUDED.last_order",
            rows,
        )

    return schedule.get("holiday_note") or None
