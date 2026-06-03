"""
_reverse_geocode.py
좌표(lat, lng) → 도로명 주소. Kakao coord2address 우선, 실패 시 VWorld 폴백.

은행/ATM/환전소처럼 데이터 소스(koreaexim 등)가 주소를 안 주는 매장의 addr 를
좌표로부터 보강하는 용도. 도로명 주소가 있으면 도로명, 없으면 지번을 반환한다.
"""

import os
from typing import Optional

import httpx

_KAKAO_URL  = "https://dapi.kakao.com/v2/local/geo/coord2address.json"
_VWORLD_URL = "https://api.vworld.kr/req/address"


async def _kakao_reverse(lat: float, lng: float) -> str:
    """Kakao Local coord2address — x=경도, y=위도. 도로명 우선, 없으면 지번."""
    key = os.getenv("KAKAO_REST_API_KEY", "")
    if not key:
        return ""
    try:
        async with httpx.AsyncClient(timeout=8) as c:
            r = await c.get(
                _KAKAO_URL,
                headers={"Authorization": f"KakaoAK {key}"},
                params={"x": str(lng), "y": str(lat)},
            )
            r.raise_for_status()
            docs = r.json().get("documents", [])
        if not docs:
            return ""
        d = docs[0]
        road = d.get("road_address") or {}
        if road.get("address_name"):
            return road["address_name"]
        addr = d.get("address") or {}
        return addr.get("address_name", "") or ""
    except Exception as e:
        print(f"[reverse_geocode] kakao 실패 ({lat},{lng}): {e}")
        return ""


async def _vworld_reverse(lat: float, lng: float) -> str:
    """VWorld getAddress — 도로명(ROAD) 우선, 실패 시 지번(PARCEL)."""
    key = os.getenv("VWORLD_API_KEY", "")
    if not key:
        return ""
    for typ in ("ROAD", "PARCEL"):
        try:
            async with httpx.AsyncClient(timeout=8) as c:
                r = await c.get(_VWORLD_URL, params={
                    "service": "address",
                    "request": "getAddress",
                    "version": "2.0",
                    "crs":     "epsg:4326",
                    "point":   f"{lng},{lat}",
                    "format":  "json",
                    "type":    typ,
                    "key":     key,
                })
                r.raise_for_status()
                result = r.json().get("response", {}).get("result", [])
            if result and result[0].get("text"):
                return result[0]["text"]
        except Exception as e:
            print(f"[reverse_geocode] vworld {typ} 실패 ({lat},{lng}): {e}")
    return ""


async def reverse_geocode(lat: Optional[float], lng: Optional[float]) -> str:
    """좌표 → 주소 문자열. 못 구하면 빈 문자열. Kakao → VWorld 순 폴백."""
    if lat is None or lng is None:
        return ""
    addr = await _kakao_reverse(float(lat), float(lng))
    if addr:
        return addr
    return await _vworld_reverse(float(lat), float(lng))
