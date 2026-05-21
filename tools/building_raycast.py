"""
building_raycast.py
사전 적재된 VWorld 건물 폴리곤(JSON)에 3D 레이캐스팅을 수행.

사용자 GPS + heading + pitch(+ altitude) → 시선이 처음 교차하는 건물 반환.

교차 판정:
  1) 2D 레이 vs 폴리곤 교차 (Shapely) — 수평 투영으로 후보 필터
  2) 교차점에서의 시선 고도가 건물 높이 범위 내인지 확인 (pitch 고려)

인덱스:
  프로세스 시작 시 JSON을 한 번만 로드하고 Shapely STRtree 인덱스 구축.
  이후 런타임 쿼리는 O(log n)에 수렴.
"""

import json
import math
import os
from typing import Optional

from shapely.geometry import LineString, Point, Polygon
from shapely.strtree import STRtree

DATA_PATH = "rag/data/vworld_buildings.json"
DEFAULT_MAX_RAY_M = 300.0  # 레이 최대 길이 (너무 멀면 오매칭 증가)

# 프로세스 내 캐싱
_buildings: list = []          # [ {meta, polygon} ... ]
_strtree: Optional[STRtree] = None
_polygon_to_idx: dict = {}     # id(polygon) → index in _buildings


# ── 좌표 유틸 ────────────────────────────────────────────────────────────────

def _meters_to_deg_lat(m: float) -> float:
    return m / 111_320.0


def _meters_to_deg_lng(m: float, lat: float) -> float:
    return m / (111_320.0 * math.cos(math.radians(lat)))


# ── 초기 로드 ────────────────────────────────────────────────────────────────

def _load_index():
    """
    JSON에서 건물을 로드해 Shapely Polygon으로 변환하고 STRtree 인덱스 구축.
    첫 호출 시 1회만 실행.
    """
    global _buildings, _strtree, _polygon_to_idx

    if _strtree is not None:
        return

    if not os.path.exists(DATA_PATH):
        print(f"[Raycast] WARNING: {DATA_PATH} 없음. "
              f"python -m rag.build_vworld_buildings 먼저 실행 필요.")
        _buildings = []
        _strtree = STRtree([])
        return

    print(f"[Raycast] JSON 로드 중: {DATA_PATH}")
    with open(DATA_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    raw = data.get("buildings", [])
    polygons = []
    entries = []

    for i, b in enumerate(raw):
        try:
            coords = json.loads(b["polygon_2d"]) if isinstance(b["polygon_2d"], str) else b["polygon_2d"]
        except Exception:
            continue
        if not coords or len(coords) < 3:
            continue
        try:
            polygon = Polygon(coords)  # [[lng, lat], ...]
            if not polygon.is_valid or polygon.is_empty:
                continue
        except Exception:
            continue

        entries.append({
            "meta": b,
            "polygon": polygon,
        })
        polygons.append(polygon)

    _buildings = entries
    _strtree = STRtree(polygons)
    _polygon_to_idx = {id(poly): i for i, poly in enumerate(polygons)}

    print(f"[Raycast] {len(entries)}개 건물 인덱스 구축 완료")


# ── 레이캐스팅 ───────────────────────────────────────────────────────────────

def find_building_by_raycast(
    user_lat: float,
    user_lng: float,
    heading: float,
    user_alt: float = 0.0,  # API 호환용 (현재 미사용)
    pitch: float = 0.0,     # API 호환용 (현재 미사용)
    max_ray_m: float = DEFAULT_MAX_RAY_M,
) -> Optional[dict]:
    """
    2D 레이캐스팅: 사용자가 바라보는 첫 번째 건물 metadata 반환.

    user_alt, pitch는 현재 사용하지 않음. pitch 기반 3D 필터를 쓰면
    ARCore 쿼터니언 해석 오차 때문에 "가까운 저층을 건너뛰고 먼 고층이 매칭"되는
    문제가 발생해 2D 가장 가까운 교차 건물을 반환하는 방식이 더 안정적임.

    Args:
        user_lat, user_lng: 사용자 GPS
        heading: ARCore 방위각 (0=북, 90=동, 180=남, 270=서. 음수 허용)
        max_ray_m: 레이 최대 거리

    Returns:
        건물 metadata dict 또는 None
    """
    del user_alt, pitch  # 미사용 파라미터 린터 경고 방지
    _load_index()
    if not _buildings:
        return None

    # 레이 생성 (위경도 평면 투영)
    heading_rad = math.radians(heading)
    dlat = _meters_to_deg_lat(max_ray_m) * math.cos(heading_rad)
    dlng = _meters_to_deg_lng(max_ray_m, user_lat) * math.sin(heading_rad)

    # Shapely는 (x=lng, y=lat)
    ray = LineString([(user_lng, user_lat), (user_lng + dlng, user_lat + dlat)])
    user_point = Point(user_lng, user_lat)

    # STRtree로 BBox 겹치는 건물만 후보로 추출 (O(log n))
    candidate_idxs = _strtree.query(ray)
    if len(candidate_idxs) == 0:
        print(f"[Raycast] 후보 건물 없음 (heading={heading:.1f}°)")
        return None

    # 사용자가 "안에 있거나 매우 가까운" 폴리곤은 제외 — 건물 안에서는 그 건물 자체를 볼 수 없음
    # (AR 사용자는 건물 밖에서 다른 건물을 바라보는 시나리오가 정상)
    # GPS 오차 고려하여 10m 이내도 같이 제외
    SELF_EXCLUDE_M = 10.0
    skip_ids = set()
    for idx in candidate_idxs:
        entry = _buildings[int(idx)]
        try:
            dist_deg = entry["polygon"].distance(user_point)
            if dist_deg * 111_320.0 <= SELF_EXCLUDE_M:
                skip_ids.add(int(idx))
                name = entry["meta"].get("bld_nm") or "(이름 없음)"
                print(f"[Raycast] 사용자 내부/근접 폴리곤 제외: {name!r}")
        except Exception:
            pass

    # 원칙: "사용자가 바라보는 첫 번째 건물" = 레이가 교차하는 가장 가까운 건물
    # pitch 필터는 정확도가 확보되지 않아 오매칭 위험이 크므로 사용하지 않음.
    # (pitch=40°일 때 앞 저층을 전부 "통과"시켜 먼 고층이 매칭되는 문제)
    best = None
    best_dist_m = float("inf")

    for idx in candidate_idxs:
        if int(idx) in skip_ids:
            continue
        entry = _buildings[int(idx)]
        polygon = entry["polygon"]

        if not ray.intersects(polygon):
            continue

        intersection = ray.intersection(polygon)
        if intersection.is_empty:
            continue

        # MultiLineString/GeometryCollection 분해 — 가장 가까운 조각의 거리
        if hasattr(intersection, "geoms"):
            nearest_geom = min(
                intersection.geoms,
                key=lambda g: user_point.distance(g),
                default=None,
            )
            if nearest_geom is None:
                continue
        else:
            nearest_geom = intersection

        # 교차 시작점(사용자 쪽에서 먼저 만나는 점)까지의 거리
        # representative_point 대신 polygon boundary와 ray 교차점의 최소 거리 사용
        try:
            dist_deg = user_point.distance(nearest_geom)
        except Exception:
            continue
        dist_m = dist_deg * 111_320.0

        if dist_m < best_dist_m:
            best = entry["meta"]
            best_dist_m = dist_m

    if best:
        name = best.get("bld_nm") or "(이름 없음)"
        print(f"[Raycast] 매칭: {name!r} "
              f"height={best.get('height')} dist={best_dist_m:.0f}m "
              f"heading={heading:.1f}°")
        _print_debug_geojson(user_lat, user_lng, user_lng + dlng, user_lat + dlat, best)
    else:
        print(f"[Raycast] 교차 건물 없음 "
              f"(heading={heading:.1f}° 후보={len(candidate_idxs)})")
        _print_debug_geojson(user_lat, user_lng, user_lng + dlng, user_lat + dlat, None)
    return best

def fetch_building_by_ufid(ufid: str) -> Optional[dict]:
    """
    ufid 로 buildings 검색 — JSON 인덱스(명동 핫패스) → DB fallback(전국).
    클라이언트가 이미 정면 건물을 식별한 경우 사용 (옵션 B 경로).
    JSON 인덱스는 명동 2km 만 커버하므로 외대 등 다른 지역은 DB 직접 조회.
    반환 형식은 find_building_by_raycast 와 호환 — meta dict 만.
    """
    # 1) JSON 인덱스 hot path (명동 영역) — 빠른 in-memory 매칭
    _load_index()
    if _buildings:
        for entry in _buildings:
            if entry["meta"].get("ufid") == ufid:
                name = entry["meta"].get("bld_nm") or "(이름 없음)"
                print(f"[UfidLookup] JSON 매칭: {name!r} ufid={ufid}")
                return entry["meta"]

    # 2) DB fallback — JSON 미커버 지역(외대 등)
    try:
        import asyncio
        import asyncpg
        import os
        from dotenv import load_dotenv
        load_dotenv()
        url = os.getenv("SUPABASE_DATABASE_URL", "")
        if not url:
            print(f"[UfidLookup] DB URL 없음 — 매칭 실패: ufid={ufid}")
            return None

        async def _query():
            conn = await asyncpg.connect(url, statement_cache_size=0)
            try:
                return await conn.fetchrow(
                    "SELECT ufid, bld_nm, center_lat, center_lng, bd_mgt_sn "
                    "FROM buildings WHERE ufid = $1",
                    ufid,
                )
            finally:
                await conn.close()

        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # 이미 비동기 컨텍스트면 asyncio.run 못 씀 → ThreadPoolExecutor wrap.
                # 호출자(place_insight_agent) 자체가 async 이므로 보통 여기 도달.
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as ex:
                    row = ex.submit(asyncio.run, _query()).result()
            else:
                row = asyncio.run(_query())
        except RuntimeError:
            row = asyncio.run(_query())

        if row:
            meta = {
                "ufid":       row["ufid"],
                "bld_nm":     row["bld_nm"] or "",
                "center_lat": float(row["center_lat"]) if row["center_lat"] is not None else None,
                "center_lng": float(row["center_lng"]) if row["center_lng"] is not None else None,
                "bd_mgt_sn":  row["bd_mgt_sn"] or "",
            }
            name = meta["bld_nm"] or "(이름 없음)"
            print(f"[UfidLookup] DB 매칭: {name!r} ufid={ufid}")
            return meta
    except Exception as e:
        print(f"[UfidLookup] DB lookup 에러: {e}")

    print(f"[UfidLookup] 매칭 실패: ufid={ufid}")
    return None

def _print_debug_geojson(
    user_lat: float, user_lng: float,
    ray_end_lng: float, ray_end_lat: float,
    matched: Optional[dict],
):
    """
    디버그용 GeoJSON FeatureCollection을 한 줄로 출력.
    geojson.io에 붙여넣으면 사용자 위치, 레이, 매칭 건물 폴리곤이 시각화됨.
    """
    features = [
        {
            "type": "Feature",
            "properties": {"name": "USER", "marker-color": "#ff0000"},
            "geometry": {"type": "Point", "coordinates": [user_lng, user_lat]},
        },
        {
            "type": "Feature",
            "properties": {"name": "RAY", "stroke": "#ff8800", "stroke-width": 3},
            "geometry": {
                "type": "LineString",
                "coordinates": [[user_lng, user_lat], [ray_end_lng, ray_end_lat]],
            },
        },
    ]
    if matched:
        try:
            coords = json.loads(matched["polygon_2d"])
            features.append({
                "type": "Feature",
                "properties": {
                    "name": matched.get("bld_nm") or "(이름 없음)",
                    "ufid": matched.get("ufid"),
                    "height": matched.get("height"),
                    "fill": "#00ff00", "fill-opacity": 0.4,
                    "stroke": "#008800",
                },
                "geometry": {"type": "Polygon", "coordinates": [coords]},
            })
        except Exception:
            pass
    fc = {"type": "FeatureCollection", "features": features}
    print(f"[GeoJSON] {json.dumps(fc, ensure_ascii=False)}")
