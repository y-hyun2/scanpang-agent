"""
query_builder.py
건물 메타 + Kakao 반경 매장 목록을 받아 네이버/카카오 검색 쿼리 스펙을 생성한다.
LLM 없이 순수 파이썬 문자열 처리.
"""


# 알려진 건물별 공식 도메인 (site: 연산자용)
_OFFICIAL_DOMAINS: dict[str, str] = {
    "롯데백화점":   "lotteshopping.com",
    "신세계백화점": "shinsegae.com",
    "현대백화점":   "hyundaidept.com",
    "눈스퀘어":     "nunsquare.com",
    "CGV":          "cgv.co.kr",
    "명동성당":     "mdsd.or.kr",
}


def _find_official_domain(bld_nm: str) -> str:
    for keyword, domain in _OFFICIAL_DOMAINS.items():
        if keyword in bld_nm:
            return domain
    return ""


def build_queries(building: dict, kakao_stores: list[dict]) -> list[dict]:
    """
    건물 메타와 Kakao 반경 매장 목록으로 검색 쿼리 스펙을 생성한다.

    Args:
        building: VWorld 건물 메타 dict (bld_nm, center_lat, center_lng, ...)
        kakao_stores: collect_stores_by_radius() 반환값

    Returns:
        [{"query": str, "intent": str, "engine": str}]
        engine: "naver_web" | "naver_blog" | "kakao_web"
    """
    bld_nm = (building.get("bld_nm") or "").strip()
    queries: list[dict] = []

    if bld_nm:
        # 건물명이 있는 경우: 5개 의도별 쿼리
        queries.append({"query": f"{bld_nm} 입점매장",   "intent": "official_directory", "engine": "naver_web"})
        queries.append({"query": f"{bld_nm} 층별안내",   "intent": "floor_layout",       "engine": "naver_web"})
        queries.append({"query": f"{bld_nm} 다녀왔어요", "intent": "recent_visit",        "engine": "naver_blog"})
        queries.append({"query": f"{bld_nm} 매장",       "intent": "supplement",          "engine": "kakao_web"})

        domain = _find_official_domain(bld_nm)
        if domain:
            queries.append({
                "query":  f"site:{domain} {bld_nm} 층",
                "intent": "official_site",
                "engine": "naver_web",
            })
    else:
        # 건물명 없는 경우: Kakao 반경 검색으로 잡힌 매장별 쿼리
        # 주소 기반으로 동네 컨텍스트 확보
        road_addr = ""
        if kakao_stores:
            road_addr = kakao_stores[0].get("address", "")
            # 도로명에서 동/로/길 단위까지만 추출 (번지 이하 제거)
            parts = road_addr.split()
            if len(parts) >= 2:
                road_addr = " ".join(parts[:2])

        seen_names: set[str] = set()
        for store in kakao_stores[:10]:  # 최대 10개 매장만
            name = store.get("name", "").strip()
            if not name or name in seen_names:
                continue
            seen_names.add(name)

            if road_addr:
                queries.append({
                    "query":  f"{name} {road_addr}",
                    "intent": "recent_visit",
                    "engine": "naver_blog",
                })
            queries.append({
                "query":  f"{name} 위치",
                "intent": "supplement",
                "engine": "kakao_web",
            })

    return queries
