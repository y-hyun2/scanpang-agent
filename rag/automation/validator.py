"""
validator.py
LLM 추출 매장을 다중 출처(URL, intent) 및 정부/Kakao DB와 교차검증한다.
2개 이상 규칙 통과 → confirmed, 1개만 → pending.
"""

import re

try:
    from rapidfuzz import fuzz as _fuzz
    _RAPIDFUZZ_OK = True
except ImportError:
    _RAPIDFUZZ_OK = False
    print("[validator] rapidfuzz 없음 — 규칙 C (퍼지 매칭) 비활성")


def _normalize(name: str) -> str:
    """소문자 변환 + 공백/특수문자 제거."""
    return re.sub(r"[\s\-_·]", "", name).lower()


def _fuzzy_match(name: str, candidates: list[str], threshold: int = 85) -> bool:
    if not _RAPIDFUZZ_OK or not candidates:
        return False
    for cand in candidates:
        if _fuzz.ratio(name, cand) >= threshold:
            return True
    return False


def cross_validate(
    extracted: list[dict],
    govt_stores: list[dict],
    kakao_stores: list[dict],
) -> dict:
    """
    추출 매장을 규칙 A/B/C로 교차검증한다.

    Args:
        extracted:    llm_extractor.extract_stores() 반환값
        govt_stores:  fetch_floor_info() 반환값 (floor_info list)
                      각 항목: {"floor": str, "stores": [str, ...]}
        kakao_stores: collect_stores_by_radius() 반환값
                      각 항목: {"name": str, ...}

    Returns:
        {
            "confirmed": [...],  각 항목에 validation_rules 키 추가
            "pending":   [...],
            "coverage_rate": float,  confirmed / total (0.0이면 분모 0)
        }
    """
    if not extracted:
        return {"confirmed": [], "pending": [], "coverage_rate": 0.0}

    # 정부 + Kakao 매장 이름 풀 (퍼지 매칭용)
    govt_names: list[str] = []
    for floor_item in govt_stores:
        for store_label in floor_item.get("stores", []):
            # "스타벅스 (카페)" 형태에서 이름만 추출
            store_name = store_label.split("(")[0].strip()
            govt_names.append(_normalize(store_name))

    kakao_names: list[str] = [
        _normalize(s.get("name", "")) for s in kakao_stores if s.get("name")
    ]
    ref_names = govt_names + kakao_names

    # 정규화 이름 → mention 그룹화
    groups: dict[str, list[dict]] = {}
    for item in extracted:
        key = _normalize(item.get("name", ""))
        if not key:
            continue
        groups.setdefault(key, []).append(item)

    confirmed: list[dict] = []
    pending:   list[dict] = []

    for norm_name, mentions in groups.items():
        rules_passed: list[str] = []

        # 규칙 A: 2개 이상 서로 다른 source URL에서 등장
        urls = {m.get("source_url", "") for m in mentions if m.get("source_url")}
        if len(urls) >= 2:
            rules_passed.append("multi_url")

        # 규칙 B: 2개 이상 서로 다른 source_intent에서 등장
        intents = {m.get("source_intent", "") for m in mentions if m.get("source_intent")}
        if len(intents) >= 2:
            rules_passed.append("multi_intent")

        # 규칙 C: 정부DB 또는 Kakao 매장명과 유사도 85% 이상
        if _fuzzy_match(norm_name, ref_names, threshold=85):
            rules_passed.append("govt_match")

        # 대표 mention (첫 번째 항목) + validation_rules 부착
        representative = dict(mentions[0])
        representative["validation_rules"] = rules_passed

        if len(rules_passed) >= 2:
            confirmed.append(representative)
        else:
            pending.append(representative)

    total = len(confirmed) + len(pending)
    coverage_rate = round(len(confirmed) / total, 4) if total > 0 else 0.0

    print(f"[validator] confirmed={len(confirmed)}, pending={len(pending)}, "
          f"coverage_rate={coverage_rate:.2%}")
    return {
        "confirmed":     confirmed,
        "pending":       pending,
        "coverage_rate": coverage_rate,
    }
