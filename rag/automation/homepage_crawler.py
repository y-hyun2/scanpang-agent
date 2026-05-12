"""
homepage_crawler.py
건물 공식 홈페이지에서 텍스트를 추출한다.
1차: httpx (정적 HTML) → 텍스트 충분하면 반환
2차: Playwright (JS 렌더링) → playwright 미설치 시 건너뜀
"""

import re
from typing import Optional

import httpx

_MIN_TEXT_LEN = 300
_FLOOR_KEYWORDS = {"층", "floor", "F", "B", "매장", "입점", "브랜드", "안내"}
_HTML_TAG_RE = re.compile(r"<[^>]+>")
_WHITESPACE_RE = re.compile(r"\s+")


def _strip_html(html: str) -> str:
    text = _HTML_TAG_RE.sub(" ", html)
    return _WHITESPACE_RE.sub(" ", text).strip()


def _has_floor_content(text: str) -> bool:
    if len(text) < _MIN_TEXT_LEN:
        return False
    return sum(1 for kw in _FLOOR_KEYWORDS if kw in text) >= 2


async def _fetch_static(url: str) -> Optional[str]:
    try:
        async with httpx.AsyncClient(
            timeout=15, follow_redirects=True,
            headers={"User-Agent": "Mozilla/5.0 (compatible; ScanPang/1.0)"},
        ) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            return _strip_html(resp.text)
    except Exception as e:
        print(f"[homepage_crawler] httpx 실패 ({url!r}): {e}")
        return None


async def _fetch_playwright(url: str) -> Optional[str]:
    try:
        from playwright.async_api import async_playwright  # type: ignore
    except ImportError:
        print("[homepage_crawler] playwright 미설치 — JS 렌더링 건너뜀")
        return None
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            page = await browser.new_page()
            await page.goto(url, timeout=20_000, wait_until="domcontentloaded")
            await page.wait_for_timeout(2_000)
            text = await page.inner_text("body")
            await browser.close()
            return _WHITESPACE_RE.sub(" ", text).strip()
    except Exception as e:
        print(f"[homepage_crawler] playwright 실패 ({url!r}): {e}")
        return None


async def crawl_homepage(url: str) -> Optional[str]:
    """
    URL에서 텍스트를 추출한다.
    httpx로 충분한 층별 내용이 있으면 바로 반환,
    부족하면 Playwright로 재시도한다.

    Returns:
        추출된 텍스트 문자열, 실패 시 None
    """
    if not url:
        return None

    text = await _fetch_static(url)
    if text and _has_floor_content(text):
        print(f"[homepage_crawler] httpx 성공: {len(text)}자")
        return text

    print(f"[homepage_crawler] httpx 내용 부족 → Playwright 시도")
    text = await _fetch_playwright(url)
    if text and _has_floor_content(text):
        print(f"[homepage_crawler] playwright 성공: {len(text)}자")
        return text

    print(f"[homepage_crawler] 층별 정보 미발견 ({url!r})")
    return None
