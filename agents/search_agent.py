"""
search_agent.py
매장·편의시설·할랄식당·기도실 등 위치 기반 검색 에이전트.
(이전 이름: convenience_agent — 책임 확장에 따라 개명)

동작:
  1. category 파라미터 있음 → LLM 스킵, 바로 검색
  2. message만 있음 → LLM으로 category + language 추출
  3. category별 라우팅 → 결과 정렬 → speech 생성
"""

import json

from dotenv import load_dotenv

from schemas.convenience import ConvenienceRequest, ConvenienceResponse, Facility
from tools.convenience_tools import (
    CATEGORY_CONFIG,
    DEFAULT_RADIUS,
    get_radius,
    kakao_category_search,
    kakao_keyword_search,
    prayer_room_search,
    public_restroom_search,
    seoul_locker_search,
)
from tools.halal_tools import halal_restaurant_search
from tools.llm_client import call_llm
from tools.open_hours_parser import is_open_now_combined

load_dotenv()

ALL_CATEGORIES = list(CATEGORY_CONFIG.keys()) + list(DEFAULT_RADIUS.keys()) + ["halal_restaurant"]

CATEGORY_EXTRACT_PROMPT = f"""
You are a facility query parser for a travel AR app.
Given a user message, return THREE things as JSON:

1) category — one of:
   convenience_store, cafe, restaurant, pharmacy, hospital,
   bank, atm, shopping, parking, subway, tourist, accommodation, cultural,
   exchange, restroom, locker, prayer_room, halal_restaurant
2) language — ko, en, ar, ja, zh
3) brand_keyword — the SPECIFIC name the user mentioned, if any.
   - If user mentions a specific brand/shop name (Starbucks, Olive Young, 스타벅스,
     올리브영, McDonald's, GS25, etc.), put that EXACT name as brand_keyword.
   - If user mentions a generic category only (카페, 식당, ATM, 화장실, etc.) and
     NO specific brand, return brand_keyword="".
   - For "halal restaurant" / "기도실" / "musalla" — these are categories, not
     brand names. brand_keyword="".

Use "halal_restaurant" category for halal/muslim food queries. Use "prayer_room"
for prayer rooms / musalla / 기도실.

Return JSON only:
{{"category": "<one of the above>", "language": "<ko|en|ar|ja|zh>", "brand_keyword": "<specific name or empty>"}}

Examples:
- "근처에 스타벅스 있나?" → {{"category": "cafe", "language": "ko", "brand_keyword": "스타벅스"}}
- "주변 카페 추천" → {{"category": "cafe", "language": "ko", "brand_keyword": ""}}
- "올리브영 어디" → {{"category": "shopping", "language": "ko", "brand_keyword": "올리브영"}}
- "Where is the nearest pharmacy?" → {{"category": "pharmacy", "language": "en", "brand_keyword": ""}}
- "Find Starbucks" → {{"category": "cafe", "language": "en", "brand_keyword": "Starbucks"}}
- "할랄 식당" → {{"category": "halal_restaurant", "language": "ko", "brand_keyword": ""}}

If uncertain, default category to "convenience_store", language to "ko", brand_keyword to "".
"""

SPEECH_PROMPT = """
You are a helpful AR navigation assistant.
Generate a concise spoken response (2-3 sentences) about the nearest facility.
Respond in the language specified by the 'language' field.
Include: facility name, distance, open hours (if available), and any notable info (wheelchair access, locker sizes, etc.).
Keep it natural and friendly for TTS.
"""


async def _extract_category_and_language(message: str, user_id: str = "") -> tuple[str, str, str]:
    """사용자 메시지 → (category, language, brand_keyword) 튜플.
    brand_keyword 가 비어있지 않으면 호출자는 카테고리 검색 대신 키워드 검색을 사용해야 한다.
    """
    content = await call_llm(
        user_id=user_id,
        purpose="conv_category",
        model="gpt-4o",
        temperature=0,
        messages=[
            {"role": "system", "content": CATEGORY_EXTRACT_PROMPT},
            {"role": "user", "content": message},
        ],
        max_tokens=80,
        response_format={"type": "json_object"},
    )
    result = json.loads(content)
    category = result.get("category", "convenience_store")
    language = result.get("language", "ko")
    brand_keyword = (result.get("brand_keyword") or "").strip()
    if category not in ALL_CATEGORIES:
        category = "convenience_store"
    return category, language, brand_keyword


async def _generate_speech(facilities: list[dict], category: str, language: str, user_id: str = "") -> str:
    if not facilities:
        messages = {
            "ko": f"주변 {category} 시설을 찾을 수 없습니다.",
            "en": f"No nearby {category} facilities found.",
            "ar": f"لا توجد施設 قريبة من نوع {category}.",
            "ja": f"近くに{category}施設が見つかりませんでした。",
            "zh": f"附近没有找到{category}设施。",
        }
        return messages.get(language, messages["en"])

    nearest = facilities[0]
    context = (
        f"Category: {category}\n"
        f"Nearest facility: {nearest['name']}\n"
        f"Distance: {nearest['distance_m']:.0f}m\n"
        f"Address: {nearest['address']}\n"
        f"Open hours: {nearest['open_hours'] or 'unknown'}\n"
        f"Extra info: {nearest['extra']}\n"
        f"Language: {language}\n"
        f"Total found: {len(facilities)} facilities"
    )
    return await call_llm(
        user_id=user_id,
        purpose="conv_speech",
        model="gpt-4o",
        temperature=0.3,
        messages=[
            {"role": "system", "content": SPEECH_PROMPT},
            {"role": "user", "content": context},
        ],
        max_tokens=150,
    )


async def run_search_agent(req: ConvenienceRequest, user_id: str = "") -> ConvenienceResponse:
    # Step 1: category + 브랜드 키워드 결정
    # message 안에 특정 상호명(스타벅스/올리브영 등) 이 있으면 brand_keyword 에 담겨오고
    # 호출자 단계에서 keyword_search 로 분기해 브랜드 매칭을 보존한다.
    # orchestrator 가 category 를 미리 결정해 보낸 경우(req.category 있음) 는 분류 LLM
    # 호출 스킵 — brand_keyword 도 빈 값으로 둔다.
    category = req.category.strip()
    language = req.language
    brand_keyword = ""

    if not category:
        if not req.message.strip():
            category = "convenience_store"
        else:
            category, language, brand_keyword = await _extract_category_and_language(
                req.message, user_id=user_id,
            )

    # Step 2: 반경 결정
    radius = get_radius(category, req.radius)

    # Step 3: 라우팅 — 브랜드 키워드가 있으면 Kakao 키워드 검색 우선.
    # 단 restroom/locker/prayer_room/halal_restaurant 처럼 전용 데이터소스를 쓰는
    # 카테고리는 키워드 검색으로 대체할 수 없으니 그대로 전용 경로 유지.
    _KAKAO_ROUTABLE = set(CATEGORY_CONFIG.keys()) | {"exchange"}
    if brand_keyword and category in _KAKAO_ROUTABLE:
        raw = await kakao_keyword_search(brand_keyword, req.lat, req.lng, radius)
        print(f"[search_agent] 브랜드 키워드 검색: {brand_keyword!r} (category={category})")
    elif category in CATEGORY_CONFIG:
        raw = await kakao_category_search(category, req.lat, req.lng, radius)
    elif category == "exchange":
        raw = await kakao_keyword_search("환전", req.lat, req.lng, radius)
    elif category == "restroom":
        raw = await public_restroom_search(req.lat, req.lng, radius)
    elif category == "locker":
        raw = await seoul_locker_search(req.lat, req.lng, radius)
    elif category == "prayer_room":
        raw = await prayer_room_search(req.lat, req.lng, radius)
    elif category == "halal_restaurant":
        # halal_restaurant_search 는 풍부한 필드(halal_type, cuisine_type 등) 반환 —
        # Facility 표준 키로 normalize 하고 나머진 extra 에 보존.
        halal_rows = await halal_restaurant_search(req.lat, req.lng, radius)
        raw = [
            {
                "name":       r.get("name_ko") or r.get("name_en") or "할랄 식당",
                "distance_m": r.get("distance_m", 0),
                "lat":        r.get("lat"),
                "lng":        r.get("lng"),
                "address":    r.get("address", ""),
                "phone":      r.get("phone", ""),
                "open_hours": r.get("opening_hours", ""),
                "extra": {
                    "halal_type":             r.get("halal_type", ""),
                    "muslim_cooks_available": r.get("muslim_cooks_available"),
                    "no_alcohol_sales":       r.get("no_alcohol_sales"),
                    "cuisine_type":           r.get("cuisine_type", []),
                    "menu_examples":          r.get("menu_examples", []),
                    "restaurant_id":          r.get("restaurant_id", ""),
                },
            }
            for r in halal_rows
        ]
    else:
        raw = []

    # Step 4: 거리순 정렬 → 상위 5개 + open_hours 기반 is_open_now 계산
    raw_sorted = sorted(raw, key=lambda x: x["distance_m"])[:5]
    for f in raw_sorted:
        f["is_open_now"] = is_open_now_combined(f.get("open_hours") or "", None)
    facilities = [Facility(**f) for f in raw_sorted]

    # Step 5: speech 생성
    speech = await _generate_speech(raw_sorted, category, language, user_id=user_id)

    return ConvenienceResponse(
        speech=speech,
        category=category,
        facilities=facilities,
        language=language,
    )
