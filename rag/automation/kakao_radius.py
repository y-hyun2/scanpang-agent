"""
kakao_radius.py
3단계 파이프라인으로 건물에 속한 매장만 정확히 수집한다.
A-1: 카테고리 18개 검색 → A-2: 패션/뷰티 키워드 보강 → A-3: 폴리곤+주소 이중 필터.
floor 정보는 Kakao API에 없으므로 절대 생성하지 않는다.
"""

import asyncio
import os
from typing import Optional

import httpx
from dotenv import load_dotenv
from shapely.geometry import Point, Polygon

load_dotenv()

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")

# A-1: Kakao category_group_code 전체 18개
_CATEGORY_CODES = [
    "MT1",  # 대형마트
    "CS2",  # 편의점
    "PS3",  # 어린이집/유치원
    "SC4",  # 학교
    "AC5",  # 학원
    "PK6",  # 주차장
    "OL7",  # 주유소/충전소
    "SW8",  # 지하철역
    "BK9",  # 은행
    "CT1",  # 문화시설
    "AG2",  # 중개업소
    "PO3",  # 공공기관
    "AT4",  # 관광명소
    "AD5",  # 숙박
    "FD6",  # 음식점
    "CE7",  # 카페
    "HP8",  # 병원
    "PM9",  # 약국
]

# A-2: 카테고리 코드로 빠지는 업종 키워드
_BASE_KEYWORDS = ["패션", "화장품", "잡화", "액세서리", "신발", "안경", "미용실"]


# ── A-1/A-2 내부 검색 헬퍼 ──────────────────────────────────────────────────

def _parse_doc(doc: dict) -> dict:
    """Kakao API 응답 문서 → 표준화 dict."""
    cat_parts = doc.get("category_name", "").split(" > ")
    return {
        "name":     doc.get("place_name", "").strip(),
        "category": cat_parts[-1] if cat_parts else "",
        "phone":    doc.get("phone", ""),
        "address":  doc.get("road_address_name", "") or doc.get("address_name", ""),
        "lat":      float(doc.get("y") or 0),
        "lng":      float(doc.get("x") or 0),
    }


async def _search_by_category(
    client: httpx.AsyncClient,
    code: str,
    lat: float,
    lng: float,
    radius: int,
) -> list[dict]:
    try:
        resp = await client.get(
            "https://dapi.kakao.com/v2/local/search/category.json",
            headers={"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"},
            params={"category_group_code": code, "x": lng, "y": lat,
                    "radius": radius, "size": 15},
        )
        resp.raise_for_status()
        return [_parse_doc(d) for d in resp.json().get("documents", [])]
    except Exception as e:
        print(f"[kakao_radius] category {code} 실패: {e}")
        return []


async def _search_by_keyword(
    client: httpx.AsyncClient,
    keyword: str,
    lat: float,
    lng: float,
    radius: int,
) -> list[dict]:
    try:
        resp = await client.get(
            "https://dapi.kakao.com/v2/local/search/keyword.json",
            headers={"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"},
            params={"query": keyword, "x": lng, "y": lat,
                    "radius": radius, "size": 15},
        )
        resp.raise_for_status()
        return [_parse_doc(d) for d in resp.json().get("documents", [])]
    except Exception as e:
        print(f"[kakao_radius] keyword {keyword!r} 실패: {e}")
        return []


# ── A-3 폴리곤+주소 이중 필터 ────────────────────────────────────────────────

def _extract_addr_key(road_addr: str) -> str:
    """
    도로명주소에서 '도로명 번지' 부분을 추출한다.
    "서울특별시 중구 명동길 14" → "명동길 14"
    토큰이 2개 이하이면 원본 그대로 반환.
    """
    parts = road_addr.strip().split()
    if len(parts) >= 3:
        return " ".join(parts[2:])   # 시/도 + 구/군 이후
    return road_addr


def _is_in_building(
    store: dict,
    polygon: Optional[Polygon],
    polygon_tolerance_m: float,
    bld_road_addr_key: str,
) -> tuple[bool, str]:
    """
    매장이 대상 건물에 속하는지 판정한다.

    Returns:
        (통과여부, match_reason)
        match_reason: "polygon" | "address" | "both" | ""
    """
    polygon_ok = False
    addr_ok    = False

    # 폴리곤 포함 검사
    if polygon is not None and not polygon.is_empty:
        try:
            point  = Point(store["lng"], store["lat"])
            dist_m = polygon.distance(point) * 111_320.0
            polygon_ok = dist_m <= polygon_tolerance_m
        except Exception:
            pass

    # 도로명주소 substring 검사
    store_addr = store.get("address", "")
    if bld_road_addr_key and store_addr:
        addr_ok = bld_road_addr_key in store_addr

    if polygon_ok and addr_ok:
        return True, "both"
    if polygon_ok:
        return True, "polygon"
    if addr_ok:
        return True, "address"
    return False, ""


# ── 메인 공개 함수 ────────────────────────────────────────────────────────────

async def collect_stores_at_building(
    ufid: str,
    bld_polygon_coords: list[list[float]],
    bld_road_address: str,
    bld_name: str = "",
    search_radius_m: int = 50,
    polygon_tolerance_m: float = 5.0,
) -> list[dict]:
    """
    Kakao Local API로 그 건물에 속한 매장만 정확히 수집한다.

    3단계: 카테고리 18개 검색 → 키워드 7~8개 검색 → 폴리곤+주소 필터.

    Args:
        ufid: VWorld 건물 고유 ID (로그용)
        bld_polygon_coords: VWorld polygon_2d 파싱 결과 [[lng, lat], ...]
        bld_road_address: 건물 도로명주소 (Kakao fetch_kakao_info 결과)
        bld_name: 건물명 (없으면 "")
        search_radius_m: API 검색 반경 (기본 50m)
        polygon_tolerance_m: 폴리곤 경계 허용 오차 m (기본 5m)

    Returns:
        list of {
            "name": str, "category": str, "phone": str, "address": str,
            "lat": float, "lng": float,
            "match_reason": "polygon" | "address" | "both"
        }
    """
    if not KAKAO_REST_API_KEY:
        print("[kakao_radius] KAKAO_REST_API_KEY 없음 — 매장 수집 건너뜀")
        return []

    # MultiPolygon 대응: coordinates -> 0 결과가 [[[lng,lat],...], ...] (링 배열)이면
    # 외곽 링([[lng,lat],...])만 추출해 [[lng,lat],...] 형태로 정규화
    if (bld_polygon_coords
            and isinstance(bld_polygon_coords[0], list)
            and bld_polygon_coords[0]
            and isinstance(bld_polygon_coords[0][0], list)):
        bld_polygon_coords = bld_polygon_coords[0]

    # 폴리곤 객체화 (없으면 None — 주소 필터만으로 동작)
    polygon: Optional[Polygon] = None
    if bld_polygon_coords and len(bld_polygon_coords) >= 3:
        try:
            poly = Polygon(bld_polygon_coords)
            polygon = poly if poly.is_valid and not poly.is_empty else None
        except Exception as e:
            print(f"[kakao_radius] 폴리곤 파싱 실패: {e}")

    # 건물 중심: 폴리곤 centroid 또는 없으면 coords 평균
    if polygon:
        center_lng = polygon.centroid.x
        center_lat = polygon.centroid.y
    elif bld_polygon_coords:
        center_lng = sum(c[0] for c in bld_polygon_coords) / len(bld_polygon_coords)
        center_lat = sum(c[1] for c in bld_polygon_coords) / len(bld_polygon_coords)
    else:
        print(f"[kakao_radius] {ufid}: 폴리곤 없음 — 주소 필터만으로 동작")
        center_lat, center_lng = 0.0, 0.0

    addr_key = _extract_addr_key(bld_road_address)

    # ── A-1: 카테고리 18개 검색 ────────────────────────────────────────────
    raw_pool: list[dict] = []

    async with httpx.AsyncClient(timeout=10) as client:
        for code in _CATEGORY_CODES:
            batch = await _search_by_category(client, code, center_lat, center_lng, search_radius_m)
            raw_pool.extend(batch)
            await asyncio.sleep(0.1)

        # ── A-2: 키워드 보강 검색 ──────────────────────────────────────────
        keywords = list(_BASE_KEYWORDS)
        if bld_name:
            keywords.append(bld_name)

        for kw in keywords:
            batch = await _search_by_keyword(client, kw, center_lat, center_lng, search_radius_m)
            raw_pool.extend(batch)
            await asyncio.sleep(0.1)

    # ── 이름 없는 항목 제거 ─────────────────────────────────────────────────
    raw_pool = [s for s in raw_pool if s["name"]]

    # ── 중복 제거: 매장명 + 좌표 5decimal(≈1m) 기준 ──────────────────────
    seen: set[str] = set()
    unique_pool: list[dict] = []
    for store in raw_pool:
        key = f"{store['name']}|{round(store['lat'], 5)}|{round(store['lng'], 5)}"
        if key not in seen:
            seen.add(key)
            unique_pool.append(store)

    print(f"[kakao_radius] {ufid}: raw={len(raw_pool)}, unique={len(unique_pool)}")

    # ── A-3: 폴리곤 + 주소 이중 필터 ──────────────────────────────────────
    result: list[dict] = []
    polygon_pass = address_pass = both_pass = discard = 0

    for store in unique_pool:
        ok, reason = _is_in_building(store, polygon, polygon_tolerance_m, addr_key)
        if ok:
            result.append({**store, "match_reason": reason})
            if reason == "both":
                both_pass += 1
            elif reason == "polygon":
                polygon_pass += 1
            else:
                address_pass += 1
        else:
            discard += 1

    print(
        f"[kakao_radius] {ufid}: 필터 결과 {len(result)}개 통과 "
        f"(polygon={polygon_pass}, address={address_pass}, both={both_pass}, 폐기={discard})"
    )
    return result
