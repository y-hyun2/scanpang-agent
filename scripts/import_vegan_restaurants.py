"""
import_vegan_restaurants.py
myeongdong_vegan.json → vegan_restaurants 테이블 import.

JSON 필드:
    id, place_id, store_name, category, addr, phone,
    lat, lng, place_url, category_key,
    details: {vegan_level, vegan_menu, restaurant_type},
    open_hours, closed_days, homepage, image_urls, floor, source, last_updated
"""

import asyncio
import json
import os
from datetime import datetime
from pathlib import Path

import httpx

from core.db import get_pool

KAKAO_KEY = os.getenv("KAKAO_REST_API_KEY", "")


async def _geocode(addr: str) -> tuple[float | None, float | None]:
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

JSON_PATH = Path(__file__).resolve().parent.parent / "rag" / "data" / "myeongdong_vegan_restaurants.json"


async def main():
    if not JSON_PATH.exists():
        print(f"JSON 없음: {JSON_PATH}")
        return
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        items = json.load(f)
    print(f"JSON 로드: {len(items)}건")

    pool = await get_pool()
    ok = skip = 0

    for i, r in enumerate(items, 1):
        lat = r.get("lat")
        lng = r.get("lng")
        if not lat or not lng:
            lat, lng = await _geocode(r.get("addr", ""))
        if not lat or not lng:
            print(f"  [{i}/{len(items)}] geocode 실패, 스킵: {r.get('store_name')!r}")
            skip += 1
            continue

        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO vegan_restaurants
                    (id, place_id, store_name, category, addr, phone,
                     lat, lng, geom, place_url, category_key,
                     details, open_hours, closed_days, homepage,
                     image_urls, floor, source, last_updated)
                VALUES ($1,$2,$3,$4,$5,$6,
                        $7,$8, ST_SetSRID(ST_MakePoint($8, $7), 4326)::geography,
                        $9,$10, $11::jsonb,$12,$13,$14,
                        $15::jsonb,$16,$17,$18::timestamptz)
                ON CONFLICT (id) DO UPDATE SET
                    store_name=EXCLUDED.store_name,
                    category=EXCLUDED.category,
                    addr=EXCLUDED.addr,
                    details=EXCLUDED.details,
                    open_hours=EXCLUDED.open_hours,
                    lat=EXCLUDED.lat, lng=EXCLUDED.lng, geom=EXCLUDED.geom,
                    last_updated=EXCLUDED.last_updated
                """,
                r.get("id"), r.get("place_id"),
                r.get("store_name", ""), r.get("category", ""),
                r.get("addr", ""), r.get("phone", ""),
                lat, lng,
                r.get("place_url", ""), r.get("category_key", ""),
                json.dumps(r.get("details") or {}, ensure_ascii=False),
                r.get("open_hours"), r.get("closed_days"), r.get("homepage"),
                json.dumps(r.get("image_urls") or [], ensure_ascii=False),
                r.get("floor", ""), r.get("source", ""),
                datetime.fromisoformat(r["last_updated"].replace("Z", "+00:00")) if r.get("last_updated") else None,
            )
        ok += 1
        print(f"  [{i}/{len(items)}] {r.get('store_name')!r} ✓ ({lat:.4f}, {lng:.4f})")

    print(f"\n=== 완료 — INSERT {ok}, 스킵 {skip} ===")


if __name__ == "__main__":
    asyncio.run(main())
