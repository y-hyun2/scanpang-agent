"""
normalize_store_hours.py
─────────────────────────────────────────────────────────────────────────────
storedetails.raw_open_hours / raw_closed_days 를 LLM 정규화해서
store_hours 테이블 + storedetails.holiday_note 를 채운다.

스크래핑(seed_from_buildings)과 분리된 별도 배치:
- raw_open_hours 는 source of truth (스크래핑이 채움)
- 이 스크립트는 raw → 구조화 store_hours 변환만 (재실행 가능)
- 정규화 로직/프롬프트 개선 시 재스크래핑 없이 이것만 다시 돌림

실행:
    python scripts/normalize_store_hours.py                 # 전체
    python scripts/normalize_store_hours.py --skip-existing # store_hours 이미 있는 매장 건너뜀
    python scripts/normalize_store_hours.py --limit 50      # 최대 N개
─────────────────────────────────────────────────────────────────────────────
"""

import argparse
import asyncio
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from core.db import close_pool, get_pool
from tools.open_hours_normalizer import normalize_and_save


async def fetch_targets(skip_existing: bool, limit: int | None) -> list[dict]:
    """raw_open_hours 가 있는 storedetails 행을 정규화 대상으로 조회."""
    pool = await get_pool()
    where = ["raw_open_hours IS NOT NULL", "raw_open_hours != ''"]
    if skip_existing:
        # store_hours 가 아직 없는 매장만
        where.append(
            "id NOT IN (SELECT DISTINCT store_id FROM store_hours)"
        )
    sql = f"""
        SELECT id, raw_open_hours, raw_closed_days
        FROM storedetails
        WHERE {' AND '.join(where)}
        ORDER BY id
    """
    if limit:
        sql += f" LIMIT {limit}"
    async with pool.acquire() as conn:
        rows = await conn.fetch(sql)
    return [dict(r) for r in rows]


async def run(skip_existing: bool, limit: int | None) -> None:
    print("storedetails 에서 raw_open_hours 조회 중...")
    targets = await fetch_targets(skip_existing, limit)
    total = len(targets)
    if total == 0:
        print("정규화 대상 없음 — 종료")
        await close_pool()
        return

    print(f"정규화 대상: {total}개 매장\n")

    pool = await get_pool()
    ok = empty = error = 0
    t_start = time.time()

    for i, t in enumerate(targets, 1):
        store_id = t["id"]
        oh = t["raw_open_hours"] or ""
        cd = t["raw_closed_days"] or ""
        try:
            async with pool.acquire() as conn:
                async with conn.transaction():
                    holiday_note = await normalize_and_save(store_id, oh, cd, conn)
                    if holiday_note:
                        await conn.execute(
                            "UPDATE storedetails SET holiday_note = $1 WHERE id = $2",
                            holiday_note, store_id,
                        )
            # store_hours 가 실제로 생겼는지 확인
            async with pool.acquire() as conn:
                cnt = await conn.fetchval(
                    "SELECT COUNT(*) FROM store_hours WHERE store_id = $1", store_id
                )
            if cnt > 0:
                ok += 1
                print(f"[{i}/{total}] {store_id} ✓ {cnt}개 구간"
                      f"{'  휴무: ' + holiday_note if holiday_note else ''}")
            else:
                empty += 1
                print(f"[{i}/{total}] {store_id} △ 구간 없음 (raw={oh[:40]!r})")
        except Exception as e:
            error += 1
            print(f"[{i}/{total}] {store_id} ✗ {type(e).__name__}: {e}")

    elapsed = time.time() - t_start
    print(f"\n완료: {total}개 ({elapsed:.0f}s)")
    print(f"  성공={ok}  구간없음={empty}  실패={error}")
    await close_pool()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="storedetails.raw_open_hours → store_hours 정규화 배치"
    )
    parser.add_argument("--skip-existing", action="store_true",
                        help="store_hours 가 이미 있는 매장은 건너뜀")
    parser.add_argument("--limit", type=int, default=None,
                        help="최대 처리 건수")
    args = parser.parse_args()
    asyncio.run(run(skip_existing=args.skip_existing, limit=args.limit))


if __name__ == "__main__":
    main()
