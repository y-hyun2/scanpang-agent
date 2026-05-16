"""
import_public_restrooms.py
data.go.kr 행정안전부 공중화장실(15155058) 캐시 JSON → Supabase + PostGIS.

기존 rag/data/public_restrooms.json (~30MB, 53,523건) 디스크 캐시를 DB로 이관.
PostGIS GEOGRAPHY(POINT) 컬럼 + GIST 인덱스로 반경 검색 가속.

사용법:
    python scripts/import_public_restrooms.py
"""

import asyncio
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.db import get_pool

JSON_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "rag", "data", "public_restrooms.json",
)

_DDL = """
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS public_restrooms (
    mng_no    TEXT PRIMARY KEY,
    name      TEXT,
    type      TEXT,            -- 공중화장실/개방화장실
    addr_road TEXT,
    addr_lot  TEXT,
    phone     TEXT,
    open_hours TEXT,
    lat       DOUBLE PRECISION,
    lng       DOUBLE PRECISION,
    geom      GEOGRAPHY(POINT, 4326),
    raw       JSONB             -- 원본 row (RSTRM_NM·MALE_TOILT_CNT 등 필드명 그대로)
);
CREATE INDEX IF NOT EXISTS public_restrooms_geom_idx
    ON public_restrooms USING GIST(geom);
"""

_UPSERT = """
INSERT INTO public_restrooms
    (mng_no, name, type, addr_road, addr_lot, phone, open_hours,
     lat, lng, geom, raw)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9,
        ST_SetSRID(ST_MakePoint($9, $8), 4326)::geography,
        $10::jsonb)
ON CONFLICT (mng_no) DO UPDATE SET
    name       = EXCLUDED.name,
    type       = EXCLUDED.type,
    addr_road  = EXCLUDED.addr_road,
    addr_lot   = EXCLUDED.addr_lot,
    phone      = EXCLUDED.phone,
    open_hours = EXCLUDED.open_hours,
    lat        = EXCLUDED.lat,
    lng        = EXCLUDED.lng,
    geom       = EXCLUDED.geom,
    raw        = EXCLUDED.raw
"""


def _to_float(v) -> float | None:
    try:
        f = float(v or 0)
        return f if f != 0 else None
    except (ValueError, TypeError):
        return None


async def run():
    if not os.path.exists(JSON_PATH):
        print(f"[ERR] {JSON_PATH} 없음. 먼저 _public_restroom_cache로 다운로드 필요.")
        return

    with open(JSON_PATH, encoding="utf-8") as f:
        rows = json.load(f)
    print(f"[INFO] JSON rows: {len(rows)}")

    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(_DDL)
        print("[OK] public_restrooms 테이블·GIST 인덱스 준비 완료")

        batch = []
        for r in rows:
            mng_no = (r.get("MNG_NO") or "").strip()
            if not mng_no:
                continue
            lat = _to_float(r.get("WGS84_LAT"))
            lng = _to_float(r.get("WGS84_LOT"))
            if lat is None or lng is None:
                continue
            batch.append((
                mng_no,
                r.get("RSTRM_NM") or "",
                r.get("SE_NM") or "",
                r.get("LCTN_ROAD_NM_ADDR") or "",
                r.get("LCTN_LOTNO_ADDR") or "",
                r.get("TELNO") or "",
                r.get("OPN_HR_DTL") or r.get("OPN_HR") or "",
                lat, lng,
                json.dumps(r, ensure_ascii=False),
            ))

        print(f"[INFO] 유효 row (좌표·mng_no 모두 있음): {len(batch)}")

        # 1,000건씩 분할 — 한번에 53k는 prepared statement 한계
        async with conn.transaction():
            for i in range(0, len(batch), 1000):
                chunk = batch[i:i+1000]
                await conn.executemany(_UPSERT, chunk)
                print(f"  inserted {min(i+1000, len(batch))}/{len(batch)}")

        cnt = await conn.fetchval("SELECT COUNT(*) FROM public_restrooms")
        print(f"[OK] public_restrooms 총 {cnt}행")

        # 빠른 sanity — 명동(37.5636,126.9822) 반경 500m
        nearby = await conn.fetch("""
            SELECT name, addr_road,
                   ROUND(ST_Distance(geom, ST_SetSRID(ST_MakePoint($2,$1),4326)::geography)::numeric, 1) AS dist_m
            FROM public_restrooms
            WHERE ST_DWithin(geom, ST_SetSRID(ST_MakePoint($2,$1),4326)::geography, 500)
            ORDER BY dist_m
            LIMIT 5
        """, 37.5636, 126.9822)
        print(f"[Sanity] 명동 500m 화장실: {len(nearby)}건")
        for r in nearby:
            print(f"    {r['dist_m']:6}m  {r['name']}  ({r['addr_road']})")

    await pool.close()


if __name__ == "__main__":
    asyncio.run(run())
