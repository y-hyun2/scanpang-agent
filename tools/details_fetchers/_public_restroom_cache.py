"""
_public_restroom_cache.py
공중화장실 검색 — Supabase + PostGIS 단일 출처.

데이터: data.go.kr 행정안전부 공중화장실(15155058) 29,132건 (좌표 보유분만).
import: scripts/import_public_restrooms.py (rag/data/public_restrooms.json → DB).

함수 시그니처는 기존(JSON 캐시 시절)과 동일 — caller(`seoul_openapi._pick_public_restroom`,
`convenience_tools.public_restroom_search`)는 raw row(RSTRM_NM, MALE_TOILT_CNT 등) dict를
그대로 받음.
"""

from core.db import get_pool


async def find_nearest(lat: float, lng: float, radius_m: int = 1000) -> list[dict]:
    """좌표 기준 radius_m 이내 화장실 raw row 반환 (거리 오름차순).
    raw JSONB에 RSTRM_NM·MALE_TOILT_CNT·TELNO 등 원본 키 그대로 들어있고
    `_distance_m` 키를 합성해서 추가한다."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT raw,
                   ROUND(ST_Distance(
                       geom,
                       ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography
                   )::numeric, 1) AS dist_m
            FROM public_restrooms
            WHERE ST_DWithin(
                geom,
                ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography,
                $3
            )
            ORDER BY dist_m
            LIMIT 100
            """,
            lat, lng, radius_m,
        )

    out: list[dict] = []
    for r in rows:
        raw = r["raw"]
        # asyncpg가 JSONB를 dict로 자동 파싱하지만 방어적으로 처리
        if isinstance(raw, str):
            import json as _json
            try:
                raw = _json.loads(raw)
            except (ValueError, TypeError):
                continue
        if not isinstance(raw, dict):
            continue
        raw["_distance_m"] = float(r["dist_m"])
        out.append(raw)
    return out
