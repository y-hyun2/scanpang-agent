"""
category_classifier.py
Kakao Local API의 `category_name` 문자열을 store_details.category_key로 매핑한다.

Kakao 카테고리 예시:
- "음식점 > 카페 > 커피전문점"      → "cafe"
- "음식점 > 한식 > 국밥"             → "restaurant"
- "여행 > 관광,명소 > 전망대"        → "tourist"
- "의료,건강 > 병원 > 종합병원"      → "hospital"
- "금융,보험 > 은행 > KB국민은행"    → "bank"
- "생활,편의 > 편의점"                → "convenience_store"
"""

# Kakao category_name에 들어가는 키워드 → category_key 매핑.
# 위에서부터 우선순위 — 첫 매치를 채택.
_CATEGORY_RULES: list[tuple[str, str]] = [
    # ── 음식·음료 ──
    ("카페",          "cafe"),
    ("커피",          "cafe"),
    ("디저트",        "cafe"),
    ("베이커리",      "cafe"),
    ("제과",          "cafe"),
    ("음식점",        "restaurant"),
    ("한식",          "restaurant"),
    ("양식",          "restaurant"),
    ("일식",          "restaurant"),
    ("중식",          "restaurant"),
    ("아시아음식",    "restaurant"),
    ("뷔페",          "restaurant"),
    # ── 의료 ──
    ("약국",          "pharmacy"),
    ("병원",          "hospital"),
    ("의원",          "hospital"),
    ("치과",          "hospital"),
    ("한의원",        "hospital"),
    # ── 금융 ──
    ("환전",          "exchange"),
    ("은행",          "bank"),
    ("ATM",           "atm"),
    ("현금인출",      "atm"),
    # ── 교통 ──
    ("지하철",        "subway"),
    ("지하철역",      "subway"),
    # ── 편의시설(공공) ──
    ("화장실",        "restroom"),
    ("물품보관",      "locker"),
    ("기도실",        "prayer_room"),
    # ── 숙박 ──
    ("호텔",          "accommodation"),
    ("숙박",          "accommodation"),
    ("게스트하우스",  "accommodation"),
    ("모텔",          "accommodation"),
    # ── 관광·문화 ──
    ("공연장",        "cultural"),
    ("박물관",        "cultural"),
    ("미술관",        "cultural"),
    ("전시관",        "cultural"),
    ("문화시설",      "cultural"),
    ("도서관",        "cultural"),
    ("관광",          "tourist"),
    ("명소",          "tourist"),
    ("전망대",        "tourist"),
    # ── 쇼핑 (마지막에 둠 — 다른 카테고리에서 안 잡힌 매장 흡수) ──
    ("편의점",        "convenience_store"),
    ("백화점",        "shopping"),
    ("쇼핑몰",        "shopping"),
    ("쇼핑",          "shopping"),
    ("유통",          "shopping"),
    ("패션",          "shopping"),
    ("의류",          "shopping"),
    ("신발",          "shopping"),
    ("화장품",        "shopping"),
    ("잡화",          "shopping"),
]


def classify_category(category_name: str) -> str:
    """
    Kakao category_name → category_key. 매칭 실패 시 'other'.

    Args:
        category_name: Kakao Local API의 category_name 필드 또는
                       store_details.category 컬럼.
    Returns:
        'cafe' | 'restaurant' | 'tourist' | 'cultural' | 'shopping' |
        'convenience_store' | 'pharmacy' | 'hospital' | 'bank' | 'atm' |
        'exchange' | 'subway' | 'restroom' | 'locker' | 'prayer_room' |
        'accommodation' | 'other'
    """
    if not category_name:
        return "other"
    for keyword, key in _CATEGORY_RULES:
        if keyword in category_name:
            return key
    return "other"


# Phase 2에서 사용 예정 — 각 category_key별 데이터 출처 우선순위 표.
# 디스패처가 이 표를 참조해 적절한 fetcher 함수를 호출한다.
CATEGORY_SOURCES: dict[str, list[str]] = {
    "tourist":           ["naver_place", "tour_api"],
    "cultural":          ["naver_place", "tour_api"],
    "shopping":          ["naver_place", "tour_api"],
    "restaurant":        ["naver_place", "tour_api"],
    "accommodation":     ["naver_place", "tour_api"],
    "cafe":              ["naver_place"],
    "convenience_store": ["kakao"],
    "pharmacy":          ["kakao"],
    "hospital":          ["kakao"],
    "bank":              ["kakao"],
    "atm":               ["kakao"],
    "exchange":          ["ecos"],          # 한국은행 환율 API
    "subway":            ["seoul_metro"],   # 서울교통공사 OpenAPI
    "restroom":          ["seoul_openapi"], # OA-22586
    "locker":            ["seoul_openapi"], # OA-22731
    "prayer_room":       ["static_json"],   # prayer_rooms.json
    "other":             ["kakao"],
}
