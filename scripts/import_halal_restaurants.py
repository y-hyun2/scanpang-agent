"""
import_halal_restaurants.py
rag/data/myeongdong_restaurants.json → halal_restaurants 테이블 import.

JSON 의 address 필드를 Kakao geocoding 으로 lat/lng 보강한 뒤 PostGIS POINT 로 저장.
운영 시 매장 검색은 이 테이블 + ST_DWithin / ST_Distance 로 처리하고 JSON 은 폐기.
"""

import asyncio
import json
import os
from pathlib import Path

import httpx

from core.db import get_pool

JSON_PATH = Path(__file__).resolve().parent.parent / "rag" / "data" / "myeongdong_restaurants.json"
KAKAO_KEY = os.getenv("KAKAO_REST_API_KEY", "")


async def _geocode(addr: str) -> tuple[float | None, float | None]:
    """Kakao Local Address API로 도로명주소 → lat/lng."""
    if not addr or not KAKAO_KEY:
        return None, None
    async with httpx.AsyncClient() as c:
        r = await c.get(
            "https://dapi.kakao.com/v2/local/search/address.json",
            headers={"Authorization": f"KakaoAK {KAKAO_KEY}"},
            params={"query": addr},
        )
        if r.status_code != 200:
            return None, None
        docs = r.json().get("documents", [])
    if not docs:
        return None, None
    d = docs[0]
    try:
        return float(d["y"]), float(d["x"])
    except (KeyError, ValueError):
        return None, None


async def main():
    if not JSON_PATH.exists():
        print(f"JSON 없음: {JSON_PATH}"); return
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        items = json.load(f)
    print(f"JSON 로드: {len(items)}건")

    pool = await get_pool()
    ok = miss = 0
    for i, r in enumerate(items, 1):
        rid = r.get("restaurant_id") or r.get("name_ko") or f"halal_{i}"
        lat = r.get("latitude")
        lng = r.get("longitude")
        if lat is None or lng is None:
            lat, lng = await _geocode(r.get("address", ""))
        if lat is None:
            print(f"  [{i}/{len(items)}] geocode fail: {r.get('name_ko')!r}")
            miss += 1
            continue

        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO halal_restaurants
                    (restaurant_id, name_ko, name_en, area_major, halal_type,
                     muslim_cooks_available, no_alcohol_sales,
                     cuisine_type, food_keywords, menu_examples,
                     short_description_ko, address, phone,
                     opening_hours, break_time, last_order,
                     lat, lng, geom)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9::jsonb,$10::jsonb,
                        $11,$12,$13,$14::jsonb,$15::jsonb,$16::jsonb,
                        $17,$18,
                        ST_SetSRID(ST_MakePoint($18, $17), 4326)::geography)
                ON CONFLICT (restaurant_id) DO UPDATE SET
                    name_ko=EXCLUDED.name_ko, name_en=EXCLUDED.name_en,
                    halal_type=EXCLUDED.halal_type,
                    address=EXCLUDED.address, phone=EXCLUDED.phone,
                    opening_hours=EXCLUDED.opening_hours,
                    lat=EXCLUDED.lat, lng=EXCLUDED.lng, geom=EXCLUDED.geom
                """,
                rid, r.get("name_ko",""), r.get("name_en",""),
                r.get("area_major",""), r.get("halal_type",""),
                r.get("muslim_cooks_available"), r.get("no_alcohol_sales"),
                json.dumps(r.get("cuisine_type", []), ensure_ascii=False),
                json.dumps(r.get("food_keywords", []), ensure_ascii=False),
                json.dumps(r.get("menu_examples", []), ensure_ascii=False),
                r.get("short_description_ko",""),
                r.get("address",""), r.get("phone",""),
                json.dumps(r.get("opening_hours") or {}, ensure_ascii=False),
                json.dumps(r.get("break_time") or {}, ensure_ascii=False),
                json.dumps(r.get("last_order") or {}, ensure_ascii=False),
                lat, lng,
            )
        ok += 1
        print(f"  [{i}/{len(items)}] {r.get('name_ko')!r} ✓ ({lat:.4f}, {lng:.4f})")
    print(f"\n=== 완료 — INSERT {ok}, geocode 실패 {miss} ===")


if __name__ == "__main__":
    asyncio.run(main())
