"""
refresh_closed_days.py
Naver Place는 오늘 기준 30일 이내의 휴무일만 노출하므로 월 1회 재크롤이 필요하다.
place_info의 ufid 전체(또는 일부)를 돌면서 fetch_place_detail로 closed_days만
다시 받아 업데이트한다. floor_info·이름 등 다른 필드는 건드리지 않는다.

실행:
    python scripts/refresh_closed_days.py                    # 전체
    python scripts/refresh_closed_days.py --limit 10         # 상위 10개만
    python scripts/refresh_closed_days.py --ufid 198...      # 특정 ufid
    python scripts/refresh_closed_days.py --older-than 25    # last_refresh가 25일 이상 지난 것만

스케줄 추천: 매월 1일 cron으로 실행 (Naver의 30일 윈도우와 맞추기 위함).
"""

import argparse
import asyncio
import os
import sys
import time
from datetime import datetime, timedelta, timezone

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.db import get_pool
from rag.automation.naver_map_scraper import fetch_place_detail


async def _fetch_targets(limit: int, ufids: list[str], older_than_days: int) -> list[dict]:
    """closed_days 재크롤 대상 조회. name_ko·addr가 있어야 검색 가능."""
    pool = await get_pool()
    where = ["name_ko IS NOT NULL", "name_ko != ''", "addr IS NOT NULL"]
    params: list = []
    if ufids:
        params.append(ufids)
        where.append(f"ufid = ANY(${len(params)}::text[])")
    if older_than_days > 0:
        cutoff = datetime.now(timezone.utc) - timedelta(days=older_than_days)
        params.append(cutoff)
        where.append(f"(last_updated IS NULL OR last_updated < ${len(params)})")

    sql = f"""
        SELECT ufid, name_ko, addr, closed_days
        FROM place_info
        WHERE {' AND '.join(where)}
        ORDER BY last_updated NULLS FIRST
    """
    if limit > 0:
        sql += f" LIMIT {limit}"

    async with pool.acquire() as conn:
        rows = await conn.fetch(sql, *params)
    return [dict(r) for r in rows]


async def _update_closed_days(ufid: str, closed_days: str) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE place_info "
            "SET closed_days = $2, last_updated = $3 "
            "WHERE ufid = $1",
            ufid, closed_days or None, datetime.now(timezone.utc),
        )


async def run(limit: int, ufids: list[str], older_than_days: int) -> None:
    targets = await _fetch_targets(limit, ufids, older_than_days)
    print(f"\n[refresh_closed_days] 대상: {len(targets)}개\n")

    t_start = time.time()
    updated, unchanged, missing, errors = 0, 0, 0, 0

    for i, t in enumerate(targets, 1):
        ufid    = t["ufid"]
        bld_nm  = t["name_ko"]
        addr    = t["addr"] or ""
        old_cd  = t["closed_days"] or ""

        query = f"{addr} {bld_nm}".strip()
        print(f"[{i:3d}/{len(targets)}] {bld_nm} (ufid={ufid[:10]}...)")

        try:
            detail = await fetch_place_detail(
                query, expected_name=bld_nm, expected_addr=addr,
            )
        except Exception as e:
            print(f"  ✗ fetch 실패: {e}")
            errors += 1
            continue

        if not detail:
            print(f"  - Naver Place 미매칭 (스킵)")
            missing += 1
            continue

        new_cd = detail.get("closed_days", "") or ""
        if new_cd == old_cd:
            print(f"  = 변경 없음: {new_cd!r}")
            unchanged += 1
            continue

        try:
            await _update_closed_days(ufid, new_cd)
            print(f"  ✓ 업데이트: {old_cd!r} → {new_cd!r}")
            updated += 1
        except Exception as e:
            print(f"  ✗ DB 업데이트 실패: {e}")
            errors += 1

    elapsed = time.time() - t_start
    print(f"\n{'='*60}")
    print(f"완료: 전체 {len(targets)}개 / {elapsed:.0f}초")
    print(f"  업데이트={updated}  변경없음={unchanged}  미매칭={missing}  실패={errors}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit",  type=int, default=0,
                        help="처리할 최대 개수 (0=무제한)")
    parser.add_argument("--ufid",   type=str, nargs="+",
                        help="특정 ufid만 갱신 (여러 개 가능)")
    parser.add_argument("--older-than", type=int, default=0, dest="older_than_days",
                        help="last_updated가 N일 이상 지난 row만 (0=모두)")
    args = parser.parse_args()
    asyncio.run(run(args.limit, args.ufid or [], args.older_than_days))


if __name__ == "__main__":
    main()
