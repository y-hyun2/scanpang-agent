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
    """
    Playwright로 페이지를 열어 텍스트를 추출한다.
    JS 드롭다운형 층 셀렉터(롯데/신세계/CGV 등)를 자동으로 클릭해
    각 층의 매장 정보가 DOM에 그려지도록 한다.
    """
    try:
        from playwright.async_api import async_playwright  # type: ignore
    except ImportError:
        print("[homepage_crawler] playwright 미설치 — JS 렌더링 건너뜀")
        return None
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            try:
                page = await browser.new_page()
                await page.goto(url, timeout=20_000, wait_until="domcontentloaded")
                await page.wait_for_timeout(2_000)

                # 초기 상태(아무것도 안 누른 상태)의 텍스트
                base_text = await page.inner_text("body")

                # 층 셀렉터(`1F`, `B1`, `지하1층`, `4층` 등) 자동 클릭.
                # 페이지 자체의 JS로 클릭/대기/텍스트 수집을 일괄 처리해
                # Playwright ↔ Python 왕복을 줄임.
                # innerText 변화를 비교해서 클릭으로 새로운 내용이 그려진 경우만 수집.
                try:
                    floor_texts = await page.evaluate(r"""
                        async () => {
                            const sleep = ms => new Promise(r => setTimeout(r, ms));
                            const floorRe = /^(B?\d+F?|지하\s*\d+\s*층?|\d+\s*층|G[F\/]?)$/i;
                            // 클릭 가능한 요소 후보
                            const sels = 'button, a, li, span[role="button"], div[role="tab"]';
                            const cands = Array.from(document.querySelectorAll(sels));
                            const matched = [];
                            const seenText = new Set();
                            for (const el of cands) {
                                const t = (el.innerText || '').trim();
                                if (!t || t.length > 6 || seenText.has(t)) continue;
                                if (!floorRe.test(t)) continue;
                                seenText.add(t);
                                matched.push({el, label: t});
                                if (matched.length > 15) break;
                            }
                            const collected = [];
                            let prevHash = (document.body.innerText || '').length;
                            for (const {el, label} of matched) {
                                try {
                                    el.click();
                                    await sleep(700);
                                    const body = document.body.innerText || '';
                                    // 동일 길이면 변화 없다고 보고 스킵 (대략적 휴리스틱)
                                    if (Math.abs(body.length - prevHash) < 30) continue;
                                    prevHash = body.length;
                                    collected.push(`\n=== ${label} ===\n${body}`);
                                } catch (e) {}
                            }
                            return collected;
                        }
                    """) or []
                except Exception as e:
                    print(f"[homepage_crawler] 층 셀렉터 자동 클릭 실패: {e}")
                    floor_texts = []

                if floor_texts:
                    print(f"[homepage_crawler] 층 셀렉터 {len(floor_texts)}개 클릭으로 추가 텍스트 수집")
                    full = base_text + "\n" + "\n".join(floor_texts)
                else:
                    full = base_text

                return _WHITESPACE_RE.sub(" ", full).strip()
            finally:
                await browser.close()
    except Exception as e:
        print(f"[homepage_crawler] playwright 실패 ({url!r}): {e}")
        return None


# JS 드롭다운형 층 셀렉터를 쓰는 사이트는 httpx로 받은 SPA 껍데기에 층 키워드가
# 우연히 충분히 있어도(메뉴 텍스트 등) 실제 매장은 안 나옴 → 무조건 Playwright 경로 사용.
_JS_HEAVY_DOMAINS = (
    "lotteshopping.com", "shinsegae.com", "hyundaidept.com",
    "cgv.co.kr", "megabox.co.kr",
    "nunsquare.com",
)


def _is_js_heavy(url: str) -> bool:
    lower = url.lower()
    return any(d in lower for d in _JS_HEAVY_DOMAINS)


async def crawl_homepage(url: str) -> Optional[str]:
    """
    URL에서 텍스트를 추출한다.
    1) JS 드롭다운형으로 알려진 도메인은 곧장 Playwright 경로(층 셀렉터 자동 클릭).
    2) 그 외는 httpx로 시도 후 부족하면 Playwright로 fallback.

    Returns:
        추출된 텍스트 문자열, 실패 시 None
    """
    if not url:
        return None

    if _is_js_heavy(url):
        print(f"[homepage_crawler] JS 드롭다운형 도메인 → Playwright 직행")
        text = await _fetch_playwright(url)
        if text and _has_floor_content(text):
            print(f"[homepage_crawler] playwright 성공: {len(text)}자")
            return text
        print(f"[homepage_crawler] 층별 정보 미발견 ({url!r})")
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
