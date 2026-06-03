"""
seed_radius.py
특정 좌표 반경 N미터 안의 placeinfo 건물만 골라, 그 건물 floor_info 매장을
storedetails 에 시드한다. (seed_from_floor_info 의 반경 한정판)

좌표는 직접 주거나(--lat/--lng) 주소로 geocode(--address) 한다.

실행:
    # 좌표 직접
    python scripts/seed_radius.py --lat 37.5636759 --lng 126.9867758 --radius 100 --skip-existing
    # 주소로 (카카오 geocode)
    python scripts/seed_radius.py --address "서울 중구 명동길 74" --radius 100 --skip-existing
"""

import argparse
import asyncio
import json
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import httpx
from dotenv import load_dotenv, dotenv_values

from core.db import get_pool
from tools.store_tools import get_store_detail

load_dotenv()
_KAKAO_KEY = os.getenv("KAKAO_REST_API_KEY", "") or dotenv_values(".env").get("KAKAO_REST_API_KEY", "")


def _haversine_m(lat1, lng1, lat2, lng2) -> float:
    R = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


async def _geocode(address: str) -> tuple[float, float] | None:
    async with httpx.AsyncClient(timeout=10) as c:
        r = await c.get(
            "https://dapi.kakao.com/v2/local/search/address.json",
            headers={"Authorization": f"KakaoAK {_KAKAO_KEY}"},
            params={"query": address},
        )
        docs = r.json().get("documents", [])
    if not docs:
        return None
    d = docs[0]
    return float(d["y"]), float(d["x"])


def _extract_stores(floor_info_raw) -> list[tuple[str, str, str]]:
    if isinstance(floor_info_raw, str):
        try:
            floor_info_raw = json.loads(floor_info_raw)
        except Exception:
            return []
    rows, seen = [], set()
    for f in floor_info_raw or []:
        if not isinstance(f, dict):
            continue
        floor = f.get("floor", "") or ""
        for s in (f.get("stores", []) or []):
            if isinstance(s, dict):
                name = (s.get("name", "") or "").strip()
                cat = (s.get("category", "") or "").strip()
            else:
                name, cat = str(s).strip(), ""
            if name and name not in seen:
                seen.add(name)
                rows.append((name, floor, cat))
    return rows


async def run(lat, lng, radius_m, skip_existing, limit, sleep_sec):
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT building_key, name_ko, lat, lng, floor_info "
            "FROM placeinfo WHERE floor_info IS NOT NULL AND lat IS NOT NULL"
        )
    targets = []
    for r in rows:
        dist = _haversine_m(lat, lng, float(r["lat"]), float(r["lng"]))
        if dist <= radius_m:
            stores = _extract_stores(r["floor_info"])
            if stores:
                targets.append((round(dist), r["building_key"], r["name_ko"] or "",
                                float(r["lat"]), float(r["lng"]), stores))
    targets.sort(key=lambda t: t[0])
    total_stores = sum(len(t[5]) for t in targets)
    print(f"반경 {radius_m:.0f}m 내 건물 {len(targets)}개 / floor_info 매장 {total_stores}개\n")
    if not targets:
        return

    existing = set()
    if skip_existing:
        async with pool.acquire() as conn:
            existing = {f"{x['place_id']}/{x['store_name']}"
                        for x in await conn.fetch("SELECT place_id, store_name FROM storedetails")}

    ok = skip = err = 0
    t0 = time.time()
    first = True
    for dist, bk, nm, b_lat, b_lng, stores in targets:
        print(f"{'='*50}\n[{dist}m] {bk}  {nm!r}  매장 {len(stores)}개")
        if limit > 0:
            stores = stores[:limit]
        for i, (name, floor, cat) in enumerate(stores, 1):
            if f"{bk}/{name}" in existing:
                print(f"   [{i:2d}] {name} — skip(기존)"); skip += 1; continue
            if not first:
                await asyncio.sleep(sleep_sec)
            first = False
            try:
                row = await get_store_detail(bk, name, lat=b_lat, lng=b_lng, floor_category=cat)
                existing.add(f"{bk}/{name}")
                print(f"   [{i:2d}] {name} ({floor}) key={row.get('category_key')} src={row.get('source') or '(empty)'}")
                ok += 1
            except Exception as e:
                print(f"   [{i:2d}] {name} ✗ {type(e).__name__}: {e}"); err += 1

    print(f"\n{'='*50}\n완료: 성공={ok} 스킵={skip} 실패={err} 소요={time.time()-t0:.0f}s")


def main():
    ap = argparse.ArgumentParser(description="좌표/주소 반경 내 storedetails 시드")
    ap.add_argument("--lat", type=float)
    ap.add_argument("--lng", type=float)
    ap.add_argument("--address", type=str, help="좌표 대신 주소(카카오 geocode)")
    ap.add_argument("--radius", type=float, default=100, help="반경 m (기본 100)")
    ap.add_argument("--skip-existing", action="store_true")
    ap.add_argument("--limit", type=int, default=0, help="건물당 매장 수(0=전체)")
    ap.add_argument("--sleep", type=float, default=1.5)
    args = ap.parse_args()

    lat, lng = args.lat, args.lng
    if (lat is None or lng is None):
        if not args.address:
            ap.error("--lat/--lng 또는 --address 필요")
        got = asyncio.run(_geocode(args.address))
        if not got:
            print(f"geocode 실패: {args.address!r}"); return
        lat, lng = got
        print(f"geocode: {args.address!r} → ({lat:.6f}, {lng:.6f})")

    asyncio.run(run(lat, lng, args.radius, args.skip_existing, args.limit, args.sleep))


if __name__ == "__main__":
    main()
