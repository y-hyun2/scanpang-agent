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
    """요일·방향별 첫차/막차 시각 — 평일·토·일/공휴일 × 상하행 = 6종.
    TAGO API totalCount 가 230 가량이라 numOfRows=1000 으로 1페이지에 다 받음."""
    async def fetch(daily: str, updown: str) -> list[dict]:
        try:
            r = await client.get(
                f"{_TAGO_BASE}/GetSubwaySttnAcctoSchdulList",
                params={"serviceKey": key, "subwayStationId": sid,
                        "dailyTypeCode": daily, "upDownTypeCode": updown,
                        "_type": "json", "numOfRows": 1000, "pageNo": 1},
            )
            r.raise_for_status()
            return _items(r.json())
        except Exception as e:
            print(f"[seoul_metro] TAGO 시간표({daily}/{updown}) 실패: {e}")
            return []

    # 6개 호출 병렬 — 평일=01, 토요일=02, 일/공휴일=03
    (wu, wd, su, sd, hu, hd) = await asyncio.gather(
        fetch("01", "U"), fetch("01", "D"),
        fetch("02", "U"), fetch("02", "D"),
        fetch("03", "U"), fetch("03", "D"),
    )

    def minmax(rows: list[dict]) -> tuple[str, str]:
        """첫차/막차 분리 — 자정 넘긴 막차(00:00~03:59)와 새벽 첫차(>=04:00)를
        분리해서 max(<04:00) → 막차, min(>=04:00) → 첫차로 잡는다.
        한국 지하철 운영 패턴 상 04:00 cutoff 이 안전."""
        DAWN_MIN = 4 * 60  # 240분

        def to_min(t: str) -> int:
            if len(t) >= 4 and t[:4].isdigit():
                return int(t[:2]) * 60 + int(t[2:4])
            return -1

        times = sorted(
            t for r in rows
            if (t := (r.get("depTime") or "")).isdigit() and len(t) >= 4
        )
        if not times:
            return "", ""
        morning = [t for t in times if to_min(t) >= DAWN_MIN]
        late_night = [t for t in times if to_min(t) < DAWN_MIN]
        first = morning[0]    if morning    else times[0]
        last  = late_night[-1] if late_night else times[-1]
        return _hhmm(first), _hhmm(last)

    def dir_dict(rows: list[dict]) -> dict:
        first, last = minmax(rows)
        return {
            "first":  first,
            "last":   last,
            "toward": (rows[0].get("endSubwayStationNm") if rows else ""),
        }

    return {
        "weekday_up":   dir_dict(wu),
        "weekday_down": dir_dict(wd),
        "saturday_up":  dir_dict(su),
        "saturday_down":dir_dict(sd),
        "holiday_up":   dir_dict(hu),
        "holiday_down": dir_dict(hd),
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
                "ORDER BY line, "
                "(CASE WHEN exit_no ~ '^[0-9]+$' THEN exit_no::int ELSE 99 END), "
                "facility_name",
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


_FAC_POS_RE = re.compile(r"\s*(.+?)\s*방면\s*([\d\-]+)\s*$")


def _summarize_fst(rows: list[dict]) -> list[dict]:
    """빠른하차정보 → 방면별 1행 (회현 방면 10-4 · 충무로 방면 1-1).

    API 응답의 facPstnNm (`'회현 방면10-4, 충무로 방면1-1'`) 한 줄에 양 방면 정보가
    다 들어있다. 첫 행 drtnInfo·qckgffVhclDoorNo 만 보면 한쪽 방면만 잡혀서
    두 방면 모두 같은 door 가 나오는 버그가 있었어서, **facPstnNm 가 가장 풍부한
    한 row** 의 fac_pos 텍스트를 파싱해 방면별 door 를 직접 추출한다.
    """
    rich = next((r for r in rows if r.get("facPstnNm")), None)
    if rich:
        fac_pos = rich.get("facPstnNm") or ""
        walk_pos = rich.get("fwkPstnNm") or ""
        fac = rich.get("plfmCmgFac") or ""
        out: list[dict] = []
        for part in fac_pos.split(","):
            m = _FAC_POS_RE.match(part.strip())
            if not m:
                continue
            out.append({
                "direction": m.group(1).strip(),  # '회현' / '충무로'
                "updown":    "",                   # API 한 행에서 한쪽만 알 수 있어 비움
                "door":      m.group(2).strip(),   # '10-4' / '1-1'
                "fac":       fac,
                "walk_pos":  walk_pos,
                "fac_pos":   fac_pos,
            })
        if out:
            return out

    # facPstnNm 비어 있으면 raw 행 dedup fallback
    seen: set[tuple] = set()
    out: list[dict] = []
    for r in rows:
        key = (r.get("drtnInfo"), r.get("upbdnbSe"),
               r.get("qckgffVhclDoorNo"), r.get("plfmCmgFac"))
        if key in seen:
            continue
        seen.add(key)
        out.append({
            "direction": r.get("drtnInfo")         or "",
            "updown":    r.get("upbdnbSe")         or "",
            "door":      r.get("qckgffVhclDoorNo") or "",
            "fac":       r.get("plfmCmgFac")       or "",
            "walk_pos":  r.get("fwkPstnNm")        or "",
            "fac_pos":   r.get("facPstnNm")        or "",
        })
    return out


# ───────────────────────── helpers ─────────────────────────

async def _noop_none(): return None
async def _noop_dict(): return {}
async def _noop_list(): return []
