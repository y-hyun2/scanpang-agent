"""
vegan_tools.py
vegan_restaurants 테이블(PostGIS) 거리 기반 검색.
"""

import json

DEFAULT_RADIUS = 50000  # meters


async def vegan_restaurant_search(
    lat: float, lng: float, radius: int = 0, vegan_level: str = "", category_key: str = ""
) -> list:
    """
    vegan_restaurants 테이블(PostGIS) 거리 검색.
    vegan_level:  "채식전문" / "채식가능" / "" (전체)
    category_key: "restaurant" / "cafe" / "" (전체)
    Returns: 거리순 상위 20개 list[dict]
    """
    from core.db import get_pool

    if radius <= 0:
        radius = DEFAULT_RADIUS

    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT id, store_name, category, category_key, addr, phone,
                   details::text AS details,
                   COALESCE(translations::text, '{}') AS translations,
                   open_hours, floor,
                   lat, lng,
                   ST_Distance(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography) AS dist
            FROM vegan_restaurants
            WHERE ST_DWithin(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography, $3)
              AND ($4 = '' OR details->>'vegan_level' = $4)
              AND ($5 = '' OR category_key = $5)
            ORDER BY dist
            LIMIT 20
            """,
            float(lat), float(lng), float(radius), vegan_level, category_key,
        )

    results = []
    for r in rows:
        details = json.loads(r["details"]) if r["details"] else {}
        results.append({
            "vegan_id":    r["id"],
            "name":        r["store_name"] or "",
            "category":    r["category"] or "",
            "category_key": r["category_key"] or "",
            "address":     r["addr"] or "",
            "phone":       r["phone"] or "",
            "lat":         float(r["lat"]) if r["lat"] is not None else None,
            "lng":         float(r["lng"]) if r["lng"] is not None else None,
            "distance_m":  round(r["dist"], 1),
            "open_hours":  r["open_hours"] or "",
            "floor":       r["floor"] or "",
            "vegan_level": details.get("vegan_level", ""),
            "vegan_menu":  details.get("vegan_menu", ""),
            # 캐시된 언어별 번역(JSONB 텍스트) — /place/search 가 영어 매장명 적용에 사용.
            "translations": r["translations"],
        })
    return results
