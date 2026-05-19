"""rag/data/prayer_rooms.json → prayer_rooms 테이블 import."""
import asyncio
import json
from pathlib import Path

from core.db import get_pool

JSON_PATH = Path(__file__).resolve().parent.parent / "rag" / "data" / "prayer_rooms.json"


async def main():
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        items = json.load(f)
    print(f"JSON 로드: {len(items)}건")

    pool = await get_pool()
    ok = 0
    for i, r in enumerate(items, 1):
        name = r.get("name") or r.get("name_en") or f"prayer_{i}"
        lat = r.get("lat")
        lng = r.get("lng")
        if lat is None or lng is None:
            print(f"  [{i}/{len(items)}] {name!r} 좌표 없음 skip")
            continue
        # PK: name(slug). 중복 시 enumerate 보장 위해 name 안되면 idx 사용.
        room_id = name
        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO prayer_rooms
                    (room_id, name, name_en, address, lat, lng, floor, open_hours,
                     phone, facilities, availability_status, capacity, notes, geom)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11,$12,$13,
                        ST_SetSRID(ST_MakePoint($6, $5), 4326)::geography)
                ON CONFLICT (room_id) DO UPDATE SET
                    name=EXCLUDED.name, address=EXCLUDED.address,
                    lat=EXCLUDED.lat, lng=EXCLUDED.lng, geom=EXCLUDED.geom,
                    floor=EXCLUDED.floor, open_hours=EXCLUDED.open_hours,
                    phone=EXCLUDED.phone, facilities=EXCLUDED.facilities,
                    availability_status=EXCLUDED.availability_status,
                    capacity=EXCLUDED.capacity, notes=EXCLUDED.notes
                """,
                room_id, name, r.get("name_en",""), r.get("address",""),
                float(lat), float(lng), r.get("floor",""), r.get("open_hours",""),
                r.get("phone",""),
                json.dumps(r.get("facilities") or {}, ensure_ascii=False),
                r.get("availability_status",""),
                str(r.get("capacity") or ""),
                r.get("notes",""),
            )
        ok += 1
        print(f"  [{i}/{len(items)}] {name!r} ✓ ({lat:.4f},{lng:.4f})")
    print(f"\n=== 완료 — INSERT {ok}/{len(items)} ===")


if __name__ == "__main__":
    asyncio.run(main())
