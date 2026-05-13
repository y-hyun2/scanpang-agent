"""
_public_restroom_cache.py
data.go.kr 행정안전부 '공중화장실정보 조회서비스' (15155058) 캐시.

전국 53k+건이고 API에 좌표/지역 필터 파라미터가 없어 1회 전체 다운로드 후
디스크에 캐싱한다. 이후 호출은 메모리·디스크 캐시에서 즉시 반환.

- 디스크 캐시: rag/data/public_restrooms.json (~30MB)
- 메모리 캐시: 첫 호출 후 프로세스 종료까지 유지
- 첫 로드 시 약 30~60초 소요 (페이지당 1000개 × 54 페이지, 10병렬)
"""

import asyncio
import json
import os
import time
from typing import Optional

import httpx

_API_URL    = "https://apis.data.go.kr/1741000/public_restroom_info/info"
_CACHE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "rag", "data", "public_restrooms.json",
)
_PER_PAGE   = 100  # data.go.kr이 1000 요청해도 100만 반환 → 명시
_CONCURRENT = 5    # 동시 호출 수 — data.go.kr rate limit 회피
_RETRIES    = 2    # 실패 시 재시도

_MEM_CACHE: Optional[list[dict]] = None


def _haversine_m(a_lat: float, a_lng: float, b_lat: float, b_lng: float) -> float:
    import math
    R = 6_371_000
    dlat = math.radians(b_lat - a_lat)
    dlng = math.radians(b_lng - a_lng)
    h = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(a_lat)) * math.cos(math.radians(b_lat))
         * math.sin(dlng / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(h), math.sqrt(1 - h))


async def _fetch_page(client: httpx.AsyncClient, key: str, page_no: int) -> list[dict]:
    """단일 페이지 호출. 실패 시 _RETRIES만큼 재시도, 그래도 실패면 빈 리스트."""
    for attempt in range(_RETRIES + 1):
        try:
            r = await client.get(
                _API_URL,
                params={"serviceKey": key, "numOfRows": _PER_PAGE,
                        "pageNo": page_no, "type": "json"},
            )
            r.raise_for_status()
            body = r.json().get("response", {}).get("body", {})
            items = body.get("items", {}).get("item", [])
            normalized = items if isinstance(items, list) else [items]
            if not normalized and attempt < _RETRIES:
                # 빈 결과 — rate limit일 수도, 잠시 후 재시도
                await asyncio.sleep(1.5 * (attempt + 1))
                continue
            return normalized
        except Exception as e:
            if attempt < _RETRIES:
                await asyncio.sleep(1.5 * (attempt + 1))
                continue
            print(f"[public_restroom] page={page_no} 실패 ({_RETRIES+1}회 시도): {e}")
            return []
    return []


async def _download_all() -> list[dict]:
    """전체 페이지를 병렬 다운로드."""
    key = os.getenv("PUBLIC_RESTROOM_API_KEY", "")
    if not key:
        print("[public_restroom] PUBLIC_RESTROOM_API_KEY 없음")
        return []

    async with httpx.AsyncClient(timeout=30) as client:
        # 1페이지 호출로 totalCount 획득
        first = await client.get(
            _API_URL,
            params={"serviceKey": key, "numOfRows": 1, "pageNo": 1, "type": "json"},
        )
        try:
            body = first.json().get("response", {}).get("body", {})
            total = int(body.get("totalCount", 0))
        except Exception as e:
            print(f"[public_restroom] totalCount 조회 실패: {e}")
            return []

        if total == 0:
            return []

        num_pages = (total + _PER_PAGE - 1) // _PER_PAGE
        print(f"[public_restroom] 전체 {total}건 / {num_pages}페이지 — 병렬 다운로드 시작")

        all_items: list[dict] = []
        for batch_start in range(1, num_pages + 1, _CONCURRENT):
            page_range = range(batch_start, min(batch_start + _CONCURRENT, num_pages + 1))
            batch = [_fetch_page(client, key, p) for p in page_range]
            results = await asyncio.gather(*batch)
            for p, r in zip(page_range, results):
                if not r:
                    print(f"[public_restroom] page={p} 결과 0건 (최종)")
                all_items.extend(r)
            print(f"[public_restroom]   {len(all_items)}/{total} (batch {batch_start}-{batch_start+_CONCURRENT-1})")
            # 배치 간 짧은 sleep — rate limit 회피
            await asyncio.sleep(0.3)

    print(f"[public_restroom] 다운로드 완료: {len(all_items)}건")
    return all_items


async def load_all() -> list[dict]:
    """캐시 우선, 없으면 다운로드."""
    global _MEM_CACHE
    if _MEM_CACHE is not None:
        return _MEM_CACHE

    # 디스크 캐시
    if os.path.exists(_CACHE_PATH):
        try:
            with open(_CACHE_PATH, encoding="utf-8") as f:
                _MEM_CACHE = json.load(f)
            print(f"[public_restroom] 디스크 캐시 로드: {len(_MEM_CACHE)}건")
            return _MEM_CACHE
        except Exception as e:
            print(f"[public_restroom] 디스크 캐시 손상: {e} → 재다운로드")

    # API 다운로드
    t0 = time.time()
    items = await _download_all()
    if items:
        os.makedirs(os.path.dirname(_CACHE_PATH), exist_ok=True)
        with open(_CACHE_PATH, "w", encoding="utf-8") as f:
            json.dump(items, f, ensure_ascii=False)
        print(f"[public_restroom] 디스크 캐시 저장: {_CACHE_PATH} ({time.time()-t0:.1f}s)")
    _MEM_CACHE = items
    return items


async def find_nearest(lat: float, lng: float, radius_m: int = 1000) -> list[dict]:
    """좌표 기준 radius_m 이내 화장실 반환 (거리 오름차순)."""
    rows = await load_all()
    if not rows:
        return []
    results = []
    for r in rows:
        try:
            r_lat = float(r.get("WGS84_LAT") or 0)
            r_lng = float(r.get("WGS84_LOT") or 0)
        except (ValueError, TypeError):
            continue
        if r_lat == 0 or r_lng == 0:
            continue
        d = _haversine_m(lat, lng, r_lat, r_lng)
        if d <= radius_m:
            results.append({**r, "_distance_m": round(d, 1)})
    results.sort(key=lambda x: x["_distance_m"])
    return results
