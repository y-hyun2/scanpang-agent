"""
scripts/pretranslate.py
모든 테이블의 영어 번역을 미리 생성해 translations JSONB에 저장한다.

대상 테이블:
  store_details, place_info, vegan_restaurants, accommodation_places,
  tourist_places, public_restrooms, halal_restaurants, prayer_rooms,
  subway_exits (station_name+line 기준 그룹핑)

사용법:
    python scripts/pretranslate.py                          # 전체
    python scripts/pretranslate.py --table store_details    # 특정 테이블만
    python scripts/pretranslate.py --limit 50               # 행 수 제한 (테스트용)
    python scripts/pretranslate.py --concurrency 15         # 동시 처리 수
"""

import argparse
import asyncio
import json
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from core.db import get_pool
from tools.translation import translate_fields, _deepl_translate


# ── 테이블별 설정 ─────────────────────────────────────────────────────────────
# pk        : 단일 PK 컬럼명 (subway_exits는 None → 별도 처리)
# name_col  : 한국어 이름 컬럼 (translations["en"]["name"]에 저장)
# addr_col  : 주소 컬럼 (없으면 None)
# extra     : {번역키: DB컬럼명} 추가 필드
# name_en_col: 이미 영문명 컬럼이 있으면 API 호출 없이 복사
TABLE_CONFIGS = [
    {
        "table": "storedetails",
        "pk": "id",
        "name_col": "store_name",
        "addr_col": "addr",
        "extra": {
            "category": "category",
        },
    },
    {
        "table": "placeinfo",
        "pk": "building_key",
        "name_col": "name_ko",
        "addr_col": "addr",
        "extra": {
            "category":      "category",
            "open_hours":    "open_hours",
            "closed_days":   "closed_days",
            "parking_info":  "parking_info",
            "admission_fee": "admission_fee",
        },
    },
    {
        "table": "vegan_restaurants",
        "pk": "id",
        "name_col": "store_name",
        "addr_col": "addr",
        "extra": {},
    },
    {
        "table": "accommodation_places",
        "pk": "id",
        "name_col": "name_ko",
        "addr_col": "addr",
        "extra": {},
    },
    {
        "table": "tourist_places",
        "pk": "id",
        "name_col": "name_ko",
        "addr_col": "addr",
        "extra": {},
    },
    {
        "table": "public_restrooms",
        "pk": "mng_no",
        "name_col": "name",
        "addr_col": "addr_road",
        "extra": {},
    },
    {
        "table": "halal_restaurants",
        "pk": "restaurant_id",
        "name_col": "name_ko",
        "addr_col": "address",
        "extra": {},
        "name_en_col": "name_en",   # 이미 영문명 있음 — API 생략
    },
    {
        "table": "prayer_rooms",
        "pk": "room_id",
        "name_col": "name",
        "addr_col": "address",
        "extra": {},
        "name_en_col": "name_en",   # 이미 영문명 있음 — API 생략
    },
]


# ── 일반 테이블 처리 ──────────────────────────────────────────────────────────

async def _process_table(cfg: dict, limit: int | None, concurrency: int):
    table      = cfg["table"]
    pk         = cfg["pk"]
    name_col   = cfg["name_col"]
    addr_col   = cfg.get("addr_col")
    extra      = cfg.get("extra", {})
    name_en_col = cfg.get("name_en_col")

    cols = [pk, name_col, "lat", "lng",
            "COALESCE(translations::text, '{}') AS translations"]
    if addr_col:
        cols.append(addr_col)
    if name_en_col:
        cols.append(name_en_col)
    for db_col in extra.values():
        if db_col not in cols:
            cols.append(db_col)

    pool = await get_pool()
    async with pool.acquire() as conn:
        query = f"""
            SELECT {', '.join(cols)}
            FROM {table}
            WHERE lat IS NOT NULL AND lng IS NOT NULL
              AND (
                translations IS NULL
                OR translations = '{{}}'::jsonb
                OR (translations->>'en') IS NULL
              )
            ORDER BY {pk}
        """
        if limit:
            query += f" LIMIT {limit}"
        rows = await conn.fetch(query)

    total = len(rows)
    print(f"\n[{table}] 번역 대상: {total}행")
    if total == 0:
        return

    sem   = asyncio.Semaphore(concurrency)
    done  = 0

    async def process(row):
        nonlocal done
        async with sem:
            pk_val = row[pk]
            try:
                existing = json.loads(row["translations"])
            except Exception:
                existing = {}

            if existing.get("en"):
                done += 1
                return

            # name_en_col이 있으면 API 호출 없이 기존 컬럼 값 사용
            if name_en_col and row.get(name_en_col):
                name_translated = row[name_en_col]
                # addr / extra 는 Translation API로 처리
                api_fields = {}
                if addr_col and row.get(addr_col, "").strip():
                    api_fields["addr"] = row[addr_col]
                for tr_key, db_col in extra.items():
                    v = row.get(db_col, "")
                    if v and str(v).strip():
                        api_fields[tr_key] = str(v)

                if api_fields:
                    try:
                        api_result = await translate_fields(
                            api_fields, lat=float(row["lat"]), lng=float(row["lng"]), lang="en"
                        )
                    except Exception as e:
                        print(f"  [오류] {table}/{pk_val}: {e}")
                        api_result = {}
                else:
                    api_result = {}

                translated = {"name": name_translated, **api_result}
            else:
                fields = {"name": row.get(name_col) or ""}
                if addr_col and row.get(addr_col, "").strip():
                    fields["addr"] = row[addr_col]
                for tr_key, db_col in extra.items():
                    v = row.get(db_col, "")
                    if v and str(v).strip():
                        fields[tr_key] = str(v)
                fields = {k: v for k, v in fields.items() if v.strip()}
                if not fields:
                    done += 1
                    return
                try:
                    translated = await translate_fields(
                        fields, lat=float(row["lat"]), lng=float(row["lng"]), lang="en"
                    )
                except Exception as e:
                    print(f"  [오류] {table}/{pk_val}: {e}")
                    done += 1
                    return

            if not translated:
                done += 1
                return

            pool2 = await get_pool()
            async with pool2.acquire() as conn2:
                await conn2.execute(
                    f"UPDATE {table} "
                    f"SET translations = COALESCE(translations, '{{}}'::jsonb) || $1::jsonb "
                    f"WHERE {pk} = $2",
                    {"en": translated},
                    pk_val,
                )

            done += 1
            pct = done * 100 // total
            name_ko = row.get(name_col) or pk_val
            name_en = translated.get("name", "")
            print(f"  [{pct:3d}%] {done}/{total}  {name_ko} → {name_en}")

    await asyncio.gather(*[process(r) for r in rows])
    print(f"  [{table}] 완료: {done}행")


# ── subway_exits 전용 처리 (복합 PK, station+line 기준 그룹핑) ────────────────

async def _process_subway_exits(limit: int | None, concurrency: int):
    pool = await get_pool()
    async with pool.acquire() as conn:
        # station_name+line 단위로 중복 제거, 대표 lat/lng 사용
        query = """
            SELECT station_name, line,
                   AVG(lat) AS lat, AVG(lng) AS lng,
                   array_agg(DISTINCT facility_name) FILTER (WHERE facility_name != '') AS facilities
            FROM subway_exits
            WHERE lat IS NOT NULL AND lng IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM subway_exits s2
                WHERE s2.station_name = subway_exits.station_name
                  AND s2.line = subway_exits.line
                  AND (s2.translations->>'en') IS NOT NULL
                LIMIT 1
              )
            GROUP BY station_name, line
        """
        if limit:
            query += f" LIMIT {limit}"
        rows = await conn.fetch(query)

    total = len(rows)
    print(f"\n[subway_exits] 번역 대상: {total}개 역")
    if total == 0:
        return

    sem  = asyncio.Semaphore(concurrency)
    done = 0

    async def process(row):
        nonlocal done
        async with sem:
            station_ko = row["station_name"]
            line_ko    = row["line"]
            lat        = float(row["lat"])
            lng        = float(row["lng"])

            # 지하철역은 DeepL로만 처리.
            try:
                name_en, line_en = await asyncio.gather(
                    _deepl_translate(station_ko + "역"),
                    _deepl_translate(line_ko),
                )
            except Exception as e:
                print(f"  [오류] subway {station_ko} {line_ko}: {e}")
                done += 1
                return
            translated = {}
            if name_en:
                translated["name"] = name_en
            if line_en:
                translated["line"] = line_en

            if not translated:
                done += 1
                return

            pool2 = await get_pool()
            async with pool2.acquire() as conn2:
                await conn2.execute(
                    "UPDATE subway_exits "
                    "SET translations = COALESCE(translations, '{}'::jsonb) || $1::jsonb "
                    "WHERE station_name = $2 AND line = $3",
                    {"en": translated},
                    station_ko,
                    line_ko,
                )

            done += 1
            pct = done * 100 // total
            print(f"  [{pct:3d}%] {done}/{total}  {station_ko} {line_ko} → {translated.get('name','')} {translated.get('line','')}")

    await asyncio.gather(*[process(r) for r in rows])
    print(f"  [subway_exits] 완료: {done}개 역")


# ── 메인 ─────────────────────────────────────────────────────────────────────

async def main(target_table: str | None, limit: int | None, concurrency: int):
    tables_to_run = (
        [cfg for cfg in TABLE_CONFIGS if cfg["table"] == target_table]
        if target_table and target_table != "subway_exits"
        else TABLE_CONFIGS
    )

    for cfg in tables_to_run:
        await _process_table(cfg, limit, concurrency)

    if not target_table or target_table == "subway_exits":
        await _process_subway_exits(limit, concurrency)

    print("\n전체 완료.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--table",       default=None, help="특정 테이블만 처리 (기본: 전체)")
    parser.add_argument("--limit",       type=int, default=None, help="행 수 제한 (테스트용)")
    parser.add_argument("--concurrency", type=int, default=10,   help="동시 처리 수 (기본: 10)")
    args = parser.parse_args()

    asyncio.run(main(args.table, args.limit, args.concurrency))
