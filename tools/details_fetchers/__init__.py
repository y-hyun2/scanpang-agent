"""
details_fetchers — 카테고리별 store_details 풍부도 fetcher.

각 fetcher는 동일한 인터페이스를 따른다:
    async def fetch(store_name: str, lat: float, lng: float,
                    building_ufid: str, category_name: str = "") -> dict

반환 dict 형식:
    {
        "open_hours":   str,
        "closed_days":  str,
        "homepage":     str,
        "image_urls":   list[str],
        "details":      dict,        # 카테고리별 가변 필드
        "source":       str,         # 어느 fetcher가 채웠는지
        "phone":        str,         # (선택)
        "category":     str,         # (선택) Kakao raw category_name
        "addr":         str,         # (선택)
    }

비어있는 키는 호출자가 기존 캐시값 / Kakao 기본정보로 보강한다.
"""

from typing import Awaitable, Callable

from tools.category_classifier import CATEGORY_SOURCES
from tools.details_fetchers import (
    kakao_basic,
    kakao_scraper,
    naver_place,
    tour_api,
    seoul_openapi,
    seoul_metro,
    koreaexim,
    static_json,
)


# source 키 → fetch 함수 매핑.
# kakao_scraper: Kakao Local API place_id 로 place.map.kakao.com 직접 진입 — 매칭 100%.
# naver_place : 매장명 검색 + entryIframe — 메뉴/편의시설/intro 풍부.
# kakao_basic : Kakao Local API 만 (영업시간 X) — 마지막 fallback.
_FETCHER_BY_SOURCE: dict[str, Callable[..., Awaitable[dict]]] = {
    "kakao_scraper": kakao_scraper.fetch,
    "naver_place":   naver_place.fetch,
    "tour_api":      tour_api.fetch,
    "kakao":         kakao_basic.fetch,
    "seoul_openapi": seoul_openapi.fetch,
    "tago_subway":   seoul_metro.fetch,
    "koreaexim":     koreaexim.fetch,
    "static_json":   static_json.fetch,
}


async def fetch_by_category(
    category_key: str,
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str,
    category_name: str = "",
) -> dict:
    """
    category_key 기준 fetcher 체인 호출 — **merge 보강 모드**.

    CATEGORY_SOURCES의 source 리스트를 순서대로 호출하되:
    1) 첫 의미있는 결과를 base로 채택 (source 필드는 이 base 것을 유지)
    2) 이후 source는 base에서 비어있는 필드만 보강 (덮어쓰지 않음)
    3) details 는 dict 단위로 머지(base 우선)

    설계 의도:
    - exchange/bank/atm 처럼 매장정보(naver_place) + 글로벌 환율(koreaexim) 두
      소스를 동시에 채워야 하는 카테고리를 위해 보강 패턴 도입.
    - 단일 source 카테고리(cafe=naver_place 등)는 동작 변화 없음.
    - 빈 source가 첫 번째로 호출되면 자연스럽게 fallback 동작도 유지됨.
    """
    sources = CATEGORY_SOURCES.get(category_key, ["kakao"])
    print(f"[details_fetchers] dispatch category_key={category_key!r} sources={sources}")
    merged: dict = {}
    for src in sources:
        fetcher = _FETCHER_BY_SOURCE.get(src)
        if fetcher is None:
            continue
        try:
            print(f"[details_fetchers] → trying {src}...")
            result = await fetcher(
                store_name=store_name,
                lat=lat,
                lng=lng,
                building_ufid=building_ufid,
                category_name=category_name,
            )
        except Exception as e:
            print(f"[details_fetchers] {src} 실패 ({store_name!r}): {e}")
            continue
        meaningful = {k: v for k, v in result.items() if k != "source" and v}
        if not meaningful:
            print(f"[details_fetchers] {src} 빈 결과 → 다음 fetcher 시도")
            continue
        print(f"[details_fetchers] ✓ {src} 성공 (fields={list(meaningful.keys())})")
        if not merged:
            merged = dict(result)
        else:
            # 빈 필드만 보강
            for k, v in result.items():
                if k == "source":
                    continue
                # addr 예외: naver_place 가 부가 정보 포함한 풀 주소
                # ("서울 중구 을지로 30 을지로입구역7,8번출구방면(롯데백화점 들어가서 우측)")
                # 를 주면 kakao 의 짧은 도로명("서울 중구 을지로 30") 위로 덮어쓰기.
                # 풀 주소가 짧은 주소를 prefix 로 포함하므로 길이 비교로 충분.
                if k == "addr" and v and isinstance(v, str):
                    base_addr = merged.get("addr") or ""
                    if len(v) > len(base_addr):
                        merged["addr"] = v
                    continue
                if not merged.get(k) and v:
                    merged[k] = v
            # details 는 dict 단위 머지 (base 우선)
            base_det = merged.get("details") or {}
            new_det = result.get("details") or {}
            if isinstance(base_det, dict) and isinstance(new_det, dict):
                merged["details"] = {**new_det, **base_det}
    return merged
