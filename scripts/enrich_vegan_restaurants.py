"""
enrich_vegan_restaurants.py
vegan_restaurants 테이블의 open_hours / closed_days / homepage / image_urls 를
Kakao(영업시간·홈페이지) + Naver Place(이미지) 스크래핑으로 채운다.

- place_id 있는 레코드: place.map.kakao.com/{place_id} 직접 진입
- place_id 없는 레코드: Kakao Local keyword 검색으로 place_id 먼저 확보
"""

import asyncio
import json
import os
import re

import httpx

from core.db import get_pool

KAKAO_KEY = os.getenv("KAKAO_REST_API_KEY", "")


async def _find_place_id(store_name: str, lat: float, lng: float) -> str:
    """Kakao Local keyword 검색으로 place_id 확보 (place_id 없는 레코드용)."""
    if not KAKAO_KEY:
        return ""
    async with httpx.AsyncClient() as c:
        r = await c.get(
            "https://dapi.kakao.com/v2/local/search/keyword.json",
            headers={"Authorization": f"KakaoAK {KAKAO_KEY}"},
            params={"query": store_name, "x": str(lng), "y": str(lat), "radius": 1000, "size": 1},
        )
        docs = r.json().get("documents", [])
    if not docs:
        return ""
    m = re.search(r"/(\d+)/?$", docs[0].get("place_url", ""))
    return m.group(1) if m else ""


async def _scrape_kakao(place_id: str, store_name: str) -> dict:
    """place.map.kakao.com/{place_id} → open_hours / closed_days / homepage."""
    try:
        from playwright.async_api import async_playwright
    except ImportError:
        print("[enrich] playwright 미설치")
        return {}

    url = f"https://place.map.kakao.com/{place_id}"
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            try:
                ctx = await browser.new_context(
                    viewport={"width": 1280, "height": 900},
                    locale="ko-KR",
                    user_agent=(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/130.0.0.0 Safari/537.36"
                    ),
                )
                page = await ctx.new_page()
                await page.goto(url, timeout=30_000, wait_until="domcontentloaded")
                await page.wait_for_timeout(3_500)

                try:
                    await page.evaluate(r"""
                        () => {
                            const candidates = document.querySelectorAll('button, a, span[role=button]');
                            for (const c of candidates) {
                                const txt = (c.innerText || '').trim();
                                if (txt !== '펼치기') continue;
                                const wrapper = c.closest('div');
                                if (wrapper && /영업/.test(wrapper.innerText || '')) {
                                    c.click(); return;
                                }
                            }
                        }
                    """)
                    await page.wait_for_timeout(1_200)
                except Exception:
                    pass

                data = await page.evaluate(r"""
                    () => {
                        const r = { open_hours: '', closed_days: '', homepage: '' };
                        const bodyTxt = document.body.innerText || '';
                        const days = ['월','화','수','목','금','토','일'];
                        const openLines = [], closedLines = [];
                        const dayHourRe   = /([월화수목금토일])\s*\(\d+\/\d+\)\s*(\d{1,2}:\d{2})\s*~\s*(\d{1,2}:\d{2})/g;
                        const dayClosedRe = /([월화수목금토일])\s*\(\d+\/\d+\)\s*([^\n]{0,40}?(?:휴무|휴일|휴업)[^\n]{0,40})/g;
                        const seen = new Set();
                        let m;
                        while ((m = dayHourRe.exec(bodyTxt)) !== null) {
                            if (seen.has(m[1])) continue;
                            seen.add(m[1]);
                            openLines.push(`${m[1]} ${m[2]}-${m[3]}`);
                        }
                        while ((m = dayClosedRe.exec(bodyTxt)) !== null) {
                            closedLines.push(`${m[1]} ${m[2].replace(/[\s ]+/g,' ').trim()}`);
                        }
                        openLines.sort((a,b) => days.indexOf(a[0]) - days.indexOf(b[0]));
                        if (openLines.length)  r.open_hours  = openLines.join(' / ');
                        if (closedLines.length) r.closed_days = closedLines.join(' / ');
                        const homeA = document.querySelector('.link_detail');
                        if (homeA) r.homepage = homeA.href || homeA.innerText.trim();
                        return r;
                    }
                """)
            finally:
                await browser.close()
    except Exception as e:
        print(f"  [kakao] 스크래핑 실패 ({store_name!r}): {e}")
        return {}

    return data


async def _scrape_naver(store_name: str, lat: float, lng: float) -> list:
    """Naver Place → image_urls."""
    try:
        from rag.automation.naver_map_scraper import fetch_place_detail
        detail = await fetch_place_detail(query=store_name, expected_name=store_name)
        if detail:
            return detail.get("image_urls", []) or []
    except Exception as e:
        print(f"  [naver] 스크래핑 실패 ({store_name!r}): {e}")
    return []


async def main():
    pool = await get_pool()

    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT id, place_id, store_name, lat, lng FROM vegan_restaurants ORDER BY store_name"
        )

    print(f"총 {len(rows)}개 레코드 처리 시작\n")
    ok = fail = 0

    for i, row in enumerate(rows, 1):
        rid        = row["id"]
        place_id   = row["place_id"] or ""
        store_name = row["store_name"]
        lat        = row["lat"] or 0.0
        lng        = row["lng"] or 0.0

        print(f"[{i}/{len(rows)}] {store_name!r}")

        # place_id 없으면 Kakao keyword 검색
        if not place_id:
            print(f"  place_id 없음 → keyword 검색 중...")
            place_id = await _find_place_id(store_name, lat, lng)
            if not place_id:
                print(f"  place_id 확보 실패, 스킵")
                fail += 1
                continue

        # Kakao: open_hours / closed_days / homepage
        kakao = await _scrape_kakao(place_id, store_name)

        # Naver: image_urls
        image_urls = await _scrape_naver(store_name, lat, lng)

        open_hours  = kakao.get("open_hours") or ""
        closed_days = kakao.get("closed_days") or ""
        homepage    = kakao.get("homepage") or ""

        print(f"  hours={open_hours!r}  closed={closed_days!r}  homepage={homepage!r}  images={len(image_urls)}장")

        async with pool.acquire() as conn:
            await conn.execute(
                """
                UPDATE vegan_restaurants
                SET open_hours  = $2,
                    closed_days = $3,
                    homepage    = $4,
                    image_urls  = $5::jsonb
                WHERE id = $1
                """,
                rid,
                open_hours or None,
                closed_days or None,
                homepage or None,
                json.dumps(image_urls, ensure_ascii=False),
            )
        ok += 1

    print(f"\n=== 완료 — UPDATE {ok}, 실패 {fail} ===")


if __name__ == "__main__":
    asyncio.run(main())
