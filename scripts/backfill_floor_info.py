"""
backfill_floor_info.py
place_info.floor_info 컬럼의 floor 라벨을 tools.floor_utils 기준으로 정규화 + 정렬.

기존 라벨: "1층"/"3F"/"지하1층"/"B1"/"미확인"/"기타"/"" 가 혼재.
실행 후: B3 → B2 → B1 → 1F → 2F → ... → RF → 기타 오름차순.
같은 라벨로 정규화되는 항목은 stores 머지.
"""

import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from core.db import get_pool, close_pool
from tools.floor_utils import sort_floor_info


async def main(dry_run: bool = False) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT ufid, floor_info FROM place_info WHERE floor_info IS NOT NULL"
        )

    changed: list[tuple[str, list, list]] = []
    for r in rows:
        old = r["floor_info"] or []
        if not isinstance(old, list) or not old:
            continue
        new = sort_floor_info(old)
        if new != old:
            changed.append((r["ufid"], old, new))

    print(f"[backfill] 대상 row: {len(rows)}개, 변경 row: {len(changed)}개")
    for ufid, old, new in changed[:5]:
        old_keys = [f.get("floor") for f in old if isinstance(f, dict)]
        new_keys = [f.get("floor") for f in new if isinstance(f, dict)]
        print(f"  - {ufid}: {old_keys} → {new_keys}")
    if len(changed) > 5:
        print(f"  ... (+{len(changed) - 5}건)")

    if dry_run:
        print("[backfill] dry_run=True — DB 미반영")
        await close_pool()
        return

    async with pool.acquire() as conn:
        async with conn.transaction():
            for ufid, _old, new in changed:
                await conn.execute(
                    "UPDATE place_info SET floor_info = $1::jsonb WHERE ufid = $2",
                    json.dumps(new, ensure_ascii=False),
                    ufid,
                )
    print(f"[backfill] {len(changed)}개 row 업데이트 완료")
    await close_pool()


if __name__ == "__main__":
    dry = "--dry" in sys.argv
    asyncio.run(main(dry_run=dry))
