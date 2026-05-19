"""
geocode_missing_restrooms.py
공중화장실 JSON 캐시에서 좌표 누락 row(24k건)를 카카오 주소→좌표로 보강 후
Supabase `public_restrooms` 테이블에 UPSERT.

사용법:
    # 특정 지역만 (주소 ILIKE) — 빠른 검증용
    python scripts/geocode_missing_restrooms.py --area 용인

    # 전체 24k 한 번에 (시간 오래 걸림)
    python scripts/geocode_missing_restrooms.py --area ''
"""

import argparse
import asyncio
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import httpx
from dotenv import load_dotenv

from core.db import get_pool

load_dotenv()

JSON_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "rag", "data", "public_restrooms.json")
KAKAO_KEY = os.getenv("KAKAO_REST_API_KEY", "")

_UPSERT = """
INSERT INTO public_restrooms
    (mng_no, name, type, addr_road, addr_lot, phone, open_hours,
     lat, lng, geom, raw)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9,
        ST_SetSRID(ST_MakePoint($9, $8), 4326)::geography,
        $10::jsonb)
ON CONFLICT (mng_no) DO UPDATE SET
    lat = EXCLUDED.lat, lng = EXCLUDED.lng, geom = EXCLUDED.geom
"""


async def kakao_addr_to_coord(client: httpx.AsyncClient, addr: str) -> tuple[float, float] | None:
    if not addr.strip():
        return None
    try:
        r = await client.get(
            "https://dapi.kakao.com/v2/local/search/address.json",
            headers={"Authorization": f"KakaoAK {KAKAO_KEY}"},
            params={"query": addr, "size": 1},
            timeout=8,
        )
        docs = r.json().get("documents", [])
        if not docs:
            return None
        d = docs[0]
        return float(d["y"]), float(d["x"])
    except Exception:
        return None


async def run(area_filter: str):
    if not KAKAO_KEY:
        print("[ERR] KAKAO_REST_API_KEY missing")
        return

    with open(JSON_PATH, encoding="utf-8") as f:
        all_rows = json.load(f)

    # 좌표 없는 row + 주소 있는 row 필터
    targets = [
        r for r in all_rows
        if not (r.get("WGS84_LAT") and r.get("WGS84_LOT"))
        and (r.get("LCTN_ROAD_NM_ADDR") or r.get("LCTN_LOTNO_ADDR"))
        and r.get("MNG_NO")
    ]
    if area_filter:
        targets = [
            r for r in targets
            if area_filter in (r.get("LCTN_ROAD_NM_ADDR", "") + r.get("LCTN_LOTNO_ADDR", ""))
        ]
    print(f"[INFO] 처리 대상: {len(targets)}건 (area_filter='{area_filter}')")

    pool = await get_pool()
    async with httpx.AsyncClient() as client:
        success, fail = 0, 0
        async with pool.acquire() as conn:
            for i, r in enumerate(targets, 1):
                addr = r.get("LCTN_ROAD_NM_ADDR") or r.get("LCTN_LOTNO_ADDR")
                coord = await kakao_addr_to_coord(client, addr)
                if not coord:
                    fail += 1
                    if i % 20 == 0:
                        print(f"  {i}/{len(targets)} (success={success}, fail={fail})")
                    continue
                lat, lng = coord
                await conn.execute(
                    _UPSERT,
                    r["MNG_NO"],
                    r.get("RSTRM_NM") or "",
                    r.get("SE_NM") or "",
                    r.get("LCTN_ROAD_NM_ADDR") or "",
                    r.get("LCTN_LOTNO_ADDR") or "",
                    r.get("TELNO") or "",
                    r.get("OPN_HR_DTL") or r.get("OPN_HR") or "",
                    lat, lng,
                    json.dumps(r, ensure_ascii=False),
                )
                success += 1
                if i % 20 == 0:
                    print(f"  {i}/{len(targets)} (success={success}, fail={fail})")
                await asyncio.sleep(0.05)  # 카카오 rate limit 회피 (초당 ~20건)

        print(f"[OK] 완료: success={success}, fail={fail}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--area", type=str, default="",
                        help="주소 부분일치 필터 (예: 용인, 모현). 빈 문자열 = 전체")
    args = parser.parse_args()
    asyncio.run(run(args.area))


if __name__ == "__main__":
    main()
