"""
seoul_metro.py
지하철역 상세 — 국토교통부 TAGO 지하철정보 API.

Base URL: https://apis.data.go.kr/1613000/SubwayInfo
4개 엔드포인트를 chain:
  1. GetKwrdFndSubwaySttnList         — 키워드 → 역 ID (railOprIsttCd, lnCd, stinCd)
  2. GetSubwaySttnExitAcctoCfrFcltyList — 출구별 주변 시설
  3. GetSubwaySttnExitAcctoBusRouteList — 출구별 버스 노선 (선택)
  4. GetSubwaySttnAcctoSchdulList       — 역별 시간표 (첫차/막차)

키: .env TAGO_API_KEY (data.go.kr 마스터 키 — TourAPI/Store/PublicRestroom과 동일)
"""

import asyncio
import os
from collections import defaultdict
from typing import Optional

import httpx

_BASE = "https://apis.data.go.kr/1613000/SubwayInfo"
_COMMON = {"_type": "json", "numOfRows": 100, "pageNo": 1}


def _items(resp_json: dict) -> list[dict]:
    """data.go.kr 표준 응답 → items.item 리스트 (단건이면 dict, 다건이면 list)."""
    try:
        body = resp_json.get("response", {}).get("body", {})
        items = body.get("items", {}).get("item", [])
        if isinstance(items, list):
            return items
        return [items] if items else []
    except Exception:
        return []


async def _search_station(client: httpx.AsyncClient, key: str, name: str) -> Optional[dict]:
    """역명 키워드로 검색 → 첫 매칭 역 row."""
    try:
        r = await client.get(
            f"{_BASE}/GetKwrdFndSubwaySttnList",
            params={"serviceKey": key, "subwayStationName": name, **_COMMON},
        )
        r.raise_for_status()
        rows = _items(r.json())
    except Exception as e:
        print(f"[seoul_metro] 역 검색 실패 ({name!r}): {e}")
        return None

    if not rows:
        return None

    # 정확 일치 우선, 없으면 첫 결과
    norm = name.replace("역", "").strip()
    for row in rows:
        sn = (row.get("subwayStationName") or "").replace("역", "").strip()
        if norm == sn:
            return row
    return rows[0]


async def _fetch_exits(client: httpx.AsyncClient, key: str, station: dict) -> list[dict]:
    """출구별 주변 시설 목록."""
    try:
        r = await client.get(
            f"{_BASE}/GetSubwaySttnExitAcctoCfrFcltyList",
            params={
                "serviceKey":         key,
                "subwayStationId":    station.get("subwayStationId", ""),
                **_COMMON,
            },
        )
        r.raise_for_status()
        return _items(r.json())
    except Exception as e:
        print(f"[seoul_metro] 출구 시설 조회 실패: {e}")
        return []


async def _fetch_schedule(client: httpx.AsyncClient, key: str, station: dict) -> list[dict]:
    """역별 시간표 (첫차/막차)."""
    try:
        r = await client.get(
            f"{_BASE}/GetSubwaySttnAcctoSchdulList",
            params={
                "serviceKey":         key,
                "subwayStationId":    station.get("subwayStationId", ""),
                **_COMMON,
            },
        )
        r.raise_for_status()
        return _items(r.json())
    except Exception as e:
        print(f"[seoul_metro] 시간표 조회 실패: {e}")
        return []


def _summarize_schedule(rows: list[dict]) -> dict:
    """시간표 rows에서 평일 첫차/막차 추출. 응답 필드명은 실제 호출로 확인 후 조정 필요."""
    if not rows:
        return {}
    # 가능한 필드명들 — API 응답 본 후 정확히 매핑
    first = min((r.get("startTime", "") or r.get("startSubwayTime", "") for r in rows), default="")
    last  = max((r.get("endTime", "") or r.get("endSubwayTime", "") for r in rows),   default="")
    return {"first_train": first, "last_train": last}


def _summarize_exits(rows: list[dict]) -> list[dict]:
    """출구번호별로 시설명 묶어 반환."""
    by_exit: dict[str, list[str]] = defaultdict(list)
    for r in rows:
        exit_no = str(r.get("exitNo", "") or r.get("exitNumber", ""))
        facility = r.get("cfFacility", "") or r.get("facilityName", "") or r.get("impFaclNm", "")
        if exit_no and facility:
            by_exit[exit_no].append(facility)
    return [
        {"exit_no": k, "facilities": v[:10]}
        for k, v in sorted(by_exit.items(), key=lambda x: int(x[0]) if x[0].isdigit() else 99)
    ]


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """매장명에서 역 키워드 추출 → TAGO 4개 API chain → details 패키징."""
    key = os.getenv("TAGO_API_KEY", "")
    if not key or not store_name:
        return {}

    # 매장명에서 역명만 추출 (예: "명동역 ATM" → "명동역", "을지로입구역" → "을지로입구역")
    query = store_name.strip()
    if "역 " in query:
        query = query.split("역", 1)[0] + "역"

    async with httpx.AsyncClient(timeout=15) as c:
        station = await _search_station(c, key, query)
        if not station:
            return {}

        exits, schedule = await asyncio.gather(
            _fetch_exits(c, key, station),
            _fetch_schedule(c, key, station),
        )

    exits_summary = _summarize_exits(exits)
    sched_summary = _summarize_schedule(schedule)

    return {
        "phone":       station.get("telno", "") or station.get("phoneNumber", ""),
        "addr":        station.get("subwayRouteName", "") or station.get("address", ""),
        "homepage":    "",
        "open_hours":  f"{sched_summary.get('first_train', '')} - {sched_summary.get('last_train', '')}".strip(" -"),
        "image_urls":  [],
        "details": {
            "subway_station_id":   station.get("subwayStationId", ""),
            "station_name":        station.get("subwayStationName", ""),
            "line":                station.get("subwayRouteName", ""),
            "exits":               exits_summary,
            "first_train":         sched_summary.get("first_train", ""),
            "last_train":          sched_summary.get("last_train", ""),
        },
        "source": "tago_subway",
    }
