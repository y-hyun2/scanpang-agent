"""
import_subway_exits.py
서울교통공사 출구별 주요시설 CSV (rag/data/subway_exits.csv) → Supabase.

테이블이 없으면 생성. 같은 행 (operator/line/station/exit/facility) 중복은 ON CONFLICT DO NOTHING.

사용법:
    python scripts/import_subway_exits.py
"""

import asyncio
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.db import get_pool

CSV_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "rag", "data", "subway_exits.csv")

_DDL = """
CREATE TABLE IF NOT EXISTS subway_exits (
    operator       text NOT NULL,
    line           text NOT NULL,
    station_name   text NOT NULL,
    exit_no        text NOT NULL,
    facility_name  text NOT NULL DEFAULT '',
    lat            double precision,
    lng            double precision,
    PRIMARY KEY (operator, line, station_name, exit_no, facility_name)
);
CREATE INDEX IF NOT EXISTS subway_exits_station_line
    ON subway_exits (station_name, line);
"""

_UPSERT = """
INSERT INTO subway_exits (operator, line, station_name, exit_no, facility_name)
VALUES ($1, $2, $3, $4, $5)
ON CONFLICT (operator, line, station_name, exit_no, facility_name) DO NOTHING
"""


async def run():
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(_DDL)
        print(f"[OK] subway_exits table ensured.")

        with open(CSV_PATH, encoding="utf-8") as f:
            reader = csv.DictReader(f)
            rows = [
                (
                    (r.get("철도운영기관") or "").strip(),
                    (r.get("노선명")       or "").strip(),
                    (r.get("역명")         or "").strip(),
                    (r.get("출구번호")     or "").strip(),
                    (r.get("출구별주요시설명") or "").strip(),
                )
                for r in reader
            ]
        rows = [r for r in rows if r[2] and r[3]]  # 역명·출구번호 필수
        print(f"[INFO] CSV rows: {len(rows)}")

        async with conn.transaction():
            await conn.executemany(_UPSERT, rows)

        # 검증
        cnt    = await conn.fetchval("SELECT COUNT(*) FROM subway_exits")
        sample = await conn.fetch(
            "SELECT exit_no, facility_name FROM subway_exits "
            "WHERE station_name='명동' AND line='4호선' ORDER BY exit_no, facility_name"
        )
        print(f"[OK] subway_exits 총 {cnt}행, 명동 4호선 출구: {len(sample)}건")
        for r in sample[:8]:
            print(f"    {r['exit_no']} → {r['facility_name']}")

    await pool.close()


if __name__ == "__main__":
    asyncio.run(run())
