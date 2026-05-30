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

import re

# Kakao category_name에 들어가는 키워드 → category_key 매핑.
# 위에서부터 우선순위 — 첫 매치를 채택.
_CATEGORY_RULES: list[tuple[str, str]] = [
    # ── 음식·음료 ──
    ("카페",          "cafe"),
    ("커피",          "cafe"),
    ("디저트",        "cafe"),
    ("베이커리",      "cafe"),
    ("제과",          "cafe"),
    # 도넛/아이스크림/빙수 체인은 메뉴 결이 카페에 가까워 cafe로.
    # 던킨 = "음식점 > 간식 > 도넛" → "도넛" 매치.
    ("도넛",          "cafe"),
    ("아이스크림",    "cafe"),
    ("빙수",          "cafe"),
    # 패스트푸드/햄버거/치킨/피자 — "음식점" 룰만으로도 restaurant로 잡히지만,
    # 명시적으로 두면 분류가 더 견고.
    ("패스트푸드",    "restaurant"),
    ("햄버거",        "restaurant"),
    ("치킨",          "restaurant"),
    ("피자",          "restaurant"),
    ("음식점",        "restaurant"),
    ("한식",          "restaurant"),
    ("양식",          "restaurant"),
    ("일식",          "restaurant"),
    ("중식",          "restaurant"),
    ("아시아음식",    "restaurant"),
    ("분식",          "restaurant"),
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
    ("금융서비스",    "bank"),
    ("금융",          "bank"),
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
    ("공연",          "cultural"),  # "공연장", "공연,연극" 모두 cover
    ("연극",          "cultural"),
    ("영화",          "cultural"),  # "영화,영상" 등
    ("영상",          "cultural"),
    ("박물관",        "cultural"),
    ("미술관",        "cultural"),
    ("전시관",        "cultural"),
    ("문화시설",      "cultural"),
    ("도서관",        "cultural"),
    ("종교",          "cultural"),  # 명동성당 등 종교시설
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
    ("드럭스토어",    "shopping"),
    ("잡화",          "shopping"),
]


# 매장명 strong override — category_name보다 우선 판정.
# Kakao Local이 카테고리를 모호하게 주는 카테고리(환전소→"금융서비스", 화장실→없음,
# 지하철역→없음)는 매장명으로 우선 잡아야 정확함.
_STRONG_NAME_RULES: list[tuple[str, str]] = [
    ("기도실",        "prayer_room"),
    ("공중화장실",    "restroom"),
    ("화장실",        "restroom"),
    ("물품보관함",    "locker"),
    ("물품보관",      "locker"),
    ("락커",          "locker"),
    ("ATM",           "atm"),
    ("외환",          "exchange"),
    ("환전",          "exchange"),
    ("지하철역",      "subway"),
    ("CGV",           "cultural"),
    ("롯데시네마",    "cultural"),
    ("메가박스",      "cultural"),
    # 의료 — "톤즈의원 명동역" 같이 의원/병원 + 인근역 표기 매장이 subway 로 잘못
    # 분류되던 버전 대응. _STATION_PATTERN 분기보다 먼저 잡혀야 함.
    ("의원",          "hospital"),
    ("병원",          "hospital"),
    ("약국",          "pharmacy"),
    # 편의점 체인 — Kakao 매칭 실패 시 다른 분류로 빠지면 곤란
    ("CU ",           "convenience_store"),
    ("씨유",          "convenience_store"),  # 한글 — '씨유용인외대' 케이스
    ("GS25",          "convenience_store"),
    ("세븐일레븐",    "convenience_store"),
    ("이마트24",      "convenience_store"),
    ("미니스톱",      "convenience_store"),
]

# 지하철역 매장명 패턴 — "X역" 단독 또는 "X역 N호선" 변형.
# 예: "명동역", "을지로입구역", "명동역 4호선", "시청역 2호선"
_STATION_PATTERN = re.compile(r"역(\s+\d+호선)?$")

# false positive 단어 — 마지막이 "X역" 이지만 지하철역이 아님.
# "X무역"/"X여행"/"X교역" 매장이 모두 subway 로 잘못 분류되던 버그 방지.
# 주의: "구역" 빼야 함 — "을지로입구역" false negative 방지.
_NON_STATION_KEYWORDS = re.compile(
    r"(무역|여행|통역|병역|수역|전역|분역|영역|권역|"
    r"번역|면역|반역|급역|음역|투역|교역|운영)$"
)


def classify_category(category_name: str, store_name: str = "") -> str:
    """
    카테고리 키 분류 — 3단계 우선순위.

    1차 (STRONG): 매장명에 환전/화장실/ATM/지하철역/기도실/물품보관/CGV/편의점 체인명
                 → 즉시 결정. category_name이 모호한 경우(예: 환전소가 "금융서비스"로
                 옴) 매장명이 더 정확한 신호라 1순위.
    2차: "{역명}역" suffix → subway.
    3차: Kakao category_name 키워드 매칭 (_CATEGORY_RULES).
    4차: category_name 없을 때 매장명 다른 키워드 fallback.

    Returns:
        'cafe' | 'restaurant' | 'tourist' | 'cultural' | 'shopping' |
        'convenience_store' | 'pharmacy' | 'hospital' | 'bank' | 'atm' |
        'exchange' | 'subway' | 'restroom' | 'locker' | 'prayer_room' |
        'accommodation' | 'other'
    """
    # 1차 STRONG: 매장명 override
    if store_name:
        for keyword, key in _STRONG_NAME_RULES:
            if keyword in store_name:
                return key
        # 2차: 지하철역 매장명 패턴 (을지로입구역, 명동역, 명동역 4호선, 시청역 2호선)
        # 단, '여행/무역/통역/교역' 등 false positive 방어. 매장명에 의원/병원/약국
        # 키워드는 위 _STRONG_NAME_RULES 가 먼저 잡았으므로 여기 도달 X.
        if (len(store_name) <= 16
            and _STATION_PATTERN.search(store_name)
            and not _NON_STATION_KEYWORDS.search(store_name)):
            return "subway"

    # 3차: category_name 기반
    if category_name:
        for keyword, key in _CATEGORY_RULES:
            if keyword in category_name:
                return key

    # 4차: 매장명에 _CATEGORY_RULES 키워드 — 정부 LocalData 업태 분류('기타 간이/
    # 비알코올/종합 소매') 처럼 룰에 없는 category_name 이 들어왔을 때 매장명에서
    # 신호 찾는다. 예: '비에치씨치킨' 매장명 → '치킨' → restaurant.
    if store_name:
        for keyword, key in _CATEGORY_RULES:
            if keyword in store_name:
                return key

    return "other"


# Phase 2에서 사용 예정 — 각 category_key별 데이터 출처 우선순위 표.
# 디스패처가 이 표를 참조해 적절한 fetcher 함수를 호출한다.
CATEGORY_SOURCES: dict[str, list[str]] = {
    # kakao_scraper 를 1순위로 두는 이유:
    #  - Kakao Local API 가 좌표 기반 결과를 주고, 그 place_id 로 페이지를 직접
    #    진입하므로 매장 매칭이 100% 보장됨(naver_place 의 fuzzy 매칭 거절률 문제 해소).
    #  - 영업시간/홈페이지/주소/전화/이미지 같은 핵심 필드 다 잡힘.
    # naver_place 를 2순위로 두는 이유:
    #  - 메뉴/편의시설(택배·반값택배)/intro 같은 풍부도는 naver 만 노출.
    # dispatcher 가 merge 모드라 kakao base 위에 빈 필드(특히 details.menu/
    # details.conveniences/details.intro)만 naver 가 보강한다.
    # tour_api 는 관광지/문화시설의 admission_fee/description_en/parking_info 등
    # 정부 TourAPI 만 주는 정보 보강용. rag.build_place_db 의 ML 의존성을 lazy 로
    # 분리해 import 부담 제거.
    "tourist":           ["kakao_scraper", "naver_place", "tour_api"],
    "cultural":          ["kakao_scraper", "naver_place", "tour_api"],
    "shopping":          ["kakao_scraper", "naver_place"],
    "restaurant":        ["kakao_scraper", "naver_place"],
    "accommodation":     ["kakao_scraper", "naver_place", "tour_api"],
    "cafe":              ["kakao_scraper", "naver_place"],
    "convenience_store": ["kakao_scraper", "naver_place"],
    "pharmacy":          ["kakao_scraper", "naver_place"],
    "hospital":          ["kakao_scraper", "naver_place"],
    # 은행/ATM/환전소: kakao + naver 매장정보 + koreaexim 환율 — 3-way merge.
    "bank":              ["kakao_scraper", "naver_place", "koreaexim"],
    "atm":               ["kakao_scraper", "naver_place", "koreaexim"],
    "exchange":          ["kakao_scraper", "naver_place", "koreaexim"],
    "subway":            ["tago_subway"],   # 국토교통부 TAGO 지하철정보
    "restroom":          ["seoul_openapi"], # OA-22586
    "locker":            ["seoul_openapi"], # OA-22731
    "prayer_room":       ["static_json"],   # prayer_rooms.json
    "other":             ["kakao_scraper", "naver_place"],
}


# 검색바 쿼리 → category_key. 한국어 카테고리명("카페"/"화장실")을 직접 분류한다.
# classify_category 는 매장명+Kakao category_name 기반이라 단순 "카페" 같은
# 일반어를 'other' 로 떨어뜨리는 한계가 있어서 검색 진입점에 별도 매핑.
_QUERY_KEYWORD_TO_CATEGORY: list[tuple[str, str]] = [
    # outdoor 카테고리(별도 출처)
    ("공중화장실",      "restroom"),
    ("화장실",          "restroom"),
    ("restroom",        "restroom"),
    ("toilet",          "restroom"),
    ("bathroom",        "restroom"),
    ("물품보관함",      "locker"),
    ("물품보관",        "locker"),
    ("락커",            "locker"),
    ("locker",          "locker"),
    ("luggage",         "locker"),
    ("기도실",          "prayer_room"),
    ("prayer room",     "prayer_room"),
    ("prayer",          "prayer_room"),
    ("mosque",          "prayer_room"),
    ("할랄 식당",       "halal_restaurant"),
    ("할랄식당",        "halal_restaurant"),
    ("할랄",            "halal_restaurant"),
    ("halal",           "halal_restaurant"),
    ("비건 카페",       "vegan_cafe"),
    ("비건카페",        "vegan_cafe"),
    ("채식 카페",       "vegan_cafe"),
    ("채식카페",        "vegan_cafe"),
    ("vegan cafe",      "vegan_cafe"),
    ("비건 식당",       "vegan_restaurant"),
    ("비건식당",        "vegan_restaurant"),
    ("비건",            "vegan_restaurant"),
    ("채식 식당",       "vegan_restaurant"),
    ("채식식당",        "vegan_restaurant"),
    ("채식",            "vegan_restaurant"),
    ("vegan",           "vegan_restaurant"),
    ("vegetarian",      "vegan_restaurant"),
    ("지하철역",        "subway"),
    ("지하철",          "subway"),
    ("subway",          "subway"),
    ("metro",           "subway"),
    # 건물 내 매장
    ("관광지",          "tourist"),
    ("관광",            "tourist"),
    ("명소",            "tourist"),
    ("tourist",         "tourist"),
    ("attraction",      "tourist"),
    ("landmark",        "tourist"),
    ("문화시설",        "cultural"),
    ("영화관",          "cultural"),
    ("박물관",          "cultural"),
    ("미술관",          "cultural"),
    ("cinema",          "cultural"),
    ("museum",          "cultural"),
    ("gallery",         "cultural"),
    ("theater",         "cultural"),
    ("theatre",         "cultural"),
    ("환전소",          "exchange"),
    ("환전",            "exchange"),
    ("exchange",        "exchange"),
    ("currency",        "exchange"),
    ("ATM",             "atm"),
    ("은행",            "bank"),
    ("bank",            "bank"),
    ("병원",            "hospital"),
    ("의원",            "hospital"),
    ("hospital",        "hospital"),
    ("clinic",          "hospital"),
    ("약국",            "pharmacy"),
    ("pharmacy",        "pharmacy"),
    ("drugstore",       "pharmacy"),
    ("편의점",          "convenience_store"),
    ("convenience store", "convenience_store"),
    ("convenience",     "convenience_store"),
    ("호텔",            "accommodation"),
    ("숙박",            "accommodation"),
    ("hotel",           "accommodation"),
    ("hostel",          "accommodation"),
    ("guesthouse",      "accommodation"),
    ("guest house",     "accommodation"),
    ("백화점",          "shopping"),
    ("쇼핑몰",          "shopping"),
    ("쇼핑",            "shopping"),
    ("shopping",        "shopping"),
    ("mall",            "shopping"),
    ("식당",            "restaurant"),
    ("음식점",          "restaurant"),
    ("restaurant",      "restaurant"),
    ("food",            "restaurant"),
    ("dining",          "restaurant"),
    ("카페",            "cafe"),
    ("커피",            "cafe"),
    ("디저트",          "cafe"),
    ("베이커리",        "cafe"),
    ("cafe",            "cafe"),
    ("coffee",          "cafe"),
    ("bakery",          "cafe"),
    ("dessert",         "cafe"),
]


def classify_query(query: str) -> str:
    """검색바 입력 → category_key.

    키워드가 공백으로 분리된 독립 단어일 때만 카테고리 검색으로 분류한다.
    '행복약국'처럼 매장명 안에 카테고리 단어가 붙어 있는 경우(독립 단어 아님)는
    'other'를 반환해 이름 ILIKE 검색만 수행한다.
    """
    q = (query or "").strip()
    for kw, key in _QUERY_KEYWORD_TO_CATEGORY:
        # 단어 경계 매칭 — 앞뒤가 공백(또는 문자열 시작/끝)일 때만 매칭.
        # IGNORECASE: 영어 모드에서 "Cafe", "Tourist Spot" 등 대소문자 혼용 처리.
        if re.search(r"(?<!\S)" + re.escape(kw) + r"(?!\S)", q, re.IGNORECASE):
            return key
    return "other"
