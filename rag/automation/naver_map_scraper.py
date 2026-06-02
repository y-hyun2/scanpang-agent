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
import json
import re
import urllib.parse as _urlparse
from datetime import datetime, timezone, timedelta

import httpx

_KST = timezone(timedelta(hours=9))

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

_UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)

# fetch_address_places / reverse_geocode — JSON API 호출용
_HEADERS = {
    "User-Agent": _UA,
    "Referer": "https://map.naver.com/",
    "Accept":  "application/json, text/plain, */*",
}

# fetch_place_detail — HTML 페이지 요청용 (Accept: application/json 제외)
_HTML_HEADERS = {
    "User-Agent": _UA,
    "Referer": "https://map.naver.com/",
    "Accept-Language": "ko-KR,ko;q=0.9",
}

# 매장이 아닌 편의시설(에스컬레이터·화장실·공중전화 등) — kind='facility'로 분류.
# floor_info에는 들어가지 않지만 추후 편의시설 안내 agent용으로 따로 보관한다.
_FACILITY_CATEGORIES = (
    "이동시설", "화장실", "공중전화", "교통", "전기차충전소", "주차장",
)


def _parse_floor(*texts: str) -> str:
    """
    주어진 텍스트들에서 첫 번째 층 표기를 찾아 **정규화된 키**로 반환한다.
    `tools.floor_utils.normalize_floor` 와 동일 형식 ("B{n}" / "{n}F" / "RF" / "기타")
    으로 통일한다. 매칭 실패 시 빈 문자열.
    """
    from tools.floor_utils import normalize_floor

    for t in texts:
        if not t:
            continue
        m = _FLOOR_RE.search(t)
        if m:
            g_basement_kor, g_basement_b, g_aboveF, g_above_kor = m.groups()
            basement = g_basement_kor or g_basement_b
            if basement:
                return normalize_floor(f"B{int(basement)}")
            above = g_aboveF or g_above_kor
            if above:
                return normalize_floor(f"{int(above)}F")
    return ""


def _classify_kind(category: str) -> str:
    """매장(store) / 편의시설(facility) 구분."""
    return "facility" if any(kw in category for kw in _FACILITY_CATEGORIES) else "store"


def _section_months(name: str) -> list[int]:
    """
    섹션 이름에서 해당 월 목록을 추출한다.
    '3~5/9~10월' → [3,4,5,9,10] / '6월~8월' → [6,7,8] / '기본' → []
    """
    months: list[int] = []
    for part in re.split(r"[/,]", name):
        m = re.search(r"(\d{1,2})\s*월?\s*[~～]\s*(\d{1,2})\s*월?", part)
        if m:
            start, end = int(m.group(1)), int(m.group(2))
            months.extend(range(start, end + 1))
    return months


def _format_weekly_hours(nbh_list) -> str:
    """
    placeDetail.newBusinessHours 배열 → "월 09:00-18:00 / 화 09:00-22:00 / ..." 문자열.

    계절별 복수 섹션('1~2/11~12월', '3~5/9~10월' …)은 현재 월에 맞는 섹션 선택.
    월 매칭 없으면 첫 번째 섹션 fallback (자료실/집중학습실처럼 병렬 섹션도 동일).
    영업일이 하나도 없으면 빈 문자열 반환.
    """
    if not nbh_list or not isinstance(nbh_list, list):
        return ""

    current_month = datetime.now(_KST).month

    section = None
    for s in nbh_list:
        months = _section_months(s.get("name", ""))
        if months and current_month in months:
            section = s
            break
    if section is None:
        section = nbh_list[0]

    daily = section.get("businessHours") or []
    parts = []
    for entry in daily:
        bh = entry.get("businessHours")
        if bh and isinstance(bh, dict):
            start = bh.get("start", "")
            end = bh.get("end", "")
            if start and end:
                parts.append(f"{entry['day']} {start}-{end}")
    return " / ".join(parts)


def _get_detail_open_hours(apollo: dict) -> str:
    """
    상세 페이지 Apollo state에서 전체 주간 영업시간 문자열을 추출한다.
    ROOT_QUERY.placeDetail(...).newBusinessHours → _format_weekly_hours().
    """
    root_q = apollo.get("ROOT_QUERY") or {}
    pd_key = next((k for k in root_q if "placeDetail" in k), None)
    if not pd_key:
        return ""
    pd = _resolve_apollo(apollo, root_q[pd_key])
    nbh = pd.get("newBusinessHours")
    return _format_weekly_hours(nbh)


# Playwright + stealth 로 Naver Place 상세 페이지를 렌더링해 전체 주간 영업시간을
# 긁어온다. 2025년 Naver SPA 전환으로 SSR Apollo state 에서 영업시간이 사라졌고,
# 일반 헤드리스 브라우저는 봇 차단(ncpt.naver.com)에 걸린다. playwright-stealth 로
# 우회 후 영업시간 토글(.gKP9i)을 클릭하면 요일별 스케줄(.w9QyJ)이 펼쳐진다.
_NAVER_PLACE_UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)


async def fetch_business_hours_playwright(place_id: str) -> str:
    """
    place_id 로 Naver Place 상세 페이지를 stealth 브라우저로 열어
    전체 주간 영업시간 텍스트를 반환한다. (raw 텍스트 — 정규화는 별도 단계)

    반환 예:
      "화 14:00 - 24:00 / 수 14:00 - 24:00 / ... / 월 14:00 - 24:00"
      브레이크: "화 11:30 - 23:00, 15:00 - 17:00 브레이크타임 / ..."
      휴무:     "... / 토 휴무 / ..."
    실패 시 빈 문자열.
    """
    if not place_id:
        return ""

    try:
        from playwright.async_api import async_playwright
        from playwright_stealth import Stealth
    except ImportError:
        print("[naver_hours] playwright / playwright-stealth 미설치")
        return ""

    url = f"https://pcmap.place.naver.com/place/{place_id}/home"
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            try:
                ctx = await browser.new_context(locale="ko-KR", user_agent=_NAVER_PLACE_UA)
                page = await ctx.new_page()
                await Stealth().apply_stealth_async(page)
                await page.goto(url, wait_until="networkidle", timeout=25_000)
                await page.wait_for_timeout(1_500)

                # 봇 차단 페이지로 리다이렉트됐는지 확인
                if "ncpt" in page.url:
                    print(f"[naver_hours] 봇 차단 감지 (place_id={place_id})")
                    return ""

                # 영업시간 토글(.gKP9i) 클릭해 요일별 스케줄 펼치기
                await page.evaluate(
                    "() => { const b = document.querySelector('.gKP9i'); if (b) b.click(); }"
                )
                await page.wait_for_timeout(1_200)

                # .w9QyJ 안 .i8cJw(요일) + .H3ua4(시간) 파싱.
                # 라스트오더 라인은 제외, 브레이크타임·휴무는 유지.
                hours = await page.evaluate(r"""
                    () => {
                        // 요일별(월~일) 외에 '매일/평일/주말/공휴일' 집계 라벨도 허용.
                        // 이 라벨로만 등록된 매장(예: 포장마차)이 통째로 누락돼
                        // 라스트오더 요약 텍스트로 폴백되는 것을 막는다.
                        const days = ['월','화','수','목','금','토','일',
                                      '매일','평일','주말','공휴일'];
                        const out = [];
                        document.querySelectorAll('.w9QyJ').forEach(w => {
                            const dayEl = w.querySelector('.i8cJw');
                            if (!dayEl) return;
                            const day = dayEl.innerText.trim();
                            if (!days.includes(day)) return;
                            const hoursEl = w.querySelector('.H3ua4');
                            // H3ua4 없으면 휴무 가능성 — w9QyJ 전체 텍스트에서 day 제외 부분 확인
                            let val;
                            if (hoursEl) {
                                // 라스트오더 라인도 raw에 보존한다(raw = source of truth).
                                // 영업구간 제외 처리는 정규화(open_hours_normalizer) 단계에서.
                                const lines = (hoursEl.innerText || '')
                                    .split('\n')
                                    .map(s => s.trim())
                                    .filter(s => s);
                                val = lines.join(', ');
                            } else {
                                const full = (w.innerText || '').replace(day, '').trim();
                                val = full.includes('휴무') ? '휴무' : full;
                            }
                            if (val) out.push(day + ' ' + val);
                        });
                        return out.join(' / ');
                    }
                """)
                return hours or ""
            finally:
                await browser.close()
    except Exception as e:
        print(f"[naver_hours] fetch 실패 (place_id={place_id}): {type(e).__name__}: {e}")
        return ""


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


def _parse_apollo_state(html: str) -> dict:
    """HTML에서 window.__APOLLO_STATE__ JSON을 추출한다."""
    idx = html.find("__APOLLO_STATE__ =")
    if idx < 0:
        return {}
    raw = html[idx + len("__APOLLO_STATE__ = "):]
    brace_count = 0
    for i, c in enumerate(raw):
        if c == "{":
            brace_count += 1
        elif c == "}":
            brace_count -= 1
            if brace_count == 0:
                try:
                    return json.loads(raw[: i + 1])
                except Exception:
                    return {}
    return {}


def _resolve_apollo(apollo: dict, node) -> dict:
    """Apollo __ref 참조를 실제 객체로 치환한다."""
    if isinstance(node, dict) and "__ref" in node:
        return apollo.get(node["__ref"]) or {}
    return node or {}


def _filter_ldb_images(urls) -> list:
    """ldb-phinf.pstatic.net 도메인 이미지만 반환 (업체 등록 사진)."""
    seen: set = set()
    result = []
    for u in (urls or []):
        if not u or u in seen:
            continue
        if "ldb-phinf.pstatic.net" in u:
            seen.add(u)
            result.append(u)
    return result


def _match_list_item(
    items: list,
    expected_name: str,
    expected_addr: str,
) -> dict | None:
    """
    PlaceListBusinessesItem 목록에서 expected_name / expected_addr 기준 최적 매칭.
    둘 다 비어있으면 첫 번째 아이템 반환.
    """
    if not items:
        return None
    if not expected_name and not expected_addr:
        return items[0]

    try:
        from rapidfuzz import fuzz as _fuzz
        use_fuzz = True
    except ImportError:
        use_fuzz = False

    best: dict | None = None
    best_score = -1

    for item in items:
        name = item.get("name", "") or ""
        addr = item.get("roadAddress", "") or item.get("address", "") or ""

        name_score = 0
        addr_score = 0

        if expected_name:
            if use_fuzz:
                name_score = _fuzz.partial_ratio(
                    expected_name.lower().replace(" ", ""),
                    name.lower().replace(" ", ""),
                )
            else:
                en = expected_name.lower()
                nl = name.lower()
                name_score = 100 if en in nl or nl in en else 0

        if expected_addr:
            norm_exp = _clean_address(expected_addr).replace("특별시", "").replace("광역시", "").strip()
            norm_got = _clean_address(addr).replace("특별시", "").replace("광역시", "").strip()
            # Naver 목록 아이템의 roadAddress는 '명동4길 12'처럼 시·구 prefix가
            # 빠질 때가 많다(반대로 들어올 때도 있음). 그래서 시·구 prefix 포함 여부로
            # 비교하면 정답인데도 거절된다. '{도로명} {번호}' core(끝 두 토큰)로
            # 비교해 prefix 유무에 영향받지 않게 한다.
            exp_core = " ".join(norm_exp.split()[-2:])
            got_core = " ".join(norm_got.split()[-2:])
            addr_match = bool(exp_core and got_core and exp_core == got_core)
            # expected_addr가 주어졌는데 지역 불일치면 하드 거절
            if not addr_match:
                continue
            addr_score = 100

        total = name_score if expected_addr else max(name_score, addr_score)
        if total > best_score:
            best_score = total
            best = item

    if best_score < 60:
        return None
    return best


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
    좌표 → 도로명주소·지번주소·건물명 변환 (Naver Map 내부 API).

    Args:
        lat, lng: WGS84 좌표
    Returns:
        {"addr": str, "addr_jibun": str, "bld_nm": str}
        - addr:       도로명 "서울특별시 중구 명동4길 35" (실패 시 빈 문자열)
        - addr_jibun: 지번  "서울특별시 중구 명동2가 50-14" (없으면 빈 문자열)
        - bld_nm:     Naver가 알고 있는 공식 건물명 (없으면 빈 문자열)

    addressDetailPlace('이 주소의 장소')는 도로명/지번 중 한쪽에만 등록된 매장이
    있어 둘 다 받아 union 하기 위함. pipeline에서 양쪽 호출 후 _merge_places로 병합.
    """
    empty = {"addr": "", "addr_jibun": "", "bld_nm": ""}
    if not lat or not lng:
        return empty

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
        return empty

    results = data.get("results") or []

    # ── 지번주소 (orders=addr) ──────────────────────────────────────────────
    # region.area3 = 동/읍/면(예: 명동2가), land.number1[-number2] = 번지
    addr_jibun = ""
    jibun = next((r for r in results if r.get("name") == "addr"), None)
    if jibun:
        jregion = jibun.get("region") or {}
        ja1 = (jregion.get("area1") or {}).get("name", "")
        ja2 = (jregion.get("area2") or {}).get("name", "")
        ja3 = (jregion.get("area3") or {}).get("name", "")  # 동/읍/면
        jland = jibun.get("land") or {}
        jn1 = jland.get("number1", "")
        jn2 = jland.get("number2", "")
        if ja1 and ja2 and ja3 and jn1:
            jnum = f"{jn1}-{jn2}" if jn2 else jn1
            addr_jibun = f"{ja1} {ja2} {ja3} {jnum}"

    # ── 도로명주소 (orders=roadaddr) + 건물명 ────────────────────────────────
    road = next((r for r in results if r.get("name") == "roadaddr"), None)
    if not road:
        return {"addr": "", "addr_jibun": addr_jibun, "bld_nm": ""}

    region = road.get("region") or {}
    area1  = (region.get("area1") or {}).get("name", "")  # 서울특별시
    area2  = (region.get("area2") or {}).get("name", "")  # 중구
    land   = road.get("land") or {}
    road_name = land.get("name", "")          # 명동4길
    number1   = land.get("number1", "")        # 35
    number2   = land.get("number2", "")        # 부번 (있을 때만)

    if not (area1 and area2 and road_name and number1):
        return {"addr": "", "addr_jibun": addr_jibun, "bld_nm": ""}

    num = f"{number1}-{number2}" if number2 else number1
    addr = f"{area1} {area2} {road_name} {num}"

    # 건물명은 addition0.type == "building"
    bld_nm = ""
    for k in ("addition0", "addition1", "addition2", "addition3", "addition4"):
        a = land.get(k) or {}
        if a.get("type") == "building" and a.get("value"):
            bld_nm = a["value"]
            break

    return {"addr": addr, "addr_jibun": addr_jibun, "bld_nm": bld_nm}


async def fetch_place_detail(
    query: str,
    expected_name: str = "",
    expected_addr: str = "",
    structured_hours: bool = False,
) -> dict:
    """
    Naver Place 상세를 httpx + Apollo state 파싱으로 가져온다.
    (Playwright 기반 접근은 2025-05 Naver 클라이언트 구조 변경으로 폐기)

    pcmap.place.naver.com SSR HTML에 window.__APOLLO_STATE__ 가 포함되어 있어
    브라우저 없이 name·phone·address·conveniences·imageUrls를 추출할 수 있다.
    영업시간 주간 스케줄과 homepage는 GraphQL lazy-load 항목이라 SSR에 없음.

    Args:
        query: 검색어 (장소명 또는 주소)
        expected_name: 이름 매칭 필터 (rapidfuzz partial_ratio >= 60)
        expected_addr: 주소 매칭 필터 (도로명+번호 포함 여부)
        structured_hours: True이면 상세 페이지 Apollo state에서 전체 주간 스케줄을 추출.
                          False(기본)이면 목록 아이템의 newBusinessHours.description 사용.

    Returns:
        {place_id, name, phone, roadAddress, address, category, conveniences,
         open_hours, closed_days, homepage, image_urls}
        실패·매칭 실패 시 빈 dict.
    """
    if not query:
        return {}

    try:
        async with httpx.AsyncClient(timeout=15, headers=_HTML_HEADERS, follow_redirects=True) as client:
            # 1. 검색 페이지 Apollo state → place ID 탐색
            search_url = (
                "https://pcmap.place.naver.com/place/list"
                f"?query={_urlparse.quote(query)}&lang=ko"
            )
            resp = await client.get(search_url)
            apollo_list = _parse_apollo_state(resp.text)

            list_items = [
                v for k, v in apollo_list.items()
                if k.startswith("PlaceListBusinessesItem:")
            ]
            if not list_items:
                print(f"[naver_map_scraper] 검색결과 없음 ({query!r})")
                return {}

            matched = _match_list_item(list_items, expected_name, expected_addr)
            if not matched:
                print(f"[naver_map_scraper] 매칭 실패 ({query!r}, expected={expected_name!r})")
                return {}

            place_id = matched["id"]
            print(f"[naver_map_scraper] place 발견: id={place_id} ({query!r})")

            # 목록 아이템에서 이미지 추출
            image_urls = _filter_ldb_images(matched.get("imageUrls") or [])

            # 2. 상세 페이지 Apollo state → conveniences·추가 이미지·영업시간
            detail_url = f"https://pcmap.place.naver.com/place/{place_id}/home"
            resp2 = await client.get(detail_url)
            apollo_detail = _parse_apollo_state(resp2.text)

            # 영업시간: structured_hours=True면 전체 주간 스케줄 추출.
            #  1순위: Apollo state (구버전 — 현재는 대부분 사라짐)
            #  2순위: Playwright+stealth 로 페이지 렌더링 후 토글 펼쳐 스크랩
            #  3순위: 목록 아이템 newBusinessHours.description (요약 텍스트 폴백)
            if structured_hours:
                open_hours = _get_detail_open_hours(apollo_detail)
                if not open_hours:
                    open_hours = await fetch_business_hours_playwright(place_id)
                if not open_hours:
                    nbh_raw = matched.get("newBusinessHours")
                    nbh = _resolve_apollo(apollo_list, nbh_raw) if nbh_raw else {}
                    open_hours = nbh.get("description", "") if isinstance(nbh, dict) else ""
            else:
                nbh_raw = matched.get("newBusinessHours")
                nbh = _resolve_apollo(apollo_list, nbh_raw) if nbh_raw else {}
                open_hours = nbh.get("description", "") if isinstance(nbh, dict) else ""

            base = {}
            for prefix in ("PlaceDetailBase", "AccommodationDetailBase",
                           "AttractionDetailBase", "RestaurantDetailBase",
                           "CafeDetailBase"):
                candidate = apollo_detail.get(f"{prefix}:{place_id}")
                if candidate:
                    base = candidate
                    break

            # 상세 페이지 전체 JSON에서 ldb-phinf 이미지 추가 수집
            extra = _filter_ldb_images(
                re.findall(
                    r"https://ldb-phinf\.pstatic\.net/[^\"\s]+",
                    json.dumps(apollo_detail, ensure_ascii=False),
                )
            )
            seen_imgs: set = set(image_urls)
            for u in extra:
                if u not in seen_imgs:
                    seen_imgs.add(u)
                    image_urls.append(u)
                if len(image_urls) >= 6:
                    break

            road_detail = base.get("road", "") or ""
            result: dict = {
                "place_id":     place_id,
                "name":         base.get("name") or matched.get("name", ""),
                "phone":        base.get("phone") or matched.get("phone", ""),
                "roadAddress":  base.get("roadAddress") or matched.get("roadAddress", ""),
                "address":      base.get("address") or matched.get("address", ""),
                "category":     base.get("category") or matched.get("category", ""),
                "conveniences": base.get("conveniences") or [],
                "open_hours":   open_hours,
                "closed_days":  "",
                "homepage":     "",
                "road_detail":  road_detail,
                "image_urls":   image_urls,
            }

            # 층 정보 추출 — base.road에서 "X층" 패턴
            m_floor = re.search(
                r"지하\s*(\d+)\s*층|B\s*(\d+)\s*층?|(\d+)\s*F|(\d+)\s*층",
                road_detail,
            )
            if m_floor:
                g = m_floor.groups()
                basement = g[0] or g[1]
                above    = g[2] or g[3]
                if basement:
                    result["floor"] = f"B{int(basement)}"
                elif above:
                    result["floor"] = f"{int(above)}F"

            # 3. 풀 주소(부가 위치 정보) 캡처 — m.place.naver.com SEO 메타.
            #    pcmap.place 의 Apollo state 는 깔끔한 도로명만 (예: "서울 중구 을지로 30"),
            #    m.place 페이지의 data-line-description 속성에 풀 주소 (예:
            #    "서울 중구 을지로 30 을지로입구역7,8번출구방면(롯데백화점 들어가서 우측)")
            #    가 들어있음. 사용자 화면(Naver 검색 결과) 표시와 1:1 일치.
            for mobile_path in (
                f"https://m.place.naver.com/restaurant/{place_id}/home",
                f"https://m.place.naver.com/place/{place_id}/home",
            ):
                try:
                    resp_m = await client.get(mobile_path)
                    if resp_m.status_code != 200:
                        continue
                    m_addr = re.search(
                        r'data-line-description="([^"]+)"',
                        resp_m.text,
                    )
                    if m_addr:
                        result["address_detail"] = m_addr.group(1).strip()
                        break
                except Exception as e:
                    print(f"[naver_map_scraper] address_detail fetch 실패 ({mobile_path[-40:]}): {e}")

        print(f"[naver_map_scraper] place_detail 성공: place_id={place_id!r} "
              f"name={result['name']!r} address_detail={result.get('address_detail', '')!r}")
        return result

    except Exception as e:
        print(f"[naver_map_scraper] fetch_place_detail 실패 ({query!r}): {e}")
        return {}


if __name__ == "__main__":
    # python -m rag.automation.naver_map_scraper "서울특별시 중구 남대문로 67"
    import sys
    addr = sys.argv[1] if len(sys.argv) > 1 else "서울특별시 중구 남대문로 67"
    result = asyncio.run(fetch_address_places(addr))
    print(f"\n=== {len(result)}개 매장 ===")
    for r in result:
        print(f"  {r}")