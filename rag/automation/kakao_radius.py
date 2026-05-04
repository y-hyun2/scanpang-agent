"""
kakao_radius.py
Kakao Local 카테고리 검색 API로 특정 좌표 반경 내 매장 목록을 수집한다.
floor 정보는 Kakao API에 없으므로 절대 생성하지 않는다.
"""

import asyncio
import math
import os
from typing import Optional

import httpx
from dotenv import load_dotenv

load_dotenv()

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")

# 검색할 Kakao 카테고리 코드 전체
_CATEGORY_CODES = [
    "MT1",  # 대형마트
    "CS2",  # 편의점
    "FD6",  # 음식점
    "CE7",  # 카페
    "HP8",  # 병원
    "PM9",  # 약국
    "BK9",  # 은행
    "AT4",  # 관광명소
    "AD5",  # 숙박
    "CT1",  # 문화시설
    "AG2",  # 중개업소
    "PO3",  # 공공기관
    "SC4",  # 학교
    "AC5",  # 학원
]


def _haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    R = 6_371_000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


async def collect_stores_by_radius(
    lat: float, lng: float, radius_m: int = 20
) -> list[dict]:
    """
    Kakao Local 카테고리 검색 API로 반경 내 모든 카테고리 매장을 수집한다.

    Args:
        lat, lng: 중심 좌표 (건물 중심점)
        radius_m: 검색 반경 (미터, 기본 20m)

    Returns:
        [{"name": str, "category": str, "phone": str, "address": str,
          "lat": float, "lng": float}]
        floor 필드는 포함하지 않는다.
    """
    if not KAKAO_REST_API_KEY:
        print("[kakao_radius] KAKAO_REST_API_KEY 없음 — 매장 수집 건너뜀")
        return []

    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    url = "https://dapi.kakao.com/v2/local/search/category.json"
    collected: list[dict] = []
    seen: set[str] = set()  # "name|lat_4d|lng_4d" 중복 제거용

    async with httpx.AsyncClient(timeout=10) as client:
        for code in _CATEGORY_CODES:
            try:
                resp = await client.get(url, headers=headers, params={
                    "category_group_code": code,
                    "x": str(lng),
                    "y": str(lat),
                    "radius": radius_m,
                    "size": 15,
                })
                resp.raise_for_status()
                docs = resp.json().get("documents", [])
            except Exception as e:
                print(f"[kakao_radius] {code} 호출 실패: {e}")
                await asyncio.sleep(0.1)
                continue

            for doc in docs:
                store_lat = float(doc.get("y") or 0)
                store_lng = float(doc.get("x") or 0)

                # 반경 외 제외 (API radius 파라미터가 이미 필터하지만 이중 검증)
                if _haversine_m(lat, lng, store_lat, store_lng) > radius_m:
                    continue

                name = doc.get("place_name", "").strip()
                if not name:
                    continue

                dedup_key = f"{name}|{round(store_lat, 4)}|{round(store_lng, 4)}"
                if dedup_key in seen:
                    continue
                seen.add(dedup_key)

                cat_parts = doc.get("category_name", "").split(" > ")
                category = cat_parts[-1] if cat_parts else ""

                collected.append({
                    "name":     name,
                    "category": category,
                    "phone":    doc.get("phone", ""),
                    "address":  doc.get("road_address_name", "") or doc.get("address_name", ""),
                    "lat":      store_lat,
                    "lng":      store_lng,
                })

            await asyncio.sleep(0.1)

    print(f"[kakao_radius] ({lat:.5f}, {lng:.5f}) 반경 {radius_m}m → {len(collected)}개 매장")
    return collected
