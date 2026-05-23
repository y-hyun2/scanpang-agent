"""
seed_tourist_places.py
Tour API locationBasedList2 기반으로 관광지(12)·문화시설(14)을 수집해
tourist_places 테이블에 적재한다.

데이터 출처:
    Tour API — 장소 발견, 주소/좌표/전화/제목, homepage/overview, 카테고리별 상세
    Naver    — image_url(업체 사진), conveniences

사용:
    python scripts/seed_tourist_places.py
    python scripts/seed_tourist_places.py --per-cluster 50 --radius 3000
    python scripts/seed_tourist_places.py --skip-existing

    # image_url 빈 레코드만 Naver로 채우기
    python scripts/seed_tourist_places.py --update-images
    python scripts/seed_tourist_places.py --update-images --name 용인자연휴양림
"""

import argparse
import asyncio
import json
import math
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import asyncpg
import h3
from dotenv import load_dotenv

from core.db import get_pool
from tools.details_fetchers import naver_place
from tools.tour_api_place_client import (
    fetch_detail_common,
    fetch_detail_intro,
    location_based_list,
)

load_dotenv()

H3_CLUSTER_RES = 7
CONTENT_TYPES = [12, 14]   # 12=관광지, 14=문화시설

# ── 테이블 ──────────────────────────────────────────────────────────────────
DROP_TABLE_SQL   = "DROP TABLE IF EXISTS tourist_places"
CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS tourist_places (
    id              TEXT PRIMARY KEY,
    content_id      TEXT UNIQUE,
    content_type_id INT,
    name_ko         TEXT,
    lat             FLOAT8,
    lng             FLOAT8,
    addr            TEXT,
    phone           TEXT,
    category        TEXT,
    overview        TEXT,
    image_urls      JSONB,
    homepage        TEXT,
    open_hours      TEXT,
    -- 관광지(12)
    infocenter      TEXT,
    parking         TEXT,
    restdate        TEXT,
    usetime         TEXT,
    -- 문화시설(14)
    infocenterculture  TEXT,
    parkingculture     TEXT,
    parkingfee         TEXT,
    restdateculture    TEXT,
    usefee             TEXT,
    usetimeculture     TEXT,
    -- Naver
    conveniences    JSONB,
    source          TEXT,
    last_updated    TIMESTAMPTZ
)
"""

UPSERT_SQL = """
INSERT INTO tourist_places (
    id, content_id, content_type_id, name_ko, lat, lng, addr, phone, category,
    overview, image_urls, homepage, open_hours,
    infocenter, parking, restdate, usetime,
    infocenterculture, parkingculture, parkingfee, restdateculture, usefee, usetimeculture,
    conveniences, source, last_updated
) VALUES (
    $1,$2,$3,$4,$5,$6,$7,$8,$9,
    $10,$11,$12,$13,
    $14,$15,$16,$17,
    $18,$19,$20,$21,$22,$23,
    $24,$25,NOW()
)
ON CONFLICT (id) DO UPDATE SET
    name_ko            = EXCLUDED.name_ko,
    addr               = EXCLUDED.addr,
    phone              = EXCLUDED.phone,
    category           = EXCLUDED.category,
    overview           = EXCLUDED.overview,
    image_urls         = EXCLUDED.image_urls,
    homepage           = EXCLUDED.homepage,
    open_hours         = EXCLUDED.open_hours,
    infocenter         = EXCLUDED.infocenter,
    parking            = EXCLUDED.parking,
    restdate           = EXCLUDED.restdate,
    usetime            = EXCLUDED.usetime,
    infocenterculture  = EXCLUDED.infocenterculture,
    parkingculture     = EXCLUDED.parkingculture,
    parkingfee         = EXCLUDED.parkingfee,
    restdateculture    = EXCLUDED.restdateculture,
    usefee             = EXCLUDED.usefee,
    usetimeculture     = EXCLUDED.usetimeculture,
    conveniences       = EXCLUDED.conveniences,
    source             = EXCLUDED.source,
    last_updated       = NOW()
"""

UPDATE_IMAGE_SQL = "UPDATE tourist_places SET image_urls = $1::jsonb, last_updated = NOW() WHERE id = $2"


# ── 건물 클러스터 ─────────────────────────────────────────────────────────────
async def load_building_clusters() -> list[tuple[str, float, float, int]]:
    bld_url = os.getenv("SUPABASE_BUILDING_DATABASE_URL", "")
    pool = await (asyncpg.create_pool(bld_url, min_size=1, max_size=3, statement_cache_size=0)
                  if bld_url else get_pool())
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT center_lat, center_lng FROM buildings "
            "WHERE center_lat IS NOT NULL AND center_lng IS NOT NULL"
        )
    cluster_map: dict[str, int] = {}
    for row in rows:
        cell = h3.latlng_to_cell(row["center_lat"], row["center_lng"], H3_CLUSTER_RES)
        cluster_map[cell] = cluster_map.get(cell, 0) + 1
    return [(cell, *h3.cell_to_latlng(cell), cnt) for cell, cnt in cluster_map.items()]


def sort_clusters(clusters, prefer_lat, prefer_lng):
    if prefer_lat is not None and prefer_lng is not None:
        def _dist(c):
            dlat = math.radians(c[1] - prefer_lat)
            dlng = math.radians(c[2] - prefer_lng)
            a = (math.sin(dlat/2)**2
                 + math.cos(math.radians(prefer_lat)) * math.cos(math.radians(c[1]))
                 * math.sin(dlng/2)**2)
            return math.asin(math.sqrt(a))
        return sorted(clusters, key=_dist)
    return sorted(clusters, key=lambda x: -x[3])


# ── Naver 이미지 + 편의시설 ──────────────────────────────────────────────────
async def _fetch_naver(name: str, lat: float, lng: float, addr: str) -> tuple[list, list, str, str, str, str, str]:
    """Naver에서 image_urls + conveniences + open_hours + phone + intro + homepage + matched_name 조회."""
    try:
        result = await naver_place.fetch(
            store_name=name, lat=lat, lng=lng,
            building_ufid="", category_name="",
            addr_hint=addr,
        )
        details = result.get("details") or {}
        urls    = result.get("image_urls") or []
        convs   = details.get("conveniences") or []
        intro   = details.get("intro") or ""
        matched = details.get("matched_name") or ""
        return (
            urls,
            convs,
            result.get("open_hours") or "",
            result.get("phone") or "",
            intro,
            result.get("homepage") or "",
            matched,
        )
    except Exception as e:
        print(f"    [naver] 실패: {e}")
        return [], [], "", "", "", "", ""


# ── 메인 시드 ────────────────────────────────────────────────────────────────
async def run(
    per_cluster: int,
    radius: int,
    sleep_sec: float,
    skip_existing: bool,
    prefer_lat: float | None = None,
    prefer_lng: float | None = None,
) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(DROP_TABLE_SQL)
        await conn.execute(CREATE_TABLE_SQL)
        existing: set[str] = (
            {r["id"] for r in await conn.fetch("SELECT id FROM tourist_places")}
            if skip_existing else set()
        )

    clusters = sort_clusters(await load_building_clusters(), prefer_lat, prefer_lng)
    if not clusters:
        print("buildings DB에 데이터 없음 — 중단")
        return

    print(f"클러스터 {len(clusters)}개")
    t_start = time.time()
    total: list[dict] = []

    for c_idx, (cell, lat, lng, bld_cnt) in enumerate(clusters, 1):
        print(f"\n{'='*60}")
        print(f"[{c_idx}/{len(clusters)}] 클러스터 {cell}  건물={bld_cnt}개")

        for ctype in CONTENT_TYPES:
            label = "관광지" if ctype == 12 else "문화시설"
            print(f"\n  [{label}(type {ctype})]")

            docs = await location_based_list(lat, lng, radius, ctype, per_cluster)
            if not docs:
                print("    결과 없음")
                continue

            for i, doc in enumerate(docs, 1):
                content_id = doc["contentid"]
                name       = doc["title"]
                s_lat      = doc["mapy"]
                s_lng      = doc["mapx"]
                addr       = doc["addr1"]
                phone      = doc["tel"]
                firstimage = doc["firstimage"]

                if not name or s_lat == 0 or s_lng == 0:
                    continue

                place_id = f"tourist__{content_id}"
                if place_id in existing:
                    print(f"    [{i:2d}] {name} — skip")
                    continue

                if i > 1:
                    await asyncio.sleep(sleep_sec)

                # Tour API 상세
                common = await fetch_detail_common(content_id)
                intro  = await fetch_detail_intro(content_id, ctype)

                # Naver: 주소→실패시 이름으로 재시도 (naver_place.fetch 내부에서 처리)
                naver_urls, conveniences, open_hours, naver_phone, naver_intro, naver_homepage, naver_name = \
                    await _fetch_naver(name, s_lat, s_lng, addr)
                # Tour API firstimage + Naver 업체 사진 합산 (dedup, Tour API 먼저)
                image_urls_combined: list[str] = []
                if firstimage:
                    image_urls_combined.append(firstimage)
                for u in naver_urls:
                    if u and u not in image_urls_combined:
                        image_urls_combined.append(u)
                # 이름: Naver 우선, 없으면 Tour API
                name     = naver_name or name
                phone    = phone or naver_phone
                homepage = common.get("homepage") or naver_homepage

                overview_raw = common.get("overview") or ""
                overview = re.sub(r"<[^>]+>", "", overview_raw).strip() or naver_intro

                async with pool.acquire() as conn:
                    await conn.execute(
                        UPSERT_SQL,
                        place_id,
                        content_id,
                        ctype,
                        name,
                        s_lat,
                        s_lng,
                        addr,
                        phone,
                        label,
                        overview,
                        json.dumps(image_urls_combined, ensure_ascii=False) if image_urls_combined else None,
                        homepage,
                        open_hours or "",
                        # 관광지(12)
                        intro.get("infocenter") or "",
                        intro.get("parking") or "",
                        intro.get("restdate") or "",
                        intro.get("usetime") or "",
                        # 문화시설(14)
                        intro.get("infocenterculture") or "",
                        intro.get("parkingculture") or "",
                        intro.get("parkingfee") or "",
                        intro.get("restdateculture") or "",
                        intro.get("usefee") or "",
                        intro.get("usetimeculture") or "",
                        json.dumps(conveniences, ensure_ascii=False) if conveniences else None,
                        "tour_api+naver" if naver_urls else "tour_api",
                    )

                existing.add(place_id)
                total.append({"name": name, "ctype": ctype})
                print(f"    [{i:2d}] {name}  imgs={len(image_urls_combined)}")

    elapsed = time.time() - t_start
    print(f"\n{'='*60}")
    print(f"완료: 총={len(total)}개  소요={elapsed:.0f}초")

    os.makedirs("logs", exist_ok=True)
    log_path = f"logs/seed_tourist_places_{int(t_start)}.json"
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump({"clusters": len(clusters), "results": total}, f, ensure_ascii=False, indent=2)
    print(f"결과 로그: {log_path}")


# ── image_url 단독 업데이트 ───────────────────────────────────────────────────
async def _get_image_url(name: str, lat: float, lng: float, addr: str) -> tuple[list, str]:
    naver_urls, *_ = await _fetch_naver(name, lat, lng, addr)
    return naver_urls, "naver_place"


async def run_update_images(limit: int, sleep_sec: float, name_filter: str = "") -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        if name_filter:
            rows = await conn.fetch(
                "SELECT id, name_ko, lat, lng, addr FROM tourist_places WHERE name_ko = $1",
                name_filter,
            )
        else:
            rows = await conn.fetch(
                """
                SELECT id, name_ko, lat, lng, addr FROM tourist_places
                WHERE (image_urls IS NULL OR image_urls = '[]'::jsonb)
                ORDER BY last_updated ASC NULLS FIRST LIMIT $1
                """,
                limit,
            )
    if not rows:
        print("image_urls 빈 레코드 없음")
        return
    print(f"대상 {len(rows)}개")
    updated = 0
    for i, row in enumerate(rows, 1):
        if i > 1:
            await asyncio.sleep(sleep_sec)
        name = row["name_ko"] or ""
        lat  = float(row["lat"]) if row["lat"] else 0.0
        lng  = float(row["lng"]) if row["lng"] else 0.0
        addr = row["addr"] or ""
        print(f"[{i}/{len(rows)}] {name}")
        naver_urls, source = await _get_image_url(name, lat, lng, addr)
        if not naver_urls:
            print("  → 이미지 없음")
            continue
        async with pool.acquire() as conn:
            await conn.execute(UPDATE_IMAGE_SQL, json.dumps(naver_urls, ensure_ascii=False), row["id"])
        updated += 1
        print(f"  → ✓ [{source}] {naver_urls[0][:80]} 외 {len(naver_urls)-1}장")
    print(f"\n완료: {updated}/{len(rows)}개 업데이트")


# ── CLI ──────────────────────────────────────────────────────────────────────
def main() -> None:
    parser = argparse.ArgumentParser(description="관광지·문화시설 tourist_places 시드")
    parser.add_argument("--per-cluster",   type=int,   default=30)
    parser.add_argument("--radius",        type=int,   default=2000)
    parser.add_argument("--sleep",         type=float, default=1.5)
    parser.add_argument("--skip-existing", action="store_true")
    parser.add_argument("--prefer-lat",    type=float, default=None)
    parser.add_argument("--prefer-lng",    type=float, default=None)
    parser.add_argument("--update-images", action="store_true",
                        help="image_url 빈 레코드만 Naver로 채움")
    parser.add_argument("--limit",         type=int,   default=200,
                        help="--update-images 모드 최대 처리 건수")
    parser.add_argument("--name",          type=str,   default="",
                        help="--update-images 모드 특정 장소명 1건만 처리")
    args = parser.parse_args()

    if args.update_images:
        asyncio.run(run_update_images(args.limit, args.sleep, args.name))
    else:
        asyncio.run(run(
            per_cluster=args.per_cluster,
            radius=args.radius,
            sleep_sec=args.sleep,
            skip_existing=args.skip_existing,
            prefer_lat=args.prefer_lat,
            prefer_lng=args.prefer_lng,
        ))


if __name__ == "__main__":
    main()