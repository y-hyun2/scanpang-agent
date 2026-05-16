"""
seoul_metro.py
지하철역 상세 — 4개 소스 통합:
  1. Supabase `subway_exits` (CSV 시드, 출구별 시설명 풍부) — 1차
  2. TAGO API `GetKwrdFndSubwaySttnList` + `Schdul` — 역 ID·시간표·라인 정보
  3. 카카오 로컬 — 출구별 위경도 (lazy 캐시)
  4. 빠른하차정보 (B553766/inout/getFstExit) — 하차문 정보. 키 활성화 시 자동 호출.

키:
  TAGO_API_KEY  — 위 1·2·4 모두 동일 마스터 키 (.env)

매장명 '명동역', '명동역 4호선', '명동역 1번 출구' 등에서 역명·호선 추출.
"""

import asyncio
import os
import re
from typing import Optional

import httpx

from core.db import get_pool
from tools.details_fetchers._subway_exit_geocoder import get_all_exits_coords


_TAGO_BASE = "https://apis.data.go.kr/1613000/SubwayInfo"
_FST_BASE  = "https://apis.data.go.kr/B553766/inout"
_COMMON    = {"_type": "json", "numOfRows": 200, "pageNo": 1}

_LINE_RE = re.compile(r"(\d+호선|경의중앙선|수인분당선|분당선|신분당선|공항철도|"
                      r"경춘선|경강선|서해선|우이신설선|김포골드라인|신림선|용인경전철)")


# ───────────────────────── 입력 파싱 ─────────────────────────

def _parse_query(store_name: str) -> tuple[str, Optional[str]]:
    """매장명에서 (역명, 호선) 추출.
    예) '명동역 4호선' → ('명동', '4호선')
        '을지로입구역'   → ('을지로입구', None)
    """
    s = (store_name or "").strip()
    line = None
    m = _LINE_RE.search(s)
    if m:
        line = m.group(1)
        s = s.replace(line, "").strip()
    # '...역' suffix 제거
    s = re.sub(r"역\s*\d*번?\s*출구$", "", s)
    s = s.rstrip("역").strip()
    return s, line


# ───────────────────────── TAGO ─────────────────────────

def _items(resp_json: dict) -> list[dict]:
    try:
        body  = resp_json.get("response", {}).get("body", {})
        items = body.get("items", {}).get("item", [])
        if isinstance(items, list): return items
        return [items] if items else []
    except Exception:
        return []


async def _tago_station(client: httpx.AsyncClient, key: str,
                        station: str, line: Optional[str]) -> Optional[dict]:
    """TAGO 키워드 검색 → 정확 일치 + 호선 매칭 row."""
    try:
        r = await client.get(
            f"{_TAGO_BASE}/GetKwrdFndSubwaySttnList",
            params={"serviceKey": key, "subwayStationName": station, **_COMMON},
        )
        r.raise_for_status()
        rows = _items(r.json())
    except Exception as e:
        print(f"[seoul_metro] TAGO 역 검색 실패 ({station!r}): {e}")
        return None

    if not rows:
        return None

    norm = station.replace("역", "").strip()
    candidates = [r for r in rows
                  if (r.get("subwayStationName") or "").replace("역", "").strip() == norm]
    if not candidates:
        candidates = rows

    if line:
        for r in candidates:
            if line in (r.get("subwayRouteName") or ""):
                return r
    return candidates[0]


def _hhmm(t: str) -> str:
    """'000100' / '0001' → '00:01'. 4자리 미만/비숫자 → ''."""
    if not t or not t.isdigit() or len(t) < 4:
        return ""
    return f"{t[:2]}:{t[2:4]}"


async def _tago_schedule(client: httpx.AsyncClient, key: str, sid: str) -> dict:
    """평일 상하행 첫차/막차 시각."""
    async def fetch(updown: str) -> list[dict]:
        try:
            r = await client.get(
                f"{_TAGO_BASE}/GetSubwaySttnAcctoSchdulList",
                params={"serviceKey": key, "subwayStationId": sid,
                        "dailyTypeCode": "01", "upDownTypeCode": updown, **_COMMON},
            )
            r.raise_for_status()
            return _items(r.json())
        except Exception as e:
            print(f"[seoul_metro] TAGO 시간표({updown}) 실패: {e}")
            return []

    up, down = await asyncio.gather(fetch("U"), fetch("D"))

    def minmax(rows: list[dict]) -> tuple[str, str]:
        times = [t for r in rows
                 if (t := (r.get("depTime") or "")).isdigit() and len(t) >= 4]
        if not times:
            return "", ""
        return _hhmm(min(times)), _hhmm(max(times))

    up_first,   up_last   = minmax(up)
    down_first, down_last = minmax(down)
    return {
        "weekday_up":   {"first": up_first,   "last": up_last,
                         "toward": (up[0].get("endSubwayStationNm") if up else "")},
        "weekday_down": {"first": down_first, "last": down_last,
                         "toward": (down[0].get("endSubwayStationNm") if down else "")},
    }


# ───────────────────────── DB 출구 시설 ─────────────────────────

async def _db_exits(station: str, line: Optional[str]) -> list[dict]:
    """subway_exits 테이블 → [{exit_no, facilities: [...]}].
    CSV에 일부 역은 '서울역'처럼 '역'까지 포함 — 두 변형 다 검색."""
    pool = await get_pool()
    variants = [station, f"{station}역"]
    async with pool.acquire() as conn:
        if line:
            rows = await conn.fetch(
                "SELECT exit_no, facility_name FROM subway_exits "
                "WHERE station_name = ANY($1::text[]) AND line=$2 "
                "ORDER BY (CASE WHEN exit_no ~ '^[0-9]+$' THEN exit_no::int ELSE 99 END), "
                "facility_name",
                variants, line,
            )
        else:
            rows = await conn.fetch(
                "SELECT exit_no, facility_name FROM subway_exits "
                "WHERE station_name = ANY($1::text[]) "
                "ORDER BY line, exit_no, facility_name",
                variants,
            )
    by_exit: dict[str, list[str]] = {}
    for r in rows:
        by_exit.setdefault(r["exit_no"], []).append(r["facility_name"])
    return [{"exit_no": k, "facilities": [f for f in v if f]}
            for k, v in by_exit.items()]


# ───────────────────────── 빠른하차정보 ─────────────────────────

async def _fst_exit(client: httpx.AsyncClient, key: str,
                    station: str, line: Optional[str]) -> list[dict]:
    """빠른하차정보 API — 역명(`stnNm`) 파라미터로 직접 필터. 키 비활성/403이면 빈 리스트."""
    try:
        params = {"serviceKey": key, "pageNo": 1, "numOfRows": 100,
                  "dataType": "JSON", "stnNm": station}
        r = await client.get(f"{_FST_BASE}/getFstExit", params=params)
        if r.status_code != 200:
            return []
        rows = _items(r.json())
    except Exception as e:
        print(f"[seoul_metro] FstExit 호출 실패: {e}")
        return []

    if line:
        rows = [r for r in rows if line in (r.get("lineNm") or "")]
    return rows


# ───────────────────────── fetch (entry point) ─────────────────────────

async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """역명+호선 추출 → 4개 소스 통합 → store_details 표준 출력."""
    key = os.getenv("TAGO_API_KEY", "")
    station, line = _parse_query(store_name)
    if not station:
        return {}

    async with httpx.AsyncClient(timeout=15) as c:
        station_row, db_exits_rows = await asyncio.gather(
            _tago_station(c, key, station, line) if key else _noop_none(),
            _db_exits(station, line),
        )

        sid    = (station_row or {}).get("subwayStationId", "")
        schedule, fst_rows = await asyncio.gather(
            _tago_schedule(c, key, sid) if (key and sid) else _noop_dict(),
            _fst_exit(c, key, station, line)   if key       else _noop_list(),
        )

    # 출구 좌표 (DB cache hit이면 카카오 호출 X)
    exit_nos = [e["exit_no"] for e in db_exits_rows if e["exit_no"]]
    coords   = await get_all_exits_coords(station, line or "", exit_nos,
                                          center_lat=lat, center_lng=lng) if exit_nos else {}

    # 출구 데이터 통합 (DB 시설 + 카카오 좌표). 빠른하차정보는 역 단위.
    exits = []
    for e in db_exits_rows:
        no = e["exit_no"]
        xy = coords.get(no)
        exits.append({
            "exit_no":    no,
            "facilities": e["facilities"][:12],
            "lat":        xy[0] if xy else None,
            "lng":        xy[1] if xy else None,
        })

    fast_alights = _summarize_fst(fst_rows)

    # 시간표 요약 (양방향 첫·막차). 일부 시간만 비어있으면 양쪽 fallback.
    sched = schedule or {}
    up    = sched.get("weekday_up",   {})
    down  = sched.get("weekday_down", {})
    first = up.get("first") or down.get("first") or ""
    last  = up.get("last")  or down.get("last")  or ""
    open_hours = f"{first}~{last}" if (first or last) else ""

    return {
        "phone":      (station_row or {}).get("telno", "") or "",
        "addr":       "",
        "homepage":   "",
        "open_hours": open_hours,
        "image_urls": [],
        "details": {
            "station_name":  (station_row or {}).get("subwayStationName", station),
            "line":          (station_row or {}).get("subwayRouteName", line or ""),
            "station_id":    sid,
            "exits":         exits,
            "schedule":      sched,
            "exit_count":    len(exits),
            "fast_alights":  fast_alights,
        },
        "source": "tago_subway",
    }


def _summarize_fst(rows: list[dict]) -> list[dict]:
    """빠른하차정보 → 역 단위 요약. 출구번호 매핑이 API에 없어서 역 통째로.
    상하행+방면 같은 묶음을 하나로 dedup. 예:
      {direction: '회현', updown: '하행', door: '10-4', fac: '에스컬레이터',
       walk_pos: '명동 B4', fac_pos: '회현 방면10-4, 충무로 방면1-1'}
    """
    seen: set[tuple] = set()
    out: list[dict] = []
    for r in rows:
        key = (r.get("drtnInfo"), r.get("upbdnbSe"),
               r.get("qckgffVhclDoorNo"), r.get("plfmCmgFac"))
        if key in seen:
            continue
        seen.add(key)
        out.append({
            "direction": r.get("drtnInfo")         or "",  # '회현', '충무로'
            "updown":    r.get("upbdnbSe")         or "",  # '상행' / '하행'
            "door":      r.get("qckgffVhclDoorNo") or "",  # '10-4'
            "fac":       r.get("plfmCmgFac")       or "",  # '에스컬레이터'
            "walk_pos":  r.get("fwkPstnNm")        or "",  # '명동 B4'
            "fac_pos":   r.get("facPstnNm")        or "",  # '회현 방면10-4, 충무로 방면1-1'
        })
    return out


# ───────────────────────── helpers ─────────────────────────

async def _noop_none(): return None
async def _noop_dict(): return {}
async def _noop_list(): return []
