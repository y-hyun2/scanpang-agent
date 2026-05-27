"""
Naver 스크래퍼 진단 스크립트.

사용:
    python scripts/test_naver.py
"""
import asyncio
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

async def main():
    # 1) Playwright 설치 확인
    print("=== 1) Playwright 설치 확인 ===")
    try:
        from playwright.async_api import async_playwright
        print("  ✓ playwright import 성공")
    except ImportError as e:
        print(f"  ✗ playwright import 실패: {e}")
        print("  → pip install playwright && playwright install chromium")
        return

    # 2) Chromium 실행 확인
    print("\n=== 2) Chromium 실행 확인 ===")
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(
                headless=True,
                args=["--disable-blink-features=AutomationControlled", "--no-sandbox"],
            )
            print(f"  ✓ Chromium 실행 성공: {browser.version}")
            await browser.close()
    except Exception as e:
        print(f"  ✗ Chromium 실행 실패: {e}")
        print("  → playwright install chromium")
        return

    # 3) Naver Map 접근 확인
    print("\n=== 3) Naver Map 페이지 로드 확인 ===")
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(
                headless=True,
                args=["--disable-blink-features=AutomationControlled", "--no-sandbox", "--disable-dev-shm-usage"],
            )
            context = await browser.new_context(
                user_agent=(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/124.0.0.0 Safari/537.36"
                ),
                viewport={"width": 1280, "height": 900},
                locale="ko-KR",
                extra_http_headers={"Accept-Language": "ko-KR,ko;q=0.9"},
            )
            await context.add_init_script(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
            )
            page = await context.new_page()

            await page.goto("https://map.naver.com/p/search/경복궁", timeout=30_000, wait_until="domcontentloaded")

            # searchIframe 또는 entryIframe이 생길 때까지 최대 12초 대기
            # Naver가 name 대신 id 속성을 사용하도록 변경함 — id로도 확인
            try:
                await page.wait_for_function(
                    "Array.from(document.querySelectorAll('iframe'))"
                    ".some(f => ['searchIframe','entryIframe'].includes(f.id)"
                    "     || ['searchIframe','entryIframe'].includes(f.name)"
                    "     || (f.src || '').includes('pcmap.place.naver.com'))",
                    timeout=12_000,
                )
                print("  ✓ iframe 감지됨")
            except Exception:
                print("  △ 12초 내 iframe 미발생")

            url = page.url
            title = await page.title()
            print(f"  URL   : {url}")
            print(f"  Title : {title}")

            # Playwright 프레임 목록 (name + url)
            for f in page.frames:
                print(f"  [playwright frame] name={f.name!r}  url={f.url!r}")

            # DOM에서 직접 iframe 속성 읽기
            dom_iframes = await page.evaluate("""
                () => Array.from(document.querySelectorAll('iframe')).map(f => ({
                    id: f.id, name: f.name, src: f.src, className: f.className
                }))
            """)
            print(f"  DOM iframes ({len(dom_iframes)}개):")
            for fi in dom_iframes:
                print(f"    id={fi['id']!r}  name={fi['name']!r}  src={fi['src'][:80]!r}")

            # 스크린샷 저장
            os.makedirs("logs", exist_ok=True)
            await page.screenshot(path="logs/naver_test.png", full_page=False)
            print("  스크린샷 저장: logs/naver_test.png")

            await browser.close()
    except Exception as e:
        print(f"  ✗ 페이지 로드 실패: {e}")

asyncio.run(main())