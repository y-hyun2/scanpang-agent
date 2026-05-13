"""
naver_map_scraper.py
Naver Map의 '이 주소의 장소' 패널을 채우는 내부 API를 직접 호출한다.

엔드포인트: https://map.naver.com/p/api/entry/addressDetailPlace
- 공식 문서화된 OpenAPI가 아니라 Naver Map UI가 사용하는 내부 API다.
- 인증/쿠키 없이 동작하며 totalCount/페이지네이션을 제공한다(페이지당 20개).
- 공식 Local Search OpenAPI의 '키워드당 ~5개 cap' 한계를 우회한다.

내부 API는 언제든 시그니처가 바뀔 수 있으니 실패 시 빈 리스트를 돌려
파이프라인을 깨뜨리지 않는다.
"""

import asyncio
import re

import httpx

_API_URL = "https://map.naver.com/p/api/entry/addressDetailPlace"
_REVERSE_GEOCODE_URL = "https://map.naver.com/p/api/location/geocode"
_FLOOR_RE = re.compile(r"[Bb지하]\d+층|\d+층|\d+F|B\d+층?")
_PER_PAGE = 20
_MAX_PAGES = 10  # 안전한도 (최대 200개)

# 도로명주소에서 "{도로명} {번호}"까지만 추출하기 위한 정규식.
# 뒤에 건물명(Noon Square)·층(4F)·호수 등이 붙으면 API가 404를 반환하므로 잘라낸다.
_ADDRESS_CORE_RE = re.compile(
    r"^(.+?(?:로|길|대로|동|읍|면|리)\s+\d+(?:-\d+)?)(?:\s|$)"
)


def _clean_address(addr: str) -> str:
    """'서울특별시 중구 명동길 14 Noon Square' → '서울특별시 중구 명동길 14'."""
    if not addr:
        return addr
    m = _ADDRESS_CORE_RE.match(addr.strip())
    return m.group(1) if m else addr

_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    ),
    "Referer": "https://map.naver.com/",
    "Accept":  "application/json, text/plain, */*",
}

# 매장이 아닌 편의시설(에스컬레이터·화장실·공중전화 등) — kind='facility'로 분류.
# floor_info에는 들어가지 않지만 추후 편의시설 안내 agent용으로 따로 보관한다.
_FACILITY_CATEGORIES = (
    "이동시설", "화장실", "공중전화", "교통", "전기차충전소", "주차장",
)


def _parse_floor(*texts: str) -> str:
    """주어진 텍스트들에서 첫 번째로 발견되는 층 표기를 반환."""
    for t in texts:
        if not t:
            continue
        m = _FLOOR_RE.search(t)
        if m:
            return m.group(0)
    return ""


def _classify_kind(category: str) -> str:
    """매장(store) / 편의시설(facility) 구분."""
    return "facility" if any(kw in category for kw in _FACILITY_CATEGORIES) else "store"


def _normalize_item(item: dict) -> dict | None:
    name = item.get("name", "") or item.get("display", "")
    categories = item.get("category", []) or []
    category = " > ".join(c for c in categories if c) if isinstance(categories, list) else str(categories)
    if not name:
        return None
    road_addr = item.get("roadAddress", "") or item.get("address", "")
    # 층 정보는 주소에 있을 수도, 매장명에 있을 수도 있음 (예: "... 3F 화장실")
    return {
        "name":     name,
        "category": category,
        "address":  road_addr,
        "floor":    _parse_floor(road_addr, name),
        "kind":     _classify_kind(category),
    }


async def fetch_address_places(
    road_addr: str,
    max_items: int = 200,
) -> list[dict]:
    """
    도로명주소로 '이 주소의 장소' 매장 목록을 가져온다.

    Args:
        road_addr: 도로명주소 (예: '서울특별시 중구 남대문로 67')
        max_items: 최대 수집 개수 (총합 cap, 페이지 자동 계산)

    Returns:
        [{"name", "category", "address", "floor"}]
        API 실패/주소 매칭 실패 시 빈 리스트.
    """
    if not road_addr:
        return []

    # 주소 정규화 → 정규화 결과가 원본과 다르면 둘 다 시도
    cleaned = _clean_address(road_addr)
    candidates = [cleaned]
    if cleaned != road_addr:
        candidates.append(road_addr)

    all_items: list[dict] = []
    seen: set[str] = set()

    try:
        async with httpx.AsyncClient(timeout=10, headers=_HEADERS) as client:
            for query in candidates:
                for page_num in range(1, _MAX_PAGES + 1):
                    try:
                        resp = await client.get(
                            _API_URL,
                            params={"address": query, "page": page_num},
                        )
                        resp.raise_for_status()
                        data = resp.json()
                    except Exception as e:
                        print(f"[naver_map_scraper] page={page_num} 실패 ({query!r}): {e}")
                        break

                    place = data.get("place") or {}
                    items = place.get("list") or []
                    total = int(place.get("totalCount") or 0)

                    if not items:
                        break

                    for raw in items:
                        norm = _normalize_item(raw)
                        if norm is None:
                            continue
                        key = norm["name"]
                        if not key or key in seen:
                            continue
                        seen.add(key)
                        all_items.append(norm)
                        if len(all_items) >= max_items:
                            break

                    if len(all_items) >= max_items or len(all_items) >= total:
                        break

                    await asyncio.sleep(0.1)

                if all_items:  # 정규화된 주소로 잡혔으면 원본 시도 불필요
                    break
    except Exception as e:
        print(f"[naver_map_scraper] 실패 ({road_addr!r}): {e}")
        return []

    print(f"[naver_map_scraper] {len(all_items)}개 수집 ({road_addr!r} → {cleaned!r})")
    return all_items


# 하위 호환: 기존 함수명 유지
scrape_address_places = fetch_address_places


async def reverse_geocode(lat: float, lng: float) -> dict:
    """
    좌표 → 도로명주소·건물명 변환 (Naver Map 내부 API).

    Args:
        lat, lng: WGS84 좌표
    Returns:
        {"addr": str, "bld_nm": str}
        - addr: "서울특별시 중구 명동4길 35" 형태 (실패 시 빈 문자열)
        - bld_nm: Naver가 알고 있는 공식 건물명 (없으면 빈 문자열)
    """
    if not lat or not lng:
        return {"addr": "", "bld_nm": ""}

    try:
        async with httpx.AsyncClient(timeout=10, headers=_HEADERS) as client:
            resp = await client.get(
                _REVERSE_GEOCODE_URL,
                params={
                    "coords":  f"{lng},{lat}",
                    "orders":  "roadaddr,addr",
                },
            )
            resp.raise_for_status()
            data = resp.json()
    except Exception as e:
        print(f"[naver_map_scraper] reverse_geocode 실패 ({lat},{lng}): {e}")
        return {"addr": "", "bld_nm": ""}

    results = data.get("results") or []
    road = next((r for r in results if r.get("name") == "roadaddr"), None)
    if not road:
        return {"addr": "", "bld_nm": ""}

    region = road.get("region") or {}
    area1  = (region.get("area1") or {}).get("name", "")  # 서울특별시
    area2  = (region.get("area2") or {}).get("name", "")  # 중구
    land   = road.get("land") or {}
    road_name = land.get("name", "")          # 명동4길
    number1   = land.get("number1", "")        # 35
    number2   = land.get("number2", "")        # 부번 (있을 때만)

    if not (area1 and area2 and road_name and number1):
        return {"addr": "", "bld_nm": ""}

    num = f"{number1}-{number2}" if number2 else number1
    addr = f"{area1} {area2} {road_name} {num}"

    # 건물명은 addition0.type == "building"
    bld_nm = ""
    for k in ("addition0", "addition1", "addition2", "addition3", "addition4"):
        a = land.get(k) or {}
        if a.get("type") == "building" and a.get("value"):
            bld_nm = a["value"]
            break

    return {"addr": addr, "bld_nm": bld_nm}


async def fetch_place_detail(
    query: str,
    expected_name: str = "",
    expected_addr: str = "",
) -> dict:
    """
    Naver Map에서 장소 상세(영업시간·휴무·전화·편의시설·홈페이지)를 가져온다.
    Playwright로 map.naver.com에 검색 → entryIframe의 Apollo state + 렌더된 DOM 파싱.

    Args:
        query: 검색어 (보통 "{addr} {bld_nm}" 형태)
        expected_name: 빌딩 이름 매칭용. fuzz partial_ratio >= 60이면 OK.
        expected_addr: 도로명주소 매칭용. 정규화된 "{도로명} {번호}" 일치하면 OK.

        name 또는 addr 중 하나라도 매칭되면 신뢰 — 둘 다 어긋날 때만 거절
        (테넌트 매장이 잘못 잡힌 경우 방어).

    Returns:
        {place_id, name, phone, roadAddress, address, category, conveniences,
         open_hours, closed_days, homepage}
        실패 / 매칭 실패 시 빈 dict.
    """
    if not query:
        return {}

    try:
        from playwright.async_api import async_playwright  # type: ignore
    except ImportError:
        print("[naver_map_scraper] playwright 미설치 — place_detail 건너뜀")
        return {}

    result: dict = {}
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            try:
                context = await browser.new_context(
                    user_agent=_HEADERS["User-Agent"],
                    viewport={"width": 1280, "height": 900},
                    locale="ko-KR",
                )
                page = await context.new_page()
                await page.goto(
                    f"https://map.naver.com/p/search/{query}",
                    timeout=30_000, wait_until="domcontentloaded",
                )
                await page.wait_for_timeout(4_000)

                # entryIframe 자동 진입이 안 됐으면 첫 검색결과 클릭
                entry_frame = next(
                    (f for f in page.frames if f.name == "entryIframe"), None,
                )
                if not entry_frame:
                    search_handle = await page.query_selector("iframe#searchIframe")
                    if search_handle:
                        search_frame = await search_handle.content_frame()
                        if search_frame:
                            first = search_frame.locator("li").first
                            if await first.count() > 0:
                                try:
                                    await first.click(timeout=3_000)
                                    await page.wait_for_timeout(4_000)
                                except Exception:
                                    pass
                    entry_frame = next(
                        (f for f in page.frames if f.name == "entryIframe"), None,
                    )

                if not entry_frame:
                    return {}

                await asyncio.sleep(1.5)
                m = re.search(r"/place/(\d+)", entry_frame.url)
                place_id = m.group(1) if m else None
                if not place_id:
                    return {}

                apollo_json = await entry_frame.evaluate(
                    "JSON.stringify(window.__APOLLO_STATE__ || {})"
                )
                import json as _json
                apollo = _json.loads(apollo_json or "{}")
                base = apollo.get(f"PlaceDetailBase:{place_id}") or {}
                if not base:
                    return {}

                name = base.get("name", "") or ""
                got_road = base.get("roadAddress", "") or ""

                # 이름 매칭 OR 주소 매칭 — 둘 다 어긋나면 거절 (테넌트 매장 방어).
                if expected_name or expected_addr:
                    name_ok = False
                    addr_ok = False
                    try:
                        from rapidfuzz import fuzz as _fuzz
                        if expected_name:
                            score = _fuzz.partial_ratio(
                                expected_name.lower().replace(" ", ""),
                                name.lower().replace(" ", ""),
                            )
                            name_ok = score >= 60
                    except ImportError:
                        # rapidfuzz 없으면 이름 검증 통과로 간주
                        name_ok = True

                    if expected_addr:
                        # 도로명+번호 정규화 후 비교 (앞 글자 시/특별시 차이 무시)
                        norm_exp = _clean_address(expected_addr).replace("특별시", "").replace("광역시", "").strip()
                        norm_got = _clean_address(got_road).replace("특별시", "").replace("광역시", "").strip()
                        addr_ok = bool(norm_exp) and norm_exp in norm_got

                    if not (name_ok or addr_ok):
                        print(f"[naver_map_scraper] place_detail 매칭 실패 "
                              f"(expected_name={expected_name!r}, got_name={name!r}, "
                              f"expected_addr={expected_addr!r}, got_road={got_road!r})")
                        return {}

                result = {
                    "place_id":     place_id,
                    "name":         name,
                    "phone":        base.get("phone", "") or "",
                    "roadAddress":  base.get("roadAddress", "") or "",
                    "address":      base.get("address", "") or "",
                    "category":     base.get("category", "") or "",
                    "conveniences": base.get("conveniences", []) or [],
                }

                # 영업시간 펼침 시도 — Naver가 클래스명을 자주 바꾸므로 여러 셀렉터/방법 시도.
                # "펼쳐보기" 텍스트는 .place_blind(스크린리더 전용)에 있어 직접 클릭 불가.
                # 최대 3번까지 펼침을 시도 (영업시간 + 요일별 토글이 따로 있는 경우 대비).
                expand_selectors = [
                    "a[role='button'][aria-expanded='false']",
                    "button[aria-expanded='false']",
                    "div.O8qbU a[role='button']",
                    "div.O8qbU button",
                ]
                for _ in range(3):
                    clicked = False
                    for sel in expand_selectors:
                        try:
                            btn = entry_frame.locator(sel).first
                            if await btn.count() > 0:
                                # 영업시간 영역 안에 있는 토글인지 확인 (다른 토글 오작동 방지)
                                ok = await btn.evaluate(
                                    "el => !!el.closest('div.O8qbU') || "
                                    "el.parentElement?.innerText?.includes('영업')"
                                )
                                if ok:
                                    await btn.click(timeout=1_500, force=True)
                                    await entry_frame.wait_for_timeout(1_000)
                                    clicked = True
                                    break
                        except Exception:
                            continue
                    if not clicked:
                        break

                dom = await entry_frame.evaluate(r"""
                    () => {
                        const r = {};
                        // 스크린리더용 텍스트(place_blind) 제거 후 가시 텍스트만 추출.
                        const visText = (el) => {
                            if (!el) return '';
                            const clone = el.cloneNode(true);
                            clone.querySelectorAll('.place_blind').forEach(b => b.remove());
                            return (clone.innerText || '').trim().replace(/\s+/g, ' ');
                        };

                        // 영업시간 블록(div.O8qbU)을 우선 식별 — 못 찾으면 em "영업" 부모로 fallback.
                        let bizBlock = document.querySelector('div.O8qbU');
                        if (!bizBlock) {
                            const ems = Array.from(document.querySelectorAll('em'));
                            const bizEm = ems.find(e => /영업/.test(e.textContent || ''));
                            if (bizEm) bizBlock = bizEm.closest('div, li');
                        }

                        if (bizBlock) {
                            // 펼쳐진 상태면 요일별 행이 보임. 클래스명이 자주 바뀌므로
                            // "월/화/수/목/금/토/일 ..." 패턴 텍스트를 가진 li/div만 모음.
                            const dayPattern = /^(월|화|수|목|금|토|일)(요일)?[\s\t]/;
                            const candidates = Array.from(bizBlock.querySelectorAll('li, div, span'));
                            const dayTexts = [];
                            const seen = new Set();
                            for (const c of candidates) {
                                const t = visText(c);
                                if (dayPattern.test(t) && t.length < 80 && !seen.has(t)) {
                                    seen.add(t);
                                    dayTexts.push(t);
                                }
                            }
                            // 최소 3개 요일 이상 잡혔을 때만 신뢰 (펼침 성공)
                            if (dayTexts.length >= 3) {
                                r.open_hours = dayTexts.slice(0, 7).join(' / ');
                            } else {
                                // 펼침 실패 — 영업시간 블록 전체 텍스트(요약)
                                r.open_hours = visText(bizBlock);
                            }
                        }

                        // 휴무일 (오늘 기준 30일 이내만 노출)
                        const closedEms = Array.from(document.querySelectorAll('em'))
                            .filter(e => /휴무/.test(e.textContent || ''));
                        if (closedEms.length > 0) {
                            const closedEm = closedEms[0];
                            const sib = closedEm.parentElement?.querySelector('.pwY9x')
                                     || closedEm.nextElementSibling
                                     || closedEm.parentElement;
                            if (sib) r.closed_days = visText(sib);
                        }

                        // 외부 홈페이지 URL
                        const all = Array.from(document.querySelectorAll('a[href^="http"]'));
                        const ext = all.find(a => !/(naver|nver|map\.|m\.naver|pcmap)/.test(a.href));
                        if (ext) r.homepage = ext.href;
                        return r;
                    }
                """)
                result.update({k: v for k, v in (dom or {}).items() if v})
            finally:
                await browser.close()
    except Exception as e:
        print(f"[naver_map_scraper] fetch_place_detail 실패 ({query!r}): {e}")
        return {}

    print(f"[naver_map_scraper] place_detail 성공: place_id={result.get('place_id')!r} "
          f"name={result.get('name')!r}")
    return result


if __name__ == "__main__":
    # python -m rag.automation.naver_map_scraper "서울특별시 중구 남대문로 67"
    import sys
    addr = sys.argv[1] if len(sys.argv) > 1 else "서울특별시 중구 남대문로 67"
    result = asyncio.run(fetch_address_places(addr))
    print(f"\n=== {len(result)}개 매장 ===")
    for r in result:
        print(f"  {r}")
