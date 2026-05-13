"""
seed_store_details.py
특정 건물(또는 매장명 리스트)의 매장을 store_details에 시드해
Supabase Table Editor에서 직접 확인할 수 있게 만든다.

사용법:
    # 건물 ufid를 주면 그 건물 floor_info의 매장 N개를 시드
    python scripts/seed_store_details.py --ufid 1981198318434513829100000000 --limit 5

    # 건물명으로 시드 (place_info에서 name_ko 부분일치)
    python scripts/seed_store_details.py --name "롯데백화점 본점" --limit 5

    # 매장명 직접 지정
    python scripts/seed_store_details.py --ufid 1981... --store "스타벅스 명동점" "올리브영 명동점"
"""

import argparse
import asyncio
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.db import get_pool
from tools.store_tools import get_store_detail


async def _resolve_building(ufid: str | None, name: str | None) -> dict | None:
    """ufid 또는 name으로 place_info row 조회."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        if ufid:
            return await conn.fetchrow(
                "SELECT ufid, name_ko, floor_info FROM place_info WHERE ufid = $1",
                ufid,
            )
        if name:
            return await conn.fetchrow(
                "SELECT ufid, name_ko, floor_info FROM place_info "
                "WHERE name_ko ILIKE $1 LIMIT 1",
                f"%{name}%",
            )
    return None


def _extract_stores(floor_info_raw, limit: int) -> list[tuple[str, str]]:
    """floor_info JSONB에서 (store_name, floor) 쌍을 limit개 추출."""
    if isinstance(floor_info_raw, str):
        floor_info = json.loads(floor_info_raw)
    else:
        floor_info = floor_info_raw or []

    pairs: list[tuple[str, str]] = []
    for floor_item in floor_info:
        floor = floor_item.get("floor", "")
        for s in floor_item.get("stores", []):
            name = s.get("name", "") if isinstance(s, dict) else str(s)
            if name:
                pairs.append((name.strip(), floor))
            if len(pairs) >= limit:
                return pairs
    return pairs


async def run(ufid: str | None, name: str | None, store_names: list[str], limit: int):
    # store 직접 지정 시 ufid 필요
    if store_names and not ufid:
        print("--store 사용 시 --ufid도 필요")
        return

    if store_names:
        building_ufid = ufid
        target_pairs = [(s, "") for s in store_names]
    else:
        bld = await _resolve_building(ufid, name)
        if not bld:
            print(f"건물 못 찾음 (ufid={ufid!r}, name={name!r})")
            return
        building_ufid = bld["ufid"]
        print(f"\n건물: {bld['name_ko']} (ufid={building_ufid})")
        target_pairs = _extract_stores(bld["floor_info"], limit)
        if not target_pairs:
            print("floor_info에 매장 없음")
            return

    print(f"시드 대상: {len(target_pairs)}개 매장\n")

    for i, (store_name, floor) in enumerate(target_pairs, 1):
        print(f"[{i}/{len(target_pairs)}] {store_name} ({floor})")
        try:
            result = await get_store_detail(building_ufid, store_name)
            if not result:
                print(f"  - 결과 없음")
                continue
            print(f"  category_key : {result.get('category_key')}")
            print(f"  category     : {result.get('category')!r}")
            print(f"  phone        : {result.get('phone')!r}")
            print(f"  addr         : {result.get('addr')!r}")
            print(f"  open_hours   : {result.get('open_hours')!r}")
            print(f"  homepage     : {result.get('homepage')!r}")
            print(f"  details      : {result.get('details')}")
            print(f"  source       : {result.get('source')}")
        except Exception as e:
            print(f"  ✗ 실패: {type(e).__name__}: {e}")
        print()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ufid",  type=str, help="건물 ufid (정확 일치)")
    parser.add_argument("--name",  type=str, help="건물명 부분일치")
    parser.add_argument("--store", type=str, nargs="+",
                        help="매장명 직접 지정 (여러 개), --ufid 필요")
    parser.add_argument("--limit", type=int, default=5,
                        help="floor_info에서 뽑을 매장 수 (기본 5)")
    args = parser.parse_args()

    if not (args.ufid or args.name):
        parser.error("--ufid 또는 --name 중 하나는 필수")

    asyncio.run(run(args.ufid, args.name, args.store or [], args.limit))


if __name__ == "__main__":
    main()
