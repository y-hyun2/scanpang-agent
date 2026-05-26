"""
test_naver_tourist_hours.py

관광지/문화시설의 네이버 Apollo state에서 영업시간 관련 필드를 전부 덤프한다.
newBusinessHours.description 외에 전체 주간 스케줄을 담은 필드가 있는지 확인한다.

사용법:
    python scripts/test_naver_tourist_hours.py [장소명]
    python scripts/test_naver_tourist_hours.py "역삼도서관"
    python scripts/test_naver_tourist_hours.py "국립중앙박물관"
"""

import asyncio
import json
import re
import sys
import urllib.parse as _urlparse

import httpx

_UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)
_HTML_HEADERS = {
    "User-Agent": _UA,
    "Referer": "https://map.naver.com/",
    "Accept-Language": "ko-KR,ko;q=0.9",
}

_HOURS_KEYWORDS = (
    "businessHours", "openHours", "operationTime", "usetime",
    "description", "schedule", "hour", "open", "close", "time",
)


def _parse_apollo_state(html: str) -> dict:
    idx = html.find("__APOLLO_STATE__ =")
    if idx < 0:
        return {}
    raw = html[idx + len("__APOLLO_STATE__ = "):]
    brace_count = 0
    for i, c in enumerate(raw):
        if c == "{":
            brace_count += 1
        elif c == "}":
            brace_count -= 1
            if brace_count == 0:
                try:
                    return json.loads(raw[: i + 1])
                except Exception:
                    return {}
    return {}


def _resolve_apollo(apollo: dict, node) -> dict:
    if isinstance(node, dict) and "__ref" in node:
        return apollo.get(node["__ref"]) or {}
    return node or {}


def _extract_hours_fields(obj: dict, apollo: dict, prefix: str = "") -> dict:
    """객체에서 hours/time 관련 키를 재귀적으로 수집한다."""
    results = {}
    for k, v in obj.items():
        full_key = f"{prefix}.{k}" if prefix else k
        if any(kw.lower() in k.lower() for kw in _HOURS_KEYWORDS):
            resolved = _resolve_apollo(apollo, v) if isinstance(v, dict) else v
            results[full_key] = resolved
            # __ref가 풀린 경우 그 하위도 재귀
            if isinstance(resolved, dict) and "__ref" not in (v if isinstance(v, dict) else {}):
                sub = _extract_hours_fields(resolved, apollo, prefix=full_key)
                results.update(sub)
    return results


async def inspect_tourist_hours(query: str):
    print(f"\n{'='*60}")
    print(f"검색어: {query!r}")
    print(f"{'='*60}\n")

    async with httpx.AsyncClient(timeout=15, headers=_HTML_HEADERS, follow_redirects=True) as client:
        # 1. 검색 목록 페이지
        search_url = (
            "https://pcmap.place.naver.com/place/list"
            f"?query={_urlparse.quote(query)}&lang=ko"
        )
        resp = await client.get(search_url)
        apollo_list = _parse_apollo_state(resp.text)

        list_items = [
            (k, v) for k, v in apollo_list.items()
            if k.startswith("PlaceListBusinessesItem:")
        ]
        if not list_items:
            print("검색 결과 없음")
            return

        # 첫 번째 결과 사용
        item_key, item = list_items[0]
        place_id = item.get("id", "")
        print(f"place_id: {place_id}")
        print(f"name: {item.get('name', '')}")
        print(f"category: {item.get('category', '')}")
        print()

        # --- 목록 아이템의 hours 관련 필드 전부 출력 ---
        print("[ 목록 페이지 - hours 관련 필드 ]")
        hours_fields = _extract_hours_fields(item, apollo_list)
        if hours_fields:
            for k, v in hours_fields.items():
                print(f"  {k}: {json.dumps(v, ensure_ascii=False)}")
        else:
            print("  (없음)")

        # newBusinessHours 직접 확인
        nbh_raw = item.get("newBusinessHours")
        if nbh_raw:
            nbh = _resolve_apollo(apollo_list, nbh_raw)
            print(f"\n  [newBusinessHours resolved]")
            print(f"  {json.dumps(nbh, ensure_ascii=False, indent=2)}")

        # businessHours 직접 확인
        bh_raw = item.get("businessHours")
        if bh_raw:
            bh = _resolve_apollo(apollo_list, bh_raw)
            print(f"\n  [businessHours resolved]")
            print(f"  {json.dumps(bh, ensure_ascii=False, indent=2)}")

        # 목록 Apollo state 전체에서 hours 관련 최상위 키 탐색
        print("\n[ 목록 Apollo state - hours 관련 최상위 키 ]")
        for k in sorted(apollo_list.keys()):
            if any(kw.lower() in k.lower() for kw in _HOURS_KEYWORDS):
                print(f"  {k}: {json.dumps(apollo_list[k], ensure_ascii=False)[:200]}")

        # 목록 Apollo state 전체에서 place_id 포함 키 탐색
        print(f"\n[ 목록 Apollo state - place_id({place_id}) 포함 키 ]")
        for k in sorted(apollo_list.keys()):
            if place_id and place_id in k:
                val_str = json.dumps(apollo_list[k], ensure_ascii=False)
                print(f"  {k}: {val_str[:300]}")

        # 2. 상세 페이지
        if not place_id:
            print("\nplace_id 없음 — 상세 페이지 스킵")
            return

        detail_url = f"https://pcmap.place.naver.com/place/{place_id}/home"
        print(f"\n[ 상세 페이지: {detail_url} ]")
        resp2 = await client.get(detail_url)
        apollo_detail = _parse_apollo_state(resp2.text)

        # DetailBase 탐색
        base = {}
        base_key = ""
        for prefix in ("AttractionDetailBase", "PlaceDetailBase",
                       "AccommodationDetailBase", "RestaurantDetailBase",
                       "CafeDetailBase"):
            candidate_key = f"{prefix}:{place_id}"
            candidate = apollo_detail.get(candidate_key)
            if candidate:
                base = candidate
                base_key = candidate_key
                break

        print(f"  base key: {base_key or '(없음)'}")
        if base:
            print("\n  [ DetailBase - hours 관련 필드 ]")
            base_hours = _extract_hours_fields(base, apollo_detail)
            if base_hours:
                for k, v in base_hours.items():
                    print(f"    {k}: {json.dumps(v, ensure_ascii=False)}")
            else:
                print("    (없음)")

            # DetailBase 전체 키 목록 (값 제외)
            print(f"\n  [ DetailBase 전체 키 ]")
            print(f"    {sorted(base.keys())}")

        # 상세 Apollo state 전체에서 hours 관련 키 탐색
        print("\n[ 상세 Apollo state - hours 관련 키 ]")
        found_any = False
        for k in sorted(apollo_detail.keys()):
            if any(kw.lower() in k.lower() for kw in _HOURS_KEYWORDS):
                val_str = json.dumps(apollo_detail[k], ensure_ascii=False)
                print(f"  {k}: {val_str[:400]}")
                found_any = True
        if not found_any:
            print("  (없음)")

        # 상세 Apollo state 전체에서 place_id 포함 키
        print(f"\n[ 상세 Apollo state - place_id({place_id}) 포함 키 ]")
        for k in sorted(apollo_detail.keys()):
            if place_id and place_id in k:
                val_str = json.dumps(apollo_detail[k], ensure_ascii=False)
                print(f"  {k}: {val_str[:400]}")

        # 3. 목록 Apollo state에서 businessHours 관련 __ref 전부 수집
        print("\n[ 목록 Apollo state - BusinessHours 타입 키 ]")
        bh_keys = [k for k in apollo_list if "BusinessHours" in k or "businesshours" in k.lower()]
        if bh_keys:
            for k in sorted(bh_keys):
                print(f"  {k}: {json.dumps(apollo_list[k], ensure_ascii=False)[:400]}")
        else:
            print("  (없음)")

        # 4. 목록 Apollo state 전체에서 schedule-like 배열 탐색
        print("\n[ 목록 Apollo state - 배열 형태의 시간 관련 값 ]")
        for k, v in apollo_list.items():
            if isinstance(v, dict):
                for sub_k, sub_v in v.items():
                    if isinstance(sub_v, list) and any(
                        kw.lower() in sub_k.lower() for kw in _HOURS_KEYWORDS
                    ):
                        print(f"  {k}.{sub_k}: {json.dumps(sub_v, ensure_ascii=False)[:400]}")

        print("\n완료.")


if __name__ == "__main__":
    query = sys.argv[1] if len(sys.argv) > 1 else "역삼도서관"
    asyncio.run(inspect_tourist_hours(query))
