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
    naver_place,
    tour_api,
    seoul_openapi,
    seoul_metro,
    ecos,
    static_json,
)


# source 키 → fetch 함수 매핑.
# Phase 1에서는 kakao_basic만 실제 구현, 나머지는 stub (빈 dict 반환).
_FETCHER_BY_SOURCE: dict[str, Callable[..., Awaitable[dict]]] = {
    "naver_place":   naver_place.fetch,
    "tour_api":      tour_api.fetch,
    "kakao":         kakao_basic.fetch,
    "seoul_openapi": seoul_openapi.fetch,
    "seoul_metro":   seoul_metro.fetch,
    "ecos":          ecos.fetch,
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
    category_key 기준으로 적절한 fetcher를 순서대로 호출해 첫 비공식 결과를 반환.

    CATEGORY_SOURCES에 등록된 source 리스트를 순회하며,
    각 fetcher의 결과 dict에 의미있는 데이터(`source` 키 외에 무엇이라도)가
    있으면 채택. 모두 비어있으면 빈 dict.
    """
    sources = CATEGORY_SOURCES.get(category_key, ["kakao"])
    last_result: dict = {}
    for src in sources:
        fetcher = _FETCHER_BY_SOURCE.get(src)
        if fetcher is None:
            continue
        try:
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
        # source 키 외에 의미있는 필드가 채워졌으면 채택
        meaningful = {k: v for k, v in result.items() if k != "source" and v}
        if meaningful:
            return result
        last_result = result
    return last_result
