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
    valid_range_found = False
    for start_s, end_s in today_ranges:
        start = _parse_hhmm(start_s)
        end = _parse_hhmm(end_s)
        if start is None or end is None or start == end:
            # start==end 는 LLM 이 종료시각만 알고 시작시각을 모를 때 생성하는 무효 구간
            continue
        valid_range_found = True
        if start <= now_min < end:
            return True

    # 유효 구간이 하나도 없으면 (모두 start==end) → 텍스트 fallback 에 위임
    if not valid_range_found:
        return None

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


def _decode_open_close(open_time: str, close_time: str) -> list[list[str]]:
    """store_hours 의 open_time/close_time 텍스트 → [[start,end], ...] 구간 리스트.

    단일 구간:    open="11:00", close="20:00"        → [["11:00","20:00"]]
    브레이크 2구간: open="11:00-15:00", close="16:00-20:00"
                                                     → [["11:00","15:00"],["16:00","20:00"]]
    자정 넘김:     close="26:00" 그대로 (is_open_now_struct 가 24+ 처리)
    """
    ot = (open_time or "").strip()
    ct = (close_time or "").strip()
    if "-" in ot:  # 브레이크로 2구간 — open_time=1구간, close_time=2구간
        out: list[list[str]] = []
        a = ot.split("-", 1)
        out.append([a[0].strip(), a[1].strip()])
        if "-" in ct:
            b = ct.split("-", 1)
            out.append([b[0].strip(), b[1].strip()])
        return out
    if ot and ct:
        return [[ot, ct]]
    return []


def _fmt_hhmm(s: str) -> str:
    """'26:00' → '익일 02:00', 그 외는 그대로. 표시용."""
    s = (s or "").strip()
    p = _parse_hhmm(s)
    if p is not None and p >= 24 * 60:
        h, m = divmod(p - 24 * 60, 60)
        return f"익일 {h:02d}:{m:02d}"
    return s


def format_schedule_text(rows) -> str:
    """store_hours 행들 → 사람이 읽는 영업시간 문자열(표시·챗봇용).

    동일 영업시간 연속 요일은 묶는다.
    예: '월~금 11:00-14:00, 17:00-18:30 / 토~일 휴무'
        '월~일 17:00-익일 02:00 (L.O 익일 01:00)'
    """
    by_dow: dict[int, object] = {}
    for r in rows:
        dow = r["day_of_week"]
        if dow is not None and 0 <= dow <= 6:
            by_dow[dow] = r
    if not by_dow:
        return ""

    # 요일별 영업시간 문자열(라벨 제외) 생성
    day_segs: list[str] = []
    for dow in range(7):
        r = by_dow.get(dow)
        if r is None:
            day_segs.append("휴무")
            continue
        intervals = _decode_open_close(r["open_time"], r["close_time"])
        seg = ", ".join(f"{_fmt_hhmm(s)}-{_fmt_hhmm(e)}" for s, e in intervals) or "휴무"
        try:
            lo = r["last_order"]
        except (KeyError, IndexError):
            lo = None
        if lo:
            lo_disp = ", ".join(_fmt_hhmm(x.strip()) for x in str(lo).split(",") if x.strip())
            seg += f" (L.O {lo_disp})"
        day_segs.append(seg)

    # 연속 동일 요일 묶기
    parts: list[str] = []
    i = 0
    while i < 7:
        j = i
        while j + 1 < 7 and day_segs[j + 1] == day_segs[i]:
            j += 1
        label = _DAY_NAMES[i] if i == j else f"{_DAY_NAMES[i]}~{_DAY_NAMES[j]}"
        parts.append(f"{label} {day_segs[i]}")
        i = j + 1
    return " / ".join(parts)


def schedule_from_store_hours(rows) -> dict:
    """store_hours 행들 → is_open_now_struct 용 schedule dict.

    rows: [{day_of_week, open_time, close_time}, ...] (asyncpg Record 또는 dict).
          PK 가 (store_id, day_of_week) 라 요일당 1행이지만 방어적으로 누적한다.
          행이 없는 요일은 빈 배열(=휴무).
    """
    weekly: dict[str, list] = {d: [] for d in _DAYS_EN}
    for r in rows:
        dow = r["day_of_week"]
        if dow is None or not (0 <= dow <= 6):
            continue
        weekly[_DAYS_EN[dow]].extend(
            _decode_open_close(r["open_time"], r["close_time"])
        )
    return {"weekly": weekly, "always_open": False}


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


# "22시에 영업 종료" / "22:30에 영업 종료" / "22:30에 운영 종료" / "22시까지" 등
_CLOSE_ONLY_RE = re.compile(
    r"(\d{1,2})(?::(\d{2}))?\s*(?:시\s*)?에?\s*(?:영업|운영)\s*종료"
    r"|(\d{1,2})(?::(\d{2}))?\s*시\s*까지"
)
# "9시 시작" / "09:00에 운영 시작" / "9시부터" 등
_OPEN_ONLY_RE = re.compile(
    r"(\d{1,2})(?::(\d{2}))?\s*(?:시\s*)?(?:에?\s*(?:영업|운영)\s*시작|부터|시작)"
)


def _parse_month_range(text: str) -> Optional[tuple[int, int]]:
    """텍스트에서 'N월~M월' 또는 'N~M월' 범위를 추출한다."""
    m = re.search(r"(\d{1,2})\s*월?\s*[~～]\s*(\d{1,2})\s*월", text)
    if m:
        return int(m.group(1)), int(m.group(2))
    return None


def _month_in_range(month: int, start: int, end: int) -> bool:
    """현재 월이 [start, end] 안에 있는지. 동절기(11~3)처럼 wrap-around 지원."""
    if start <= end:
        return start <= month <= end
    return month >= start or month <= end


def _find_weekday_weekend_line(lines: list[str], today_idx: int) -> Optional[str]:
    """평일/주말 키워드가 포함된 라인 목록에서 오늘에 해당하는 줄을 반환한다."""
    has_ww = any("평일" in ln or "주말" in ln for ln in lines)
    if not has_ww:
        return None
    is_weekday = today_idx < 5  # 0=월 ~ 4=금
    if is_weekday:
        return next((ln for ln in lines if "평일" in ln), None)
    return next((ln for ln in lines if "주말" in ln), None)


def _find_monthly_line(lines: list[str], current_month: int) -> Optional[str]:
    """[N월~M월] / 하절기(N월~M월) 등 계절·월별 라인에서 현재 월에 맞는 줄을 반환한다."""
    for ln in lines:
        rng = _parse_month_range(ln)
        if rng and _month_in_range(current_month, rng[0], rng[1]):
            return ln
    return None


def _check_single_time(s: str, now_t: time) -> Optional[bool]:
    """범위 없이 종료 또는 시작 시각만 있는 경우 부분 판정.

    종료 시각 이후 → False, 시작 시각 이전 → False, 그 외 → None(불확실).
    """
    m = _CLOSE_ONLY_RE.search(s)
    if m:
        # 그룹: (h1, m1) for "영업/운영 종료" | (h2, m2) for "시까지"
        h = int(m.group(1) or m.group(3))
        mn = int(m.group(2) or m.group(4) or 0)
        close_t = _hm_to_time(h, mn)
        return False if now_t >= close_t else None

    m = _OPEN_ONLY_RE.search(s)
    if m:
        h = int(m.group(1))
        mn = int(m.group(2) or 0)
        open_t = _hm_to_time(h, mn)
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

    _SPLIT_RE = re.compile(r'\n|<br\s*/?>\s*|\s*/\s*')

    # 1) 항상 열림 키워드
    if any(kw in text for kw in _ALWAYS_OPEN_KEYWORDS):
        split_lines = [ln.strip() for ln in _SPLIT_RE.split(text) if ln.strip()]
        today_line = _find_today_line(split_lines, today_idx)
        if today_line and any(kw in today_line for kw in _CLOSED_KEYWORDS):
            return False
        return True

    lines = [ln.strip() for ln in _SPLIT_RE.split(text) if ln.strip()]

    # 2) 요일별 라인 형식 — 오늘 줄에서 판정
    today_line = _find_today_line(lines, today_idx) if len(lines) > 1 else None
    if today_line:
        if any(kw in today_line for kw in _CLOSED_KEYWORDS):
            return False
        rng = _parse_range(today_line)
        if rng:
            return _is_in_range(now_t, rng[0], rng[1])
        return _check_single_time(today_line, now_t)

    # 3) 평일/주말 라인 형식
    if len(lines) > 1:
        ww_line = _find_weekday_weekend_line(lines, today_idx)
        if ww_line:
            if any(kw in ww_line for kw in _CLOSED_KEYWORDS):
                return False
            rng = _parse_range(ww_line)
            if rng:
                return _is_in_range(now_t, rng[0], rng[1])
            return _check_single_time(ww_line, now_t)

    # 4) 계절/월별 라인 형식 ([N월~M월], 하절기, 동절기 등)
    if len(lines) > 1:
        monthly_line = _find_monthly_line(lines, now.month)
        if monthly_line:
            if any(kw in monthly_line for kw in _CLOSED_KEYWORDS):
                return False
            rng = _parse_range(monthly_line)
            if rng:
                return _is_in_range(now_t, rng[0], rng[1])
            return _check_single_time(monthly_line, now_t)

    # 5) 단순 단일 라인 ("매일 09:00-22:00" 같은 경우)
    if any(kw in text for kw in _CLOSED_KEYWORDS):
        if not _RANGE_RE.search(text):
            return False
    rng = _parse_range(text)
    if rng:
        return _is_in_range(now_t, rng[0], rng[1])

    # 6) 단일 종료/시작 시각 패턴 ("N시에 영업종료", "N시까지", "N시 시작" 등)
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
        # HH:MM 형식 종료/시작
        ("22:30에 영업 종료",             None),   # 14:30 < 22:30 → 불확실
        ("13:00에 영업 종료",             False),  # 14:30 >= 13:00 → 종료
        ("09:00에 운영 시작",             None),   # 14:30 >= 09:00 → 불확실
        ("15:00에 운영 시작",             False),  # 14:30 < 15:00 → 아직 시작 전
        # 평일/주말 형식 (금요일 = 평일)
        ("평일 09:00~21:00 / 주말 09:00~20:00",  True),   # 금=평일, 14:30 in 09-21
        ("평일 09:00~12:00 / 주말 09:00~20:00",  False),  # 금=평일, 14:30 > 12:00
        ("평일 09:00~21:00<br>주말 09:00~20:00", True),   # <br> 구분자
        # 월별 형식 (5월)
        ("[1월~2월] 09:00~17:00<br>[3월~5월] 09:00~18:00<br>[6월~8월] 09:00~18:30", True),  # 5월→3~5월 구간
        ("[1월~2월] 09:00~13:00<br>[3월~5월] 09:00~13:00<br>[6월~8월] 09:00~18:30", False), # 5월→14:30>13:00
        # 하절기/동절기 형식 (5월=하절기)
        ("하절기(4월~10월) 09:00~21:00 / 동절기(11월~3월) 09:00~20:00", True),  # 5월=하절기
        ("하절기(4월~10월) 09:00~13:00 / 동절기(11월~3월) 09:00~20:00", False), # 5월=하절기, 14:30>13:00
    ]
    for s, want in cases:
        got = is_open_now(s, now=now)
        ok = "✓" if got == want else "✗"
        print(f"  {ok} is_open_now({s!r:48s}) = {got}  (want {want})")
