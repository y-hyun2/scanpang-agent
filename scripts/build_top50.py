"""
build_top50.py
명동 인근 이름 있는 건물 중 5층 이상을 명동 중심 거리 순으로 50개 선별해
place_info DB를 쌓는다. 실패한 건물은 건너뛰고 계속 진행한다.

실행:
    source venv/bin/activate
    python scripts/build_top50.py
    python scripts/build_top50.py --limit 10  # 상위 10개만 테스트
    python scripts/build_top50.py --skip 5    # 5번부터 재시작
"""

import argparse
import asyncio
import json
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.automation.pipeline import process_one_building

MYEONGDONG_LAT = 37.5636
MYEONGDONG_LNG = 126.9822
DATA_PATH      = "rag/data/vworld_buildings.json"


def _dist_m(lat: float, lng: float) -> float:
    dlat = math.radians(lat - MYEONGDONG_LAT)
    dlng = math.radians(lng - MYEONGDONG_LNG)
    a = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(MYEONGDONG_LAT))
         * math.cos(math.radians(lat))
         * math.sin(dlng / 2) ** 2)
    return 6_371_000 * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def get_top50(limit: int = 50) -> list[dict]:
    with open(DATA_PATH, encoding="utf-8") as f:
        data = json.load(f)

    candidates = [
        b for b in data["buildings"]
        if (b.get("bld_nm") or "").strip()
        and int(b.get("grnd_flr") or 0) >= 5
        and _dist_m(float(b["center_lat"]), float(b["center_lng"])) <= 2000
    ]
    candidates.sort(
        key=lambda b: _dist_m(float(b["center_lat"]), float(b["center_lng"]))
    )
    return candidates[:limit]


async def run(limit: int, skip: int):
    buildings = get_top50(limit + skip)
    targets   = buildings[skip:]

    print(f"\n처리 대상: {len(targets)}개 건물 (skip={skip}, limit={limit})\n")

    results = []
    t_start = time.time()

    for i, b in enumerate(targets, 1):
        ufid    = b["ufid"]
        bld_nm  = b["bld_nm"]
        dist    = _dist_m(float(b["center_lat"]), float(b["center_lng"]))
        floor   = b.get("grnd_flr", "?")

        print(f"[{skip+i:2d}/{skip+len(targets)}] {bld_nm} ({floor}층, {dist:.0f}m)")

        t0 = time.time()
        try:
            result = await process_one_building(ufid)
        except Exception as e:
            result = {"ufid": ufid, "name_ko": bld_nm, "status": "error", "error": str(e)}
        elapsed = time.time() - t0

        status = result.get("status", "?")
        conf   = result.get("confirmed_count", 0)
        cov    = result.get("coverage_rate", 0.0)
        err    = result.get("error") or ""
        print(f"  → {status}  confirmed={conf}  coverage={cov:.0%}  ({elapsed:.1f}s)"
              + (f"  ERROR: {err}" if err else ""))

        results.append({**result, "bld_nm": bld_nm, "elapsed": round(elapsed, 1)})

        # 빌드 중간 요약 (10개마다)
        if i % 10 == 0:
            ok  = sum(1 for r in results if r["status"] == "ok")
            par = sum(1 for r in results if r["status"] == "partial")
            err_cnt = sum(1 for r in results if r["status"] == "error")
            total_t = time.time() - t_start
            print(f"\n  ── 중간 요약 ({i}개) ──")
            print(f"  ok={ok}  partial={par}  error={err_cnt}  누적={total_t:.0f}s\n")

    # 최종 요약
    total_t = time.time() - t_start
    ok      = sum(1 for r in results if r["status"] == "ok")
    partial = sum(1 for r in results if r["status"] == "partial")
    errors  = [r for r in results if r["status"] == "error"]

    print("\n" + "=" * 60)
    print(f"완료: {len(results)}개  ok={ok}  partial={partial}  error={len(errors)}")
    print(f"총 소요: {total_t:.0f}초 ({total_t/60:.1f}분)")
    if errors:
        print("\n실패 목록:")
        for r in errors:
            print(f"  {r['bld_nm']}  {r.get('error','')}")
    print()

    # 결과 로그 저장
    log_path = "logs/build_top50_result.json"
    os.makedirs("logs", exist_ok=True)
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"결과 로그: {log_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=50, help="처리할 건물 수 (기본 50)")
    parser.add_argument("--skip",  type=int, default=0,  help="앞에서 건너뛸 수 (재시작용)")
    args = parser.parse_args()
    asyncio.run(run(args.limit, args.skip))


if __name__ == "__main__":
    main()
