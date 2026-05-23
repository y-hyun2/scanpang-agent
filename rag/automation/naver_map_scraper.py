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
            addr_score = 100 if norm_exp and norm_exp in norm_got else 0

        total = max(name_score, addr_score)
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
    Naver Place 상세를 httpx + Apollo state 파싱으로 가져온다.
    (Playwright 기반 접근은 2025-05 Naver 클라이언트 구조 변경으로 폐기)

    pcmap.place.naver.com SSR HTML에 window.__APOLLO_STATE__ 가 포함되어 있어
    브라우저 없이 name·phone·address·conveniences·imageUrls를 추출할 수 있다.
    영업시간 주간 스케줄과 homepage는 GraphQL lazy-load 항목이라 SSR에 없음.

    Args:
        query: 검색어 (장소명 또는 주소)
        expected_name: 이름 매칭 필터 (rapidfuzz partial_ratio >= 60)
        expected_addr: 주소 매칭 필터 (도로명+번호 포함 여부)

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

            # 목록 아이템에서 이미지·영업시간 현황 추출
            image_urls = _filter_ldb_images(matched.get("imageUrls") or [])
            nbh_raw = matched.get("newBusinessHours")
            nbh = _resolve_apollo(apollo_list, nbh_raw) if nbh_raw else {}
            open_hours = nbh.get("description", "") if isinstance(nbh, dict) else ""

            # 2. 상세 페이지 Apollo state → conveniences·추가 이미지
            detail_url = f"https://pcmap.place.naver.com/place/{place_id}/home"
            resp2 = await client.get(detail_url)
            apollo_detail = _parse_apollo_state(resp2.text)

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