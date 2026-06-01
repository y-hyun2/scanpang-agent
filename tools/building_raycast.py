"""
building_raycast.py
Frontend 가 건물 핀(폴리곤 + building_key)을 들고 사용자가 핀을 탭하면
building_key 를 함께 보낸다. 백엔드는 building 테이블 lookup.
"""

from typing import Optional

from core.db import get_pool


async def fetch_building_by_key(building_key: str) -> Optional[dict]:
    """
    building_key 로 building 테이블 lookup.
    building_key = COALESCE(bd_mgt_sn, ufid)
    """
    if not building_key:
        return None

    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT building_key, ufid, bld_nm, center_lat, center_lng, bd_mgt_sn "
            "FROM building WHERE building_key = $1",
            building_key,
        )

    if not row:
        print(f"[BuildingLookup] 매칭 실패: building_key={building_key}")
        return None

    meta = {
        "building_key": row["building_key"],
        "ufid":         row["ufid"] or "",
        "bld_nm":       row["bld_nm"] or "",
        "center_lat":   float(row["center_lat"]) if row["center_lat"] is not None else None,
        "center_lng":   float(row["center_lng"]) if row["center_lng"] is not None else None,
        "bd_mgt_sn":    row["bd_mgt_sn"] or "",
    }
    name = meta["bld_nm"] or "(이름 없음)"
    print(f"[BuildingLookup] 매칭: {name!r} building_key={building_key}")
    return meta
