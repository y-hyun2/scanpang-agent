"""
floor_utils.py
빌딩 층 표기 정규화 + 정렬 유틸.

place_info.floor_info / store_details.floor 등에 들어오는 다양한 층 표기
("1층", "B1층", "지하1층", "하1층", "1F", "B1", "RF", "옥상", "기타", "미확인")
를 `B3 / B2 / B1 / 1F / 2F / ... / RF / 기타` 형식으로 통일하고,
화면 표시·정렬 시 **아래(지하) → 위(지상) → 옥상 → 기타** 오름차순으로 정렬한다.
"""

from __future__ import annotations

import re
from typing import Tuple

# "지하1층" / "하1층" / "B1" / "B1층" 등 — 지하층 패턴
_BASEMENT_RE = re.compile(r"^(?:B|지하|하)\s*(\d+)\s*층?$", re.IGNORECASE)
# "1F" / "1층" / "1 F" 등 — 지상층 패턴
_ABOVE_RE = re.compile(r"^(\d+)\s*[F층]$", re.IGNORECASE)

# 옥상으로 분류할 라벨들
_ROOF_LABELS = {"RF", "옥상", "옥상층", "루프탑", "RFL", "PH"}
# 기타로 떨어뜨릴 라벨들 — 빈값/모호한 값 모두 통일
_UNKNOWN_LABELS = {"", "기타", "미확인", "정보없음", "정보 없음", "?"}


def normalize_floor(label: str | None) -> str:
    """
    임의 층 표기 → `B{n}` / `{n}F` / `RF` / `기타` 형식으로 정규화.

    예:
      "지하2층" / "B2층" / "B2" / "하2층" → "B2"
      "3층"     / "3F"   / "3 층"          → "3F"
      "옥상"    / "RF"                     → "RF"
      ""/None/"미확인"/"기타"               → "기타"
    """
    if not label:
        return "기타"
    s = str(label).strip().replace(" ", "")
    if s in _UNKNOWN_LABELS:
        return "기타"
    if s.upper() in _ROOF_LABELS or s in _ROOF_LABELS:
        return "RF"

    m = _BASEMENT_RE.match(s)
    if m:
        try:
            return f"B{int(m.group(1))}"
        except ValueError:
            return "기타"

    m = _ABOVE_RE.match(s)
    if m:
        try:
            return f"{int(m.group(1))}F"
        except ValueError:
            return "기타"

    return "기타"


def floor_sort_key(label: str | None) -> Tuple[int, int]:
    """
    정규화된 라벨에 대한 정렬 키.
    오름차순 정렬 시 ``B3 → B2 → B1 → 1F → 2F → ... → RF → 기타`` 순.

    bucket 코드:
      0 = 지하 (음수로 키 → B3 가 가장 작음)
      1 = 지상
      2 = 옥상
      3 = 기타/알수없음
    """
    if not label:
        return (3, 0)
    s = str(label).strip()
    if s == "RF":
        return (2, 0)
    if s == "기타":
        return (3, 0)
    if s.startswith("B"):
        try:
            n = int(s[1:])
            return (0, -n)  # B3 가 B2 보다 먼저
        except ValueError:
            return (3, 0)
    if s.endswith("F"):
        try:
            return (1, int(s[:-1]))
        except ValueError:
            return (3, 0)
    return (3, 0)


def sort_floor_info(floor_info: list[dict]) -> list[dict]:
    """
    floor_info JSON 리스트(`[{floor, stores}]`)를 정규화 + 오름차순 정렬.

    같은 정규화 라벨로 떨어지는 항목은 stores 를 합친다.
    """
    if not floor_info:
        return []

    merged: dict[str, list] = {}
    for item in floor_info:
        if not isinstance(item, dict):
            continue
        norm = normalize_floor(item.get("floor"))
        stores = item.get("stores") or []
        merged.setdefault(norm, []).extend(stores)

    return [
        {"floor": f, "stores": s}
        for f, s in sorted(merged.items(), key=lambda x: floor_sort_key(x[0]))
    ]
