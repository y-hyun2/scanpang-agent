"""
fetch_buildings_vworld.py
─────────────────────────────────────────────────────────────────────────────
VWorld WFS에서 중심 좌표 반경 내 모든 건물을 수집해 building 테이블에 적재.
저장 컬럼: building_key, bd_mgt_sn, ufid, bld_nm, usability,
         grnd_flr, ugrnd_flr, height, estimated_height,
         center_lat, center_lng, h3_index_10, geom

building_key = COALESCE(bd_mgt_sn, ufid)
ufid = "NN" 인 건물도 bd_mgt_sn 이 있으면 수집 (ufid = NULL 로 저장)

실행:
    python scripts/fetch_buildings_vworld.py
    python scripts/fetch_buildings_vworld.py --lat 37.5636 --lng 126.9822 --radius 500
─────────────────────────────────────────────────────────────────────────────
"""

import os
import sys
import time
import math
import json
import requests
import h3
import psycopg2
from shapely.geometry import shape
from dotenv import load_dotenv

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
load_dotenv()


# ============================================================================
# 적재 대상 (중심 좌표 + 반경)
# ============================================================================
CENTER_LAT   = 37.4968
CENTER_LNG   = 127.0407
RADIUS_M     = 200
COMMIT_EVERY = 100


# ============================================================================
# BBOX 계산
# ============================================================================
def bbox_for_radius(lat, lng, radius_m):
    """원형 반경을 감싸는 최소 축정렬 BBOX (min_lat, min_lng, max_lat, max_lng)."""
    lat_dpm = 1.0 / 110_574.0
    lng_dpm = 1.0 / (111_320.0 * math.cos(math.radians(lat)))
    dlat = radius_m * lat_dpm
    dlng = radius_m * lng_dpm
    return (lat - dlat, lng - dlng, lat + dlat, lng + dlng)


# ============================================================================
# 용도 코드 → 시맨틱 카테고리
# ============================================================================
USABILITY_TO_CATEGORY = {
    "02000": "단독주택", "03000": "공동주택",
    "04000": "근린생활", "05000": "근린생활",
    "06000": "문화집회", "07000": "판매",
    "14000": "업무",     "15000": "숙박",
}


def estimate_height(category, floor_count):
    """카테고리 + 지상 층수 기반 폴백 높이 추정 (m)."""
    if not floor_count or floor_count < 1:
        floor_count = 2.5
    if "상업" in category or "업무" in category or "판매" in category:
        return (4.5 + (floor_count - 1) * 3.5) * 1.15
    elif "주거" in category or "주택" in category or "아파트" in category:
        return (floor_count * 2.8) * 1.10
    else:
        return (floor_count * 3.0) * 1.10


# ============================================================================
# VWorld WFS 페이징 (BBOX 재귀 4분할)
# ============================================================================
VWORLD_PAGE_LIMIT = 1000
REQUEST_DELAY_SEC = 0.15


def split_bbox(bbox):
    min_lat, min_lng, max_lat, max_lng = bbox
    mid_lat = (min_lat + max_lat) / 2
    mid_lng = (min_lng + max_lng) / 2
    return [
        (min_lat, min_lng, mid_lat, mid_lng),
        (min_lat, mid_lng, mid_lat, max_lng),
        (mid_lat, min_lng, max_lat, mid_lng),
        (mid_lat, mid_lng, max_lat, max_lng),
    ]


def fetch_features_recursive(bbox, vworld_key, depth=0, max_depth=8,
                             seen_ids=None, collected=None, stats=None):
    if seen_ids is None:
        seen_ids = set()
    if collected is None:
        collected = []
    if stats is None:
        stats = {"requests": 0, "max_depth_reached": 0, "capped_bboxes": 0}

    indent = "  " * depth
    bbox_str = f"{bbox[0]:.6f},{bbox[1]:.6f},{bbox[2]:.6f},{bbox[3]:.6f}"

    wfs_url = (
        "https://api.vworld.kr/req/wfs"
        f"?key={vworld_key}"
        "&domain=http://localhost"
        "&SERVICE=WFS&VERSION=1.1.0&REQUEST=GetFeature"
        "&TYPENAME=lt_c_bldginfo"
        "&OUTPUTFORMAT=application/json"
        "&SRSNAME=EPSG:4326"
        f"&BBOX={bbox_str},EPSG:4326"
    )

    try:
        time.sleep(REQUEST_DELAY_SEC)
        response = requests.get(wfs_url, timeout=30)
        data = response.json()
        stats["requests"] += 1
    except Exception as e:
        print(f"{indent}⚠ 요청 실패: {e}")
        return collected, stats

    features = data.get("features", [])
    new_count = 0
    for f in features:
        props     = f.get("properties") or {}
        bd_mgt_sn = props.get("bd_mgt_sn") or ""
        ufid      = props.get("ufid") or ""
        if ufid.strip().upper() == "NN":
            ufid = ""
        # building_key: bd_mgt_sn 우선, 없으면 ufid
        dedup_key = bd_mgt_sn or ufid
        if dedup_key and dedup_key not in seen_ids:
            seen_ids.add(dedup_key)
            collected.append(f)
            new_count += 1

    stats["max_depth_reached"] = max(stats["max_depth_reached"], depth)
    print(f"{indent}[d={depth}] {len(features):4d}건 수신 / {new_count:4d}건 신규 (누계 {len(collected)})")

    if len(features) >= VWORLD_PAGE_LIMIT and depth < max_depth:
        stats["capped_bboxes"] += 1
        for sub_bbox in split_bbox(bbox):
            fetch_features_recursive(sub_bbox, vworld_key, depth + 1, max_depth,
                                     seen_ids, collected, stats)
    elif len(features) >= VWORLD_PAGE_LIMIT:
        print(f"{indent}⚠ max_depth({max_depth}) 도달 — 일부 누락 가능")

    return collected, stats


# ============================================================================
# DB 연결
# ============================================================================
def _connect(db_url: str):
    return psycopg2.connect(db_url)


# ============================================================================
# 메인 파이프라인
# ============================================================================
def run_pipeline(lat=CENTER_LAT, lng=CENTER_LNG, radius_m=RADIUS_M):
    print("1. VWorld API에서 데이터를 가져오는 중...")

    vworld_key = os.environ.get("VWORLD_API_KEY")
    if not vworld_key:
        print("치명적 에러: .env에서 VWORLD_API_KEY를 찾을 수 없다.")
        return

    target_bbox = bbox_for_radius(lat, lng, radius_m)
    print(f"중심: ({lat}, {lng}) | 반경: {radius_m}m")
    print(f"BBOX: {target_bbox}\n")

    features, stats = fetch_features_recursive(target_bbox, vworld_key)
    print(f"\n수집 완료: 요청 {stats['requests']}회, "
          f"최대 깊이 {stats['max_depth_reached']}, "
          f"한도 도달 BBOX {stats['capped_bboxes']}개")
    print(f"수집된 고유 건물 수: {len(features)}개")

    print("\n2. PostGIS DB 연결 및 데이터 적재 시작...")

    db_url = os.environ.get("SUPABASE_DATABASE_URL")
    if not db_url:
        print("치명적 에러: .env에서 SUPABASE_DATABASE_URL을 찾을 수 없다.")
        return

    conn   = _connect(db_url)
    cursor = conn.cursor()

    # 수기로 제외(삭제)한 건물은 다시 적재하지 않는다.
    # building 테이블의 기존 행은 ON CONFLICT DO NOTHING이 보호하지만, 하드 삭제분은
    # 충돌이 안 나 되살아나므로 building_exclusion에 기록된 key를 미리 걸러낸다.
    cursor.execute("SELECT building_key FROM building_exclusion")
    excluded_keys = {row[0] for row in cursor.fetchall()}
    print(f"  제외 목록(building_exclusion): {len(excluded_keys)}개 — 적재 시 스킵")

    insert_query = """
        INSERT INTO building (
            building_key,
            bd_mgt_sn, ufid,
            bld_nm, usability,
            grnd_flr, ugrnd_flr,
            height, estimated_height,
            center_lat, center_lng,
            h3_index_10, geom
        ) VALUES (
            %s,
            %s, %s,
            %s, %s,
            %s, %s,
            %s, %s,
            %s, %s,
            %s, ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326))
        )
        ON CONFLICT (building_key) DO NOTHING;
    """

    success_count    = 0
    skipped_no_key   = 0
    skipped_no_geom  = 0
    skipped_excluded = 0

    for feature in features:
        props    = feature.get("properties", {})
        geometry = feature.get("geometry")
        if not geometry:
            skipped_no_geom += 1
            continue

        bd_mgt_sn = props.get("bd_mgt_sn") or None
        ufid      = props.get("ufid") or None
        if ufid and ufid.strip().upper() == "NN":
            ufid = None

        building_key = bd_mgt_sn or ufid
        if not building_key:
            skipped_no_key += 1
            continue

        if building_key in excluded_keys:
            skipped_excluded += 1
            continue

        poly = shape(geometry)
        if poly.is_empty:
            skipped_no_geom += 1
            continue
        centroid = poly.centroid
        c_lat, c_lng = centroid.y, centroid.x

        bld_nm            = props.get("bld_nm") or None
        usability         = props.get("usability") or None
        semantic_category = USABILITY_TO_CATEGORY.get(usability or "", "일반")

        raw_grnd_flr = props.get("grnd_flr")
        grnd_flr     = raw_grnd_flr if (raw_grnd_flr and raw_grnd_flr >= 1) else 1
        ugrnd_flr    = props.get("ugrnd_flr") or 0

        raw_height = props.get("height")
        height     = raw_height if (raw_height is not None and raw_height > 0) else None

        floor_for_estimate = raw_grnd_flr if (raw_grnd_flr and raw_grnd_flr >= 1) else None
        est_height         = estimate_height(semantic_category, floor_for_estimate)

        h3_index = h3.latlng_to_cell(c_lat, c_lng, 10)

        params = (
            building_key,
            bd_mgt_sn, ufid,
            bld_nm, usability,
            grnd_flr, ugrnd_flr,
            height, est_height,
            c_lat, c_lng,
            h3_index, json.dumps(geometry),
        )

        for attempt in range(2):
            try:
                cursor.execute(insert_query, params)
                success_count += 1
                break
            except psycopg2.OperationalError as e:
                if attempt == 0:
                    print(f"  ⚠ 연결 끊김, 재연결 후 재시도... (building_key: {building_key})")
                    try:
                        conn.close()
                    except Exception:
                        pass
                    conn   = _connect(db_url)
                    cursor = conn.cursor()
                else:
                    print(f"  ✗ 재시도 실패, 건너뜀 (building_key: {building_key}): {e}")
            except Exception as e:
                print(f"데이터 삽입 오류 (building_key: {building_key}): {e}")
                try:
                    conn.rollback()
                except Exception:
                    pass
                break

        if success_count > 0 and success_count % COMMIT_EVERY == 0:
            conn.commit()
            print(f"  → {success_count}건 커밋 완료")

    conn.commit()
    cursor.close()
    conn.close()

    print(f"\n작업 완료. 총 {success_count}개 적재 성공.")
    if skipped_excluded:
        print(f"  - building_exclusion에 등록되어 스킵: {skipped_excluded}개")
    if skipped_no_key:
        print(f"  - bd_mgt_sn·ufid 둘 다 없어 스킵: {skipped_no_key}개")
    if skipped_no_geom:
        print(f"  - geometry 없음으로 스킵: {skipped_no_geom}개")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="VWorld WFS → building 테이블 적재")
    parser.add_argument("--lat",    type=float, default=CENTER_LAT)
    parser.add_argument("--lng",    type=float, default=CENTER_LNG)
    parser.add_argument("--radius", type=int,   default=RADIUS_M, dest="radius_m")
    args = parser.parse_args()
    run_pipeline(lat=args.lat, lng=args.lng, radius_m=args.radius_m)
