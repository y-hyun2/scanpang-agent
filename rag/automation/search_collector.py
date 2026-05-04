"""
search_collector.py
query_builder가 생성한 쿼리 스펙을 받아 네이버/카카오 Search API를 호출하고
결과를 정규화해 반환한다. HTML 태그 제거 및 중복 URL 필터링 포함.
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
KAKAO_REST_API_KEY  = os.getenv("KAKAO_REST_API_KEY", "")

_HTML_TAG_RE = re.compile(r"<[^>]+>")


def _strip_html(text: str) -> str:
    return _HTML_TAG_RE.sub("", text).strip()


def _naver_available() -> bool:
    return bool(NAVER_CLIENT_ID and NAVER_CLIENT_SECRET)


def _kakao_available() -> bool:
    return bool(KAKAO_REST_API_KEY)


async def _fetch_naver(
    client: httpx.AsyncClient,
    query: str,
    endpoint: str,
    intent: str,
) -> list[dict]:
    if not _naver_available():
        return []
    try:
        resp = await client.get(
            f"https://openapi.naver.com/v1/search/{endpoint}.json",
            headers={
                "X-Naver-Client-Id":     NAVER_CLIENT_ID,
                "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
            },
            params={"query": query, "display": 5},
        )
        resp.raise_for_status()
        items = resp.json().get("items", [])
    except Exception as e:
        print(f"[search_collector] 네이버 {endpoint} 실패 ({query!r}): {e}")
        return []

    source = "naver_web" if endpoint == "webkr" else "naver_blog"
    results = []
    for item in items:
        results.append({
            "title":   _strip_html(item.get("title", "")),
            "snippet": _strip_html(item.get("description", "")),
            "url":     item.get("link", "") or item.get("bloggerlink", ""),
            "source":  source,
            "intent":  intent,
            "query":   query,
        })
    return results


async def _fetch_kakao_web(
    client: httpx.AsyncClient,
    query: str,
    intent: str,
) -> list[dict]:
    if not _kakao_available():
        return []
    try:
        resp = await client.get(
            "https://dapi.kakao.com/v2/search/web",
            headers={"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"},
            params={"query": query, "size": 5},
        )
        resp.raise_for_status()
        docs = resp.json().get("documents", [])
    except Exception as e:
        print(f"[search_collector] 카카오 web 실패 ({query!r}): {e}")
        return []

    results = []
    for doc in docs:
        results.append({
            "title":   _strip_html(doc.get("title", "")),
            "snippet": _strip_html(doc.get("contents", "")),
            "url":     doc.get("url", ""),
            "source":  "kakao_web",
            "intent":  intent,
            "query":   query,
        })
    return results


async def collect(query_specs: list[dict]) -> list[dict]:
    """
    쿼리 스펙 리스트를 순회하며 각 엔진 API를 호출하고 결과를 정규화·병합한다.

    Args:
        query_specs: [{"query": str, "intent": str, "engine": str}, ...]

    Returns:
        [{"title": str, "snippet": str, "url": str,
          "source": str, "intent": str, "query": str}]
        중복 URL은 제거된다.
    """
    if not query_specs:
        return []

    if not _naver_available():
        print("[search_collector] NAVER_CLIENT_ID / NAVER_CLIENT_SECRET 없음 — 네이버 검색 건너뜀")
    if not _kakao_available():
        print("[search_collector] KAKAO_REST_API_KEY 없음 — 카카오 검색 건너뜀")

    all_results: list[dict] = []
    seen_urls: set[str] = set()

    async with httpx.AsyncClient(timeout=10) as client:
        for spec in query_specs:
            query  = spec["query"]
            intent = spec["intent"]
            engine = spec["engine"]

            if engine == "naver_web":
                batch = await _fetch_naver(client, query, "webkr", intent)
            elif engine == "naver_blog":
                batch = await _fetch_naver(client, query, "blog", intent)
            elif engine == "kakao_web":
                batch = await _fetch_kakao_web(client, query, intent)
            else:
                print(f"[search_collector] 알 수 없는 engine: {engine!r}")
                batch = []

            for item in batch:
                url = item.get("url", "")
                if url and url in seen_urls:
                    continue
                if url:
                    seen_urls.add(url)
                all_results.append(item)

            await asyncio.sleep(0.1)

    print(f"[search_collector] {len(query_specs)}개 쿼리 → {len(all_results)}개 결과 수집")
    return all_results
