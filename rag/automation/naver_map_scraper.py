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
_FLOOR_RE = re.compile(r"지하\s*(\d+)\s*층|B\s*(\d+)\s*층?|(\d+)\s*F|(\d+)\s*층")
_PER_PAGE = 20
_MAX_PAGES = 20  # 안전한도 (최대 400개). 롯데백화점 등 200+매장 빌딩 대응.

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
    """
    주어진 텍스트들에서 첫 번째 층 표기를 찾아 **정규화된 키**로 반환한다.
    "3층"/"3F"/"3 층"/"지상3층"은 모두 "3F"로,
    "지하1층"/"B1"/"B1층"은 모두 "B1"으로 통일한다.
    """
    for t in texts:
        if not t:
            continue
        m = _FLOOR_RE.search(t)
        if m:
            g_basement_kor, g_basement_b, g_aboveF, g_above_kor = m.groups()
            # 지하 그룹이 잡혔으면 B{n}
            basement = g_basement_kor or g_basement_b
            if basement:
                return f"B{int(basement)}"
            above = g_aboveF or g_above_kor
            if above:
                return f"{int(above)}F"
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
                                except Exception as click_e:
                                    print(f"[naver_map_scraper] 검색결과 클릭 실패 ({query!r}): {click_e}")
                    entry_frame = next(
                        (f for f in page.frames if f.name == "entryIframe"), None,
                    )

                if not entry_frame:
                    print(f"[naver_map_scraper] entryIframe 미발견 ({query!r})")
                    return {}

                await asyncio.sleep(1.5)
                # Naver Place URL은 카테고리별로 path가 다름:
                #   일반 매장:    pcmap.place.naver.com/place/{id}/home
                #   음식점/카페:  pcmap.place.naver.com/restaurant/{id}/home
                #   카페 변형:     pcmap.place.naver.com/cafe/{id}/home
                #   숙박:         pcmap.place.naver.com/accommodation/{id}/home
                m = re.search(r"/(?:place|restaurant|cafe|accommodation|attraction)/(\d+)", entry_frame.url)
                place_id = m.group(1) if m else None
                if not place_id:
                    print(f"[naver_map_scraper] place_id 추출 실패 ({query!r}, url={entry_frame.url!r})")
                    return {}

                apollo_json = await entry_frame.evaluate(
                    "JSON.stringify(window.__APOLLO_STATE__ || {})"
                )
                import json as _json
                apollo = _json.loads(apollo_json or "{}")
                # Apollo state 키도 카테고리별로 prefix 다름. PlaceDetailBase,
                # RestaurantDetailBase, AccommodationDetailBase 등 순회.
                base = {}
                for prefix in ("PlaceDetailBase", "RestaurantDetailBase",
                               "CafeDetailBase", "AccommodationDetailBase",
                               "AttractionDetailBase"):
                    candidate = apollo.get(f"{prefix}:{place_id}")
                    if candidate:
                        base = candidate
                        break
                if not base:
                    print(f"[naver_map_scraper] DetailBase:{place_id} 비어있음 (Apollo 키 {len(apollo)}개)")
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
                    # base.road = "을지로3가역 12번출구 앞, 포포인츠 호텔 명동점 1층"
                    # 매장이 속한 건물명·층 정보. floor 정규화 추출용.
                    "road_detail":  base.get("road", "") or "",
                }
                # 층 정보 추출 — base.road에서 "X층" 패턴
                m_floor = re.search(r"지하\s*(\d+)\s*층|B\s*(\d+)\s*층?|(\d+)\s*F|(\d+)\s*층",
                                    result["road_detail"])
                if m_floor:
                    g = m_floor.groups()
                    basement = g[0] or g[1]
                    above    = g[2] or g[3]
                    if basement:
                        result["floor"] = f"B{int(basement)}"
                    elif above:
                        result["floor"] = f"{int(above)}F"

                # 영업시간 펼침 토글 클릭 — 페이지에 여러 aria-expanded=false 토글이 있으므로
                # "영업시간"이라는 텍스트가 ancestor에 있는 토글만 골라서 클릭한다.
                # 1순위: 클래스명 a.gKP9i (현재 Naver Place의 영업시간 토글 클래스).
                # 2순위: 모든 a/button[aria-expanded=false] 중 ancestor에 '영업' 텍스트 있는 것.
                try:
                    clicked = await entry_frame.evaluate(r"""
                        () => {
                            // 1순위: 정확한 클래스
                            let toggle = document.querySelector('a.gKP9i');
                            if (!toggle) {
                                // 2순위: aria-expanded=false 후보 중 '영업' 텍스트 가진 ancestor 찾기
                                const cands = document.querySelectorAll(
                                    "a[role='button'][aria-expanded='false'], button[aria-expanded='false']"
                                );
                                for (const c of cands) {
                                    const txt = c.innerText || '';
                                    if (/영업|펼쳐보기/.test(txt) && c.closest('div.O8qbU')) {
                                        toggle = c;
                                        break;
                                    }
                                }
                            }
                            if (!toggle) return false;
                            toggle.click();
                            return true;
                        }
                    """)
                    if clicked:
                        await entry_frame.wait_for_timeout(1_500)
                except Exception:
                    pass

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

                        // ── 영업시간 영역 식별 ──
                        // div.O8qbU는 페이지에 여러 개 (주소/영업시간/편의시설 등 섹션마다).
                        // '영업'/'영업시간' em이 포함된 O8qbU만 정확히 골라야 함.
                        const ems = Array.from(document.querySelectorAll('em'));
                        const bizEm = ems.find(e => /영업/.test(e.textContent || ''));
                        let bizBlock = bizEm ? bizEm.closest('div.O8qbU') : null;
                        // closest('div.O8qbU') 실패 fallback: O8qbU 후보들 중 '영업' 텍스트 가진 것
                        if (!bizBlock) {
                            const o8s = Array.from(document.querySelectorAll('div.O8qbU'));
                            bizBlock = o8s.find(d => /영업시간|영업\s*중|영업\s*종료/.test(d.innerText || ''));
                        }

                        if (bizBlock) {
                            // 펼쳐진 후 요일별 영업시간을 깔끔히 추출.
                            // 패턴: "수(5/13)매장07:00 - 17:00드라이브스루..." 같이 노이즈 섞임.
                            // → 정규식으로 "{요일} HH:MM-HH:MM"만 뽑아내고 요일별 중복 제거.
                            const dayHourRe = /^(월|화|수|목|금|토|일)(?:\([\d/]+\))?\s*(?:매장|영업|평일|주말|24시간|연중무휴)?\s*(\d{1,2}:\d{2})\s*[-~]\s*(\d{1,2}:\d{2})/;
                            const all = Array.from(bizBlock.querySelectorAll('li, div, span'));
                            const byDay = new Map();  // 요일별 1개만 보존
                            for (const el of all) {
                                const t = visText(el);
                                if (t.length > 200) continue;
                                const m = t.match(dayHourRe);
                                if (m && !byDay.has(m[1])) {
                                    byDay.set(m[1], `${m[1]} ${m[2]}-${m[3]}`);
                                }
                            }
                            const order = ['월','화','수','목','금','토','일'];
                            const lines = order.filter(d => byDay.has(d)).map(d => byDay.get(d));
                            if (lines.length >= 3) {
                                r.open_hours = lines.join(' / ');
                            } else {
                                // 펼침 실패 — 요약 텍스트 fallback
                                const blockText = visText(bizBlock);
                                r.open_hours = /영업/.test(blockText) ? blockText : '';
                            }
                        }

                        // ── 휴무일 (오늘 기준 30일 이내만 노출) ──
                        const closedEms = Array.from(document.querySelectorAll('em'))
                            .filter(e => /휴무/.test(e.textContent || ''));
                        if (closedEms.length > 0) {
                            const closedEm = closedEms[0];
                            const sib = closedEm.parentElement?.querySelector('.pwY9x')
                                     || closedEm.nextElementSibling
                                     || closedEm.parentElement;
                            if (sib) r.closed_days = visText(sib);
                        }

                        // ── 외부 홈페이지 URL ──
                        const all = Array.from(document.querySelectorAll('a[href^="http"]'));
                        const ext = all.find(a => !/(naver|nver|map\.|m\.naver|pcmap)/.test(a.href));
                        if (ext) r.homepage = ext.href;

                        // ── 메뉴 추출 (카페/식당 핵심) ──
                        // 가격 패턴(N,NNN원 또는 NNNN원)을 가진 짧은 텍스트를
                        // 메뉴 후보로 본다. 가격 앞 텍스트를 이름으로 추출.
                        const priceRe = /(\d{1,3}(,\d{3})+|\d{3,6})\s*원/;
                        const menuCandidates = Array.from(
                            document.querySelectorAll('li, .place_section_content > div > div, .order_list li')
                        );
                        const menuItems = [];
                        const seenMenu = new Set();
                        for (const el of menuCandidates) {
                            const t = visText(el);
                            if (!t || t.length > 80) continue;
                            const m = t.match(priceRe);
                            if (!m) continue;
                            const price = m[0];
                            const name = t.substring(0, t.indexOf(price)).trim();
                            // 이름이 너무 짧거나 가격 포함이면 노이즈
                            if (!name || name.length > 40 || /\d원/.test(name)) continue;
                            const key = name + '|' + price;
                            if (seenMenu.has(key)) continue;
                            seenMenu.add(key);
                            menuItems.push({name, price});
                            if (menuItems.length >= 20) break;
                        }
                        if (menuItems.length > 0) r.menu = menuItems;

                        // ── 이미지 URL ──
                        // Naver 매장 이미지는 pstatic.net CDN. 작은 아이콘·아바타 제외하기
                        // 위해 src에 'restaurant'/'place'/'photo' 포함 또는 width 충분한 것만.
                        const imgs = Array.from(document.querySelectorAll('img'));
                        const imgUrls = [];
                        const seenImg = new Set();
                        for (const img of imgs) {
                            const src = img.src || img.dataset?.src || '';
                            if (!src || seenImg.has(src)) continue;
                            // 외부 CDN이거나 Naver pstatic 매장 이미지만
                            if (!/pstatic\.net|naver\.net/.test(src)) continue;
                            // 작은 아이콘 제외 (icon, sprite, blank 키워드)
                            if (/icon|sprite|blank|spacer|profile/i.test(src)) continue;
                            seenImg.add(src);
                            imgUrls.push(src);
                            if (imgUrls.length >= 6) break;
                        }
                        if (imgUrls.length > 0) r.image_urls = imgUrls;

                        // ── 소개·설명 (관광지/문화시설용) ──
                        // class O8qbU에 '소개' em이 있는 블록 — '영업'과 다른 별도 블록일 수 있음.
                        const introEm = ems.find(e => /소개|설명/.test(e.textContent || ''));
                        if (introEm) {
                            const block = introEm.closest('div.O8qbU, li, div');
                            if (block) {
                                const intro = visText(block);
                                // em 텍스트 자체(=label)는 빼고 본문만
                                const labelText = visText(introEm);
                                r.intro = intro.startsWith(labelText) ? intro.slice(labelText.length).trim() : intro;
                            }
                        }
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
