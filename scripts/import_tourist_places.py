"""
import_tourist_places.py
관광지 건물을 분류해 store_details 에 적재.

분류 기준:
  1. buildings.usability = '06000'  (문화/종교/사회시설)
  2. place_info.category 에 TOURIST_KEYWORDS 중 하나라도 포함

각 관광지에 대해 Tour API (한국관광공사) 로 입장료·운영시간·주차 정보를 가져와
place_info 의 기존 값과 병합 후 store_details 에 UPSERT.

  id 형식: tourist__{ufid}
  category_key: "tourist"

실행:
    cd scanpang-server-hufs
    python -m scripts.import_tourist_places
"""

import asyncio
import os

from dotenv import load_dotenv

from core.db import get_pool

load_dotenv()

FETCH_QUERY = """
SELECT
    b.ufid,
    COALESCE(pi.name_ko, b.bld_nm, '')   AS name_ko,
    COALESCE(pi.category, '')             AS category,
    COALESCE(pi.lat, b.center_lat)        AS lat,
    COALESCE(pi.lng, b.center_lng)        AS lng,
    COALESCE(pi.addr, '')                 AS addr,
    COALESCE(pi.phone, '')                AS phone,
    pi.image_url,
    pi.homepage,
    pi.open_hours,
    pi.closed_days,
    pi.parking_info,
    pi.admission_fee
FROM buildings b
LEFT JOIN place_info pi ON pi.ufid = b.ufid
WHERE b.usability = '06000'
ORDER BY name_ko
"""


async def main() -> None:
    pool = await get_pool()

    async with pool.acquire() as conn:
        rows = await conn.fetch(FETCH_QUERY)

    print(f"관광지 후보 {len(rows)}개")
    if not rows:
        print("대상 없음 — 종료")
        return

    from tools.tour_api_client import fetch_tour_info

    success = 0
    for row in rows:
        ufid     = row["ufid"]
        name_ko  = row["name_ko"]
        print(f"  [{ufid}] {name_ko} ...", end=" ", flush=True)

        tour: dict = {}
        if name_ko:
            try:
                tour = await fetch_tour_info(place_name=name_ko) or {}
            except Exception as e:
                print(f"Tour API 실패: {e}")

        # place_info 기존 값 우선, Tour API 로 빈 필드만 보강
        open_hours   = row["open_hours"]   or tour.get("open_hours", "")   or ""
        closed_days  = row["closed_days"]  or tour.get("closed_days", "")  or ""
        parking_info = row["parking_info"] or tour.get("parking_info", "") or ""
        admission_fee = row["admission_fee"] or tour.get("admission_fee", "") or ""
        homepage     = row["homepage"]     or tour.get("homepage", "")     or ""
        image_url    = row["image_url"]    or tour.get("image_url", "")    or ""
        image_urls   = [image_url] if image_url else []

        details = {
            "admission_fee": admission_fee,
            "parking_info":  parking_info,
        }

        # tourist 카테고리는 tourist_places 별도 테이블로 관리 — store_details INSERT 하지 않음.
        print(f"스킵 (tourist_places 테이블로 관리)")
        success += 1

    print(f"\n총 {success}/{len(rows)}개 처리 완료 (store_details 미적재)")


if __name__ == "__main__":
    asyncio.run(main())