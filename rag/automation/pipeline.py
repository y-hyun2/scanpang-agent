"""
pipeline.py
ufid 하나를 받아 전체 자동화 파이프라인을 실행하고 Supabase place_info에 저장한다.
각 단계 실패 시 partial 결과라도 저장하며 에러를 로그에 기록한다.
"""

import json
from datetime import datetime, timezone

from dotenv import load_dotenv

from core.db import get_pool
from rag.automation.kakao_radius import collect_stores_at_building
from rag.automation.homepage_crawler import crawl_homepage
from rag.automation.llm_extractor import extract_floor_info_from_homepage
from rag.automation.search_collector import (
    fetch_naver_local,
    fetch_naver_address_places,
    naver_image_search,
)
from rag.automation.naver_map_scraper import (
    fetch_address_places as fetch_naver_map_places,
    fetch_place_detail as fetch_naver_place_detail,
    reverse_geocode as naver_reverse_geocode,
)
from rag.automation.govt_api import (
    fetch_building_key,
    fetch_kakao_info,
    fetch_floor_info,
)
from tools.floor_utils import normalize_floor, floor_sort_key

load_dotenv()


# Naver Place API의 link 필드가 공식 홈페이지 대신 소셜 계정을 반환하는 경우가 잦다.
# 다음 도메인이 포함되면 공식 홈페이지로 간주하지 않는다.
_SOCIAL_URL_PATTERNS = (
    "instagram.com", "facebook.com", "twitter.com", "x.com",
    "youtube.com", "youtu.be",
    "blog.naver.com", "cafe.naver.com", "post.naver.com",
    "tiktok.com",
    "pf.kakao.com", "kakao.com/o/",
    "linktr.ee",
)


def _is_social_url(url: str) -> bool:
    if not url:
        return False
    lower = url.lower()
    return any(p in lower for p in _SOCIAL_URL_PATTERNS)


async def _fetch_building(ufid: str) -> dict | None:
    """Supabase buildings 테이블에서 ufid로 건물 메타 + 폴리곤 좌표 조회."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT ufid, bld_nm, grnd_flr, ugrnd_flr, height, usability,
                   center_lat, center_lng,
                   ST_AsGeoJSON(geom)::json -> 'coordinates' -> 0 AS polygon_coords
            FROM buildings
            WHERE ufid = $1
            """,
            ufid,
        )
    return dict(row) if row else None


def _naver_places_to_floor_info(places: list[dict]) -> list[dict]:
    """
    fetch_naver_address_places 결과를 floor_info 형태로 변환한다.
    floor 필드가 있는 매장은 해당 층에, 없는 매장은 '기타'로 묶는다.
    floor 정보가 하나도 없으면 빈 리스트 반환 (층 구조가 의미없음).
    """
    floor_map: dict[str, list[dict]] = {}
    for p in places:
        name  = p.get("name", "").strip()
        cat   = p.get("category", "")
        floor = normalize_floor(p.get("floor", ""))
        if not name:
            continue
        floor_map.setdefault(floor, []).append({"name": name, "category": cat})

    # 층 정보가 전혀 없으면 (전부 '기타') 빈 리스트로 처리
    has_real_floor = any(k != "기타" for k in floor_map)
    if not has_real_floor:
        return []

    return [
        {"floor": f, "stores": stores}
        for f, stores in sorted(floor_map.items(), key=lambda x: floor_sort_key(x[0]))
    ]


def _enrich_govt_with_kakao(
    govt_stores: list[dict],
    kakao_stores: list[dict],
) -> list[dict]:
    """
    소상공인 API 매장명·카테고리를 Kakao 공식 매장명·카테고리로 교체한다.
    매칭 기준: partial_ratio >= 75 (짧은 쪽이 긴 이름에 포함되는 형태 허용).
    매칭되지 않은 매장은 소상공인 원본 유지.
    """
    if not govt_stores or not kakao_stores:
        return govt_stores

    try:
        from rapidfuzz import fuzz as _fuzz
    except ImportError:
        return govt_stores

    def _best_kakao(govt_name: str) -> tuple[str, str]:
        """(kakao_name, kakao_category) 반환. 미매칭이면 ("", "")."""
        best_score, best_name, best_cat = 0, "", ""
        gn = govt_name.lower().replace(" ", "")
        for ks in kakao_stores:
            kn = ks.get("name", "").lower().replace(" ", "")
            score = _fuzz.partial_ratio(gn, kn)
            if score > best_score:
                best_score = score
                best_name  = ks.get("name", "")
                best_cat   = ks.get("category", "")
        return (best_name, best_cat) if best_score >= 75 else ("", "")

    result = []
    replaced = 0
    for floor_item in govt_stores:
        new_stores = []
        for store in floor_item.get("stores", []):
            govt_name = store.get("name", "") if isinstance(store, dict) else store.split("(")[0]
            orig_cat  = store.get("category", "") if isinstance(store, dict) else ""
            kakao_name, kakao_cat = _best_kakao(govt_name)
            if kakao_name:
                new_stores.append({"name": kakao_name, "category": kakao_cat or orig_cat})
                replaced += 1
            else:
                new_stores.append({"name": govt_name, "category": orig_cat})
        result.append({"floor": floor_item["floor"], "stores": new_stores})

    total = sum(len(f["stores"]) for f in result)
    print(f"[pipeline] Kakao 매장명/카테고리 교체: {replaced}/{total}개")
    return result


def _merge_naver_govt(
    naver_places: list[dict],
    govt_stores: list[dict],
    kakao_stores: list[dict],
) -> list[dict]:
    """
    Naver 주소장소(이름/카테고리 정확) + 소상공인 API(층 번호 정확)를 하이브리드 병합.

    규칙:
    - 소상공인 각 매장에 Naver 매장명이 fuzzy 매칭(partial_ratio >= 75)되면
      → Naver 이름/카테고리로 교체, 층은 소상공인 유지
    - Naver에만 있고 소상공인에 없는 매장
      → Naver 주소에 층 명시된 경우 해당 층에, 없으면 '미확인'에 추가
    - 소상공인에만 있고 Naver에 없는 매장
      → Kakao 이름/카테고리 보완 후 유지
    """
    if not govt_stores and not naver_places:
        return []

    try:
        from rapidfuzz import fuzz as _fuzz
        def _match(a: str, b: str) -> int:
            return _fuzz.partial_ratio(a.lower().replace(" ", ""), b.lower().replace(" ", ""))
    except ImportError:
        def _match(a: str, b: str) -> int:
            return 100 if a.lower() in b.lower() or b.lower() in a.lower() else 0

    # Naver 이름 → (name, category, floor) 맵
    naver_lookup = [
        (p["name"], p.get("category", ""), p.get("floor", ""))
        for p in naver_places if p.get("name")
    ]
    # Kakao 이름 → category 맵
    kakao_map = {ks["name"].lower().replace(" ", ""): ks.get("category", "")
                 for ks in kakao_stores if ks.get("name")}

    def _best_naver(govt_name: str) -> tuple[str, str]:
        best_score, best_name, best_cat = 0, "", ""
        for n_name, n_cat, _ in naver_lookup:
            score = _match(govt_name, n_name)
            if score > best_score:
                best_score, best_name, best_cat = score, n_name, n_cat
        return (best_name, best_cat) if best_score >= 75 else ("", "")

    def _best_kakao_cat(name: str) -> str:
        best_score, best_cat = 0, ""
        for k, v in kakao_map.items():
            score = _match(name, k)
            if score > best_score:
                best_score, best_cat = score, v
        return best_cat if best_score >= 75 else ""

    # ── 소상공인 기반 병합 ──
    result = []
    matched_naver_names: set[str] = set()
    naver_replaced = 0

    for floor_item in govt_stores:
        new_stores = []
        for store in floor_item.get("stores", []):
            govt_name = store.get("name", "") if isinstance(store, dict) else store.split("(")[0]
            orig_cat  = store.get("category", "") if isinstance(store, dict) else ""
            n_name, n_cat = _best_naver(govt_name)
            if n_name:
                new_stores.append({"name": n_name, "category": n_cat or orig_cat or _best_kakao_cat(n_name)})
                matched_naver_names.add(n_name)
                naver_replaced += 1
            else:
                kakao_cat = _best_kakao_cat(govt_name)
                new_stores.append({"name": govt_name, "category": kakao_cat or orig_cat})
        result.append({"floor": floor_item["floor"], "stores": new_stores})

    # ── Naver에만 있는 매장 추가 ──
    floor_idx = {f["floor"]: i for i, f in enumerate(result)}
    naver_only = 0
    for n_name, n_cat, n_floor in naver_lookup:
        if n_name in matched_naver_names:
            continue
        target_floor = n_floor or "미확인"
        store_obj = {"name": n_name, "category": n_cat or _best_kakao_cat(n_name)}
        if target_floor in floor_idx:
            result[floor_idx[target_floor]]["stores"].append(store_obj)
        else:
            result.append({"floor": target_floor, "stores": [store_obj]})
            floor_idx[target_floor] = len(result) - 1
        naver_only += 1

    total = sum(len(f["stores"]) for f in result)
    print(f"[pipeline] 하이브리드 병합: 총 {total}개 "
          f"(Naver교체={naver_replaced}, Naver전용추가={naver_only})")
    return result


async def process_one_building(ufid: str) -> dict:
    """
    ufid 하나에 대해 전체 자동화 파이프라인을 실행한다.

    단계: VWorld 조회 → Kakao 기본정보 → 반경 매장수집 → 쿼리빌드
          → 검색수집 → LLM파싱 → 정부DB → 교차검증 → Supabase upsert

    Returns:
        {"ufid": str, "name_ko": str, "status": "ok"|"partial"|"error",
         "confirmed_count": int, "coverage_rate": float, "error": str|None}
    """
    print(f"\n[pipeline] ===== {ufid} 처리 시작 =====")
    result = {
        "ufid":            ufid,
        "name_ko":         "",
        "status":          "error",
        "confirmed_count": 0,
        "coverage_rate":   0.0,
        "error":           None,
    }

    # ── 1) Supabase buildings 테이블에서 건물 메타 + 폴리곤 조회 ─────────────
    building = await _fetch_building(ufid)
    if not building:
        result["error"] = f"ufid {ufid} not found in buildings table"
        print(f"[pipeline] {result['error']}")
        return result

    vworld_bld_nm = (building.get("bld_nm") or "").strip()
    lat = float(building.get("center_lat", 0))
    lng = float(building.get("center_lng", 0))

    # ── 1.5) Naver Reverse Geocode (좌표 → 도로명주소 + 공식 건물명) ─────────
    # VWorld 건물명/주소가 부정확하거나 비어있는 경우가 많아 좌표 기반으로 신뢰도 높은
    # Naver 도로명주소와 공식 건물명을 먼저 확보한다.
    naver_geo: dict = {}
    try:
        naver_geo = await naver_reverse_geocode(lat, lng)
    except Exception as e:
        print(f"[pipeline] naver_reverse_geocode 실패: {e}")

    # 건물명 우선순위: 1) VWorld → 2) Naver 공식 → 3) 빈 문자열
    # VWorld에 이름이 있으면 그걸 우선한다. Naver reverse geocode는 좌표 근처
    # 가장 큰 단지/복합건물명을 반환할 때가 있어(예: 인근 빌라가 모두
    # '마포센트럴 아이파크'로 덮임) VWorld 이름을 잘못 덮어쓰는 문제가 있다.
    # VWorld에 이름이 없을 때만 Naver로 보완한다.
    bld_nm  = vworld_bld_nm or naver_geo.get("bld_nm", "")
    name_ko = bld_nm
    result["name_ko"] = name_ko
    print(f"[pipeline] 건물: {name_ko!r}  ({lat:.5f}, {lng:.5f})  "
          f"[naver={naver_geo.get('bld_nm', '')!r}, vworld={vworld_bld_nm!r}]")

    # 주소 우선순위: Naver reverse geocode 결과를 1순위로 채택
    addr = naver_geo.get("addr", "")

    # Naver reverse geocode 가 행정구역(읍/면/동) 단어 빠진 짧은 주소를 줄 때가
    # 있음 (예: '경기도 용인시 처인구 외대로 6' — 모현읍 누락 → Naver
    # addressDetailPlace API 가 404 → 정부DB fallback → 카테고리 인허가 업태로
    # 들어감). Kakao coord2address 로 풀 도로명주소를 받아 보강.
    if addr and not any(suf in addr for suf in ("읍", "면", "동")):
        try:
            import httpx as _httpx, os as _os
            _key = _os.getenv("KAKAO_REST_API_KEY", "")
            if _key:
                async with _httpx.AsyncClient() as _c:
                    _r = await _c.get(
                        "https://dapi.kakao.com/v2/local/geo/coord2address.json",
                        headers={"Authorization": f"KakaoAK {_key}"},
                        params={"x": lng, "y": lat},
                    )
                    _docs = _r.json().get("documents", [])
                _road = (_docs[0].get("road_address") or {}) if _docs else {}
                _full = _road.get("address_name", "") or ""
                if _full and any(suf in _full for suf in ("읍", "면", "동")):
                    print(f"[pipeline] addr 보강: {addr!r} → {_full!r}")
                    addr = _full
        except Exception as _e:
            print(f"[pipeline] Kakao coord2address 보강 실패: {_e}")

    # ── 2) Kakao 기본 정보 (전화·카테고리 보완용) ────────────────────────────
    # " 및 "로 연결된 복합 건물명(예: "롯데호텔 및 백화점")은 검색 키워드로 적합하지 않아
    # Kakao/Naver 모두 미매칭된다. 첫 번째 이름으로만 시도한다.
    search_nm = bld_nm.split(" 및 ")[0].strip() if bld_nm and " 및 " in bld_nm else bld_nm

    kakao_info: dict = {}
    if search_nm:
        try:
            kakao_info = await fetch_kakao_info(search_nm)
        except Exception as e:
            print(f"[pipeline] fetch_kakao_info 실패: {e}")

    phone    = kakao_info.get("phone", "")
    category = kakao_info.get("category", "")
    # addr은 이미 reverse geocode로 채워졌으니 Kakao addr는 사용하지 않음
    if not addr:
        addr = kakao_info.get("addr", "")

    # ── 2.5) Naver Local 검색 (phone·homepage 보완) ─────────────────────────
    naver_local: dict = {}
    if search_nm:
        try:
            naver_local = await fetch_naver_local(search_nm)
        except Exception as e:
            print(f"[pipeline] fetch_naver_local 실패: {e}")

    # phone: Naver Local 우선, Kakao 보완 (Naver가 직통번호 정확도 높음).
    phone = naver_local.get("phone", "") or phone

    # ── 2.6) Naver Place 상세 정보 (가장 풍부한 단일 소스) ──────────────────
    # map.naver.com에 건물명으로 검색 → 매칭되는 Naver Place 페이지에서
    # 영업시간·휴무일·전화·편의시설·홈페이지를 한 번에 수집한다.
    # 빌딩 자체가 Naver Place에 등록 안 된 경우(예: 작은 오피스 빌딩) 이름 매칭
    # (fuzz partial_ratio < 60)에 걸려 빈 dict 반환 → 기존 LLM 추출이 fallback.
    place_detail: dict = {}
    if bld_nm:
        place_query = f"{addr} {bld_nm}".strip() if addr else bld_nm
        try:
            place_detail = await fetch_naver_place_detail(
                place_query, expected_name=bld_nm, expected_addr=addr,
            )
        except Exception as e:
            print(f"[pipeline] fetch_naver_place_detail 실패: {e}")

    if place_detail:
        # phone은 Naver Place가 비어있을 때만 보강 (이미 Naver Local에서 받음)
        phone = phone or place_detail.get("phone", "")

    # homepage 우선순위 결정:
    # 1순위) ③ Naver Place detail의 "홈페이지" 버튼 (사용자에게 노출되는 공식 링크 — 최신·정확)
    # 2순위) ② Naver Local의 link 필드 (인스타·블로그 비율 높아 신뢰 낮음)
    # 3순위) ② Kakao의 place_url 필드
    # 소셜·블로그 도메인은 단계별로 거른 뒤 다음 fallback으로 넘어간다.
    def _accept(url: str) -> str:
        return url if (url and not _is_social_url(url)) else ""

    homepage = (
        _accept(place_detail.get("homepage", ""))
        or _accept(naver_local.get("homepage", ""))
        or _accept(kakao_info.get("homepage", ""))
    )
    if homepage:
        src = ("Naver Place" if homepage == place_detail.get("homepage")
               else "Naver Local" if homepage == naver_local.get("homepage")
               else "Kakao")
        print(f"[pipeline] homepage 출처: {src} → {homepage}")

    # ── 2.7) 공식 홈페이지 크롤 ─────────────────────────────────────────────
    homepage_text: str = ""
    if homepage:
        try:
            homepage_text = await crawl_homepage(homepage) or ""
        except Exception as e:
            print(f"[pipeline] crawl_homepage 실패: {e}")

    # ── 2.8) 네이버 "이 주소의 장소" 수집 ───────────────────────────────────
    # 1순위: Naver Map 내부 API(`addressDetailPlace`) — UI에 표시되는 전체 매장
    #        리스트(보통 50~100+개) 수집. Local Search OpenAPI의 5~15개 cap 우회.
    # 2순위: Local Search OpenAPI에 주소로 검색 (내부 API 실패 시 fallback).
    # 3순위: Local Search OpenAPI에 건물명으로 검색.
    naver_raw: list[dict] = []
    if addr:
        try:
            naver_raw = await fetch_naver_map_places(addr)
        except Exception as e:
            print(f"[pipeline] fetch_naver_map_places 실패: {e}")

    if not naver_raw:
        for _query in [q for q in (addr, bld_nm) if q]:
            try:
                naver_raw = await fetch_naver_address_places(_query)
            except Exception as e:
                print(f"[pipeline] fetch_naver_address_places 실패 ({_query!r}): {e}")
            if naver_raw:
                break

    # 매장(store)과 편의시설(facility) 분리.
    # kind 필드는 naver_map_scraper 결과에만 있으므로 없으면 store로 간주.
    naver_addr_places: list[dict] = [p for p in naver_raw if p.get("kind", "store") == "store"]
    naver_facilities: list[dict]  = [p for p in naver_raw if p.get("kind") == "facility"]
    if naver_facilities:
        print(f"[pipeline] 편의시설(화장실/엘리베이터 등) 분리 보관: {len(naver_facilities)}개")

    # ── 2.9) 우선순위 1·2 조기 확정 ─────────────────────────────────────────
    # 홈페이지 LLM 추출(1순위) 또는 Naver 층 명시 데이터(2순위)로 floor_info가
    # 이미 채워질 수 있으면 Kakao radius·정부DB API 호출을 스킵해 시간 절약.
    homepage_floor_info: list[dict] = []
    if homepage_text:
        try:
            raw_hp = await extract_floor_info_from_homepage(bld_nm, homepage_text)
            # LLM이 "3층"/"3F"/"B1" 혼용해 출력 → 통일 키로 정규화 후 동일 층 병합
            merged: dict[str, list[dict]] = {}
            for f in raw_hp:
                key = normalize_floor(f.get("floor", ""))
                merged.setdefault(key, []).extend(f.get("stores", []))
            homepage_floor_info = [
                {"floor": k, "stores": s}
                for k, s in sorted(merged.items(), key=lambda x: floor_sort_key(x[0]))
            ]
        except Exception as e:
            print(f"[pipeline] extract_floor_info_from_homepage 실패: {e}")

    naver_floor_info = _naver_places_to_floor_info(naver_addr_places)
    floor_info_locked = bool(homepage_floor_info or naver_floor_info)
    if floor_info_locked:
        reason = "홈페이지 LLM" if homepage_floor_info else "Naver 주소장소(층 명시)"
        print(f"[pipeline] floor_info {reason} 확정 → Kakao radius·정부DB 스킵")

    # ── 3) Kakao 건물 소속 매장 수집 (폴리곤+주소 이중 필터) ──────────────
    # floor_info_locked 상태면 정부DB 보정용으로만 쓰이는 kakao_stores가 불필요.
    kakao_stores: list[dict] = []
    if not floor_info_locked:
        try:
            # asyncpg가 json 표현식 결과를 문자열로 반환하는 경우 대비
            polygon_coords = building.get("polygon_coords") or []
            if isinstance(polygon_coords, str):
                polygon_coords = json.loads(polygon_coords)
            kakao_stores = await collect_stores_at_building(
                ufid=ufid,
                bld_polygon_coords=polygon_coords,
                bld_road_address=addr,
                bld_name=bld_nm,
            )
        except Exception as e:
            print(f"[pipeline] collect_stores_at_building 실패: {e}")

    # ── 4) 운영정보 결정 ─────────────────────────────────────────────────────
    # Naver Place detail이 매칭되면 그걸 사용. 안 되면 빈 값으로 둔다.
    # (이전엔 웹검색+LLM(extract_building_info) fallback이 있었는데 정확도 낮고
    #  비용 커서 제거. Naver Place에 없는 작은 건물은 운영정보 자체가 공개 안 된
    #  경우가 많아 빈 값이 차라리 안전함.)
    if place_detail:
        open_hours    = place_detail.get("open_hours", "")
        closed_days   = place_detail.get("closed_days", "")
        conv = place_detail.get("conveniences", []) or []
        parking_terms = [c for c in conv if "주차" in c or "발렛" in c]
        parking_info  = ", ".join(parking_terms) if parking_terms else ""
    else:
        open_hours    = ""
        closed_days   = ""
        parking_info  = ""
    admission_fee = ""  # 정형 소스 없음

    # ── 5) 네이버 이미지 검색 ───────────────────────────────────────────────
    image_url = ""
    if bld_nm:
        try:
            image_url = await naver_image_search(f"{bld_nm} 외관")
        except Exception as e:
            print(f"[pipeline] naver_image_search 실패: {e}")

    # ── 6) 정부 DB (소상공인 API) ───────────────────────────────────────────
    # floor_info_locked 상태면 3·4순위 fallback이 발동될 일이 없어 정부DB 불필요.
    govt_stores: list[dict] = []
    if not floor_info_locked:
        try:
            building_key = await fetch_building_key(addr) if addr else None
            if building_key:
                raw_govt = await fetch_floor_info(building_key)
                # 정부DB floor("1", "B1", "기타")를 통일 키("1F", "B1", "기타")로 정규화
                # → "3F" vs "3" 분리 버킷 방지.
                govt_stores = [
                    {"floor": normalize_floor(f["floor"]), "stores": f.get("stores", [])}
                    for f in raw_govt
                ]
                print(f"[pipeline] 정부DB: {sum(len(f['stores']) for f in govt_stores)}개 매장")
        except Exception as e:
            print(f"[pipeline] 정부DB 조회 실패: {e}")

    # ── 7) floor_info 우선순위 결정 (4단계) ──────────────────────────────────
    # 1순위: 홈페이지 LLM 추출 (homepage_floor_info — §2.9에서 계산)
    # 2순위: Naver 주소장소 中 층 명시 (naver_floor_info — §2.9에서 계산)
    # 3순위: Naver + 소상공인 하이브리드 (이름·카테고리=Naver, 층=정부DB)
    # 4순위: 소상공인 + Kakao 이름 교체
    # (이전 5순위 "LLM confirmed only"는 웹검색+LLM 의존이라 제거.)
    if homepage_floor_info:
        floor_info_list = homepage_floor_info
        print(f"[pipeline] floor_info 출처: 홈페이지 ({len(homepage_floor_info)}개 층)")
    elif naver_floor_info:
        floor_info_list = naver_floor_info
        total = sum(len(f["stores"]) for f in naver_floor_info)
        print(f"[pipeline] floor_info 출처: 네이버 주소장소 ({len(naver_floor_info)}개 층, {total}개 매장)")
    elif naver_addr_places and govt_stores:
        floor_info_list = _merge_naver_govt(naver_addr_places, govt_stores, kakao_stores)
        print(f"[pipeline] floor_info 출처: Naver×정부DB 하이브리드")
    elif govt_stores:
        floor_info_list = _enrich_govt_with_kakao(govt_stores, kakao_stores)
        print(f"[pipeline] floor_info 출처: 정부DB+Kakao")
    else:
        floor_info_list = []
        print(f"[pipeline] floor_info 채울 소스 없음")

    # confirmed_count·coverage_rate는 cross_validate 제거로 의미를 잃음 → 0으로 고정.
    result["confirmed_count"] = sum(len(f.get("stores", [])) for f in floor_info_list)
    result["coverage_rate"]   = 0.0

    # ── 9) Supabase upsert ───────────────────────────────────────────────────

    try:
        pool = await get_pool()
        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO place_info
                    (ufid, name_ko, lat, lng, addr, phone, category,
                     floor_info, coverage_rate, last_updated, source,
                     image_url, homepage, open_hours, closed_days,
                     parking_info, admission_fee, facilities)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10,$11,
                        $12,$13,$14,$15,$16,$17,$18::jsonb)
                ON CONFLICT (ufid) DO UPDATE SET
                    name_ko       = EXCLUDED.name_ko,
                    addr          = EXCLUDED.addr,
                    phone         = EXCLUDED.phone,
                    category      = EXCLUDED.category,
                    floor_info    = EXCLUDED.floor_info,
                    coverage_rate = EXCLUDED.coverage_rate,
                    last_updated  = EXCLUDED.last_updated,
                    source        = EXCLUDED.source,
                    image_url     = EXCLUDED.image_url,
                    homepage      = EXCLUDED.homepage,
                    open_hours    = EXCLUDED.open_hours,
                    closed_days   = EXCLUDED.closed_days,
                    parking_info  = EXCLUDED.parking_info,
                    admission_fee = EXCLUDED.admission_fee,
                    facilities    = EXCLUDED.facilities
                """,
                ufid, name_ko, lat, lng, addr, phone, category,
                json.dumps(floor_info_list, ensure_ascii=False),
                result["coverage_rate"],
                datetime.now(timezone.utc),
                "automated",
                image_url or None, homepage or None,
                open_hours or None, closed_days or None,
                parking_info or None, admission_fee or None,
                json.dumps(naver_facilities, ensure_ascii=False) if naver_facilities else None,
            )
        print(f"[pipeline] Supabase upsert 완료: {ufid}")
        # floor_info가 어떤 소스로든 채워졌으면 성공으로 간주.
        # floor_info_locked일 때 govt_stores가 비어있어도 1·2순위로 채워졌기 때문에 ok.
        result["status"] = "ok" if floor_info_list else "partial"
    except Exception as e:
        result["error"] = f"Supabase upsert 실패: {e}"
        print(f"[pipeline] {result['error']}")
        result["status"] = "partial"

    print(f"[pipeline] ===== {ufid} 완료 status={result['status']} =====\n")
    return result
