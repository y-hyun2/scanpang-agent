"""
refresh_open_hours.py
storedetails에서 영업시간이 비었거나(NULL) "01:00에 라스트오더"/"19:00에 영업 종료"
같은 상태 조각만 들어간 매장을 골라, Naver Place 상세를 다시 긁어 raw_open_hours만
갱신한다. 다른 필드(category·addr·phone 등)는 건드리지 않는다.

이런 조각이 들어가는 원인: 네이버 영업시간 토글 파싱이 빈값을 반환하면 목록 요약
(newBusinessHours.description)으로 폴백하는데, 그 요약이 상태 텍스트인 경우가 있다.
(매일/평일/주말 라벨 누락 버그는 naver_map_scraper 파서 수정으로 해결됨.)

안전장치: 새로 받은 값이 '진짜 스케줄'(시간 범위 '-' 또는 '휴무' 포함)일 때만 UPDATE.
          또 조각이면 기존 값을 유지(덮어쓰지 않음).

실행:
    python scripts/refresh_open_hours.py                 # NULL + 조각 전부
    python scripts/refresh_open_hours.py --junk-only     # 조각만 (NULL 제외)
    python scripts/refresh_open_hours.py --limit 20
"""

import argparse
import asyncio
import os
import re
import sys
import time
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.db import get_pool
from rag.automation.naver_map_scraper import fetch_place_detail

# 상태 조각 패턴: "...에 라스트오더 / 영업 종료 / 영업시작" 류.
_JUNK_RE = re.compile(r"에\s*(라스트오더|영업\s*종료|영업\s*시작|영업\s*중)")
# 진짜 스케줄 판별: 시간 범위(09:00 - 18:00) 또는 '휴무' 포함.
_SCHEDULE_RE = re.compile(r"\d{1,2}:\d{2}\s*[-~]\s*|휴무")


def _is_junk(v: str | None) -> bool:
    if not v:
        return False
    return bool(_JUNK_RE.search(v)) and not _SCHEDULE_RE.search(v)


def _is_real_schedule(v: str) -> bool:
    return bool(v) and bool(_SCHEDULE_RE.search(v))


async def _fetch_targets(limit: int, junk_only: bool) -> list[dict]:
    pool = await get_pool()
    if junk_only:
        where = "raw_open_hours ~ '에 ?(라스트오더|영업 ?종료|영업 ?시작|영업 ?중)'"
    else:
        where = ("raw_open_hours IS NULL "
                 "OR raw_open_hours ~ '에 ?(라스트오더|영업 ?종료|영업 ?시작|영업 ?중)'")
    sql = f"""
        SELECT id, store_name, addr, raw_open_hours
        FROM storedetails
        WHERE store_name IS NOT NULL AND store_name != ''
          AND ({where})
        ORDER BY raw_open_hours NULLS FIRST
    """
    if limit > 0:
        sql += f" LIMIT {limit}"
    async with pool.acquire() as conn:
        rows = await conn.fetch(sql)
    return [dict(r) for r in rows]


async def _update_open_hours(store_id: str, open_hours: str) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE storedetails SET raw_open_hours = $2, last_updated = $3 WHERE id = $1",
            store_id, open_hours, datetime.now(timezone.utc),
        )


async def run(limit: int, junk_only: bool, sleep_sec: float) -> None:
    targets = await _fetch_targets(limit, junk_only)
    print(f"\n[refresh_open_hours] 대상: {len(targets)}개\n")

    t_start = time.time()
    updated = unchanged = missing = errors = 0

    for i, t in enumerate(targets, 1):
        store_id = t["id"]
        name     = t["store_name"]
        addr     = t["addr"] or ""
        old_oh   = t["raw_open_hours"]

        print(f"[{i:3d}/{len(targets)}] {name!r}  (old={old_oh!r})")
        try:
            if i > 1:
                await asyncio.sleep(sleep_sec)
            detail = await fetch_place_detail(
                query=name, expected_name=name, expected_addr=addr,
                structured_hours=True,
            )
        except Exception as e:
            print(f"  ✗ fetch 실패: {type(e).__name__}: {e}")
            errors += 1
            continue

        new_oh = (detail or {}).get("open_hours", "") or ""
        if not detail:
            print(f"  - Naver 미매칭 (스킵)")
            missing += 1
            continue
        if not _is_real_schedule(new_oh):
            # 여전히 조각이거나 빈값 — 기존 값 유지(덮어쓰지 않음)
            print(f"  - 스케줄 아님, 유지: {new_oh!r}")
            unchanged += 1
            continue
        if new_oh == old_oh:
            print(f"  = 변경 없음")
            unchanged += 1
            continue

        try:
            await _update_open_hours(store_id, new_oh)
            print(f"  ✓ 업데이트 → {new_oh!r}")
            updated += 1
        except Exception as e:
            print(f"  ✗ DB 업데이트 실패: {e}")
            errors += 1

    elapsed = time.time() - t_start
    print(f"\n{'='*60}")
    print(f"완료: 전체 {len(targets)}개 / {elapsed:.0f}초")
    print(f"  업데이트={updated}  유지={unchanged}  미매칭={missing}  실패={errors}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="storedetails 영업시간(NULL/조각) Naver 재크롤 갱신"
    )
    parser.add_argument("--limit", type=int, default=0, help="최대 개수 (0=무제한)")
    parser.add_argument("--junk-only", action="store_true",
                        help="NULL 제외, 상태 조각만 대상")
    parser.add_argument("--sleep", type=float, default=1.5,
                        help="매장 간 sleep 초 (기본 1.5)")
    args = parser.parse_args()
    asyncio.run(run(args.limit, args.junk_only, args.sleep))


if __name__ == "__main__":
    main()
