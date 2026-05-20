"""
seed_from_buildings.py
buildings DB에 데이터가 있는 지역의 store_details를 자동으로 시드한다.

H3 res-7로 건물 클러스터를 구성한 뒤 Kakao 카테고리 검색(페이지네이션, 최대 45개/카테고리)으로
주변 매장을 수집한다.

id 규칙:
  - Kakao 좌표가 buildings.geom 폴리곤 안에 있으면 → {ufid}__{store_name}
  - 어떤 건물 폴리곤에도 속하지 않으면     → __outdoor__{store_name}

사용:
    python scripts/seed_from_buildings.py
    python scripts/seed_from_buildings.py --per-category 45 --radius 1500
    python scripts/seed_from_buildings.py --skip-existing
    python scripts/seed_from_buildings.py --categories cafe convenience_store pharmacy
"""

import argparse
import asyncio
import json
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import h3
import httpx
from dotenv import load_dotenv

from core.db import get_pool, get_building_pool
from tools.store_tools import get_store_detail

load_dotenv()
KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")

H3_CLUSTER_RES = 7  # ~5km² 단위 클러스터 (명동/역삼/외대 각 1~2셀)

CATEGORY_CODES: dict[str, str] = {
    "cafe":              "CE7",
    "restaurant":        "FD6",
    "convenience_store": "CS2",
    "bank":              "BK9",
    "hospital":          "HP8",
    "pharmacy":          "PM9",
    "tourist":           "AT4",
    "cultural":          "CT1",
    "accommodation":     "AD5",
    "shopping":          "MT1",
    "subway":            "SW8",
}

DEFAULT_CATEGORIES = list(CATEGORY_CODES.keys())


# ── Kakao API ─────────────────────────────────────────────────────────────────

async def _kakao_category_search(
    client: httpx.AsyncClient,
    code: str,
    lat: float,
    lng: float,
    radius: int,
    max_items: int,
) -> list[dict]:
    """Kakao 카테고리 검색 — 최대 3페이지(45개)까지 페이지네이션."""
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


# ── 건물 폴리곤 포함 여부 확인 ────────────────────────────────────────────────

async def _find_building_for_store(
    bld_pool,
    store_lat: float,
    store_lng: float,
    retries: int = 3,
) -> str | None:
    """
    store의 좌표가 buildings.geom 폴리곤 안에 있으면 해당 ufid 반환.
    없으면 None → 호출 측에서 __outdoor__ 처리.
    네트워크 단절 시 최대 retries회 재시도.
    """
    for attempt in range(retries):
        try:
            async with bld_pool.acquire() as conn:
                row = await conn.fetchrow(
                    """
                    SELECT ufid FROM buildings
                    WHERE ST_Within(
                        ST_SetSRID(ST_MakePoint($1, $2), 4326),
                        geom
                    )
                    LIMIT 1
                    """,
                    store_lng, store_lat,
                )
            return row["ufid"] if row else None
        except Exception as e:
            if attempt < retries - 1:
                wait = 2 ** attempt  # 1s, 2s
                print(f"    [bld_pool] 연결 오류 재시도 {attempt + 1}/{retries - 1} ({wait}s): {e}")
                await asyncio.sleep(wait)
            else:
                print(f"    [bld_pool] 재시도 실패, __outdoor__ 처리: {e}")
                return None


# ── buildings 로드 & 클러스터링 ───────────────────────────────────────────────

async def load_building_clusters() -> list[tuple[str, float, float, int]]:
    """buildings DB에서 H3 res-7 클러스터별 (cell, lat, lng, 건물수)를 반환."""
    pool = await get_building_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT center_lat, center_lng FROM buildings "
            "WHERE center_lat IS NOT NULL AND center_lng IS NOT NULL"
        )

    cluster_map: dict[str, int] = {}
    for row in rows:
        cell = h3.latlng_to_cell(row["center_lat"], row["center_lng"], H3_CLUSTER_RES)
        cluster_map[cell] = cluster_map.get(cell, 0) + 1

    clusters = []
    for cell, cnt in cluster_map.items():
        center = h3.cell_to_latlng(cell)
        clusters.append((cell, center[0], center[1], cnt))
    return clusters


def sort_clusters(
    clusters: list[tuple[str, float, float, int]],
    prefer_lat: float | None,
    prefer_lng: float | None,
) -> list[tuple[str, float, float, int]]:
    if prefer_lat is not None and prefer_lng is not None:
        def _dist(c):
            dlat = math.radians(c[1] - prefer_lat)
            dlng = math.radians(c[2] - prefer_lng)
            a = math.sin(dlat/2)**2 + math.cos(math.radians(prefer_lat)) * math.cos(math.radians(c[1])) * math.sin(dlng/2)**2
            return math.asin(math.sqrt(a))
        return sorted(clusters, key=_dist)
    return sorted(clusters, key=lambda x: -x[3])


# ── 메인 ──────────────────────────────────────────────────────────────────────

async def run(
    categories: list[str],
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

    clusters = sort_clusters(await load_building_clusters(), prefer_lat, prefer_lng)
    if not clusters:
        print("buildings DB에 데이터 없음 — 중단")
        return

    bld_cnt_total = sum(c[3] for c in clusters)
    print(f"클러스터 {len(clusters)}개 (건물 {bld_cnt_total}개, H3 res-{H3_CLUSTER_RES}):")
    for cell, lat, lng, cnt in clusters:
        print(f"  {cell}  lat={lat:.4f} lng={lng:.4f}  건물={cnt}개")

    pool = await get_pool()
    bld_pool = await get_building_pool()

    async with pool.acquire() as conn:
        existing: set[str] = (
            {r["id"] for r in await conn.fetch("SELECT id FROM store_details")}
            if skip_existing else set()
        )

    total_results: list[dict] = []
    t_start = time.time()

    async with httpx.AsyncClient() as client:
        for c_idx, (cell, lat, lng, bld_cnt) in enumerate(clusters, 1):
            print(f"\n{'='*60}")
            print(f"[{c_idx}/{len(clusters)}] 클러스터 {cell}  건물={bld_cnt}개")

            for cat in categories:
                code = CATEGORY_CODES.get(cat)
                if not code:
                    continue

                print(f"\n  [{cat}]")
                docs = await _kakao_category_search(client, code, lat, lng, radius, per_category)
                if not docs:
                    print("    결과 없음")
                    continue

                for i, d in enumerate(docs, 1):
                    name = (d.get("place_name") or "").strip()
                    s_lat = float(d.get("y") or 0)
                    s_lng = float(d.get("x") or 0)
                    if not name or s_lat == 0 or s_lng == 0:
                        continue

                    # 건물 폴리곤 포함 여부로 place_id 결정
                    ufid = await _find_building_for_store(bld_pool, s_lat, s_lng)
                    place_id = ufid if ufid else "__outdoor__"
                    store_id = f"{place_id}__{name}"

                    if store_id in existing:
                        print(f"    [{i:2d}] {name} — skip")
                        continue

                    if i > 1:
                        await asyncio.sleep(sleep_sec)

                    try:
                        row = await get_store_detail(place_id, name, lat=s_lat, lng=s_lng)
                        existing.add(store_id)
                        total_results.append({
                            "cluster": cell,
                            "store_name": name,
                            "category": cat,
                            "place_id": place_id,
                            "category_key": row.get("category_key"),
                            "source": row.get("source") or "(empty)",
                        })
                        print(
                            f"    [{i:2d}] {name}"
                            f"  place={'건물내' if ufid else 'outdoor'}"
                            f"  key={row.get('category_key')}"
                        )
                    except Exception as e:
                        print(f"    [{i:2d}] {name} ✗ {type(e).__name__}: {e}")
                        total_results.append({
                            "cluster": cell, "store_name": name,
                            "category": cat, "error": str(e),
                        })

    total_t = time.time() - t_start
    ok = [r for r in total_results if "error" not in r]
    indoor = sum(1 for r in ok if r.get("place_id") != "__outdoor__")
    print(f"\n{'='*60}")
    print(f"완료: 성공={len(ok)}개 (건물내={indoor} outdoor={len(ok)-indoor})  "
          f"실패={len(total_results)-len(ok)}개  소요={total_t:.0f}초")

    by_key: dict[str, int] = {}
    for r in ok:
        k = r.get("category_key") or "?"
        by_key[k] = by_key.get(k, 0) + 1
    print("\ncategory_key별:")
    for k, n in sorted(by_key.items()):
        print(f"  {k:24s}: {n}")

    os.makedirs("logs", exist_ok=True)
    log_path = f"logs/seed_from_buildings_{int(t_start)}.json"
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump(
            {"clusters": len(clusters), "buildings": bld_cnt_total,
             "per_category": per_category, "radius": radius,
             "results": total_results},
            f, ensure_ascii=False, indent=2,
        )
    print(f"\n결과 로그: {log_path}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="buildings DB 커버리지 기반 store_details 자동 시드"
    )
    parser.add_argument(
        "--per-category", type=int, default=30,
        help="클러스터당 카테고리별 최대 매장 수 (기본 30, 최대 45)",
    )
    parser.add_argument(
        "--radius", type=int, default=2000,
        help="Kakao 검색 반경 m (기본 2000)",
    )
    parser.add_argument(
        "--categories", nargs="+", default=DEFAULT_CATEGORIES,
        help=f"시드할 카테고리. 기본: 전체({', '.join(DEFAULT_CATEGORIES)})",
    )
    parser.add_argument(
        "--sleep", type=float, default=1.5,
        help="매장 간 sleep 초 (기본 1.5)",
    )
    parser.add_argument(
        "--skip-existing", action="store_true",
        help="store_details에 이미 있는 id는 건너뜀",
    )
    parser.add_argument(
        "--prefer-lat", type=float, default=None,
        help="이 좌표에 가까운 클러스터부터 처리 (예: 37.3387)",
    )
    parser.add_argument(
        "--prefer-lng", type=float, default=None,
        help="이 좌표에 가까운 클러스터부터 처리 (예: 127.2674)",
    )
    args = parser.parse_args()

    asyncio.run(run(
        categories=args.categories,
        per_category=min(args.per_category, 45),
        radius=args.radius,
        sleep_sec=args.sleep,
        skip_existing=args.skip_existing,
        prefer_lat=args.prefer_lat,
        prefer_lng=args.prefer_lng,
    ))


if __name__ == "__main__":
    main()
