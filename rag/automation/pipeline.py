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
from rag.automation.llm_extractor import (
    extract_stores,
    extract_building_info,
    extract_floor_info_from_homepage,
)
from rag.automation.query_builder import build_queries
from rag.automation.search_collector import collect, fetch_naver_local, naver_image_search
from rag.automation.validator import cross_validate
from rag.automation.govt_api import (
    fetch_building_key,
    fetch_kakao_info,
    fetch_floor_info,
)

load_dotenv()


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


def _confirmed_to_floor_info(confirmed: list[dict]) -> list[dict]:
    floor_map: dict[str, list[dict]] = {}
    for store in confirmed:
        floor = store.get("floor") or "미확인"
        name  = store.get("name", "").strip()
        cat   = store.get("category") or ""
        if name:
            floor_map.setdefault(floor, []).append({"name": name, "category": cat})

    def _sort_key(floor_str: str):
        if floor_str == "미확인":
            return (2, 0)
        s = floor_str.upper().replace("층", "").replace("F", "").replace("B", "-").strip()
        try:
            n = int(s)
            return (0, n) if n >= 0 else (1, abs(n))
        except ValueError:
            return (2, 0)

    return [
        {"floor": f, "stores": stores}
        for f, stores in sorted(floor_map.items(), key=lambda x: _sort_key(x[0]))
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


def _enrich_govt_with_confirmed(
    govt_stores: list[dict],
    confirmed: list[dict],
) -> list[dict]:
    """confirmed 중 govt_stores에 없는 매장을 층별로 병합한다."""
    if not confirmed:
        return govt_stores

    try:
        from rapidfuzz import fuzz as _fuzz
        def _already_in(name: str, name_set: set[str]) -> bool:
            n = name.lower()
            return any(_fuzz.ratio(n, g) >= 70 for g in name_set)
    except ImportError:
        def _already_in(name: str, name_set: set[str]) -> bool:
            return name.lower() in name_set

    govt_name_set: set[str] = set()
    for floor_item in govt_stores:
        for store in floor_item.get("stores", []):
            n = store.get("name", "") if isinstance(store, dict) else store.split("(")[0]
            if n:
                govt_name_set.add(n.strip().lower())

    extra: dict[str, list] = {}
    for store in confirmed:
        name = store.get("name", "").strip()
        if not name or _already_in(name, govt_name_set):
            continue
        floor = store.get("floor") or "미확인"
        extra.setdefault(floor, []).append(
            {"name": name, "category": store.get("category") or ""}
        )

    if not extra:
        return govt_stores

    result = [{"floor": f["floor"], "stores": list(f["stores"])} for f in govt_stores]
    floor_idx = {f["floor"]: i for i, f in enumerate(result)}
    for floor, stores in extra.items():
        if floor in floor_idx:
            result[floor_idx[floor]]["stores"].extend(stores)
        else:
            result.append({"floor": floor, "stores": stores})
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

    bld_nm  = (building.get("bld_nm") or "").strip()
    lat     = float(building.get("center_lat", 0))
    lng     = float(building.get("center_lng", 0))
    name_ko = bld_nm or "이름 없는 건물"
    result["name_ko"] = name_ko
    print(f"[pipeline] 건물: {name_ko!r}  ({lat:.5f}, {lng:.5f})")

    # ── 2) Kakao 기본 정보 (주소·전화·카테고리) ──────────────────────────────
    kakao_info: dict = {}
    if bld_nm:
        try:
            kakao_info = await fetch_kakao_info(bld_nm)
        except Exception as e:
            print(f"[pipeline] fetch_kakao_info 실패: {e}")

    addr     = kakao_info.get("addr", "")
    phone    = kakao_info.get("phone", "")
    category = kakao_info.get("category", "")

    # ── 2.5) Naver Local 검색 (phone·addr·homepage 보완) ────────────────────
    naver_local: dict = {}
    if bld_nm:
        try:
            naver_local = await fetch_naver_local(bld_nm)
        except Exception as e:
            print(f"[pipeline] fetch_naver_local 실패: {e}")

    # Naver 우선, Kakao 보완 (Naver가 직통번호·도로명주소 정확도 높음)
    phone    = naver_local.get("phone", "")    or phone
    addr     = naver_local.get("addr", "")     or addr
    homepage = naver_local.get("homepage", "") or kakao_info.get("homepage", "")

    # ── 2.7) 공식 홈페이지 크롤 ─────────────────────────────────────────────
    homepage_text: str = ""
    if homepage:
        try:
            homepage_text = await crawl_homepage(homepage) or ""
        except Exception as e:
            print(f"[pipeline] crawl_homepage 실패: {e}")

    # ── 3) Kakao 건물 소속 매장 수집 (폴리곤+주소 이중 필터) ──────────────
    kakao_stores: list[dict] = []
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

    # ── 4) 쿼리 스펙 생성 ────────────────────────────────────────────────────
    query_specs = build_queries(building, kakao_stores)
    print(f"[pipeline] 쿼리 {len(query_specs)}개 생성")

    # ── 5) 검색 결과 수집 ────────────────────────────────────────────────────
    search_results: list[dict] = []
    try:
        search_results = await collect(query_specs)
    except Exception as e:
        print(f"[pipeline] collect 실패: {e}")

    # ── 6) LLM 매장 추출 ────────────────────────────────────────────────────
    extracted: list[dict] = []
    try:
        extracted = await extract_stores(bld_nm, search_results)
    except Exception as e:
        print(f"[pipeline] extract_stores 실패: {e}")

    # ── 6.5) LLM 건물 운영정보 추출 ─────────────────────────────────────────
    bld_info: dict = {}
    try:
        bld_info = await extract_building_info(bld_nm, search_results)
    except Exception as e:
        print(f"[pipeline] extract_building_info 실패: {e}")

    open_hours    = bld_info.get("open_hours", "")
    closed_days   = bld_info.get("closed_days", "")
    parking_info  = bld_info.get("parking_info", "")
    admission_fee = bld_info.get("admission_fee", "")
    homepage      = homepage or bld_info.get("homepage", "")

    # ── 6.7) 네이버 이미지 검색 ─────────────────────────────────────────────
    image_url = ""
    if bld_nm:
        try:
            image_url = await naver_image_search(f"{bld_nm} 외관")
        except Exception as e:
            print(f"[pipeline] naver_image_search 실패: {e}")

    # ── 7) 정부 DB (소상공인 API) ────────────────────────────────────────────
    govt_stores: list[dict] = []
    try:
        building_key = await fetch_building_key(addr) if addr else None
        if building_key:
            govt_stores = await fetch_floor_info(building_key)
            print(f"[pipeline] 정부DB: {sum(len(f['stores']) for f in govt_stores)}개 매장")
    except Exception as e:
        print(f"[pipeline] 정부DB 조회 실패: {e}")

    # ── 7.5) 홈페이지 텍스트 → 층별 매장 추출 ──────────────────────────────
    homepage_floor_info: list[dict] = []
    if homepage_text:
        try:
            homepage_floor_info = await extract_floor_info_from_homepage(bld_nm, homepage_text)
        except Exception as e:
            print(f"[pipeline] extract_floor_info_from_homepage 실패: {e}")

    # ── 8) 교차검증 ──────────────────────────────────────────────────────────
    validation    = cross_validate(extracted, govt_stores, kakao_stores)
    confirmed     = validation["confirmed"]
    coverage_rate = validation["coverage_rate"]

    result["confirmed_count"] = len(confirmed)
    result["coverage_rate"]   = coverage_rate

    # ── 8.5) floor_info 우선순위: 홈페이지 > 정부DB(Kakao카테고리보완) > confirmed
    if homepage_floor_info:
        floor_info_list = homepage_floor_info
        print(f"[pipeline] floor_info 출처: 홈페이지 ({len(homepage_floor_info)}개 층)")
    elif govt_stores:
        enriched = _enrich_govt_with_confirmed(govt_stores, confirmed)
        floor_info_list = _enrich_govt_with_kakao(enriched, kakao_stores)
        print(f"[pipeline] floor_info 출처: 정부DB+Kakao카테고리")
    else:
        floor_info_list = _confirmed_to_floor_info(confirmed)
        print(f"[pipeline] floor_info 출처: LLM confirmed")

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
                     parking_info, admission_fee)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10,$11,
                        $12,$13,$14,$15,$16,$17)
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
                    admission_fee = EXCLUDED.admission_fee
                """,
                ufid, name_ko, lat, lng, addr, phone, category,
                json.dumps(floor_info_list, ensure_ascii=False),
                coverage_rate,
                datetime.now(timezone.utc),
                "automated",
                image_url or None, homepage or None,
                open_hours or None, closed_days or None,
                parking_info or None, admission_fee or None,
            )
        print(f"[pipeline] Supabase upsert 완료: {ufid}")
        result["status"] = "ok" if confirmed or govt_stores else "partial"
    except Exception as e:
        result["error"] = f"Supabase upsert 실패: {e}"
        print(f"[pipeline] {result['error']}")
        result["status"] = "partial"

    print(f"[pipeline] ===== {ufid} 완료 status={result['status']} =====\n")
    return result
