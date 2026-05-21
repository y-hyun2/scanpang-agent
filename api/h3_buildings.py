import json
from typing import Optional

import h3
from cachetools import TTLCache
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from core.db import get_building_pool, get_pool


router = APIRouter(prefix="/buildings", tags=["spatial"])

CACHE_TTL_SECONDS = 600
CACHE_MAX_ENTRIES = 2048
_response_cache: TTLCache = TTLCache(maxsize=CACHE_MAX_ENTRIES, ttl=CACHE_TTL_SECONDS)


class BuildingDto(BaseModel):
    ufid: Optional[str]
    bld_nm: Optional[str]
    render_height: float
    h3_index_10: str
    geom: dict


class BuildingsChunkResponse(BaseModel):
    center_cell: str
    cells_queried: list[str]
    count: int
    buildings: list[BuildingDto]


@router.get("", response_model=BuildingsChunkResponse)
async def get_buildings_chunk(
        h3_cell: Optional[str] = Query(None, min_length=15, max_length=15,
                                       description="H3 셀 ID 직접 지정 (테스트용)"),
        lat: Optional[float] = Query(None, ge=-90, le=90,
                                     description="사용자 위도 (h3_cell 미지정 시 사용)"),
        lng: Optional[float] = Query(None, ge=-180, le=180,
                                     description="사용자 경도"),
):
    """
    중심 H3 셀 + 1-ring(주변 6개) = 7개 셀의 건물 반환.
    h3_cell 또는 (lat, lng) 둘 중 하나 필수.
    """
    # 입력 정규화: lat/lng → h3_cell
    if h3_cell is None:
        if lat is None or lng is None:
            raise HTTPException(400, "h3_cell 또는 (lat, lng) 둘 중 하나가 필요합니다")
        h3_cell = h3.latlng_to_cell(lat, lng, 10)

    cached = _response_cache.get(h3_cell)
    if cached is not None:
        return cached

    if not h3.is_valid_cell(h3_cell):
        raise HTTPException(status_code=400, detail="유효하지 않은 H3 셀 ID")
    if h3.get_resolution(h3_cell) != 10:
        raise HTTPException(status_code=400, detail="Resolution 10 셀만 허용")

    cells = list(h3.grid_disk(h3_cell, 1))

    query = """
        SELECT
            ufid,
            bld_nm,
            COALESCE(NULLIF(height, 0), estimated_height) AS render_height,
            h3_index_10,
            ST_AsGeoJSON(geom) AS geom_json,
            ST_Y(ST_Centroid(geom)) AS center_lat,
            ST_X(ST_Centroid(geom)) AS center_lng
        FROM buildings
        WHERE h3_index_10 = ANY($1::varchar[]);
    """
    bld_pool = await get_building_pool()
    async with bld_pool.acquire() as conn:
        rows = await conn.fetch(query, cells)

    # ── place_info.name_ko 보강: cadastral bld_nm이 NULL인 건물에 한해 ──
    # 좌표 근접(≈50m) 매칭으로 main pool의 place_info에서 진짜 건물명을 가져와 채움.
    # 한 청크당 단 1회 배치 쿼리. 매칭 안 되면 NULL 유지(프론트에서 폴백 라벨 표시).
    name_overrides: dict[str, str] = {}  # bd_mgt_sn → name_ko
    missing = [
        (r["bd_mgt_sn"], float(r["center_lat"]), float(r["center_lng"]))
        for r in rows
        if (not r["bld_nm"]) and r["bd_mgt_sn"] and r["center_lat"] is not None
    ]
    if missing:
        try:
            keys = [m[0] for m in missing]
            lats = [m[1] for m in missing]
            lngs = [m[2] for m in missing]
            main_pool = await get_pool()
            async with main_pool.acquire() as conn:
                enrich_rows = await conn.fetch(
                    """
                    SELECT q.key AS bd_mgt_sn, p.name_ko
                    FROM unnest($1::text[], $2::float8[], $3::float8[]) AS q(key, lat, lng)
                    CROSS JOIN LATERAL (
                        SELECT name_ko
                        FROM place_info
                        WHERE name_ko IS NOT NULL
                          AND ABS(lat - q.lat) < 0.0005
                          AND ABS(lng - q.lng) < 0.0005
                        ORDER BY (lat - q.lat) * (lat - q.lat) + (lng - q.lng) * (lng - q.lng)
                        LIMIT 1
                    ) p
                    """,
                    keys, lats, lngs,
                )
            for er in enrich_rows:
                if er["name_ko"]:
                    name_overrides[er["bd_mgt_sn"]] = er["name_ko"]
        except Exception as e:
            # enrichment 실패해도 핀 자체는 떠야 함 — bld_nm은 NULL로 유지(프론트 폴백)
            print(f"[h3_buildings] place_info 이름 보강 실패 (계속 진행): {e}")

    buildings = [
        BuildingDto(
            ufid=r["ufid"],
            bld_nm=r["bld_nm"] or None,
            render_height=float(r["render_height"] or 0),
            h3_index_10=r["h3_index_10"],
            geom=json.loads(r["geom_json"]),
        )
        for r in rows
    ]

    response = BuildingsChunkResponse(
        center_cell=h3_cell,
        cells_queried=cells,
        count=len(buildings),
        buildings=buildings,
    )

    _response_cache[h3_cell] = response
    return response