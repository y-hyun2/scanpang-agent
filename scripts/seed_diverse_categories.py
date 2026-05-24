"""
seed_diverse_categories.py
각 category_key별 대표 매장 2개씩 store_details에 누적 시드.

- 기존 데이터 삭제 X (이미 있는 row는 그대로 두거나 ON CONFLICT로 덮어쓰기만)
- 명동 중심 좌표(37.5636, 126.9822) 기준 outdoor 진입으로 처리
- 카테고리별 fetcher 동작을 한 번에 확인 가능

사용:
    python scripts/seed_diverse_categories.py                # 전체
    python scripts/seed_diverse_categories.py --skip-existing # 이미 있는 매장 건너뜀
    python scripts/seed_diverse_categories.py --category cafe restaurant  # 특정 카테고리만
"""

import argparse
import asyncio
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.db import get_pool
from tools.store_tools import get_store_detail


# 명동 중심 좌표 (모든 매장 공통 진입 좌표)
MYEONGDONG_LAT = 37.5636
MYEONGDONG_LNG = 126.9822
PLACE_ID       = "__outdoor__"   # outdoor 모드 sentinel

# 카테고리별 대표 매장 — (store_name) 또는 (store_name, lat, lng) 튜플
# 명동 중심에서 1km 밖 매장은 좌표 override 필요 (예: N서울타워).
DIVERSE_STORES: dict[str, list] = {
    "cafe":              ["스타벅스 포포인츠 명동점", "블루보틀 명동점"],
    "restaurant":        ["멜팅소울 롯데백화점 본점", "명동교자 본점"],
    "shopping":          ["올리브영 명동중앙점", "롯데백화점 본점"],
    "convenience_store": ["CU 명동중앙점", "GS25 명동역점"],
    "pharmacy":          ["명동 온누리약국", "다보아약국 명동"],
    "hospital":          ["을지병원 명동", "고려대학교 의료원"],
    "bank":              ["KEB하나은행 명동점", "신한은행 명동지점"],
    "atm":               ["KEB하나은행 ATM 명동점", "우리은행 ATM 명동"],
    "exchange":          ["명동 외환센터", "대한환전소 명동점"],
    "subway":            ["명동역", "을지로입구역"],
    "restroom":          ["명동 공중화장실", "회현역 화장실"],
    "locker":            ["명동역 물품보관함", "을지로입구역 물품보관함"],
    "prayer_room":       ["Adnan Kebab Halal 기도실", "캄풍쿠 기도실"],
}


async def _existing_ids() -> set[str]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch("SELECT id FROM store_details")
    return {r["id"] for r in rows}


async def _resolve_building(store_name: str) -> dict | None:
    """
    매장명이 어느 빌딩에 속하는지 자동 매핑.
    1) place_info.name_ko = 매장명 (매장 = 건물 자체. 예: 명동성당, N서울타워)
    2) place_info.floor_info 안 stores[].name = 매장명 (매장 ⊂ 건물의 층별정보)
    Returns: {"ufid", "lat", "lng", "match_kind": "building"|"floor_info"} 또는 None
    """
    pool = await get_pool()
    async with pool.acquire() as conn:
        # 1차: 건물명 정확/부분 일치
        row = await conn.fetchrow(
            "SELECT ufid, lat, lng FROM place_info "
            "WHERE name_ko ILIKE $1 ORDER BY length(name_ko) LIMIT 1",
            f"%{store_name}%",
        )
        if row:
            return {**dict(row), "match_kind": "building"}

        # 2차: floor_info JSONB 안 매장명 검색
        row = await conn.fetchrow(
            """
            SELECT ufid, lat, lng FROM place_info
            WHERE floor_info IS NOT NULL
              AND EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements(floor_info) AS fl,
                       jsonb_array_elements(fl->'stores') AS s
                  WHERE s->>'name' ILIKE $1
              )
            LIMIT 1
            """,
            f"%{store_name}%",
        )
        if row:
            return {**dict(row), "match_kind": "floor_info"}
    return None


async def run(categories: list[str], skip_existing: bool, sleep_sec: float):
    # (category, store_name, lat_override, lng_override) — 좌표 없으면 None
    targets: list[tuple[str, str, float | None, float | None]] = []
    for cat in categories or DIVERSE_STORES.keys():
        for entry in DIVERSE_STORES.get(cat, []):
            if isinstance(entry, tuple):
                name, lat_o, lng_o = entry
                targets.append((cat, name, lat_o, lng_o))
            else:
                targets.append((cat, entry, None, None))

    if not targets:
        print("대상 카테고리 없음")
        return

    existing = await _existing_ids() if skip_existing else set()
    skipped_existing = 0

    print(f"\n총 {len(targets)}개 매장 시드 (sleep={sleep_sec}s 사이)\n")
    t_start = time.time()
    results: list[dict] = []

    for i, (cat, store_name, lat_o, lng_o) in enumerate(targets, 1):
        # 진입점 결정 우선순위:
        # 1) DIVERSE_STORES에 좌표 override 명시된 경우 (예: N서울타워) — 그 좌표 사용
        # 2) place_info에 매핑되는 빌딩이 있으면 빌딩 ufid + 좌표
        # 3) 그 외 명동 중심 outdoor
        if lat_o is not None and lng_o is not None:
            place_id = PLACE_ID
            lat, lng = lat_o, lng_o
            entry_label = "outdoor(override)"
        else:
            bld = await _resolve_building(store_name)
            if bld:
                place_id = bld["ufid"]
                lat, lng = float(bld["lat"]), float(bld["lng"])
                entry_label = f"building({bld['match_kind']})"
            else:
                place_id = PLACE_ID
                lat, lng = MYEONGDONG_LAT, MYEONGDONG_LNG
                entry_label = "outdoor"

        cache_id = f"{place_id}__{store_name}"
        if skip_existing and cache_id in existing:
            print(f"[{i:2d}/{len(targets)}] {store_name} ({cat}) — 캐시 hit, skip")
            skipped_existing += 1
            continue

        if i > 1:
            await asyncio.sleep(sleep_sec)

        t0 = time.time()
        try:
            r = await get_store_detail(place_id, store_name, lat=lat, lng=lng)
            elapsed = time.time() - t0
            d = r.get("details") if r else {}
            d_keys = list(d.keys()) if isinstance(d, dict) else []
            results.append({
                "store_name": store_name, "category": cat, "entry": entry_label,
                "category_key": r.get("category_key"),
                "source":     r.get("source"),
                "has_data":   bool(r.get("phone") or r.get("addr") or d_keys),
                "elapsed":    round(elapsed, 1),
            })
            print(f"[{i:2d}/{len(targets)}] {store_name} ({cat}) [{entry_label}] "
                  f"→ key={r.get('category_key')} src={r.get('source') or '(empty)'} "
                  f"({elapsed:.1f}s)")
        except Exception as e:
            print(f"[{i:2d}/{len(targets)}] {store_name} ({cat}) ✗ {type(e).__name__}: {e}")
            results.append({"store_name": store_name, "category": cat, "error": str(e)})

    # ── 요약 ──
    total_t = time.time() - t_start
    by_src: dict[str, int] = {}
    by_key: dict[str, int] = {}
    for r in results:
        src = r.get("source") or "(empty)"
        key = r.get("category_key") or "?"
        by_src[src] = by_src.get(src, 0) + 1
        by_key[key] = by_key.get(key, 0) + 1

    print(f"\n{'='*60}")
    print(f"완료: 처리={len(results)}개  스킵={skipped_existing}개  소요={total_t:.0f}초")
    print(f"\ncategory_key별 분포:")
    for k, n in sorted(by_key.items()): print(f"  {k:24s} : {n}")
    print(f"\nsource별 분포:")
    for s, n in sorted(by_src.items()): print(f"  {s:24s} : {n}")

    # 결과 로그 저장
    os.makedirs("logs", exist_ok=True)
    log_path = "logs/seed_diverse_result.json"
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n결과 로그: {log_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--category", type=str, nargs="+",
                        help=f"특정 카테고리만 (전체: {', '.join(DIVERSE_STORES.keys())})")
    parser.add_argument("--skip-existing", action="store_true",
                        help="store_details에 이미 있는 매장은 건너뜀")
    parser.add_argument("--sleep", type=float, default=3.0,
                        help="매장간 sleep 초 (Naver Place transient 회피, 기본 3)")
    args = parser.parse_args()

    asyncio.run(run(args.category or [], args.skip_existing, args.sleep))


if __name__ == "__main__":
    main()
