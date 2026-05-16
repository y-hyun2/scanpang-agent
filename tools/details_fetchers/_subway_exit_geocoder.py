"""
_subway_exit_geocoder.py
'{역명}역 {n}번 출구' 카카오 로컬 키워드 검색 → 좌표.
첫 호출 시 카카오 호출 후 subway_exits 테이블 lat/lng 컬럼에 UPDATE.
이후 동일 출구는 DB 캐시 hit.

테이블 스키마 가정: subway_exits(operator, line, station_name, exit_no, facility_name, lat, lng)
좌표는 (operator, line, station, exit_no) 기준 동일 — facility_name 무관하게 같은 출구는 같은 좌표.
"""

import asyncio
import os
from typing import Optional

import httpx

from core.db import get_pool

_KAKAO_KEY  = lambda: os.getenv("KAKAO_REST_API_KEY", "")
_KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"


async def _kakao_search(query: str, center_lat: Optional[float] = None,
                        center_lng: Optional[float] = None) -> Optional[tuple[float, float]]:
    """카카오 키워드 검색 → 첫 결과 (lat, lng) or None."""
    key = _KAKAO_KEY()
    if not key:
        return None
    params = {"query": query, "size": 1}
    if center_lat and center_lng:
        params.update({"y": str(center_lat), "x": str(center_lng), "radius": "1000"})
    try:
        async with httpx.AsyncClient(timeout=8) as c:
            r = await c.get(_KEYWORD_URL, headers={"Authorization": f"KakaoAK {key}"}, params=params)
            r.raise_for_status()
            docs = r.json().get("documents", [])
            if not docs:
                return None
            d = docs[0]
            return float(d["y"]), float(d["x"])
    except Exception as e:
        print(f"[exit_geocoder] kakao 검색 실패 ({query!r}): {e}")
        return None


async def get_exit_coords(
    station_name: str,
    line: str,
    exit_no: str,
    center_lat: Optional[float] = None,
    center_lng: Optional[float] = None,
) -> Optional[tuple[float, float]]:
    """
    출구 1개의 (lat, lng) — 캐시 hit이면 DB에서, miss면 카카오 검색 후 UPDATE.

    Args:
        station_name: '명동', '을지로입구' 등 ('역' 접미 제외)
        line:         '4호선', '2호선' 등
        exit_no:      '1', '2', ...
        center_lat/lng: 검색 정확도용 (역 근처로 가중치). None이면 전국 검색.
    """
    pool = await get_pool()
    variants = [station_name, f"{station_name}역"]
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT station_name, lat, lng FROM subway_exits "
            "WHERE station_name = ANY($1::text[]) AND line=$2 AND exit_no=$3 "
            "LIMIT 1",
            variants, line, exit_no,
        )
        if row and row["lat"] and row["lng"]:
            return float(row["lat"]), float(row["lng"])

    # miss → 카카오 검색. DB에 어떤 변형이 저장돼 있는지 row로 확인.
    actual_name = row["station_name"] if row else station_name
    query = f"{station_name}역 {exit_no}번 출구"
    coords = await _kakao_search(query, center_lat, center_lng)
    if not coords:
        return None

    lat, lng = coords
    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE subway_exits SET lat=$1, lng=$2 "
            "WHERE station_name=$3 AND line=$4 AND exit_no=$5",
            lat, lng, actual_name, line, exit_no,
        )
    return lat, lng


async def get_all_exits_coords(
    station_name: str,
    line: str,
    exit_nos: list[str],
    center_lat: Optional[float] = None,
    center_lng: Optional[float] = None,
) -> dict[str, tuple[float, float]]:
    """여러 출구 좌표를 병렬로 조회 → {exit_no: (lat, lng)}."""
    results = await asyncio.gather(
        *[get_exit_coords(station_name, line, n, center_lat, center_lng) for n in exit_nos],
        return_exceptions=False,
    )
    return {no: xy for no, xy in zip(exit_nos, results) if xy}
