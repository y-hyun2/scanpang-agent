"""
kakao_scraper.py
Kakao Local API 로 place_id 를 받아 `place.map.kakao.com/{id}` 페이지를
Playwright 로 직접 진입해 상세를 긁는다.

설계 의도:
- naver_place 는 매장명으로 검색 → 클릭 → entryIframe 진입 패턴이라 같은 이름의
  다른 지점이 잡혀 매칭 검증에서 거절되는 경우가 잦음.
- Kakao 는 Local Keyword API 가 좌표 기반 정렬 결과를 주고, 그 결과의 `place_url`
  에서 매장 id 를 바로 추출 가능. 그 id 로 페이지 진입하면 매칭 문제 0.
- 영업시간/홈페이지/주소/전화/이미지 추출에 충분. 단 메뉴·편의시설·intro 는
  Kakao 페이지에 안 노출되므로 그 부분은 naver_place 보강이 필요(dispatcher merge).

반환 dict 형식은 naver_place 와 동일.
"""

import os
import re

import httpx


_KAKAO_LOCAL_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"


async def _find_place_info(store_name: str, lat: float, lng: float) -> dict:
    """
    Kakao Local keyword API → place_id + 매장 메타정보(addr/phone/lat/lng).

    Kakao Local 응답과 place.map.kakao.com 페이지의 정합성을 보장할 수 없음
    (같은 place_id 인데 응답·페이지 주소가 다른 매장 실제로 존재 — 예: 우체국365코너
    Local='서울 서대문구', 페이지='부산 사하구'). 따라서 Local API 응답을 신뢰할
    base 로 두고, 페이지 데이터는 시·도가 일치할 때만 영업시간/홈페이지/이미지만 채택.
    """
    key = os.getenv("KAKAO_REST_API_KEY", "")
    if not key:
        return {}
    params = {
        "query": store_name,
        "x": str(lng),
        "y": str(lat),
        "radius": 1000,
        "size": 1,
    }
    headers = {"Authorization": f"KakaoAK {key}"}
    async with httpx.AsyncClient() as c:
        r = await c.get(_KAKAO_LOCAL_URL, headers=headers, params=params)
        r.raise_for_status()
        docs = r.json().get("documents", [])
    if not docs:
        return {}
    d = docs[0]
    url = d.get("place_url", "")
    m = re.search(r"/(\d+)/?$", url)
    if not m:
        return {}
    cat_parts = d.get("category_name", "").split(" > ")
    short_cat = cat_parts[1] if len(cat_parts) > 1 else (cat_parts[0] if cat_parts else "")
    return {
        "place_id":   m.group(1),
        "place_name": d.get("place_name", ""),
        "addr":       d.get("road_address_name", ""),
        "phone":      d.get("phone", ""),
        "category":   short_cat,
        "lat":        float(d.get("y", 0)),
        "lng":        float(d.get("x", 0)),
    }


def _major_region(addr: str) -> str:
    """주소의 시·도 부분만 추출 — '서울'/'부산'/'경기' 등."""
    if not addr:
        return ""
    first = addr.strip().split()[0]
    # '서울특별시' → '서울', '경상남도' → '경상남도' 등 — 앞 2글자 비교가 가장 robust.
    return first[:2]


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    if not store_name:
        return {}
    try:
        from playwright.async_api import async_playwright  # type: ignore
    except ImportError:
        print("[kakao_scraper] playwright 미설치")
        return {}

    local = await _find_place_info(store_name, lat, lng)
    if not local:
        print(f"[kakao_scraper] Local API 결과 없음 ({store_name!r})")
        return {}
    place_id = local["place_id"]

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

                # 영업정보 펼치기 토글 — '펼치기' 텍스트이면서 '영업'을 ancestor div 에 포함.
                # 토글이 여러 개라(주소/영업/...) 영업 박스의 것만 골라서 클릭.
                try:
                    await page.evaluate(r"""
                        () => {
                            const candidates = document.querySelectorAll('button, a, span[role=button]');
                            for (const c of candidates) {
                                const txt = (c.innerText || '').trim();
                                if (txt !== '펼치기') continue;
                                const wrapper = c.closest('div');
                                if (wrapper && /영업/.test(wrapper.innerText || '')) {
                                    c.click();
                                    return;
                                }
                            }
                        }
                    """)
                    await page.wait_for_timeout(1_200)
                except Exception:
                    pass

                # DOM 파싱 — 영업시간/홈페이지/주소/전화/이미지/카테고리
                data = await page.evaluate(r"""
                    () => {
                        const r = { open_hours: '', closed_days: '', homepage: '',
                                    addr: '', phone: '', category: '', image_urls: [] };

                        // 카테고리: og:title 아래 카테고리 span (예: "은행")
                        const catEl = document.querySelector('.info_cate, .ico_subcate, .txt_subcate');
                        if (catEl) r.category = (catEl.innerText || '').trim();

                        // 영업시간 — 펼친 상태에서 요일 라인 매치
                        // 패턴: "화(5/19) 09:00 ~ 16:00", "토(5/23) 휴무일"
                        const bodyTxt = document.body.innerText || '';
                        const days = ['월','화','수','목','금','토','일'];
                        const openLines = [];
                        const closedLines = [];
                        const dayHourRe = /([월화수목금토일])\s*\(\d+\/\d+\)\s*(\d{1,2}:\d{2})\s*~\s*(\d{1,2}:\d{2})/g;
                        const dayClosedRe = /([월화수목금토일])\s*\(\d+\/\d+\)\s*([^\n]{0,40}?(?:휴무|휴일|휴업)[^\n]{0,40})/g;
                        const seen = new Set();
                        let m;
                        while ((m = dayHourRe.exec(bodyTxt)) !== null) {
                            if (seen.has(m[1])) continue;
                            seen.add(m[1]);
                            openLines.push(`${m[1]} ${m[2]}-${m[3]}`);
                        }
                        while ((m = dayClosedRe.exec(bodyTxt)) !== null) {
                            const reason = m[2].replace(/[\s ]+/g, ' ').trim();
                            closedLines.push(`${m[1]} ${reason}`);
                        }
                        // openLines 정렬 (월~일)
                        openLines.sort((a,b) => days.indexOf(a[0]) - days.indexOf(b[0]));
                        if (openLines.length) r.open_hours = openLines.join(' / ');
                        if (closedLines.length) r.closed_days = closedLines.join(' / ');

                        // 홈페이지 — .link_detail (kakao 페이지 본문에 외부 url 노출)
                        const homeA = document.querySelector('.link_detail');
                        if (homeA) r.homepage = homeA.href || homeA.innerText.trim();

                        // 주소·전화 — .unit_default 라벨/값 매핑
                        document.querySelectorAll('.unit_default').forEach(u => {
                            const labelEl = u.querySelector('.tit_default, strong');
                            const valEl   = u.querySelector('.txt_detail, .link_address');
                            if (!labelEl || !valEl) return;
                            const label = (labelEl.innerText || '').trim();
                            const value = (valEl.innerText || '').trim();
                            if (label.includes('주소') && !r.addr) r.addr = value.split(/\(우\)|복사/)[0].trim();
                            else if (label.includes('전화') && !r.phone) r.phone = value;
                        });
                        // fallback: .txt_address 단독
                        if (!r.addr) {
                            const addrEl = document.querySelector('.txt_address');
                            if (addrEl) r.addr = (addrEl.innerText || '').trim();
                        }

                        // 이미지 — img[src*=cthumb] 매장 사진만, 로고/아이콘 제외
                        const seenImg = new Set();
                        document.querySelectorAll('img').forEach(i => {
                            const src = i.src || i.dataset?.src;
                            if (!src || seenImg.has(src)) return;
                            if (!/kakaocdn|daumcdn/i.test(src)) return;
                            if (/logo|sprite|icon|blank/i.test(src)) return;
                            if (r.image_urls.length >= 6) return;
                            seenImg.add(src);
                            r.image_urls.push(src);
                        });
                        return r;
                    }
                """)

                # 매장명/place_id 도 같이 details 로 넘김.
                name_el = await page.evaluate(
                    "document.querySelector('.tit_location, .tit_name, h1, h2')?.innerText?.trim() || ''"
                )

            finally:
                await browser.close()
    except Exception as e:
        print(f"[kakao_scraper] fetch 실패 ({store_name!r}): {e}")
        return {}

    # ── 시·도 정합성 검증 ─────────────────────────────────────────────────
    # Kakao Local API 의 응답 좌표·주소는 신뢰할 수 있는 base (호출 시 우리가
    # 좌표/radius 를 강제). 그러나 같은 place_id 의 페이지 데이터가 전혀 다른
    # 시·도(예: 우체국365코너 Local='서울 서대문구', 페이지='부산 사하구')일 수
    # 있음. 이런 경우 페이지에서 가져온 영업시간/홈페이지/이미지 모두 다른 매장
    # 정보일 가능성이 높으므로 통째로 거절하고 Local API addr/phone 만 채택.
    page_addr  = data.get("addr", "")
    local_addr = local["addr"]
    page_region  = _major_region(page_addr)
    local_region = _major_region(local_addr)
    region_match = (not page_region) or (not local_region) or (page_region == local_region)
    if not region_match:
        print(f"[kakao_scraper] 시·도 불일치 — Local={local_region!r} vs Page={page_region!r}, "
              f"페이지 데이터 거절 ({store_name!r})")
        data = {}

    final_addr = local_addr or data.get("addr", "")
    final_phone = local["phone"] or data.get("phone", "")
    print(f"[kakao_scraper] 성공: place_id={place_id} name={name_el!r} addr={final_addr!r}")

    return {
        "phone":       final_phone,
        "addr":        final_addr,
        "homepage":    data.get("homepage", ""),
        "category":    local["category"] or data.get("category", "") or category_name,
        "open_hours":  data.get("open_hours", ""),
        "closed_days": data.get("closed_days", ""),
        # 이미지는 Naver Place '업체 사진' 만 채택 — kakao 사진은 사용하지 않음
        # (dispatcher merge 가 빈 list 를 미보강 상태로 보고 naver_place 가 보강).
        "image_urls":  [],
        "floor":       "",  # Kakao 페이지엔 층 정보 없음 (naver base.road 보강 필요)
        "details": {
            "place_id":     place_id,
            "matched_name": name_el or local["place_name"] or store_name,
            "category":     local["category"] or data.get("category", "") or category_name,
        },
        "source": "kakao_scraper",
    }
