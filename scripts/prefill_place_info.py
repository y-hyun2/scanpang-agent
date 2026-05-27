"""
prefill_place_info.py
─────────────────────────────────────────────────────────────────────────────
buildings 테이블에 쌓인 모든 건물에 대해 place_info를 사전 적재한다.
스캔 없이 pipeline.process_one_building을 직접 배치 실행.

실행:
    python scripts/prefill_place_info.py                      # 전체 처리
    python scripts/prefill_place_info.py --skip-existing      # 이미 적재된 ufid 건너뜀
    python scripts/prefill_place_info.py --delay 2.0          # API 호출 간격(초) 조정
    python scripts/prefill_place_info.py --limit 50           # 최대 N개만 처리
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
from rag.automation.pipeline import process_one_building


async def fetch_target_ufids(skip_existing: bool, limit: int | None) -> list[str]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT ufid FROM buildings WHERE ufid IS NOT NULL ORDER BY ufid"
        )
    all_ufids = [r["ufid"] for r in rows]

    if skip_existing:
        async with pool.acquire() as conn:
            done_rows = await conn.fetch("SELECT ufid FROM place_info")
        done = {r["ufid"] for r in done_rows}
        all_ufids = [u for u in all_ufids if u not in done]
        print(f"  --skip-existing: {len(done)}개 이미 적재됨, {len(all_ufids)}개 남음")

    if limit:
        all_ufids = all_ufids[:limit]

    return all_ufids


async def run(skip_existing: bool, delay: float, limit: int | None) -> None:
    print("buildings 테이블에서 ufid 조회 중...")
    ufids = await fetch_target_ufids(skip_existing, limit)

    total = len(ufids)
    if total == 0:
        print("처리할 건물 없음 — 종료")
        return

    print(f"처리 대상: {total}개 건물 (API 호출 간격: {delay}s)\n")

    ok_count      = 0
    partial_count = 0
    error_count   = 0
    t_start       = time.time()

    for i, ufid in enumerate(ufids, 1):
        print(f"[{i}/{total}] {ufid}")
        try:
            result = await process_one_building(ufid)
            status = result.get("status", "error")
            name   = result.get("name_ko", "")
            if status == "ok":
                ok_count += 1
                print(f"  ✓ ok  — {name!r}  (confirmed={result.get('confirmed_count')}, "
                      f"coverage={result.get('coverage_rate', 0):.0%})")
            elif status == "partial":
                partial_count += 1
                print(f"  △ partial — {name!r}")
            else:
                error_count += 1
                print(f"  ✗ error — {result.get('error')}")
        except Exception as e:
            error_count += 1
            print(f"  ✗ 예외: {e}")

        if i < total:
            await asyncio.sleep(delay)

    elapsed = time.time() - t_start
    print(f"\n완료: {total}개 처리 ({elapsed:.0f}s)")
    print(f"  ok={ok_count}  partial={partial_count}  error={error_count}")

    await close_pool()


def main() -> None:
    parser = argparse.ArgumentParser(description="buildings → place_info 사전 적재")
    parser.add_argument("--skip-existing", action="store_true",
                        help="이미 place_info에 있는 ufid는 건너뜀")
    parser.add_argument("--delay", type=float, default=1.0,
                        help="건물 간 처리 대기 시간(초). 기본 1.0")
    parser.add_argument("--limit", type=int, default=None,
                        help="최대 처리 건수 (미지정 시 전체)")
    args = parser.parse_args()
    asyncio.run(run(
        skip_existing=args.skip_existing,
        delay=args.delay,
        limit=args.limit,
    ))


if __name__ == "__main__":
    main()
