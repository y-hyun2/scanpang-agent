"""
buildings 테이블의 h3_index_10, estimated_height 컬럼을 일괄 채워넣는 백필.
한 번만 실행하면 됨. 이후 새 적재 시엔 import 스크립트가 직접 채울 예정.

실행:
    python scripts/backfill_buildings_metadata.py
"""

import asyncio
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import asyncpg
import h3
from dotenv import load_dotenv

load_dotenv()

BATCH_SIZE = 1000

# usability 코드 → 카테고리 분기 키워드 (estimated_height 계산 전용)
# semantic_category 컬럼이 아니라 이 스크립트 안에서만 쓰는 임시 매핑.
USABILITY_TO_CATEGORY = {
    "02000": "주거",   "03000": "주거",
    "04000": "근생",   "05000": "근생",
    "06000": "문화",   "07000": "판매",
    "14000": "업무",   "15000": "숙박",
}


def estimate_height(usability_code: str, grnd_flr: int) -> float:
    """
    실측 높이가 없을 때 쓰는 통계적 폴백.
    층수도 없거나 1이면 도심 평균 2.5층으로 가정.
    """
    floor = grnd_flr if (grnd_flr and grnd_flr >= 1) else 2.5
    category = USABILITY_TO_CATEGORY.get(usability_code or "", "")

    if category in ("업무", "판매"):
        return (4.5 + (floor - 1) * 3.5) * 1.15
    elif category == "주거":
        return (floor * 2.8) * 1.10
    else:
        return (floor * 3.0) * 1.10


async def backfill():
    db_url = os.getenv("SUPABASE_DATABASE_URL")
    if not db_url:
        print("SUPABASE_DATABASE_URL이 .env에 없습니다.")
        sys.exit(1)

    conn = await asyncpg.connect(db_url, statement_cache_size=0)
    try:
        total = await conn.fetchval("""
            SELECT COUNT(*) FROM buildings
            WHERE h3_index_10 IS NULL OR estimated_height IS NULL
        """)
        print(f"백필 대상: {total}건")

        if total == 0:
            print("이미 모두 채워져있음.")
            return

        processed = 0
        skipped = 0

        while True:
            rows = await conn.fetch("""
                SELECT ufid, center_lat, center_lng, grnd_flr, usability
                FROM buildings
                WHERE h3_index_10 IS NULL OR estimated_height IS NULL
                LIMIT $1
            """, BATCH_SIZE)

            if not rows:
                break

            updates = []
            for r in rows:
                lat = r["center_lat"]
                lng = r["center_lng"]
                if not lat or not lng:
                    skipped += 1
                    continue

                h3_cell = h3.latlng_to_cell(lat, lng, 10)
                est_h = estimate_height(r["usability"], r["grnd_flr"] or 0)
                updates.append((h3_cell, est_h, r["ufid"]))

            await conn.executemany("""
                UPDATE buildings
                SET h3_index_10 = $1, estimated_height = $2
                WHERE ufid = $3
            """, updates)

            processed += len(updates)
            print(f"  진행: {processed}/{total}")

        print(f"\n완료: {processed}건 백필, {skipped}건 좌표 없어서 스킵")
    finally:
        await conn.close()


if __name__ == "__main__":
    asyncio.run(backfill())