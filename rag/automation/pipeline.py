"""
pipeline.py
ufid 하나를 받아 전체 자동화 파이프라인을 실행하고 ChromaDB place_info에 저장한다.
각 단계 실패 시 partial 결과라도 저장하며 에러를 로그에 기록한다.
"""

import json
import os
from datetime import datetime, timezone
from typing import Optional

import chromadb
from chromadb.utils.embedding_functions import DefaultEmbeddingFunction
from dotenv import load_dotenv

from rag.automation.kakao_radius import collect_stores_at_building
from rag.automation.llm_extractor import extract_stores
from rag.automation.query_builder import build_queries
from rag.automation.search_collector import collect
from rag.automation.validator import cross_validate
from rag.build_place_db import (
    fetch_building_key,
    fetch_kakao_info,
    fetch_floor_info,
)

load_dotenv()

_DATA_PATH = "rag/data/vworld_buildings.json"

# 프로세스 내 VWorld 인덱스 캐시
_ufid_index: Optional[dict[str, dict]] = None


def _get_ufid_index() -> dict[str, dict]:
    global _ufid_index
    if _ufid_index is not None:
        return _ufid_index

    if not os.path.exists(_DATA_PATH):
        print(f"[pipeline] {_DATA_PATH} 없음")
        _ufid_index = {}
        return _ufid_index

    with open(_DATA_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    _ufid_index = {b["ufid"]: b for b in data.get("buildings", []) if b.get("ufid")}
    print(f"[pipeline] VWorld 인덱스 로드: {len(_ufid_index)}개 건물")
    return _ufid_index


def _get_place_collection():
    client = chromadb.PersistentClient(path="./chroma_db")
    return client.get_or_create_collection(
        "place_info", embedding_function=DefaultEmbeddingFunction()
    )


def _confirmed_to_floor_info(confirmed: list[dict]) -> list[dict]:
    """
    validator confirmed 매장 → ChromaDB 기존 floor_info 포맷으로 변환.
    {"floor": str, "stores": [str, ...]}
    floor가 null인 매장은 "미확인" 버킷으로 분류.
    """
    floor_map: dict[str, list[str]] = {}
    for store in confirmed:
        floor = store.get("floor") or "미확인"
        name  = store.get("name", "").strip()
        cat   = store.get("category", "")
        label = f"{name} ({cat})" if cat else name
        if name:
            floor_map.setdefault(floor, []).append(label)

    # 층 정렬: B층 → 지하, 숫자 오름차순, 미확인 마지막
    def _sort_key(floor_str: str):
        if floor_str == "미확인":
            return (2, 0)
        s = floor_str.upper().replace("층", "").replace("F", "").replace("B", "-").strip()
        try:
            n = int(s)
            return (0, n) if n >= 0 else (1, abs(n))
        except ValueError:
            return (2, 0)

    return [
        {"floor": f, "stores": stores}
        for f, stores in sorted(floor_map.items(), key=lambda x: _sort_key(x[0]))
    ]


async def process_one_building(ufid: str) -> dict:
    """
    ufid 하나에 대해 전체 자동화 파이프라인을 실행한다.

    단계: VWorld 조회 → Kakao 기본정보 → 반경 매장수집 → 쿼리빌드
          → 검색수집 → LLM파싱 → 정부DB → 교차검증 → ChromaDB upsert

    Returns:
        {"ufid": str, "name_ko": str, "status": "ok"|"partial"|"error",
         "confirmed_count": int, "coverage_rate": float, "error": str|None}
    """
    print(f"\n[pipeline] ===== {ufid} 처리 시작 =====")
    result = {
        "ufid":            ufid,
        "name_ko":         "",
        "status":          "error",
        "confirmed_count": 0,
        "coverage_rate":   0.0,
        "error":           None,
    }

    # ── 1) VWorld 메타 조회 ──────────────────────────────────────────────────
    index = _get_ufid_index()
    building = index.get(ufid)
    if not building:
        result["error"] = f"ufid {ufid} not found in vworld_buildings.json"
        print(f"[pipeline] {result['error']}")
        return result

    bld_nm  = (building.get("bld_nm") or "").strip()
    lat     = float(building.get("center_lat", 0))
    lng     = float(building.get("center_lng", 0))
    name_ko = bld_nm or "이름 없는 건물"
    result["name_ko"] = name_ko
    print(f"[pipeline] 건물: {name_ko!r}  ({lat:.5f}, {lng:.5f})")

    # ── 2) Kakao 기본 정보 (주소·전화·카테고리) ──────────────────────────────
    kakao_info: dict = {}
    if bld_nm:
        try:
            kakao_info = await fetch_kakao_info(bld_nm)
        except Exception as e:
            print(f"[pipeline] fetch_kakao_info 실패: {e}")

    addr  = kakao_info.get("addr", "")
    phone = kakao_info.get("phone", "")
    category = kakao_info.get("category", "")

    # ── 3) Kakao 건물 소속 매장 수집 (폴리곤+주소 이중 필터) ──────────────
    kakao_stores: list[dict] = []
    try:
        polygon_coords = json.loads(building.get("polygon_2d", "[]"))
        kakao_stores = await collect_stores_at_building(
            ufid=ufid,
            bld_polygon_coords=polygon_coords,
            bld_road_address=addr,
            bld_name=bld_nm,
        )
    except Exception as e:
        print(f"[pipeline] collect_stores_at_building 실패: {e}")

    # ── 4) 쿼리 스펙 생성 ────────────────────────────────────────────────────
    query_specs = build_queries(building, kakao_stores)
    print(f"[pipeline] 쿼리 {len(query_specs)}개 생성")

    # ── 5) 검색 결과 수집 ────────────────────────────────────────────────────
    search_results: list[dict] = []
    try:
        search_results = await collect(query_specs)
    except Exception as e:
        print(f"[pipeline] collect 실패: {e}")

    # ── 6) LLM 매장 추출 ────────────────────────────────────────────────────
    extracted: list[dict] = []
    try:
        extracted = await extract_stores(bld_nm, search_results)
    except Exception as e:
        print(f"[pipeline] extract_stores 실패: {e}")

    # ── 7) 정부 DB (소상공인 API) ────────────────────────────────────────────
    govt_stores: list[dict] = []
    try:
        building_key = await fetch_building_key(addr) if addr else None
        if building_key:
            govt_stores = await fetch_floor_info(building_key)
            print(f"[pipeline] 정부DB: {sum(len(f['stores']) for f in govt_stores)}개 매장")
    except Exception as e:
        print(f"[pipeline] 정부DB 조회 실패: {e}")

    # ── 8) 교차검증 ──────────────────────────────────────────────────────────
    validation = cross_validate(extracted, govt_stores, kakao_stores)
    confirmed      = validation["confirmed"]
    coverage_rate  = validation["coverage_rate"]

    result["confirmed_count"] = len(confirmed)
    result["coverage_rate"]   = coverage_rate

    # ── 9) ChromaDB upsert ───────────────────────────────────────────────────
    # 정부DB가 더 풍부하면 정부DB 기준, 없으면 confirmed 기준 floor_info 사용
    if govt_stores:
        floor_info_list = govt_stores
    else:
        floor_info_list = _confirmed_to_floor_info(confirmed)

    try:
        collection = _get_place_collection()
        doc_text   = " ".join(filter(None, [name_ko, category, addr]))
        metadata   = {
            "ufid":          ufid,
            "name_ko":       name_ko,
            "lat":           lat,
            "lng":           lng,
            "addr":          addr,
            "phone":         phone,
            "category":      category,
            "floor_info":    json.dumps(floor_info_list, ensure_ascii=False),
            "coverage_rate": coverage_rate,
            "last_updated":  datetime.now(timezone.utc).isoformat(),
            "source":        "automated",
        }
        collection.upsert(
            ids=[ufid],
            documents=[doc_text],
            metadatas=[metadata],
        )
        print(f"[pipeline] ChromaDB upsert 완료: {ufid}")
        result["status"] = "ok" if confirmed or govt_stores else "partial"
    except Exception as e:
        result["error"] = f"ChromaDB upsert 실패: {e}"
        print(f"[pipeline] {result['error']}")
        result["status"] = "partial"

    print(f"[pipeline] ===== {ufid} 완료 status={result['status']} =====\n")
    return result
