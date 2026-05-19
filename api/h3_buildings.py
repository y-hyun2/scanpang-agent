import json
from typing import Annotated, Optional

import h3
from cachetools import TTLCache
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from core.db import get_building_pool


router = APIRouter(prefix="/buildings", tags=["spatial"])

CACHE_TTL_SECONDS = 600
CACHE_MAX_ENTRIES = 2048
_response_cache: TTLCache = TTLCache(maxsize=CACHE_MAX_ENTRIES, ttl=CACHE_TTL_SECONDS)


class BuildingDto(BaseModel):
    ufid: Optional[str]
    bd_mgt_sn: Optional[str]
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
            bd_mgt_sn,
            bld_nm,
            COALESCE(NULLIF(height, 0), estimated_height) AS render_height,
            h3_index_10,
            ST_AsGeoJSON(geom) AS geom_json
        FROM buildings
        WHERE h3_index_10 = ANY($1::varchar[]);
    """
    pool = await get_building_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(query, cells)

    buildings = [
        BuildingDto(
            ufid=r["ufid"],
            bd_mgt_sn=r["bd_mgt_sn"],
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