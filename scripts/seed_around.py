"""
seed_around.py
주어진 좌표 주변에서 Kakao 카테고리 검색으로 매장을 자동 수집해
store_details 에 누적 시드한다.

명동 시드(seed_diverse_categories.py)와 달리 **매장명을 미리 알 필요가 없어** —
임의 지역(예: 외대 글로벌캠퍼스 주변) 테스트용 DB 채우기에 적합.

사용:
    # 외대 글로벌캠퍼스 주변 2km, 카테고리 기본 세트 각 5개씩
    python scripts/seed_around.py --lat 37.3387 --lng 127.2674

    # 카테고리/개수/반경 커스텀
    python scripts/seed_around.py --lat 37.3387 --lng 127.2674 \\
        --radius 3000 --per-category 8 \\
        --categories cafe restaurant convenience_store tourist bank
"""

import argparse
import asyncio
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import httpx
from dotenv import load_dotenv

from core.db import get_pool
from tools.store_tools import get_store_detail

load_dotenv()
KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")
PLACE_ID = "__outdoor__"

# 사용자 친화 카테고리명 → Kakao category_group_code 매핑.
# restroom 은 Kakao 에 없어 별도 처리(아래 _seed_restrooms).
CATEGORY_CODES: dict[str, str] = {
    "cafe":              "CE7",
    "restaurant":        "FD6",
    "convenience_store": "CS2",
    "bank":              "BK9",
    "hospital":          "HP8",
    "pharmacy":          "PM9",
    "tourist":           "AT4",
    "cultural":          "CT1",
    "accommodation":     "AD5",
    "shopping":          "MT1",  # 대형마트 — 일반 쇼핑은 Kakao 카테고리 없어 마트로 대체
    "subway":            "SW8",
}

DEFAULT_CATEGORIES = [
    "cafe", "restaurant", "convenience_store",
    "tourist", "cultural", "bank",
]


async def _kakao_category_search(
    client: httpx.AsyncClient,
    code: str,
    lat: float,
    lng: float,
    radius: int,
    size: int,
) -> list[dict]:
    """Kakao Local 카테고리 검색 — 페이지네이션 없이 1페이지 size 개."""
    try:
        resp = await client.get(
            "https://dapi.kakao.com/v2/local/search/category.json",
            headers={"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"},
            params={
                "category_group_code": code,
                "x": lng, "y": lat,
                "radius": radius,
                "size": min(size, 15),  # Kakao 한도
                "sort": "distance",
            },
            timeout=10.0,
        )
        resp.raise_for_status()
        return resp.json().get("documents", [])
    except Exception as e:
        print(f"  [kakao] category={code} 실패: {e}")
        return []


async def _seed_restrooms(lat: float, lng: float, radius: int, count: int) -> list[dict]:
    """
    공중화장실은 Kakao 에 없어 data.go.kr 캐시(public_restroom)에서 가까운 N개 추출.
    이미 _public_restroom_cache 가 53k entry 를 들고 있으므로 매장명 없이도 바로 시드 가능.
    """
    from tools.details_fetchers._public_restroom_cache import find_nearest
    nearby = await find_nearest(lat, lng, radius_m=radius)
    nearby = nearby[:count]
    results: list[dict] = []
    for r in nearby:
        name = (r.get("RSTRM_NM") or r.get("name") or "공중화장실").strip()
        r_lat = float(r.get("WGS84_LAT") or r.get("lat") or 0)
        r_lng = float(r.get("WGS84_LOT") or r.get("lng") or 0)
        if r_lat == 0 or r_lng == 0:
            continue
        try:
            row = await get_store_detail(PLACE_ID, name, lat=r_lat, lng=r_lng)
            results.append({
                "store_name": name, "category": "restroom",
                "category_key": row.get("category_key"),
                "source": row.get("source") or "(empty)",
            })
            print(f"  ✓ {name} (restroom) src={row.get('source')}")
        except Exception as e:
            print(f"  ✗ {name} (restroom) — {type(e).__name__}: {e}")
    return results


async def run(
    lat: float, lng: float, radius: int,
    categories: list[str], per_category: int,
    sleep_sec: float, skip_existing: bool,
):
    if not KAKAO_REST_API_KEY:
        print("KAKAO_REST_API_KEY 가 .env 에 없음 — 중단")
        return

    pool = await get_pool()
    async with pool.acquire() as conn:
        existing = {r["id"] for r in await conn.fetch("SELECT id FROM store_details")} if skip_existing else set()

    results: list[dict] = []
    t_start = time.time()

    async with httpx.AsyncClient() as client:
        for cat in categories:
            print(f"\n=== {cat} ===")
            if cat == "restroom":
                results.extend(await _seed_restrooms(lat, lng, radius, per_category))
                continue

            code = CATEGORY_CODES.get(cat)
            if not code:
                print(f"  알 수 없는 카테고리 — 건너뜀")
                continue

            docs = await _kakao_category_search(client, code, lat, lng, radius, per_category)
            if not docs:
                print(f"  {cat}: 결과 없음")
                continue

            for i, d in enumerate(docs[:per_category], 1):
                name = (d.get("place_name") or "").strip()
                d_lat = float(d.get("y") or 0)
                d_lng = float(d.get("x") or 0)
                if not name or d_lat == 0 or d_lng == 0:
                    continue

                cache_id = f"{PLACE_ID}__{name}"
                if cache_id in existing:
                    print(f"  [{i:2d}] {name} — 캐시 hit, skip")
                    continue

                if i > 1:
                    await asyncio.sleep(sleep_sec)

                t0 = time.time()
                try:
                    row = await get_store_detail(PLACE_ID, name, lat=d_lat, lng=d_lng)
                    elapsed = time.time() - t0
                    results.append({
                        "store_name": name, "category": cat,
                        "category_key": row.get("category_key"),
                        "source": row.get("source") or "(empty)",
                        "elapsed": round(elapsed, 1),
                    })
                    print(f"  [{i:2d}] {name} → key={row.get('category_key')} "
                          f"src={row.get('source') or '(empty)'} ({elapsed:.1f}s)")
                except Exception as e:
                    print(f"  [{i:2d}] {name} ✗ {type(e).__name__}: {e}")
                    results.append({"store_name": name, "category": cat, "error": str(e)})

    # 요약
    total_t = time.time() - t_start
    by_key: dict[str, int] = {}
    by_src: dict[str, int] = {}
    for r in results:
        k = r.get("category_key") or "?"
        s = r.get("source") or "(empty)"
        by_key[k] = by_key.get(k, 0) + 1
        by_src[s] = by_src.get(s, 0) + 1

    print(f"\n{'='*60}")
    print(f"완료: 처리={len(results)}개  소요={total_t:.0f}초")
    print(f"\ncategory_key별 분포:")
    for k, n in sorted(by_key.items()): print(f"  {k:24s} : {n}")
    print(f"\nsource별 분포:")
    for s, n in sorted(by_src.items()): print(f"  {s:24s} : {n}")

    os.makedirs("logs", exist_ok=True)
    log_path = f"logs/seed_around_{int(t_start)}.json"
    with open(log_path, "w", encoding="utf-8") as f:
        json.dump({
            "lat": lat, "lng": lng, "radius": radius,
            "categories": categories, "per_category": per_category,
            "results": results,
        }, f, ensure_ascii=False, indent=2)
    print(f"\n결과 로그: {log_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lat", type=float, required=True, help="중심 위도")
    parser.add_argument("--lng", type=float, required=True, help="중심 경도")
    parser.add_argument("--radius", type=int, default=2000, help="반경 m (기본 2000)")
    parser.add_argument("--per-category", type=int, default=5, help="카테고리당 매장 수 (기본 5, 최대 15)")
    parser.add_argument("--categories", type=str, nargs="+",
                        default=DEFAULT_CATEGORIES,
                        help=f"카테고리 (기본: {', '.join(DEFAULT_CATEGORIES)}). "
                             f"가능: {', '.join(list(CATEGORY_CODES.keys()) + ['restroom'])}")
    parser.add_argument("--sleep", type=float, default=2.0,
                        help="매장간 sleep 초 (Naver Place transient 회피, 기본 2)")
    parser.add_argument("--skip-existing", action="store_true",
                        help="store_details 에 이미 있는 매장은 건너뜀")
    args = parser.parse_args()

    asyncio.run(run(
        args.lat, args.lng, args.radius,
        args.categories, args.per_category,
        args.sleep, args.skip_existing,
    ))


if __name__ == "__main__":
    main()
