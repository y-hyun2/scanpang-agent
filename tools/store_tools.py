"""
store_tools.py
개별 매장 상세 정보 조회 — store_details 테이블 lazy 캐시.

흐름:
1) cache hit → 그대로 반환
2) miss → ① place_info에서 건물 좌표 조회
         → ② Kakao Local로 기본 메타(category_name, phone, addr) 1차 획득
         → ③ category_classifier로 category_key 분류
         → ④ details_fetchers.fetch_by_category로 카테고리별 풍부도 수집
         → ⑤ 결과를 store_details에 INSERT (UPSERT)
"""

from datetime import datetime, timezone
import json

from core.db import get_pool
from tools.place_tools import check_kakao_open_status
from tools.category_classifier import classify_category
from tools.details_fetchers import fetch_by_category


# 명동 기본 좌표 fallback
_DEFAULT_LAT = 37.5636
_DEFAULT_LNG = 126.9822


async def get_store_detail(place_id: str, store_name: str) -> dict:
    """
    Args:
        place_id:   건물 ufid (place_info FK)
        store_name: 매장명

    Returns:
        store_details row dict. 모든 fetcher 실패 시 최소 정보만.
    """
    cache_id = f"{place_id}__{store_name}"
    pool     = await get_pool()

    # ── ① 캐시 조회 ─────────────────────────────────────────────────────────
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT * FROM store_details WHERE id = $1", cache_id
        )
    if row:
        return _row_to_dict(row)

    # ── ② 건물 좌표 + floor (있으면) 조회 ───────────────────────────────────
    async with pool.acquire() as conn:
        coord = await conn.fetchrow(
            "SELECT lat, lng FROM place_info WHERE ufid = $1", place_id
        )
    lat = float(coord["lat"]) if coord and coord["lat"] is not None else _DEFAULT_LAT
    lng = float(coord["lng"]) if coord and coord["lng"] is not None else _DEFAULT_LNG

    # ── ③ Kakao Local 1차 — category_name 확보용 (분류 입력) ─────────────────
    kakao = await check_kakao_open_status(store_name, lat, lng) or {}
    category_name = kakao.get("category", "") or ""
    category_key  = classify_category(category_name)

    # ── ④ 카테고리별 fetcher 디스패치 ───────────────────────────────────────
    fetched = await fetch_by_category(
        category_key   = category_key,
        store_name     = store_name,
        lat            = lat,
        lng            = lng,
        building_ufid  = place_id,
        category_name  = category_name,
    )

    # fetcher 결과가 비어있으면 Kakao 1차 메타로 fallback
    phone      = fetched.get("phone")      or kakao.get("phone", "")
    addr       = fetched.get("addr")       or kakao.get("addr", "")
    homepage   = fetched.get("homepage")   or kakao.get("place_url", "")
    open_hours = fetched.get("open_hours", "")
    closed_days = fetched.get("closed_days", "")
    image_urls = fetched.get("image_urls", []) or []
    details    = fetched.get("details", {}) or {}
    source     = fetched.get("source", "kakao" if kakao else "")

    # ── ⑤ store_details UPSERT ──────────────────────────────────────────────
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO store_details
                (id, place_id, store_name, category, category_key,
                 addr, phone, lat, lng, place_url,
                 details, open_hours, closed_days, homepage, image_urls,
                 floor, source, last_updated)
            VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,
                    $11::jsonb,$12,$13,$14,$15::jsonb,$16,$17,$18)
            ON CONFLICT (id) DO UPDATE SET
                category      = EXCLUDED.category,
                category_key  = EXCLUDED.category_key,
                addr          = EXCLUDED.addr,
                phone         = EXCLUDED.phone,
                place_url     = EXCLUDED.place_url,
                details       = EXCLUDED.details,
                open_hours    = EXCLUDED.open_hours,
                closed_days   = EXCLUDED.closed_days,
                homepage      = EXCLUDED.homepage,
                image_urls    = EXCLUDED.image_urls,
                source        = EXCLUDED.source,
                last_updated  = EXCLUDED.last_updated
            """,
            cache_id, place_id, store_name,
            category_name, category_key,
            addr, phone, lat, lng, kakao.get("place_url", ""),
            json.dumps(details, ensure_ascii=False),
            open_hours, closed_days, homepage,
            json.dumps(image_urls, ensure_ascii=False),
            None, source, datetime.now(timezone.utc),
        )

    return {
        "id":            cache_id,
        "place_id":      place_id,
        "store_name":    store_name,
        "category":      category_name,
        "category_key":  category_key,
        "addr":          addr,
        "phone":         phone,
        "lat":           lat,
        "lng":           lng,
        "place_url":     kakao.get("place_url", ""),
        "details":       details,
        "open_hours":    open_hours,
        "closed_days":   closed_days,
        "homepage":      homepage,
        "image_urls":    image_urls,
        "source":        source,
    }


def _row_to_dict(row) -> dict:
    """asyncpg Record → 일반 dict. JSONB 필드는 이미 파싱돼서 옴."""
    d = dict(row)
    # asyncpg가 JSONB를 string으로 반환할 수도 있어 방어적으로 파싱
    for k in ("details", "image_urls"):
        v = d.get(k)
        if isinstance(v, str):
            try:
                d[k] = json.loads(v)
            except Exception:
                pass
    return d
