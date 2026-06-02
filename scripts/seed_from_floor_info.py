"""
seed_from_floor_info.py
placeinfo.floor_info에 이미 확정된 건물별 매장 명단을 읽어 store_details에 시드한다.

seed_from_buildings.py(건물 좌표 반경 Kakao 카테고리 스캔)와 달리, **건물 주변
동네를 긁지 않는다.** placeinfo.floor_info(파이프라인이 홈페이지 LLM·네이버 층명시·
정부DB 등으로 확정한 입주 매장 목록)의 매장만 골라 get_store_detail로 상세를 수집한다.
→ 그 건물에 실제로 입주한 매장만 storedetails에 쌓이고, 옆 골목 매장 오매칭이 없다.

사용:
    python scripts/seed_from_floor_info.py
    python scripts/seed_from_floor_info.py --skip-existing
    python scripts/seed_from_floor_info.py --limit 0            # 건물당 전체 매장(기본)
    python scripts/seed_from_floor_info.py --limit 5            # 건물당 최대 5개만
    python scripts/seed_from_floor_info.py --prefer-lat 37.5636 --prefer-lng 126.9822
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

from dotenv import load_dotenv

from core.db import get_pool
from tools.store_tools import get_store_detail

load_dotenv()


# ── buildings 로드 ────────────────────────────────────────────────────────────

async def load_buildings() -> list[dict]:
    """placeinfo에서 floor_info가 있는 건물 목록 반환."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT building_key, name_ko, lat, lng, addr, floor_info "
            "FROM placeinfo "
            "WHERE floor_info IS NOT NULL"
        )
    out: list[dict] = []
    for r in rows:
        stores = _extract_stores(r["floor_info"])
        if not stores:
            continue
        out.append({
            "building_key": r["building_key"],
            "name_ko":      r["name_ko"] or "",
            "lat":          float(r["lat"]) if r["lat"] is not None else None,
            "lng":          float(r["lng"]) if r["lng"] is not None else None,
            "addr":         r["addr"] or "",
            "stores":       stores,
        })
    return out


def _extract_stores(floor_info_raw) -> list[tuple[str, str, str]]:
    """floor_info JSONB → [(store_name, floor, category)] (순서 보존, 중복 제거)."""
    if isinstance(floor_info_raw, str):
        try:
            floor_info = json.loads(floor_info_raw)
        except Exception:
            return []
    else:
        floor_info = floor_info_raw or []

    rows: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for floor_item in floor_info:
        if not isinstance(floor_item, dict):
            continue
        floor = floor_item.get("floor", "") or ""
        for s in floor_item.get("stores", []) or []:
            if isinstance(s, dict):
                name = (s.get("name", "") or "").strip()
                category = (s.get("category", "") or "").strip()
            else:
                name, category = str(s).strip(), ""
            if name and name not in seen:
                seen.add(name)
                rows.append((name, floor, category))
    return rows


def sort_buildings(
    buildings: list[dict],
    prefer_lat: float | None,
    prefer_lng: float | None,
) -> list[dict]:
    if prefer_lat is None or prefer_lng is None:
        return buildings

    def _dist(b: dict) -> float:
        if b["lat"] is None or b["lng"] is None:
            return float("inf")
        dlat = math.radians(b["lat"] - prefer_lat)
        dlng = math.radians(b["lng"] - prefer_lng)
        a = (math.sin(dlat / 2) ** 2
             + math.cos(math.radians(prefer_lat)) * math.cos(math.radians(b["lat"]))
             * math.sin(dlng / 2) ** 2)
        return math.asin(math.sqrt(a))

    return sorted(buildings, key=_dist)


# ── 메인 ──────────────────────────────────────────────────────────────────────

async def run(
    limit: int,
    sleep_sec: float,
    skip_existing: bool,
    prefer_lat: float | None = None,
    prefer_lng: float | None = None,
) -> None:
    buildings = sort_buildings(await load_buildings(), prefer_lat, prefer_lng)
    if not buildings:
        print("placeinfo에 floor_info 매장이 있는 건물 없음 — 중단")
        return

    total_stores = sum(len(b["stores"]) for b in buildings)
    print(f"처리 대상 건물 {len(buildings)}개 / floor_info 매장 {total_stores}개"
          + (f" (건물당 최대 {limit}개)" if limit > 0 else " (건물당 전체)"))

    pool = await get_pool()

    os.makedirs("logs", exist_ok=True)
    resume_path = pathlib.Path("logs/seed_from_floor_info_resume.txt")

    async with pool.acquire() as conn:
        existing: set[str] = (
            {f"{r['place_id']}/{r['store_name']}"
             for r in await conn.fetch("SELECT place_id, store_name FROM storedetails")}
            if skip_existing else set()
        )

    done_keys: set[str] = set()
    if skip_existing and resume_path.exists():
        done_keys = set(resume_path.read_text(encoding="utf-8").splitlines())
        print(f"resume 파일에서 {len(done_keys)}개 건물 skip 로드")

    total_results: list[dict] = []
    t_start = time.time()
    first_store = True

    for b_idx, b in enumerate(buildings, 1):
        building_key = b["building_key"]
        if building_key in done_keys:
            continue

        lat, lng = b["lat"], b["lng"]
        print(f"\n{'='*60}")
        print(f"[{b_idx}/{len(buildings)}] {building_key}  {b['name_ko']}"
              + (f"  lat={lat:.5f} lng={lng:.5f}" if lat and lng else "  (좌표 없음)"))

        stores = b["stores"]
        if limit > 0:
            stores = stores[:limit]

        for i, (name, floor, category) in enumerate(stores, 1):
            dedup_key = f"{building_key}/{name}"
            if dedup_key in existing:
                print(f"    [{i:2d}] {name} ({floor}) — skip (기존)")
                continue

            if not first_store:
                await asyncio.sleep(sleep_sec)
            first_store = False

            try:
                row = await get_store_detail(
                    building_key, name, lat=lat, lng=lng, floor_category=category,
                )
                existing.add(dedup_key)
                total_results.append({
                    "building_key": building_key,
                    "store_name":   name,
                    "floor":        floor,
                    "place_id":     building_key,
                    "category_key": row.get("category_key"),
                    "source":       row.get("source") or "(empty)",
                })
                print(f"    [{i:2d}] {name} ({floor})"
                      f"  key={row.get('category_key')}"
                      f"  source={row.get('source') or '(empty)'}")
            except Exception as e:
                print(f"    [{i:2d}] {name} ({floor}) ✗ {type(e).__name__}: {e}")
                total_results.append({
                    "building_key": building_key, "store_name": name,
                    "floor": floor, "error": str(e),
                })

        if skip_existing:
            with resume_path.open("a", encoding="utf-8") as f:
                f.write(building_key + "\n")
            done_keys.add(building_key)

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

    log_path = f"logs/seed_from_floor_info_{int(t_start)}.json"
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump(
            {"buildings": len(buildings), "limit": limit, "results": total_results},
            f, ensure_ascii=False, indent=2,
        )
    print(f"\n결과 로그: {log_path}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="placeinfo.floor_info 매장 명단으로 store_details 시드"
    )
    parser.add_argument(
        "--limit", type=int, default=0,
        help="건물당 시드할 매장 수 (기본 0 = 전체)",
    )
    parser.add_argument(
        "--sleep", type=float, default=1.5,
        help="매장 간 sleep 초 (기본 1.5 — Naver transient 실패 방지)",
    )
    parser.add_argument(
        "--skip-existing", action="store_true",
        help="storedetails에 이미 있는 매장·완료 건물은 건너뜀",
    )
    parser.add_argument(
        "--prefer-lat", type=float, default=None,
        help="이 좌표에 가까운 건물부터 처리 (예: 37.5636)",
    )
    parser.add_argument(
        "--prefer-lng", type=float, default=None,
        help="이 좌표에 가까운 건물부터 처리 (예: 126.9822)",
    )
    args = parser.parse_args()

    asyncio.run(run(
        limit=args.limit,
        sleep_sec=args.sleep,
        skip_existing=args.skip_existing,
        prefer_lat=args.prefer_lat,
        prefer_lng=args.prefer_lng,
    ))


if __name__ == "__main__":
    main()
