"""
search_collector.py
Naver Local Search OpenAPI + Naver Image Search OpenAPI 래퍼.
"""

import asyncio
import os
import re
from typing import Optional

import httpx
from dotenv import load_dotenv

load_dotenv()

NAVER_CLIENT_ID     = os.getenv("NAVER_CLIENT_ID", "")
NAVER_CLIENT_SECRET = os.getenv("NAVER_CLIENT_SECRET", "")

_HTML_TAG_RE  = re.compile(r"<[^>]+>")
_FLOOR_RE     = re.compile(r"[Bb지하]\d+층|\d+층")  # "3층", "B2층", "지하1층"


def _strip_html(text: str) -> str:
    return _HTML_TAG_RE.sub("", text).strip()


def _parse_floor(addr: str) -> str:
    """주소 문자열에서 층 정보 추출. '... 3층' → '3층'. 없으면 ''."""
    m = _FLOOR_RE.search(addr)
    return m.group(0) if m else ""


def _naver_available() -> bool:
    return bool(NAVER_CLIENT_ID and NAVER_CLIENT_SECRET)


async def fetch_naver_local(place_name: str) -> dict:
    """네이버 지역 검색 API → name, addr, phone, category, homepage."""
    if not _naver_available():
        return {}
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(
                "https://openapi.naver.com/v1/search/local.json",
                headers={
                    "X-Naver-Client-Id":     NAVER_CLIENT_ID,
                    "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
                },
                params={"query": place_name, "display": 5},
            )
            resp.raise_for_status()
            items = resp.json().get("items", [])
        except Exception as e:
            print(f"[search_collector] 네이버 local 실패 ({place_name!r}): {e}")
            return {}

    if not items:
        return {}
    matched = next(
        (i for i in items if _strip_html(i.get("title", "")) == place_name),
        items[0],
    )
    return {
        "name":     _strip_html(matched.get("title", "")),
        "addr":     matched.get("roadAddress", "") or matched.get("address", ""),
        "phone":    matched.get("telephone", ""),
        "category": matched.get("category", ""),
        "homepage": matched.get("link", ""),
    }


async def fetch_naver_address_places(road_addr: str) -> list[dict]:
    """
    도로명주소로 네이버 로컬 검색 → 해당 주소의 매장 목록을 반환한다.
    ('이 주소의 장소' 기능과 동일한 데이터 소스)

    Naver Local Search API display 최대 5개 제한을 페이지네이션으로 우회해
    최대 25개(5페이지)까지 수집한다.

    Returns:
        [{"name": str, "category": str, "address": str, "floor": str}]
        floor: 주소에 층 정보가 명시된 경우만 채워짐 (e.g. "3층", "B1층")
    """
    if not _naver_available() or not road_addr:
        return []

    places: list[dict] = []
    seen: set[str] = set()

    async with httpx.AsyncClient(timeout=10) as client:
        for start in range(1, 26, 5):  # 1, 6, 11, 16, 21
            try:
                resp = await client.get(
                    "https://openapi.naver.com/v1/search/local.json",
                    headers={
                        "X-Naver-Client-Id":     NAVER_CLIENT_ID,
                        "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
                    },
                    params={"query": road_addr, "display": 5, "start": start},
                )
                resp.raise_for_status()
                items = resp.json().get("items", [])
            except Exception as e:
                print(f"[search_collector] 네이버 주소장소 실패 ({road_addr!r}, start={start}): {e}")
                break

            if not items:
                break

            for item in items:
                name = _strip_html(item.get("title", ""))
                if not name or name in seen:
                    continue
                seen.add(name)
                addr = item.get("roadAddress", "") or item.get("address", "")
                places.append({
                    "name":     name,
                    "category": item.get("category", ""),
                    "address":  addr,
                    "floor":    _parse_floor(addr),
                })

            await asyncio.sleep(0.1)

    print(f"[search_collector] 네이버 주소장소: {len(places)}개 ({road_addr!r})")
    return places


async def naver_image_search(query: str) -> str:
    """네이버 이미지 검색 API → 첫 번째 썸네일 URL (없으면 빈 문자열)."""
    if not _naver_available():
        return ""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(
                "https://openapi.naver.com/v1/search/image",
                headers={
                    "X-Naver-Client-Id":     NAVER_CLIENT_ID,
                    "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
                },
                params={"query": query, "display": 1, "filter": "large"},
            )
            resp.raise_for_status()
            items = resp.json().get("items", [])
        except Exception as e:
            print(f"[search_collector] 네이버 image 실패 ({query!r}): {e}")
            return ""
    return items[0].get("thumbnail", "") if items else ""


