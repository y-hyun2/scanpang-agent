"""
seed_accommodation_places.py
buildings DB 커버리지 기반으로 숙박시설을 수집해 accommodation_places 테이블에 적재.

seed_tourist_places.py 와 동일한 구조로 Kakao AD5(숙박)를 검색하고,
kakao_scraper → naver_place → tour_api 체인으로 보강한다.

사용:
    python scripts/seed_accommodation_places.py
    python scripts/seed_accommodation_places.py --per-category 45 --radius 2000
    python scripts/seed_accommodation_places.py --skip-existing
"""

import argparse
import asyncio
import json
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import asyncpg
import h3
import httpx
from dotenv import load_dotenv

from core.db import get_pool
from tools.details_fetchers import fetch_by_category

load_dotenv()
KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")

H3_CLUSTER_RES = 7

CATEGORY_CODES = {
    "accommodation": "AD5",
}

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS accommodation_places (
    id              TEXT PRIMARY KEY,
    kakao_id        TEXT UNIQUE,
    name_ko         TEXT,
    lat             FLOAT8,
    lng             FLOAT8,
    addr            TEXT,
    phone           TEXT,
    category        TEXT,
    overview        TEXT,
    image_url       TEXT,
    homepage        TEXT,
    open_hours      TEXT,
    closed_days     TEXT,
    parking_info    TEXT,
    admission_fee   TEXT,
    place_url       TEXT,
    content_id      TEXT,
    content_type_id INT,
    source          TEXT,
    last_updated    TIMESTAMPTZ
);
"""

UPSERT_SQL = """
INSERT INTO accommodation_places
    (id, kakao_id, name_ko, lat, lng, addr, phone, category,
     overview, image_url, homepage,
     open_hours, closed_days, parking_info, admission_fee,
     place_url, content_id, content_type_id, source, last_updated)
VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,NOW())
ON CONFLICT (id) DO UPDATE SET
    name_ko         = EXCLUDED.name_ko,
    addr            = EXCLUDED.addr,
    phone           = EXCLUDED.phone,
    category        = EXCLUDED.category,
    overview        = EXCLUDED.overview,
    image_url       = EXCLUDED.image_url,
    homepage        = EXCLUDED.homepage,
    open_hours      = EXCLUDED.open_hours,
    closed_days     = EXCLUDED.closed_days,
    parking_info    = EXCLUDED.parking_info,
    admission_fee   = EXCLUDED.admission_fee,
    place_url       = EXCLUDED.place_url,
    content_id      = EXCLUDED.content_id,
    content_type_id = EXCLUDED.content_type_id,
    source          = EXCLUDED.source,
    last_updated    = NOW()
"""


async def _kakao_category_search(
    client: httpx.AsyncClient,
    code: str,
    lat: float,
    lng: float,
    radius: int,
    max_items: int,
) -> list[dict]:
    results: list[dict] = []
    for page in range(1, 4):
        if len(results) >= max_items:
            break
        try:
            resp = await client.get(
                "https://dapi.kakao.com/v2/local/search/category.json",
                headers={"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"},
                params={
                    "category_group_code": code,
                    "x": lng, "y": lat,
                    "radius": radius,
                    "size": 15,
                    "page": page,
                    "sort": "distance",
                },
                timeout=10.0,
            )
            resp.raise_for_status()
            data = resp.json()
            docs = data.get("documents", [])
            if not docs:
                break
            results.extend(docs)
            if data.get("meta", {}).get("is_end", True):
                break
        except Exception as e:
            print(f"    [kakao] code={code} page={page} 실패: {e}")
            break
    return results[:max_items]


async def load_building_clusters() -> list[tuple[str, float, float, int]]:
    bld_url = os.getenv("SUPABASE_BUILDING_DATABASE_URL", "")
    if bld_url:
        bld_pool = await asyncpg.create_pool(bld_url, min_size=1, max_size=3, statement_cache_size=0)
    else:
        bld_pool = await get_pool()
    async with bld_pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT center_lat, center_lng FROM buildings "
            "WHERE center_lat IS NOT NULL AND center_lng IS NOT NULL"
        )
    cluster_map: dict[str, int] = {}
    for row in rows:
        cell = h3.latlng_to_cell(row["center_lat"], row["center_lng"], H3_CLUSTER_RES)
        cluster_map[cell] = cluster_map.get(cell, 0) + 1
    return [
        (cell, *h3.cell_to_latlng(cell), cnt)
        for cell, cnt in cluster_map.items()
    ]


def sort_clusters(
    clusters: list[tuple],
    prefer_lat: float | None,
    prefer_lng: float | None,
) -> list[tuple]:
    if prefer_lat is not None and prefer_lng is not None:
        def _dist(c):
            dlat = math.radians(c[1] - prefer_lat)
            dlng = math.radians(c[2] - prefer_lng)
            a = math.sin(dlat/2)**2 + math.cos(math.radians(prefer_lat)) * math.cos(math.radians(c[1])) * math.sin(dlng/2)**2
            return math.asin(math.sqrt(a))
        return sorted(clusters, key=_dist)
    return sorted(clusters, key=lambda x: -x[3])


async def run(
    per_category: int,
    radius: int,
    sleep_sec: float,
    skip_existing: bool,
    prefer_lat: float | None = None,
    prefer_lng: float | None = None,
) -> None:
    if not KAKAO_REST_API_KEY:
        print("KAKAO_REST_API_KEY 가 .env 에 없음 — 중단")
        return

    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(CREATE_TABLE_SQL)
        existing: set[str] = (
            {r["id"] for r in await conn.fetch("SELECT id FROM accommodation_places")}
            if skip_existing else set()
        )

    clusters = sort_clusters(await load_building_clusters(), prefer_lat, prefer_lng)
    if not clusters:
        print("buildings DB에 데이터 없음 — 중단")
        return

    print(f"클러스터 {len(clusters)}개:")
    for cell, lat, lng, cnt in clusters:
        print(f"  {cell}  lat={lat:.4f} lng={lng:.4f}  건물={cnt}개")

    t_start = time.time()
    total: list[dict] = []

    async with httpx.AsyncClient() as client:
        for c_idx, (cell, lat, lng, bld_cnt) in enumerate(clusters, 1):
            print(f"\n{'='*60}")
            print(f"[{c_idx}/{len(clusters)}] 클러스터 {cell}  건물={bld_cnt}개")

            for cat, code in CATEGORY_CODES.items():
                print(f"\n  [{cat}]")
                docs = await _kakao_category_search(client, code, lat, lng, radius, per_category)
                if not docs:
                    print("    결과 없음")
                    continue

                for i, d in enumerate(docs, 1):
                    kakao_id  = d.get("id", "")
                    name      = (d.get("place_name") or "").strip()
                    s_lat     = float(d.get("y") or 0)
                    s_lng     = float(d.get("x") or 0)
                    addr      = d.get("road_address_name") or d.get("address_name") or ""
                    phone     = d.get("phone") or ""
                    category  = d.get("category_name") or cat
                    place_url = d.get("place_url") or ""

                    if not name or s_lat == 0 or s_lng == 0:
                        continue

                    place_id = f"accommodation__{kakao_id}" if kakao_id else f"accommodation__{name}"
                    if place_id in existing:
                        print(f"    [{i:2d}] {name} — skip")
                        continue

                    if i > 1:
                        await asyncio.sleep(sleep_sec)

                    enriched = await fetch_by_category(
                        category_key="accommodation",
                        store_name=name,
                        lat=s_lat,
                        lng=s_lng,
                        building_ufid="",
                        category_name=category,
                    )
                    details = enriched.get("details") or {}
                    image_urls_list = enriched.get("image_urls") or []

                    async with pool.acquire() as conn:
                        await conn.execute(
                            UPSERT_SQL,
                            place_id,
                            kakao_id or None,
                            name,
                            s_lat,
                            s_lng,
                            enriched.get("addr") or addr,
                            enriched.get("phone") or phone,
                            category,
                            details.get("overview") or "",
                            image_urls_list[0] if image_urls_list else "",
                            enriched.get("homepage") or "",
                            enriched.get("open_hours") or "",
                            enriched.get("closed_days") or "",
                            details.get("parking_info") or "",
                            details.get("admission_fee") or "",
                            place_url,
                            details.get("content_id") or None,
                            details.get("content_type_id") or None,
                            enriched.get("source") or "kakao",
                        )

                    existing.add(place_id)
                    total.append({"name": name, "enriched": bool(enriched)})
                    print(f"    [{i:2d}] {name}  source={enriched.get('source', '-')}")

    elapsed = time.time() - t_start
    enriched_cnt = sum(1 for r in total if r["enriched"])
    print(f"\n{'='*60}")
    print(f"완료: 총={len(total)}개  보강성공={enriched_cnt}개  소요={elapsed:.0f}초")

    os.makedirs("logs", exist_ok=True)
    log_path = f"logs/seed_accommodation_places_{int(t_start)}.json"
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump({"clusters": len(clusters), "results": total}, f, ensure_ascii=False, indent=2)
    print(f"결과 로그: {log_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="숙박시설 accommodation_places 시드")
    parser.add_argument("--per-category", type=int, default=30)
    parser.add_argument("--radius",       type=int, default=2000)
    parser.add_argument("--sleep",        type=float, default=1.5)
    parser.add_argument("--skip-existing", action="store_true")
    parser.add_argument("--prefer-lat",  type=float, default=None)
    parser.add_argument("--prefer-lng",  type=float, default=None)
    args = parser.parse_args()

    asyncio.run(run(
        per_category=min(args.per_category, 45),
        radius=args.radius,
        sleep_sec=args.sleep,
        skip_existing=args.skip_existing,
        prefer_lat=args.prefer_lat,
        prefer_lng=args.prefer_lng,
    ))


if __name__ == "__main__":
    main()