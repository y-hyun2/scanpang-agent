"""
import_buildings_to_postgis.py
vworld_buildings.json (22,000개) → Supabase PostGIS buildings 테이블 일괄 import.

실행:
    python scripts/import_buildings_to_postgis.py --dry-run   # 파싱만 확인
    python scripts/import_buildings_to_postgis.py             # 실제 import
    python scripts/import_buildings_to_postgis.py --reset     # 테이블 초기화 후 재import
"""

import argparse
import asyncio
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import asyncpg
from dotenv import load_dotenv

load_dotenv()

DATA_PATH   = "rag/data/vworld_buildings.json"
DB_URL      = os.getenv("SUPABASE_DATABASE_URL", "")
BATCH_SIZE  = 500


def _polygon_to_wkt(coords: list) -> str | None:
    if not coords or len(coords) < 3:
        return None
    ring = coords if coords[0] == coords[-1] else coords + [coords[0]]
    pts = ", ".join(f"{c[0]} {c[1]}" for c in ring)
    return f"POLYGON(({pts}))"


async def create_table(conn: asyncpg.Connection):
    await conn.execute("""
        CREATE EXTENSION IF NOT EXISTS postgis;

        CREATE TABLE IF NOT EXISTS buildings (
            ufid        TEXT PRIMARY KEY,
            bld_nm      TEXT,
            grnd_flr    INT,
            ugrnd_flr   INT,
            height      FLOAT8,
            usability   TEXT,
            center_lat  FLOAT8,
            center_lng  FLOAT8,
            geom        GEOMETRY(Polygon, 4326)
        );

        CREATE INDEX IF NOT EXISTS idx_buildings_geom
            ON buildings USING GIST (geom);

        CREATE INDEX IF NOT EXISTS idx_buildings_bld_nm
            ON buildings (bld_nm)
            WHERE bld_nm IS NOT NULL;
    """)
    print("[DB] 테이블 + 인덱스 준비 완료")


async def reset_table(conn: asyncpg.Connection):
    await conn.execute("DROP TABLE IF EXISTS buildings;")
    print("[DB] buildings 테이블 삭제")
    await create_table(conn)


async def upsert_batch(conn: asyncpg.Connection, batch: list):
    await conn.executemany(
        """
        INSERT INTO buildings
            (ufid, bld_nm, grnd_flr, ugrnd_flr, height, usability,
             center_lat, center_lng, geom)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, ST_GeomFromText($9, 4326))
        ON CONFLICT (ufid) DO UPDATE SET
            bld_nm     = EXCLUDED.bld_nm,
            grnd_flr   = EXCLUDED.grnd_flr,
            geom       = EXCLUDED.geom,
            center_lat = EXCLUDED.center_lat,
            center_lng = EXCLUDED.center_lng;
        """,
        batch,
    )


async def run(dry_run: bool, reset: bool):
    if not os.path.exists(DATA_PATH):
        print(f"파일 없음: {DATA_PATH}")
        sys.exit(1)

    with open(DATA_PATH, encoding="utf-8") as f:
        data = json.load(f)

    buildings = data.get("buildings", [])
    print(f"로드: {len(buildings)}개 건물 (from {DATA_PATH})")

    # 파싱 검증
    valid, skipped = [], 0
    for b in buildings:
        ufid   = (b.get("ufid") or "").strip()
        coords = json.loads(b.get("polygon_2d", "[]"))
        wkt    = _polygon_to_wkt(coords)
        if not ufid or not wkt:
            skipped += 1
            continue
        valid.append((
            ufid,
            (b.get("bld_nm") or "").strip() or None,
            int(b.get("grnd_flr") or 0),
            int(b.get("ugrnd_flr") or 0),
            float(b.get("height") or 0),
            (b.get("usability") or ""),
            float(b.get("center_lat") or 0),
            float(b.get("center_lng") or 0),
            wkt,
        ))

    print(f"파싱: 유효={len(valid)}, 건너뜀={skipped}")

    if dry_run:
        print("[dry-run] DB 연결 없이 종료")
        return

    if not DB_URL:
        print("SUPABASE_DATABASE_URL 이 .env에 없습니다.")
        sys.exit(1)

    conn = await asyncpg.connect(DB_URL)
    try:
        if reset:
            await reset_table(conn)
        else:
            await create_table(conn)

        inserted = 0
        for i in range(0, len(valid), BATCH_SIZE):
            batch = valid[i:i + BATCH_SIZE]
            await upsert_batch(conn, batch)
            inserted += len(batch)
            print(f"  진행: {inserted}/{len(valid)}")

        print(f"\n완료: {inserted}개 upsert")
    finally:
        await conn.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="파싱만 확인, DB 연결 없음")
    parser.add_argument("--reset",   action="store_true", help="테이블 삭제 후 재생성")
    args = parser.parse_args()
    asyncio.run(run(args.dry_run, args.reset))


if __name__ == "__main__":
    main()
