"""
seed_from_buildings.py
buildings DB의 모든 건물에 대해 store_details를 자동으로 시드한다.

각 건물의 center_lat/center_lng 기준 소반경(기본 150m) Kakao 카테고리 검색으로
해당 건물 근처 매장을 수집한다. 매장은 ST_DWithin 50m로 건물 폴리곤에 매칭하며,
어느 건물에도 속하지 않는 매장은 __outdoor__ 처리(건너뜀)한다.

사용:
    python scripts/seed_from_buildings.py
    python scripts/seed_from_buildings.py --per-category 15 --radius 200
    python scripts/seed_from_buildings.py --skip-existing
    python scripts/seed_from_buildings.py --categories cafe convenience_store pharmacy
"""

import argparse
import asyncio
import json
import math
import os
import pathlib
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import httpx
from dotenv import load_dotenv

from core.db import get_pool
from tools.store_tools import get_store_detail

load_dotenv()
KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")

CATEGORY_CODES: dict[str, str] = {
    "cafe":              "CE7",
    "restaurant":        "FD6",
    "convenience_store": "CS2",
    "bank":              "BK9",
    "hospital":          "HP8",
    "pharmacy":          "PM9",
    "shopping":          "MT1",
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
    30m 이내에서 가장 가까운 buildings.geom 폴리곤의 ufid 반환.
    없으면 None → 호출 측에서 __outdoor__ 처리.
    네트워크 단절 시 최대 retries회 재시도.
    """
    for attempt in range(retries):
        try:
            async with bld_pool.acquire() as conn:
                row = await conn.fetchrow(
                    """
                    SELECT ufid
                    FROM buildings
                    WHERE ST_DWithin(
                        geom::geography,
                        ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
                        50
                    )
                    ORDER BY geom::geography <-> ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
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


# ── buildings 로드 ────────────────────────────────────────────────────────────

async def load_buildings() -> list[tuple[str, str, float, float]]:
    """buildings DB에서 (ufid, bld_nm, center_lat, center_lng) 목록 반환."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT ufid, bld_nm, center_lat, center_lng FROM buildings "
            "WHERE ufid IS NOT NULL AND center_lat IS NOT NULL AND center_lng IS NOT NULL"
        )
    return [(r["ufid"], r["bld_nm"] or "", r["center_lat"], r["center_lng"]) for r in rows]


def sort_buildings(
    buildings: list[tuple[str, str, float, float]],
    prefer_lat: float | None,
    prefer_lng: float | None,
) -> list[tuple[str, str, float, float]]:
    if prefer_lat is not None and prefer_lng is not None:
        def _dist(b):
            dlat = math.radians(b[2] - prefer_lat)
            dlng = math.radians(b[3] - prefer_lng)
            a = math.sin(dlat/2)**2 + math.cos(math.radians(prefer_lat)) * math.cos(math.radians(b[2])) * math.sin(dlng/2)**2
            return math.asin(math.sqrt(a))
        return sorted(buildings, key=_dist)
    return buildings


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

    buildings = sort_buildings(await load_buildings(), prefer_lat, prefer_lng)
    if not buildings:
        print("buildings DB에 데이터 없음 — 중단")
        return

    print(f"처리 대상 건물 {len(buildings)}개 (반경 {radius}m, 카테고리당 최대 {per_category}개)")

    pool = await get_pool()

    os.makedirs("logs", exist_ok=True)
    resume_path = pathlib.Path("logs/seed_from_buildings_resume.txt")

    async with pool.acquire() as conn:
        existing: set[str] = (
            {r["id"] for r in await conn.fetch("SELECT id FROM store_details")}
            if skip_existing else set()
        )

    done_ufids: set[str] = set()
    if skip_existing and resume_path.exists():
        done_ufids = set(resume_path.read_text(encoding="utf-8").splitlines())
        print(f"resume 파일에서 {len(done_ufids)}개 건물 skip 로드")

    total_results: list[dict] = []
    t_start = time.time()
    first_store = True

    async with httpx.AsyncClient() as client:
        for b_idx, (ufid, bld_nm, lat, lng) in enumerate(buildings, 1):
            if ufid in done_ufids:
                continue
            print(f"\n{'='*60}")
            print(f"[{b_idx}/{len(buildings)}] {ufid}  {bld_nm}  lat={lat:.5f} lng={lng:.5f}")

            for cat in categories:
                code = CATEGORY_CODES.get(cat)
                if not code:
                    continue

                docs = await _kakao_category_search(client, code, lat, lng, radius, per_category)
                if not docs:
                    continue

                print(f"  [{cat}] {len(docs)}개")
                for i, d in enumerate(docs, 1):
                    name = (d.get("place_name") or "").strip()
                    s_lat = float(d.get("y") or 0)
                    s_lng = float(d.get("x") or 0)
                    if not name or s_lat == 0 or s_lng == 0:
                        continue

                    matched_ufid = await _find_building_for_store(pool, s_lat, s_lng)
                    if not matched_ufid:
                        print(f"    [{i:2d}] {name} - 건물 미매칭 skip")
                        continue

                    store_id = f"{matched_ufid}__{name}"
                    if store_id in existing:
                        print(f"    [{i:2d}] {name} — skip (기존)")
                        continue

                    if not first_store:
                        await asyncio.sleep(sleep_sec)
                    first_store = False

                    try:
                        row = await get_store_detail(matched_ufid, name, lat=s_lat, lng=s_lng)
                        existing.add(store_id)
                        total_results.append({
                            "building_ufid": ufid,
                            "matched_ufid": matched_ufid,
                            "store_name": name,
                            "category": cat,
                            "place_id": matched_ufid,
                            "category_key": row.get("category_key"),
                            "source": row.get("source") or "(empty)",
                        })
                        print(
                            f"    [{i:2d}] {name}"
                            f"  matched={matched_ufid}"
                            f"  key={row.get('category_key')}"
                        )
                    except Exception as e:
                        print(f"    [{i:2d}] {name} ✗ {type(e).__name__}: {e}")
                        total_results.append({
                            "building_ufid": ufid, "store_name": name,
                            "category": cat, "error": str(e),
                        })

            if skip_existing:
                with resume_path.open("a", encoding="utf-8") as f:
                    f.write(ufid + "\n")
                done_ufids.add(ufid)

    total_t = time.time() - t_start
    ok = [r for r in total_results if "error" not in r]
    print(f"\n{'='*60}")
    print(f"완료: 성공={len(ok)}개  실패={len(total_results)-len(ok)}개  소요={total_t:.0f}초")

    by_key: dict[str, int] = {}
    for r in ok:
        k = r.get("category_key") or "?"
        by_key[k] = by_key.get(k, 0) + 1
    print("\ncategory_key별:")
    for k, n in sorted(by_key.items()):
        print(f"  {k:24s}: {n}")

    log_path = f"logs/seed_from_buildings_{int(t_start)}.json"
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump(
            {"buildings": len(buildings), "per_category": per_category,
             "radius": radius, "results": total_results},
            f, ensure_ascii=False, indent=2,
        )
    print(f"\n결과 로그: {log_path}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="buildings DB 전체 건물에 대해 store_details 자동 시드"
    )
    parser.add_argument(
        "--per-category", type=int, default=15,
        help="건물당 카테고리별 최대 매장 수 (기본 15, 최대 45)",
    )
    parser.add_argument(
        "--radius", type=int, default=150,
        help="Kakao 검색 반경 m (기본 150 — 건물 단위 소반경)",
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
        help="이 좌표에 가까운 건물부터 처리 (예: 37.3387)",
    )
    parser.add_argument(
        "--prefer-lng", type=float, default=None,
        help="이 좌표에 가까운 건물부터 처리 (예: 127.2674)",
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
