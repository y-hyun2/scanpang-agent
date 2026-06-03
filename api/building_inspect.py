"""
building_inspect.py
건물 폴리곤 점검 웹앱용 엔드포인트.

/inspect/buildings          GET  — 전체 building 로드 (bbox 선택)
/inspect/overlaps           GET  — 3% 이상 겹치는 쌍 탐지
/inspect/clusters           GET  — 근접 건물 그룹 탐지 (주소 중복 확인용)
/inspect/building           POST — 신규 건물 추가
/inspect/building/{key}     PUT  — 폴리곤/메타 수정
/inspect/building/{key}     DELETE — 건물 삭제
/inspect/merge              POST — 여러 건물 폴리곤 병합
/inspect/address            GET  — Kakao 역지오코딩
/inspect/vworld             GET  — VWorld 실시간 폴리곤
"""

import json
import os
import time

import h3
import httpx
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from typing import Optional, List
from core.db import get_pool

router = APIRouter(prefix="/inspect", tags=["inspect"])


@router.get("/config", include_in_schema=False)
async def inspect_config():
    """프론트엔드에 필요한 공개 설정값 반환."""
    return {
        "kakao_js_key":      os.environ.get("KAKAO_JAVASCRIPT_KEY", ""),
        "naver_map_client_id": os.environ.get("NAVER_MAP_CLIENT_ID", ""),
    }


@router.get("/tile/{z}/{y}/{x}.png", include_in_schema=False)
async def vworld_tile(z: int, y: int, x: int):
    """VWorld 지도 타일 프록시 — API 키를 프론트에 노출하지 않음."""
    from fastapi.responses import Response
    key    = os.environ.get("VWORLD_API_KEY", "")
    domain = os.environ.get("VWORLD_DOMAIN", "localhost")
    if not key:
        raise HTTPException(500, "VWORLD_API_KEY 미설정")
    url = f"https://api.vworld.kr/req/wmts/1.0.0/{key}/Base/{z}/{y}/{x}.png?domain={domain}"
    async with httpx.AsyncClient() as client:
        try:
            r = await client.get(url, timeout=10.0)
            return Response(content=r.content, media_type="image/png",
                            headers={"Cache-Control": "public, max-age=86400"})
        except Exception:
            raise HTTPException(502, "타일 로드 실패")

MAX_BUILDINGS   = 10000
OVERLAP_THRESH  = 0.03   # 3%
CLUSTER_DIST_M  = 20     # 20m 이내 비겹침 건물 = 주소 중복 후보
VWORLD_PAGE_LIMIT = 1000


# ── 공통 유틸 ──────────────────────────────────────────────────────────────────

def _validate_bbox(min_lat, min_lng, max_lat, max_lng):
    if max_lat - min_lat <= 0 or max_lng - min_lng <= 0:
        raise HTTPException(400, "max_lat/max_lng는 min보다 커야 합니다.")
    if max_lat - min_lat > 0.3 or max_lng - min_lng > 0.4:
        raise HTTPException(400, "선택 영역이 너무 넓습니다.")


def _row_to_feature(r) -> dict:
    geom = json.loads(r["geom_json"]) if r["geom_json"] else None
    if not geom:
        return None
    return {
        "type": "Feature",
        "geometry": geom,
        "properties": {
            "building_key":     r["building_key"] or "",
            "ufid":             r["ufid"] or "",
            "bld_nm":           r["bld_nm"] or "",
            "bd_mgt_sn":        r["bd_mgt_sn"] or "",
            "usability":        r["usability"] or "",
            "grnd_flr":         r["grnd_flr"],
            "ugrnd_flr":        r["ugrnd_flr"],
            "height":           float(r["height"]) if r["height"] else None,
            "estimated_height": float(r["estimated_height"]) if r["estimated_height"] else None,
            "render_height":    float(r["render_height"]) if r["render_height"] else 0.0,
            "center_lat":       float(r["center_lat"]) if r["center_lat"] else None,
            "center_lng":       float(r["center_lng"]) if r["center_lng"] else None,
            "h3_index_10":      r["h3_index_10"] or "",
        },
    }


# ── 전체 건물 로드 ─────────────────────────────────────────────────────────────

@router.get("/buildings")
async def inspect_buildings(
    min_lat: Optional[float] = Query(None),
    min_lng: Optional[float] = Query(None),
    max_lat: Optional[float] = Query(None),
    max_lng: Optional[float] = Query(None),
):
    """building 테이블 전체 or BBOX 필터 로드."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        if all(v is not None for v in [min_lat, min_lng, max_lat, max_lng]):
            _validate_bbox(min_lat, min_lng, max_lat, max_lng)
            rows = await conn.fetch(
                """
                SELECT building_key, ufid, bld_nm, bd_mgt_sn, usability,
                       grnd_flr, ugrnd_flr, height, estimated_height,
                       COALESCE(NULLIF(height,0), estimated_height) AS render_height,
                       center_lat, center_lng, h3_index_10,
                       ST_AsGeoJSON(geom) AS geom_json
                FROM building
                WHERE ST_Intersects(geom, ST_MakeEnvelope($1,$2,$3,$4,4326))
                LIMIT $5
                """,
                min_lng, min_lat, max_lng, max_lat, MAX_BUILDINGS,
            )
        else:
            rows = await conn.fetch(
                """
                SELECT building_key, ufid, bld_nm, bd_mgt_sn, usability,
                       grnd_flr, ugrnd_flr, height, estimated_height,
                       COALESCE(NULLIF(height,0), estimated_height) AS render_height,
                       center_lat, center_lng, h3_index_10,
                       ST_AsGeoJSON(geom) AS geom_json
                FROM building
                LIMIT $1
                """,
                MAX_BUILDINGS,
            )

    features = [f for r in rows if (f := _row_to_feature(r))]
    return {"type": "FeatureCollection", "count": len(features),
            "capped": len(rows) >= MAX_BUILDINGS, "features": features}


# ── 겹침 감지 ─────────────────────────────────────────────────────────────────

@router.get("/overlaps")
async def inspect_overlaps():
    """3% 이상 겹치는 building 쌍 반환."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT
                a.building_key AS key_a, a.bld_nm AS name_a,
                b.building_key AS key_b, b.bld_nm AS name_b,
                ROUND((
                    ST_Area(ST_Intersection(a.geom::geography, b.geom::geography)) /
                    LEAST(ST_Area(a.geom::geography), ST_Area(b.geom::geography))
                )::numeric * 100, 1) AS overlap_pct,
                ST_AsGeoJSON(a.geom) AS geom_a,
                ST_AsGeoJSON(b.geom) AS geom_b,
                COALESCE(NULLIF(a.height,0), a.estimated_height) AS height_a,
                COALESCE(NULLIF(b.height,0), b.estimated_height) AS height_b,
                a.grnd_flr AS floor_a, b.grnd_flr AS floor_b,
                a.center_lat AS lat_a, a.center_lng AS lng_a,
                b.center_lat AS lat_b, b.center_lng AS lng_b
            FROM building a
            JOIN building b ON a.building_key < b.building_key
            WHERE ST_Intersects(a.geom, b.geom)
              AND ST_Area(ST_Intersection(a.geom::geography, b.geom::geography)) /
                  LEAST(ST_Area(a.geom::geography), ST_Area(b.geom::geography)) > $1
            ORDER BY overlap_pct DESC
            LIMIT 500
            """,
            OVERLAP_THRESH,
        )

    pairs = [
        {
            "key_a": r["key_a"], "name_a": r["name_a"] or "",
            "key_b": r["key_b"], "name_b": r["name_b"] or "",
            "overlap_pct": float(r["overlap_pct"]),
            "height_a": float(r["height_a"]) if r["height_a"] else 0,
            "height_b": float(r["height_b"]) if r["height_b"] else 0,
            "floor_a": r["floor_a"], "floor_b": r["floor_b"],
            "lat_a": float(r["lat_a"]) if r["lat_a"] else None,
            "lng_a": float(r["lng_a"]) if r["lng_a"] else None,
            "lat_b": float(r["lat_b"]) if r["lat_b"] else None,
            "lng_b": float(r["lng_b"]) if r["lng_b"] else None,
            "geom_a": json.loads(r["geom_a"]) if r["geom_a"] else None,
            "geom_b": json.loads(r["geom_b"]) if r["geom_b"] else None,
        }
        for r in rows
    ]
    return {"count": len(pairs), "pairs": pairs}


# ── 근접 클러스터 감지 (주소 중복 후보) ────────────────────────────────────────

@router.get("/clusters")
async def inspect_clusters():
    """서로 겹치진 않지만 20m 이내 건물 그룹 반환 (주소 중복 확인용)."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT
                a.building_key AS key_a, a.bld_nm AS name_a,
                b.building_key AS key_b, b.bld_nm AS name_b,
                ROUND(ST_Distance(a.geom::geography, b.geom::geography)::numeric, 1) AS dist_m,
                ST_AsGeoJSON(a.geom) AS geom_a,
                ST_AsGeoJSON(b.geom) AS geom_b,
                a.center_lat AS lat_a, a.center_lng AS lng_a,
                b.center_lat AS lat_b, b.center_lng AS lng_b,
                COALESCE(NULLIF(a.height,0), a.estimated_height) AS height_a,
                COALESCE(NULLIF(b.height,0), b.estimated_height) AS height_b,
                a.grnd_flr AS floor_a, b.grnd_flr AS floor_b
            FROM building a
            JOIN building b ON a.building_key < b.building_key
            WHERE NOT ST_Intersects(a.geom, b.geom)
              AND ST_DWithin(a.geom::geography, b.geom::geography, $1)
            ORDER BY dist_m
            LIMIT 500
            """,
            CLUSTER_DIST_M,
        )

    pairs = [
        {
            "key_a": r["key_a"], "name_a": r["name_a"] or "",
            "key_b": r["key_b"], "name_b": r["name_b"] or "",
            "dist_m": float(r["dist_m"]),
            "height_a": float(r["height_a"]) if r["height_a"] else 0,
            "height_b": float(r["height_b"]) if r["height_b"] else 0,
            "floor_a": r["floor_a"], "floor_b": r["floor_b"],
            "lat_a": float(r["lat_a"]) if r["lat_a"] else None,
            "lng_a": float(r["lng_a"]) if r["lng_a"] else None,
            "lat_b": float(r["lat_b"]) if r["lat_b"] else None,
            "lng_b": float(r["lng_b"]) if r["lng_b"] else None,
            "geom_a": json.loads(r["geom_a"]) if r["geom_a"] else None,
            "geom_b": json.loads(r["geom_b"]) if r["geom_b"] else None,
        }
        for r in rows
    ]
    return {"count": len(pairs), "pairs": pairs}


# ── 건물 삭제 ─────────────────────────────────────────────────────────────────

@router.delete("/building/{building_key}")
async def delete_building(building_key: str):
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.transaction():
            # 자식 테이블 순서대로 삭제 (FK 제약 해소)
            await conn.execute(
                "DELETE FROM store_hours WHERE store_id IN "
                "(SELECT id FROM storedetails WHERE place_id = $1)", building_key
            )
            await conn.execute("DELETE FROM storedetails WHERE place_id = $1", building_key)
            await conn.execute("DELETE FROM placeinfo WHERE building_key = $1", building_key)
            # VWorld 재수집 시 되살아나지 않도록 제외 목록에 기록 (삭제 직전, bld_nm 보존)
            await conn.execute(
                """
                INSERT INTO building_exclusion (building_key, bld_nm, reason)
                SELECT building_key, bld_nm, '점검기 수기 삭제'
                FROM building WHERE building_key = $1
                ON CONFLICT (building_key) DO NOTHING
                """,
                building_key,
            )
            result = await conn.execute(
                "DELETE FROM building WHERE building_key = $1", building_key
            )
    deleted = int(result.split()[-1])
    if deleted == 0:
        raise HTTPException(404, f"building_key '{building_key}' 없음")
    return {"deleted": building_key}


class BulkDeleteRequest(BaseModel):
    keys: List[str]


@router.post("/buildings/delete")
async def bulk_delete_buildings(req: BulkDeleteRequest):
    """여러 building_key를 한 트랜잭션으로 삭제 + building_exclusion에 기록."""
    if not req.keys:
        raise HTTPException(400, "삭제할 building_key 목록이 비어있습니다.")
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.execute(
                "DELETE FROM store_hours WHERE store_id IN "
                "(SELECT id FROM storedetails WHERE place_id = ANY($1::text[]))", req.keys
            )
            await conn.execute("DELETE FROM storedetails WHERE place_id = ANY($1::text[])", req.keys)
            await conn.execute("DELETE FROM placeinfo WHERE building_key = ANY($1::text[])", req.keys)
            # 재수집 시 되살아나지 않도록 제외 목록 기록 (삭제 직전, bld_nm 보존)
            await conn.execute(
                """
                INSERT INTO building_exclusion (building_key, bld_nm, reason)
                SELECT building_key, bld_nm, '점검기 다중 삭제'
                FROM building WHERE building_key = ANY($1::text[])
                ON CONFLICT (building_key) DO NOTHING
                """,
                req.keys,
            )
            result = await conn.execute(
                "DELETE FROM building WHERE building_key = ANY($1::text[])", req.keys
            )
    return {"deleted": int(result.split()[-1]), "requested": len(req.keys)}


# ── 폴리곤 수정 ───────────────────────────────────────────────────────────────

class UpdateBuildingRequest(BaseModel):
    geojson: dict           # GeoJSON geometry (Polygon or MultiPolygon)
    bld_nm:  Optional[str] = None
    grnd_flr: Optional[int] = None
    ugrnd_flr: Optional[int] = None
    height: Optional[float] = None
    usability: Optional[str] = None


@router.put("/building/{building_key}")
async def update_building(building_key: str, req: UpdateBuildingRequest):
    """폴리곤 수정 + centroid/H3 재계산."""
    geom_str = json.dumps(req.geojson)
    pool = await get_pool()
    async with pool.acquire() as conn:
        # centroid 계산
        coord_row = await conn.fetchrow(
            """
            SELECT ST_Y(ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON($1),4326))) AS lat,
                   ST_X(ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON($1),4326))) AS lng
            """,
            geom_str,
        )
    c_lat = float(coord_row["lat"])
    c_lng = float(coord_row["lng"])
    h3_idx = h3.latlng_to_cell(c_lat, c_lng, 10)

    set_clauses = [
        "geom         = ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON($1),4326))",
        "center_lat   = $2",
        "center_lng   = $3",
        "h3_index_10  = $4",
    ]
    params = [geom_str, c_lat, c_lng, h3_idx]
    idx = 5

    if req.bld_nm is not None:
        set_clauses.append(f"bld_nm = ${idx}");   params.append(req.bld_nm);   idx += 1
    if req.grnd_flr is not None:
        set_clauses.append(f"grnd_flr = ${idx}"); params.append(req.grnd_flr); idx += 1
    if req.ugrnd_flr is not None:
        set_clauses.append(f"ugrnd_flr = ${idx}");params.append(req.ugrnd_flr);idx += 1
    if req.height is not None:
        set_clauses.append(f"height = ${idx}");   params.append(req.height);   idx += 1
    if req.usability is not None:
        set_clauses.append(f"usability = ${idx}");params.append(req.usability);idx += 1

    params.append(building_key)
    sql = f"UPDATE building SET {', '.join(set_clauses)} WHERE building_key = ${idx}"

    async with pool.acquire() as conn:
        result = await conn.execute(sql, *params)

    if int(result.split()[-1]) == 0:
        raise HTTPException(404, f"building_key '{building_key}' 없음")
    return {"updated": building_key, "center_lat": c_lat, "center_lng": c_lng, "h3_index_10": h3_idx}


# ── 신규 건물 추가 ─────────────────────────────────────────────────────────────

class CreateBuildingRequest(BaseModel):
    geojson:   Optional[dict] = None    # 폴리곤 없으면 None
    center_lat: float
    center_lng: float
    bld_nm:    Optional[str] = None
    grnd_flr:  Optional[int] = None
    ugrnd_flr: Optional[int] = None
    height:    Optional[float] = None
    usability: Optional[str] = None
    bd_mgt_sn: Optional[str] = None
    ufid:      Optional[str] = None


@router.post("/building")
async def create_building(req: CreateBuildingRequest):
    import time as _time
    h3_idx       = h3.latlng_to_cell(req.center_lat, req.center_lng, 10)
    building_key = req.bd_mgt_sn or req.ufid or f"manual_{int(_time.time()*1000)}"

    pool = await get_pool()
    async with pool.acquire() as conn:
        if req.geojson:
            await conn.execute(
                """
                INSERT INTO building
                    (building_key, bd_mgt_sn, ufid, bld_nm, usability,
                     grnd_flr, ugrnd_flr, height, center_lat, center_lng,
                     h3_index_10, geom)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,
                        ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON($12),4326)))
                ON CONFLICT (building_key) DO NOTHING
                """,
                building_key, req.bd_mgt_sn, req.ufid, req.bld_nm, req.usability,
                req.grnd_flr, req.ugrnd_flr, req.height,
                req.center_lat, req.center_lng, h3_idx,
                json.dumps(req.geojson),
            )
        else:
            await conn.execute(
                """
                INSERT INTO building
                    (building_key, bd_mgt_sn, ufid, bld_nm, usability,
                     grnd_flr, ugrnd_flr, height, center_lat, center_lng, h3_index_10)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
                ON CONFLICT (building_key) DO NOTHING
                """,
                building_key, req.bd_mgt_sn, req.ufid, req.bld_nm, req.usability,
                req.grnd_flr, req.ugrnd_flr, req.height,
                req.center_lat, req.center_lng, h3_idx,
            )
    return {"created": building_key}


# ── 병합 ──────────────────────────────────────────────────────────────────────

class MergeRequest(BaseModel):
    keep:   str        # 유지할 building_key (메타 기준)
    delete: List[str]  # 삭제할 building_key 목록


@router.post("/merge")
async def merge_buildings(req: MergeRequest):
    """여러 건물 폴리곤을 ST_Union으로 합치고 delete 목록 삭제."""
    if not req.delete:
        raise HTTPException(400, "삭제할 building_key 목록이 비어있습니다.")

    all_keys = [req.keep] + req.delete
    pool = await get_pool()
    async with pool.acquire() as conn:
        # ST_Union으로 합친 폴리곤 계산
        union_row = await conn.fetchrow(
            """
            SELECT
                ST_AsGeoJSON(ST_Multi(ST_Union(geom))) AS merged_geom,
                ST_Y(ST_Centroid(ST_Union(geom))) AS c_lat,
                ST_X(ST_Centroid(ST_Union(geom))) AS c_lng
            FROM building
            WHERE building_key = ANY($1::text[])
            """,
            all_keys,
        )
        if not union_row or not union_row["merged_geom"]:
            raise HTTPException(404, "대상 building_key 를 찾을 수 없습니다.")

        c_lat  = float(union_row["c_lat"])
        c_lng  = float(union_row["c_lng"])
        h3_idx = h3.latlng_to_cell(c_lat, c_lng, 10)

        # keep 건물에 병합 폴리곤 적용
        await conn.execute(
            """
            UPDATE building
            SET geom        = ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON($1),4326)),
                center_lat  = $2,
                center_lng  = $3,
                h3_index_10 = $4
            WHERE building_key = $5
            """,
            union_row["merged_geom"], c_lat, c_lng, h3_idx, req.keep,
        )
        # 병합 흡수된 건물은 제외 목록에 기록 (재수집 시 분리 건물로 되살아남 방지)
        await conn.execute(
            """
            INSERT INTO building_exclusion (building_key, bld_nm, reason)
            SELECT building_key, bld_nm, $2
            FROM building WHERE building_key = ANY($1::text[])
            ON CONFLICT (building_key) DO NOTHING
            """,
            req.delete, f"점검기 병합(keep={req.keep})",
        )
        # delete 목록 삭제
        await conn.execute(
            "DELETE FROM building WHERE building_key = ANY($1::text[])",
            req.delete,
        )

    return {"merged_into": req.keep, "deleted": req.delete,
            "center_lat": c_lat, "center_lng": c_lng}


# ── 제외 목록 (수기 삭제 기록) 관리 ────────────────────────────────────────────

@router.get("/exclusion")
async def list_exclusion():
    """building_exclusion 전체 조회 (최근 등록순). VWorld 재수집 시 스킵되는 건물 목록."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT building_key, bld_nm, reason, created_at
            FROM building_exclusion
            ORDER BY created_at DESC
            """
        )
    return {
        "count": len(rows),
        "items": [
            {
                "building_key": r["building_key"],
                "bld_nm":       r["bld_nm"],
                "reason":       r["reason"],
                "created_at":   r["created_at"].isoformat() if r["created_at"] else None,
            }
            for r in rows
        ],
    }


@router.delete("/exclusion/{building_key}")
async def remove_exclusion(building_key: str):
    """제외 목록에서 제거 → 다음 VWorld 재수집 때 다시 적재될 수 있게 해제(되살리기)."""
    pool = await get_pool()
    async with pool.acquire() as conn:
        result = await conn.execute(
            "DELETE FROM building_exclusion WHERE building_key = $1", building_key
        )
    if int(result.split()[-1]) == 0:
        raise HTTPException(404, f"building_exclusion 에 '{building_key}' 없음")
    return {"removed": building_key}


# ── Kakao 역지오코딩 ───────────────────────────────────────────────────────────

@router.get("/address")
async def get_address(
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
):
    kakao_key = os.environ.get("KAKAO_REST_API_KEY", "")
    if not kakao_key:
        raise HTTPException(500, "KAKAO_REST_API_KEY 환경변수 미설정")
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            "https://dapi.kakao.com/v2/local/geo/coord2address.json",
            headers={"Authorization": f"KakaoAK {kakao_key}"},
            params={"x": lng, "y": lat},
            timeout=8.0,
        )
        resp.raise_for_status()
        data = resp.json()
    docs = data.get("documents", [])
    if not docs:
        return {"road": None, "jibun": None}
    addr  = docs[0]
    road  = (addr.get("road_address") or {}).get("address_name") or None
    jibun = (addr.get("address") or {}).get("address_name") or None
    return {"road": road, "jibun": jibun}


# ── Kakao 주소 검색 (수동 추가용) ─────────────────────────────────────────────

@router.get("/geocode")
async def geocode_address(q: str = Query(...)):
    """주소 → 좌표 (수동 추가 시 위치 찾기용)."""
    kakao_key = os.environ.get("KAKAO_REST_API_KEY", "")
    if not kakao_key:
        raise HTTPException(500, "KAKAO_REST_API_KEY 환경변수 미설정")
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            "https://dapi.kakao.com/v2/local/search/address.json",
            headers={"Authorization": f"KakaoAK {kakao_key}"},
            params={"query": q, "size": 1},
            timeout=8.0,
        )
        data = resp.json()
    docs = data.get("documents", [])
    if not docs:
        return {"lat": None, "lng": None}
    return {"lat": float(docs[0]["y"]), "lng": float(docs[0]["x"]),
            "address": docs[0].get("address_name", "")}


# ── VWorld 실시간 ─────────────────────────────────────────────────────────────

def _vworld_url(key, domain, bbox_str):
    return (
        "https://api.vworld.kr/req/wfs"
        f"?key={key}&domain={domain}"
        "&SERVICE=WFS&VERSION=1.1.0&REQUEST=GetFeature"
        "&TYPENAME=lt_c_bldginfo&OUTPUTFORMAT=application/json"
        f"&SRSNAME=EPSG:4326&BBOX={bbox_str},EPSG:4326"
    )


async def _fetch_vworld_bbox(client, bbox, key, domain, seen, collected, depth=0, max_depth=6):
    min_lat, min_lng, max_lat, max_lng = bbox
    bbox_str = f"{min_lat:.6f},{min_lng:.6f},{max_lat:.6f},{max_lng:.6f}"
    try:
        resp = await client.get(_vworld_url(key, domain, bbox_str), timeout=20.0)
        data = resp.json()
    except Exception:
        return
    features = data.get("features") or []
    for f in features:
        bd = (f.get("properties") or {}).get("bd_mgt_sn")
        if bd and bd not in seen:
            seen.add(bd); collected.append(f)
    if len(features) >= VWORLD_PAGE_LIMIT and depth < max_depth:
        mid_lat = (min_lat + max_lat) / 2
        mid_lng = (min_lng + max_lng) / 2
        for sub in [(min_lat,min_lng,mid_lat,mid_lng),(min_lat,mid_lng,mid_lat,max_lng),
                    (mid_lat,min_lng,max_lat,mid_lng),(mid_lat,mid_lng,max_lat,max_lng)]:
            await _fetch_vworld_bbox(client, sub, key, domain, seen, collected, depth+1, max_depth)


@router.get("/vworld")
async def inspect_vworld(
    min_lat: float = Query(...), min_lng: float = Query(...),
    max_lat: float = Query(...), max_lng: float = Query(...),
):
    _validate_bbox(min_lat, min_lng, max_lat, max_lng)
    key    = os.environ.get("VWORLD_API_KEY", "")
    domain = os.environ.get("VWORLD_DOMAIN", "localhost")
    if not key:
        raise HTTPException(500, "VWORLD_API_KEY 환경변수 미설정")
    seen, collected = set(), []
    async with httpx.AsyncClient() as client:
        await _fetch_vworld_bbox(client, (min_lat,min_lng,max_lat,max_lng), key, domain, seen, collected)
    features = []
    for f in collected:
        geom  = f.get("geometry")
        props = f.get("properties") or {}
        if not geom: continue
        features.append({"type":"Feature","geometry":geom,"properties":{
            "ufid":props.get("ufid",""), "bld_nm":props.get("bld_nm",""),
            "bd_mgt_sn":props.get("bd_mgt_sn",""), "usability":props.get("usability",""),
            "grnd_flr":props.get("grnd_flr"), "ugrnd_flr":props.get("ugrnd_flr"),
            "height":props.get("height"),
        }})
    return {"type":"FeatureCollection","count":len(features),"features":features}
