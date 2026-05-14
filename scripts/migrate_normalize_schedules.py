"""
scripts/migrate_normalize_schedules.py
기존 store_details row 의 open_hours 를 LLM 으로 일괄 정규화해
details.schedule 에 채워 넣는다.

이미 schedule 이 있는 row 는 기본적으로 skip — 강제 재계산은 --force.

사용:
    python scripts/migrate_normalize_schedules.py             # 누락분만
    python scripts/migrate_normalize_schedules.py --force     # 전체 재계산
    python scripts/migrate_normalize_schedules.py --limit 50  # 상위 N개만 (테스트용)
"""

import argparse
import asyncio
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.db import get_pool
from tools.open_hours_normalizer import normalize_open_hours


async def run(force: bool, limit: int | None, sleep_sec: float):
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT id, store_name, open_hours, details
            FROM store_details
            WHERE open_hours IS NOT NULL AND open_hours != ''
            ORDER BY last_updated DESC NULLS LAST
            """
        )

    if not rows:
        print("정규화할 row 없음")
        return

    if limit:
        rows = rows[:limit]

    print(f"전체 {len(rows)}개 row 검사")
    updated = 0
    skipped_existing = 0
    skipped_failed = 0
    t_start = time.time()

    for i, r in enumerate(rows, 1):
        details_raw = r["details"]
        try:
            details = details_raw if isinstance(details_raw, dict) else json.loads(details_raw or "{}")
        except (ValueError, TypeError):
            details = {}
        if not isinstance(details, dict):
            details = {}

        if not force and isinstance(details.get("schedule"), dict):
            skipped_existing += 1
            continue

        if i > 1:
            await asyncio.sleep(sleep_sec)

        schedule = await normalize_open_hours(r["open_hours"])
        if not schedule:
            print(f"  [{i:3d}/{len(rows)}] {r['store_name']!r} — 정규화 실패")
            skipped_failed += 1
            continue

        details["schedule"] = schedule
        async with pool.acquire() as conn:
            await conn.execute(
                "UPDATE store_details SET details = $1::jsonb WHERE id = $2",
                json.dumps(details, ensure_ascii=False),
                r["id"],
            )
        updated += 1
        print(f"  [{i:3d}/{len(rows)}] {r['store_name']!r} → schedule 저장")

    total_t = time.time() - t_start
    print(f"\n완료: updated={updated}  skipped_existing={skipped_existing}  "
          f"skipped_failed={skipped_failed}  소요={total_t:.0f}초")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true",
                        help="이미 schedule 있는 row 도 재계산")
    parser.add_argument("--limit", type=int, default=None,
                        help="상위 N개만 처리 (테스트용)")
    parser.add_argument("--sleep", type=float, default=0.3,
                        help="LLM 호출 사이 sleep 초 (기본 0.3)")
    args = parser.parse_args()

    asyncio.run(run(args.force, args.limit, args.sleep))


if __name__ == "__main__":
    main()
