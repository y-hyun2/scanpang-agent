"""
redis_usage_inspect.py
사용량 통계 Redis 키를 사람이 읽기 쉽게 출력.

실행:
    python scripts/redis_usage_inspect.py            # 로컬 Redis (.env REDIS_URL)
    python scripts/redis_usage_inspect.py --user UID # 특정 사용자만
    python scripts/redis_usage_inspect.py --url redis://...   # Railway 등 외부 Redis

KST 기준 오늘/이번달 + 용도별 + 전역 비용을 한 번에 보여준다.
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import redis.asyncio as aioredis
from dotenv import load_dotenv

load_dotenv()


def _today_kst() -> str:
    return datetime.now(timezone(timedelta(hours=9))).strftime("%Y-%m-%d")


def _month_kst() -> str:
    return datetime.now(timezone(timedelta(hours=9))).strftime("%Y-%m")


async def _scan(client: aioredis.Redis, pattern: str) -> list[str]:
    """SCAN 으로 키 전체 수집 (KEYS 는 prod 차단되므로 SCAN 권장)."""
    keys: list[str] = []
    cursor = 0
    while True:
        cursor, batch = await client.scan(cursor=cursor, match=pattern, count=500)
        keys.extend(batch)
        if cursor == 0:
            break
    return keys


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--user", default="", help="특정 user_id 만 필터링")
    parser.add_argument("--url", default=os.getenv("REDIS_URL", "redis://localhost:6379/0"))
    args = parser.parse_args()

    client = aioredis.from_url(args.url, encoding="utf-8", decode_responses=True)
    try:
        await client.ping()
    except Exception as e:
        print(f"❌ Redis 연결 실패: {e}")
        return

    print(f"📊 사용량 통계  (KST {_today_kst()})  Redis = {args.url}")
    print("=" * 70)

    # 1) 전역 비용
    today = _today_kst()
    cost = await client.get(f"global:cost_won:day:{today}") or "0"
    print(f"\n[전체 LLM 비용 — 오늘]  ₩{int(cost):,}")

    # 2) 사용자별 일/월
    user_pattern = f"user:{args.user or '*'}:tokens:day:{today}"
    day_keys = await _scan(client, user_pattern)
    if not day_keys:
        print(f"\n[사용자별]  오늘 사용 기록 없음  (pattern: {user_pattern})")
        return

    rows = []
    for k in day_keys:
        # k = user:{uid}:tokens:day:YYYY-MM-DD
        uid = k.split(":")[1]
        day_tokens = int(await client.get(k) or 0)
        month_tokens = int(await client.get(f"user:{uid}:tokens:month:{_month_kst()}") or 0)
        rows.append((uid, day_tokens, month_tokens))
    rows.sort(key=lambda x: -x[1])  # 일 사용량 많은 순

    print(f"\n[사용자별]  총 {len(rows)}명")
    print(f"  {'user_id':<40} {'today':>10} {'this month':>12}")
    print(f"  {'-' * 40} {'-' * 10} {'-' * 12}")
    for uid, d, m in rows[:30]:
        print(f"  {uid:<40} {d:>10,} {m:>12,}")
    if len(rows) > 30:
        print(f"  ... 외 {len(rows) - 30}명")

    # 3) 용도별 (필터된 사용자만 또는 전체 집계)
    purpose_pattern = f"user:{args.user or '*'}:purpose:*:day:{today}"
    purpose_keys = await _scan(client, purpose_pattern)
    if purpose_keys:
        totals: dict[str, int] = defaultdict(int)
        for k in purpose_keys:
            # k = user:{uid}:purpose:{purpose}:day:YYYY-MM-DD
            parts = k.split(":")
            purpose = parts[3]
            totals[purpose] += int(await client.get(k) or 0)
        print(f"\n[용도별 합계 — 오늘]")
        for purpose, total in sorted(totals.items(), key=lambda x: -x[1]):
            print(f"  {purpose:<20} {total:>10,} tokens")

    await client.aclose()


if __name__ == "__main__":
    asyncio.run(main())
