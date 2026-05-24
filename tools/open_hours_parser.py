"""
tools/open_hours_parser.py
영업중 여부 판정 — 두 단계로 동작:

1. [is_open_now_struct]: store_details.details.schedule 의 구조화 데이터를 사용.
   open_hours_normalizer 가 미리 LLM 으로 정규화해 둔 weekly schedule 이 있을 때
   쓰이며 가장 정확. LLM 호출 없음.

2. [is_open_now]: 자유 텍스트 open_hours 만 있을 때의 휴리스틱 fallback.
   정규식 + 요일 키워드 매칭. 모호한 경우 None 반환.

응답 엔드포인트는 둘을 결합해 `is_open_now_combined(open_hours, schedule)` 형태로
호출하면 schedule 우선 + fallback 텍스트 순으로 평가한다.
"""
from __future__ import annotations

import re
from datetime import datetime, time, timezone, timedelta
from typing import Optional

KST = timezone(timedelta(hours=9))

_DAYS_EN = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]


def _parse_hhmm(s: str) -> Optional[int]:
    """'HH:MM' → 0~24*60+ 분. 26:00 같은 24+ 도 허용."""
    m = re.match(r"^(\d{1,2}):(\d{2})$", s.strip())
    if not m:
        return None
    h, mn = int(m.group(1)), int(m.group(2))
    return h * 60 + mn


def is_open_now_struct(schedule: dict, now: Optional[datetime] = None) -> Optional[bool]:
    """
    구조화 schedule(open_hours_normalizer 결과) 기반 영업중 판정.

    schedule 형식: {"always_open": bool, "weekly": {"mon": [[s,e],...], ...}}
    반환: True / False / None(스키마 이상 등 판정 불가)
    """
    if not isinstance(schedule, dict):
        return None
    if schedule.get("always_open") is True:
        return True

    weekly = schedule.get("weekly")
    if not isinstance(weekly, dict):
        return None

    if now is None:
        now = datetime.now(KST)
    elif now.tzinfo is None:
        now = now.replace(tzinfo=KST)

    today_key = _DAYS_EN[now.weekday()]
    today_ranges = weekly.get(today_key, [])
    if not isinstance(today_ranges, list):
        return None

    if not today_ranges:
        # 빈 배열 = 명시적 휴무
        return False

    now_min = now.hour * 60 + now.minute
    # 자정 넘김은 전날 구간 종료가 24+ 분이라 검사할 필요 없음.
    # 단, 오늘이 26:00 까지 영업이면 어제 구간이 오늘 새벽까지 이어진다는 의미라
    # 어제 구간도 확인.
    for start_s, end_s in today_ranges:
        start = _parse_hhmm(start_s)
        end = _parse_hhmm(end_s)
        if start is None or end is None:
            continue
        if start <= now_min < end:
            return True

    # 어제 자정 넘긴 구간이 오늘 새벽까지 이어지는지 확인 (예: 어제 22:00-26:00)
    yesterday_key = _DAYS_EN[(now.weekday() - 1) % 7]
    yesterday_ranges = weekly.get(yesterday_key, [])
    if isinstance(yesterday_ranges, list):
        for start_s, end_s in yesterday_ranges:
            end = _parse_hhmm(end_s)
            if end is None or end <= 24 * 60:
                continue
            # 어제 종료가 24:00 초과 → 그만큼 오늘 새벽까지 영업
            overflow = end - 24 * 60
            if now_min < overflow:
                return True

    return False


def is_open_now_combined(
    open_hours: Optional[str],
    schedule: Optional[dict],
    now: Optional[datetime] = None,
) -> Optional[bool]:
    """schedule 우선 → 실패 시 자유텍스트 휴리스틱."""
    if schedule:
        struct = is_open_now_struct(schedule, now=now)
        if struct is not None:
            return struct
    return is_open_now(open_hours or "", now=now)

# 0=월, ..., 6=일 (datetime.weekday() 와 동일)
_KOR_DAY = {"월": 0, "화": 1, "수": 2, "목": 3, "금": 4, "토": 5, "일": 6}
_KOR_DAY_FULL = {
    "월요일": 0, "화요일": 1, "수요일": 2, "목요일": 3,
    "금요일": 4, "토요일": 5, "일요일": 6,
}
_DAY_NAMES = ["월", "화", "수", "목", "금", "토", "일"]

# HH:MM (24h) 또는 H:MM 매칭
_TIME_RE = re.compile(r"(\d{1,2})\s*[:시]\s*(\d{2})?")
_RANGE_RE = re.compile(
    r"(\d{1,2})\s*[:시]\s*(\d{2})?\s*[-~∼–—]\s*(\d{1,2})\s*[:시]\s*(\d{2})?"
)

_CLOSED_KEYWORDS = ("휴무", "휴업", "정기휴", "closed", "closed today")
_ALWAYS_OPEN_KEYWORDS = ("24시간", "24 시간", "24h", "연중무휴")


def _hm_to_time(h: int, m: int) -> time:
    if h == 24 and m == 0:
        # "24:00" 은 다음 날 00:00 — 같은 날 마지막 순간으로 취급
        return time(23, 59)
    return time(min(h, 23), min(m, 59))


def _parse_range(s: str) -> Optional[tuple[time, time]]:
    """문자열에서 첫 HH:MM-HH:MM 범위 추출."""
    m = _RANGE_RE.search(s)
    if not m:
        return None
    h1, m1, h2, m2 = m.groups()
    return (
        _hm_to_time(int(h1), int(m1 or 0)),
        _hm_to_time(int(h2), int(m2 or 0)),
    )


_CLOSE_ONLY_RE = re.compile(r"(\d{1,2})\s*시\s*(?:에?\s*영업\s*종료|까지)")
_OPEN_ONLY_RE  = re.compile(r"(\d{1,2})\s*시\s*(?:시작|부터|에\s*시작)")


def _check_single_time(s: str, now_t: time) -> Optional[bool]:
    """범위 없이 종료 또는 시작 시각만 있는 경우 부분 판정.

    종료 시각 이후 → False, 시작 시각 이전 → False, 그 외 → None(불확실).
    """
    m = _CLOSE_ONLY_RE.search(s)
    if m:
        close_t = _hm_to_time(int(m.group(1)), 0)
        return False if now_t >= close_t else None

    m = _OPEN_ONLY_RE.search(s)
    if m:
        open_t = _hm_to_time(int(m.group(1)), 0)
        return False if now_t < open_t else None

    return None


def _is_in_range(now: time, start: time, end: time) -> bool:
    """now 가 [start, end] 안에 있는지 — 23:00-02:00 같은 자정 넘김도 처리."""
    if start <= end:
        return start <= now <= end
    # 자정 넘김 (예: 22:00-02:00)
    return now >= start or now <= end


def _find_today_line(lines: list[str], today_idx: int) -> Optional[str]:
    """오늘 요일 키워드가 포함된 줄을 우선 매칭, 없으면 None."""
    today_short = _DAY_NAMES[today_idx]
    today_full = today_short + "요일"
    for ln in lines:
        if today_full in ln or re.search(rf"(^|[^가-힣]){today_short}([^가-힣]|$)", ln):
            return ln
    return None


def is_open_now(open_hours: str, now: Optional[datetime] = None) -> Optional[bool]:
    """
    영업 중이면 True, 영업 종료면 False, 판정 불가면 None.

    Args:
        open_hours: store_details.open_hours 원문.
        now: 테스트용 override. None 이면 KST 현재시각.
    """
    if not open_hours or not open_hours.strip():
        return None

    text = open_hours.strip()
    if now is None:
        now = datetime.now(KST)
    elif now.tzinfo is None:
        now = now.replace(tzinfo=KST)

    now_t = now.time()
    today_idx = now.weekday()

    # 1) 항상 열림 키워드
    if any(kw in text for kw in _ALWAYS_OPEN_KEYWORDS):
        # 단, 오늘 휴무 명시면 False 우선
        split_lines = [ln.strip() for ln in re.split(r'\n|\s*/\s*', text) if ln.strip()]
        today_line = _find_today_line(split_lines, today_idx)
        if today_line and any(kw in today_line for kw in _CLOSED_KEYWORDS):
            return False
        return True

    lines = [ln.strip() for ln in re.split(r'\n|\s*/\s*', text) if ln.strip()]

    # 2) 요일별 라인 형식 — 오늘 줄에서 판정
    today_line = _find_today_line(lines, today_idx) if len(lines) > 1 else None
    if today_line:
        if any(kw in today_line for kw in _CLOSED_KEYWORDS):
            return False
        rng = _parse_range(today_line)
        if rng:
            return _is_in_range(now_t, rng[0], rng[1])
        # 단일 종료/시작 시각 시도 후 판정 불가
        return _check_single_time(today_line, now_t)

    # 3) 단순 단일 라인 ("매일 09:00-22:00" 같은 경우)
    if any(kw in text for kw in _CLOSED_KEYWORDS):
        # "정기휴무" 단독이면 영업 종료
        if not _RANGE_RE.search(text):
            return False
    rng = _parse_range(text)
    if rng:
        return _is_in_range(now_t, rng[0], rng[1])

    # 4) 단일 종료/시작 시각 패턴 ("N시에 영업종료", "N시까지", "N시 시작" 등)
    return _check_single_time(text, now_t)


# ── 자가 테스트 (python tools/open_hours_parser.py) ──
if __name__ == "__main__":
    now = datetime(2026, 5, 15, 14, 30, tzinfo=KST)  # 금요일 14:30
    cases = [
        ("",                              None),
        ("10:00-22:00",                   True),
        ("10:00-13:00",                   False),
        ("22:00-02:00",                   False),  # 14:30 은 범위 밖
        ("매일 09:00-22:00",              True),
        ("24시간 영업",                   True),
        ("연중무휴 24시간",                True),
        ("월 09:00-22:00\n금 11:00-23:00", True),
        ("월 09:00-22:00\n금 휴무",        False),
        ("정기휴무",                      False),
        ("월요일 휴무",                   False),  # v1: 다른 요일 휴무라도 영업종료로 보수 판정
        ("13시에 영업종료",               False),  # now 14:30 >= 13:00 → 종료
        ("18시에 영업종료",               None),   # now 14:30 < 18:00 → 불확실 (시작 시각 모름)
        ("9시 시작",                      None),   # now 14:30 >= 9:00 → 불확실 (종료 시각 모름)
        ("15시 시작",                     False),  # now 14:30 < 15:00 → 아직 시작 전
    ]
    for s, want in cases:
        got = is_open_now(s, now=now)
        ok = "✓" if got == want else "✗"
        print(f"  {ok} is_open_now({s!r:48s}) = {got}  (want {want})")
