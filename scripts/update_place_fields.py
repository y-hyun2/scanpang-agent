"""
update_place_fields.py
tourist_places / accommodation_places 테이블의 기존 레코드에 대해
name_ko · phone · homepage · overview · open_hours 를 Naver + Tour API로 갱신.

테이블 DROP 없이 UPDATE만 수행 — 기존 데이터 유지.

사용:
    # 관광지·문화시설 전체
    python scripts/update_place_fields.py --table tourist

    # 숙박 전체
    python scripts/update_place_fields.py --table accommodation

    # 특정 장소명 1건만
    python scripts/update_place_fields.py --table tourist --name 용인자연휴양림

    # 슬립 간격 조정 (기본 1.5초)
    python scripts/update_place_fields.py --table tourist --sleep 1.0
"""

import argparse
import asyncio
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv

from core.db import get_pool
from tools.details_fetchers import naver_place
from tools.tour_api_place_client import fetch_detail_common

load_dotenv()


async def _fetch_naver(tour_name: str, lat: float, lng: float, addr: str) -> dict:
    """Naver에서 matched_name·phone·homepage·open_hours·intro 조회."""
    try:
        result = await naver_place.fetch(
            store_name=tour_name, lat=lat, lng=lng,
            building_ufid="", category_name="",
            addr_hint=addr,
        )
        if not result:
            return {}
        details = result.get("details") or {}
        return {
            "name":       details.get("matched_name") or "",
            "phone":      result.get("phone") or "",
            "homepage":   result.get("homepage") or "",
            "open_hours": result.get("open_hours") or "",
            "intro":      details.get("intro") or "",
        }
    except Exception as e:
        print(f"    [naver] 실패: {e}")
        return {}


async def run(table: str, sleep_sec: float, name_filter: str) -> None:
    assert table in ("tourist", "accommodation"), "table은 tourist 또는 accommodation"
    tbl = f"{table}_places"

    pool = await get_pool()
    async with pool.acquire() as conn:
        # open_hours 컬럼 없으면 자동 추가
        await conn.execute(f"ALTER TABLE {tbl} ADD COLUMN IF NOT EXISTS open_hours TEXT")

        if name_filter:
            rows = await conn.fetch(
                f"SELECT id, content_id, content_type_id, name_ko, lat, lng, addr FROM {tbl} WHERE name_ko = $1",
                name_filter,
            )
        else:
            rows = await conn.fetch(
                f"SELECT id, content_id, content_type_id, name_ko, lat, lng, addr FROM {tbl} ORDER BY last_updated ASC NULLS FIRST"
            )

    if not rows:
        print("레코드 없음")
        return

    print(f"[{tbl}] 대상 {len(rows)}개\n{'='*60}")
    t_start = time.time()
    updated = 0

    for i, row in enumerate(rows, 1):
        if i > 1:
            await asyncio.sleep(sleep_sec)

        tour_name  = row["name_ko"] or ""
        content_id = row["content_id"] or ""
        lat        = float(row["lat"]) if row["lat"] else 0.0
        lng        = float(row["lng"]) if row["lng"] else 0.0
        addr       = row["addr"] or ""
        print(f"[{i}/{len(rows)}] {tour_name}")

        # Tour API: homepage + overview (tourist만)
        common = await fetch_detail_common(content_id) if content_id else {}

        # Naver
        naver = await _fetch_naver(tour_name, lat, lng, addr)

        name_ko  = naver.get("name") or tour_name
        phone    = naver.get("phone") or ""
        homepage = common.get("homepage") or naver.get("homepage") or ""
        open_hours = naver.get("open_hours") or ""

        overview_raw = common.get("overview") or ""
        overview = re.sub(r"<[^>]+>", "", overview_raw).strip() or naver.get("intro") or ""

        # COALESCE: 새 값이 있을 때만 덮어씀 (기존 비어있거나 새 값이 더 좋을 때)
        async with pool.acquire() as conn:
            await conn.execute(
                f"""
                UPDATE {tbl} SET
                    name_ko    = CASE WHEN $1 <> '' THEN $1 ELSE name_ko END,
                    phone      = CASE WHEN $2 <> '' THEN $2 ELSE phone END,
                    homepage   = CASE WHEN $3 <> '' THEN $3 ELSE homepage END,
                    overview   = CASE WHEN $4 <> '' THEN $4 ELSE overview END,
                    open_hours = CASE WHEN $5 <> '' THEN $5 ELSE open_hours END,
                    last_updated = NOW()
                WHERE id = $6
                """,
                name_ko, phone, homepage, overview, open_hours, row["id"],
            )

        updated += 1
        changed = name_ko != tour_name
        print(
            f"  {'→ 이름: ' + name_ko + '  ' if changed else ''}"
            f"phone={'✓' if phone else '✗'}  "
            f"homepage={'✓' if homepage else '✗'}  "
            f"overview={'✓' if overview else '✗'}  "
            f"open_hours={'✓' if open_hours else '✗'}"
        )

    elapsed = time.time() - t_start
    print(f"\n{'='*60}")
    print(f"완료: {updated}/{len(rows)}개  소요={elapsed:.0f}초")


def main() -> None:
    parser = argparse.ArgumentParser(description="장소 필드 업데이트 (name/phone/homepage/overview/open_hours)")
    parser.add_argument("--table",  required=True, choices=["tourist", "accommodation"])
    parser.add_argument("--name",   type=str, default="", help="특정 장소명 1건만 처리")
    parser.add_argument("--sleep",  type=float, default=1.5)
    args = parser.parse_args()
    asyncio.run(run(args.table, args.sleep, args.name))


if __name__ == "__main__":
    main()