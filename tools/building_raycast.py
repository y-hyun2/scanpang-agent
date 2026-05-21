"""
building_raycast.py
Frontend 가 건물 핀(폴리곤 + ufid)을 들고 사용자가 핀을 탭하면 ufid 를 함께
보낸다. 백엔드는 raycast 없이 ufid 로 buildings 테이블만 lookup.
"""

from typing import Optional

from core.db import get_pool


async def fetch_building_by_ufid(ufid: str) -> Optional[dict]:
    """
    ufid 로 buildings 테이블 lookup. 반환 형식은 vworld meta dict.
    명동/외대 구분 없이 buildings 테이블 전국 인덱스에서 한 번 조회.
    """
    if not ufid:
        return None

    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT ufid, bld_nm, center_lat, center_lng, bd_mgt_sn "
            "FROM buildings WHERE ufid = $1",
            ufid,
        )

    if not row:
        print(f"[UfidLookup] 매칭 실패: ufid={ufid}")
        return None

    meta = {
        "ufid":       row["ufid"],
        "bld_nm":     row["bld_nm"] or "",
        "center_lat": float(row["center_lat"]) if row["center_lat"] is not None else None,
        "center_lng": float(row["center_lng"]) if row["center_lng"] is not None else None,
        "bd_mgt_sn":  row["bd_mgt_sn"] or "",
    }
    name = meta["bld_nm"] or "(이름 없음)"
    print(f"[UfidLookup] 매칭: {name!r} ufid={ufid}")
    return meta
