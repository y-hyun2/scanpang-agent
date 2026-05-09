from core.db import get_pool
from tools.place_tools import check_kakao_open_status


async def get_store_detail(place_id: str, store_name: str) -> dict:
    """
    개별 매장 상세 정보 조회.
    Supabase store_details 캐시 hit이면 바로 반환,
    miss이면 place_info에서 건물 좌표를 꺼내 Kakao API 호출 후 캐싱.
    """
    cache_id = f"{place_id}__{store_name}"
    pool     = await get_pool()

    # ── 캐시 조회 ────────────────────────────────────────────────────────────
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT * FROM store_details WHERE id = $1", cache_id
        )
    if row:
        return dict(row)

    # ── 캐시 miss: 건물 좌표 조회 ────────────────────────────────────────────
    async with pool.acquire() as conn:
        coord = await conn.fetchrow(
            "SELECT lat, lng FROM place_info WHERE ufid = $1", place_id
        )
    lat = coord["lat"] if coord else 37.5636
    lng = coord["lng"] if coord else 126.9822

    # ── Kakao API 호출 ────────────────────────────────────────────────────────
    kakao_data = await check_kakao_open_status(store_name, lat, lng)
    if not kakao_data:
        return {}

    # ── Supabase 캐시 저장 ───────────────────────────────────────────────────
    metadata = {k: v for k, v in kakao_data.items() if isinstance(v, (str, int, float, bool))}
    metadata["place_id"]   = place_id
    metadata["store_name"] = store_name

    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO store_details
                (id, place_id, store_name, category, addr, phone, lat, lng, place_url)
            VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
            ON CONFLICT (id) DO NOTHING
            """,
            cache_id,
            place_id,
            store_name,
            metadata.get("category", ""),
            metadata.get("addr", ""),
            metadata.get("phone", ""),
            float(metadata.get("lat", lat)),
            float(metadata.get("lng", lng)),
            metadata.get("place_url", ""),
        )

    return metadata
