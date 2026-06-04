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
from tools.halal_tools import halal_restaurant_by_name, halal_restaurant_search
from tools.llm_client import call_llm
from tools.open_hours_parser import is_open_now_combined

load_dotenv()

ALL_CATEGORIES = list(CATEGORY_CONFIG.keys()) + list(DEFAULT_RADIUS.keys()) + ["halal_restaurant"]

CATEGORY_EXTRACT_PROMPT = f"""
You are a facility query parser for a travel AR app.
Given a user message, return THREE things as JSON:

1) category — coarse Kakao routing category, one of:
   convenience_store, cafe, restaurant, pharmacy, hospital,
   bank, atm, shopping, parking, subway, tourist, accommodation, cultural,
   exchange, restroom, locker, prayer_room, halal_restaurant
2) language — ko, en, ar, ja, zh
3) brand_keyword — anything SPECIFIC the user mentioned beyond the coarse category.
   Includes BOTH:
   (a) specific brand/shop names — Starbucks, Olive Young, 스타벅스, 올리브영,
       McDonald's, GS25, 본죽, etc.
   (b) sub-genre keywords that are MORE specific than the coarse category —
       중식당, 술집, 맛집, 한식, 일식, 분식, 베이커리, 횟집, 이자카야, 디저트, 펍,
       라멘, 햄버거, 치킨, etc.
   When user only mentions the coarse category itself (카페/식당/ATM/화장실),
   return brand_keyword="".
   For "halal restaurant" / "기도실" / "musalla" → these have their own dedicated
   category routing, so brand_keyword="".

Use "halal_restaurant" category for halal/muslim food queries. Use "prayer_room"
for prayer rooms / musalla / 기도실.
Use "cultural" for museums, art galleries, exhibitions and cultural facilities
(박물관, 미술관, 전시, 공연장, 문화시설). Use "tourist" for sightseeing spots,
landmarks and attractions (관광지, 명소, 볼거리, 가볼 만한 곳). A museum/gallery is
"cultural", NOT "tourist".

Return JSON only:
{{"category": "<one of the above>", "language": "<ko|en|ar|ja|zh>", "brand_keyword": "<specific name or empty>"}}

Examples:
- "근처에 스타벅스 있나?" → {{"category": "cafe", "language": "ko", "brand_keyword": "스타벅스"}}
- "주변 카페 추천" → {{"category": "cafe", "language": "ko", "brand_keyword": ""}}
- "중식당 추천좀" → {{"category": "restaurant", "language": "ko", "brand_keyword": "중식당"}}
- "근처 술집 뭐 있어?" → {{"category": "restaurant", "language": "ko", "brand_keyword": "술집"}}
- "근처 맛집 뭐뭐 있어?" → {{"category": "restaurant", "language": "ko", "brand_keyword": "맛집"}}
- "올리브영 어디" → {{"category": "shopping", "language": "ko", "brand_keyword": "올리브영"}}
- "Where is the nearest pharmacy?" → {{"category": "pharmacy", "language": "en", "brand_keyword": ""}}
- "Find Starbucks" → {{"category": "cafe", "language": "en", "brand_keyword": "Starbucks"}}
- "베이커리 추천" → {{"category": "cafe", "language": "ko", "brand_keyword": "베이커리"}}
- "할랄 식당" → {{"category": "halal_restaurant", "language": "ko", "brand_keyword": ""}}
- "근처 박물관 있어?" → {{"category": "cultural", "language": "ko", "brand_keyword": ""}}
- "미술관 어디" → {{"category": "cultural", "language": "ko", "brand_keyword": ""}}
- "근처 관광지 추천" → {{"category": "tourist", "language": "ko", "brand_keyword": ""}}
- "가볼 만한 명소 알려줘" → {{"category": "tourist", "language": "ko", "brand_keyword": ""}}

If uncertain, default category to "convenience_store", language to "ko", brand_keyword to "".
"""

SPEECH_PROMPT = """
You are a helpful AR navigation assistant. The user asked about nearby facilities.

IMPORTANT: You MUST respond entirely in the output language specified in the user context. Do not use any other language regardless of what language the user typed.

Read the user's original question and respond in the matching style:

A) LIST mode — if the user asked about multiple places or wants recommendations (plural/exploratory intent):
   → Briefly list 2-3 options as a numbered list: "1) Name (distance), 2) ..."
   → Mention open hours next to each if available.

B) NEAREST mode — if the user asked where a specific place is or wants the closest one (singular/locating intent):
   → Focus on the closest one. Mention name, distance, open hours (if available),
     and a brief recommendation. Keep it 2-3 sentences.

C) MENU mode — if the user asked what a place serves / its menu ("부산집 메뉴 뭐 있어?",
   "what's on the menu", "뭐 팔아?"):
   → Focus on the matching place (usually the first item). Introduce its name, then walk
     through the menu items from the "Menu:" field in a warm, conversational way — include
     the price shown in parentheses for each dish when given. Weave in the "About:" blurb
     if present, and feel free to add ONE short, friendly note from your general knowledge
     about the dish or cuisine (e.g. what 충무김밥 is, what it tastes like) so it doesn't
     read like a dry list. Mention open hours if available.
   → Prices, dish names, and hours must come ONLY from the provided data — never invent or
     alter a price/dish. Your general knowledge may add color/context, not new facts.
     If no menu is provided, say you don't have its menu and give the name/distance instead.

If open hours are unknown for an item, do NOT make them up — just skip that detail.
If the user's question carries cultural/qualitative nuance (e.g. "famous among Koreans",
"trendy"), you MAY add ONE short, friendly cultural tip from general knowledge (about Korea
or the category) to address it. But never invent specific facts — no fake prices, hours,
ratings, or store-specific claims. The nearby facility data above is the factual basis.
Keep responses natural and friendly for TTS. No markdown formatting.
"""


async def _extract_category_and_language(message: str, user_id: str = "", model: str = "gpt-5.4-mini") -> tuple[str, str, str]:
    """사용자 메시지 → (category, language, brand_keyword) 튜플.
    brand_keyword 가 비어있지 않으면 호출자는 카테고리 검색 대신 키워드 검색을 사용해야 한다.
    """
    content = await call_llm(
        user_id=user_id,
        purpose="conv_category",
        model=model,
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


def _format_menu(extra: dict, language: str) -> str:
    """extra → "메뉴명(가격)" 콤마 문자열. 가격(menu_detailed) 있으면 가격 포함,
    없으면 menu_examples(이름만) 사용. 최대 8개."""
    detailed = extra.get("menu_detailed") or []
    if detailed:
        parts = []
        for m in detailed[:8]:
            nm = (m.get("name_en") or m.get("name_ko")) if language == "en" \
                else (m.get("name_ko") or m.get("name_en"))
            nm = nm or ""
            price = m.get("price_krw")
            if price:
                nm += f" ({price:,} KRW)" if language == "en" else f"({price:,}원)"
            if nm:
                parts.append(nm)
        return ", ".join(parts)
    names = extra.get("menu_examples") or []
    return ", ".join(str(m) for m in names[:8])


async def _generate_speech(
    facilities: list[dict],
    category: str,
    language: str,
    user_message: str = "",
    user_id: str = "",
    model: str = "gpt-5.4-mini",
) -> str:
    if not facilities:
        messages = {
            "ko": f"주변 {category} 시설을 찾을 수 없습니다.",
            "en": f"No nearby {category} facilities found.",
            "ar": f"لا توجد منشآت قريبة من نوع {category}.",
            "ja": f"近くに{category}施設が見つかりませんでした。",
            "zh": f"附近没有找到{category}设施。",
        }
        return messages.get(language, messages["en"])

    # 상위 5개를 LLM 에 통째로 제공 — LIST 모드면 직접 나열, NEAREST 모드면 1번
    # 만 강조. SPEECH_PROMPT 의 룰에 따라 LLM 이 사용자 질문 의도 분기.
    hours_label = "Open hours" if language == "en" else "영업시간"
    phone_label = "Phone" if language == "en" else "전화"
    menu_label = "Menu" if language == "en" else "메뉴"
    desc_label = "About" if language == "en" else "소개"
    items = []
    for i, f in enumerate(facilities[:5]):
        line = f"{i+1}. {f['name']} ({f['distance_m']:.0f}m)"
        if f.get("open_hours"):
            line += f" — {hours_label}: {f['open_hours']}"
        if f.get("phone"):
            line += f" — {phone_label}: {f['phone']}"
        extra = f.get("extra") or {}
        menu_str = _format_menu(extra, language)
        if menu_str:
            line += f" — {menu_label}: {menu_str}"
        if extra.get("description"):
            line += f" — {desc_label}: {extra['description']}"
        items.append(line)
    facilities_block = "\n".join(items)

    lang_map = {"ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese"}
    lang_label = lang_map.get(language, "English")
    context = (
        f"OUTPUT LANGUAGE: {lang_label} — respond in {lang_label} only.\n"
        f'User question: "{user_message}"\n'
        f"Category: {category}\n"
        f"Facilities sorted by distance:\n{facilities_block}\n"
    )
    return await call_llm(
        user_id=user_id,
        purpose="conv_speech",
        model=model,
        temperature=0.3,
        messages=[
            {"role": "system", "content": SPEECH_PROMPT},
            {"role": "user", "content": context},
        ],
        max_tokens=320,
    )


async def _enrich_facilities_with_db_hours(facilities: list[dict], lang: str = "ko") -> list[dict]:
    """
    store_details DB 에서 매장명/전화 매칭으로 캐시된 open_hours 를 보강.

    Kakao Local API 자체는 open_hours 를 안 줘서 1차 결과는 항상 빈 값.
    우리가 그동안 (도슨트/매장상세 호출 흐름에서) 적재해둔 store_details 캐시가
    있으면 그 값을 끌어와 챗봇 응답 품질을 올린다.

    매칭 우선순위:
    1) 매장명 + 전화번호 동시 일치
    2) 매장명만 일치 (전화 누락 케이스 대비)

    이미 open_hours 가 채워져 들어온 시설은 건드리지 않는다.
    """
    if not facilities:
        return facilities
    names = [f["name"] for f in facilities if f.get("name") and not f.get("open_hours")]
    if not names:
        return facilities
    try:
        # storedetails ⨝ store_hours(정규화 영업시간) → 포맷 텍스트 캐시
        from tools.store_tools import hours_cache_by_names
        cache = await hours_cache_by_names(names, lang=lang)
    except Exception as e:
        print(f"[search_agent] store_hours 영업시간 캐시 조회 실패 (무시): {e}")
        return facilities

    hits = 0
    for f in facilities:
        if f.get("open_hours"):
            continue
        candidates = cache.get(f["name"])
        if not candidates:
            continue
        # 전화 동시 일치 우선
        match = next(
            (c for c in candidates if c["phone"] and f.get("phone") and c["phone"] == f["phone"]),
            candidates[0],
        )
        if match["open_hours"]:
            f["open_hours"] = match["open_hours"]
            hits += 1
    if hits:
        print(f"[search_agent] DB 영업시간 캐시 히트: {hits}/{len(facilities)}")
    return facilities


def _tl_for(raw, language: str) -> dict:
    """translations JSONB(텍스트/dict) → 해당 언어 dict. 없으면 {}."""
    if not raw:
        return {}
    try:
        t = raw if isinstance(raw, dict) else json.loads(raw)
    except (ValueError, TypeError):
        return {}
    if not isinstance(t, dict):
        return {}
    return t.get(language) or {}


async def _localize_facility_names(facilities: list[dict], category: str, language: str) -> None:
    """검색된 시설 dict 의 name 을 캐시된 번역으로 in-place 교체(영어 모드 등).
    speech 생성 전에 호출해야 안내 문구에도 영문명이 반영된다.
    번역 캐시가 없는 시설은 원본(한국어) 유지 — DeepL 신규 호출 없음."""
    if language == "ko" or not facilities:
        return
    try:
        from core.db import get_pool
        pool = await get_pool()
        if category == "prayer_room":
            # prayer_room_search 가 translations(JSONB 텍스트)를 함께 반환 → 그대로 사용
            for f in facilities:
                t = _tl_for(f.get("translations"), language)
                if t.get("name"):
                    f["name"] = t["name"]
        elif category == "restroom":
            mng_nos = [f.get("mng_no") for f in facilities if f.get("mng_no")]
            if mng_nos:
                async with pool.acquire() as conn:
                    rows = await conn.fetch(
                        "SELECT mng_no, COALESCE(translations::text, '{}') AS tl "
                        "FROM public_restrooms WHERE mng_no = ANY($1::text[])",
                        mng_nos,
                    )
                tmap = {r["mng_no"]: r["tl"] for r in rows}
                for f in facilities:
                    t = _tl_for(tmap.get(f.get("mng_no")), language)
                    if t.get("name"):
                        f["name"] = t["name"]
        else:
            # Kakao/exchange 등 일반 카테고리 → store_details 의 캐시된 번역을 상호명 일치로 매칭
            names = [f["name"] for f in facilities if f.get("name")]
            if names:
                async with pool.acquire() as conn:
                    rows = await conn.fetch(
                        "SELECT store_name, COALESCE(translations::text, '{}') AS tl "
                        "FROM storedetails WHERE store_name = ANY($1::text[]) "
                        "AND translations::text <> '{}'",
                        names,
                    )
                tmap = {}
                for r in rows:
                    t = _tl_for(r["tl"], language)
                    if t.get("name"):
                        tmap.setdefault(r["store_name"], t["name"])
                for f in facilities:
                    if f.get("name") in tmap:
                        f["name"] = tmap[f["name"]]
    except Exception as e:
        print(f"[search_agent] 시설명 번역 적용 실패 (무시): {e}")


def _normalize_halal_rows(halal_rows: list[dict], language: str) -> list[dict]:
    """halal_restaurants 결과(풍부한 필드) → Facility 표준 키로 normalize.
    halal_type/cuisine_type/menu_examples 등은 extra 에 보존 — speech 와 카드에서 활용."""
    return [
        {
            "name":       ((r.get("name_en") or r.get("name_ko")) if language == "en"
                           else (r.get("name_ko") or r.get("name_en"))) or "할랄 식당",
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
                "menu_detailed":          r.get("menu_detailed", []),
                "description":            r.get("short_description_ko", ""),
                "restaurant_id":          r.get("restaurant_id", ""),
            },
        }
        for r in halal_rows
    ]


async def run_search_agent(req: ConvenienceRequest, user_id: str = "") -> ConvenienceResponse:
    # Step 1: category + 브랜드 키워드 결정
    # message 안에 특정 상호명(스타벅스/올리브영 등) 이 있으면 brand_keyword 에 담겨오고
    # 호출자 단계에서 keyword_search 로 분기해 브랜드 매칭을 보존한다.
    # orchestrator 가 sub_category 를 미리 결정해 category 를 채워 보내도 message 안에
    # 브랜드명이 있으면 그걸 잃지 않도록 항상 브랜드 추출은 한다 — 한 번의 LLM 호출에서
    # category + brand_keyword 둘 다 받아오는 구조.
    category = req.category.strip()
    language = req.language
    brand_keyword = ""

    if not req.message.strip():
        # 카테고리 탭 UI 같이 메시지 없이 카테고리만 들어온 경우 — 분류 호출 X.
        if not category:
            category = "convenience_store"
    else:
        # 메시지 있으면 항상 LLM 분류 → category(없을 때만 채택) + brand_keyword.
        llm_category, llm_language, brand_keyword = await _extract_category_and_language(
            req.message, user_id=user_id,
        )
        if not category:
            category = llm_category
            language = llm_language

    # Step 2: 반경 결정
    radius = get_radius(category, req.radius)

    # Step 3: 라우팅 — 브랜드 키워드가 있으면 Kakao 키워드 검색 우선.
    # 단 restroom/locker/prayer_room/halal_restaurant 처럼 전용 데이터소스를 쓰는
    # 카테고리는 키워드 검색으로 대체할 수 없으니 그대로 전용 경로 유지.
    _KAKAO_ROUTABLE = set(CATEGORY_CONFIG.keys()) | {"exchange"}

    # Step 3.0: 식당류 상호명 질의("부산집 메뉴 뭐 있어?")는 먼저 halal_restaurants 를
    # 이름으로 조회한다. 우리 DB 에 있는 매장이면 Kakao 보다 풍부하고 메뉴(menu_examples)도
    # 함께 잡힌다. 매칭 없으면 아래 일반 경로로 fall-through.
    _HALAL_NAME_CATEGORIES = {"restaurant", "halal_restaurant", "cafe"}
    halal_named = []
    if brand_keyword and category in _HALAL_NAME_CATEGORIES:
        halal_named = await halal_restaurant_by_name(brand_keyword, req.lat, req.lng)

    if halal_named:
        category = "halal_restaurant"
        raw = _normalize_halal_rows(halal_named, language)
        print(f"[search_agent] 할랄 상호명 검색: {brand_keyword!r} → {len(raw)}건")
    elif brand_keyword and category in _KAKAO_ROUTABLE:
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
        halal_rows = await halal_restaurant_search(req.lat, req.lng, radius)
        raw = _normalize_halal_rows(halal_rows, language)
    else:
        raw = []

    # Step 4: 거리순 정렬 → 상위 5개
    raw_sorted = sorted(raw, key=lambda x: x["distance_m"])[:5]

    # Step 4.5: Kakao Local API 는 open_hours 를 안 줘서 store_details DB 캐시에서 보강
    raw_sorted = await _enrich_facilities_with_db_hours(raw_sorted, lang=language)

    # Step 4.55: 영어 등 비-한국어 모드면 캐시된 번역으로 시설명 교체 (speech 생성 전).
    # 안내 문구·Guide 버튼 모두 영문명으로 나오게. 캐시 없는 시설은 한국어 유지.
    await _localize_facility_names(raw_sorted, category, language)

    # Step 4.6: open_hours 기반 is_open_now 계산 (보강 후의 값으로)
    for f in raw_sorted:
        f["is_open_now"] = is_open_now_combined(f.get("open_hours") or "", None)
    facilities = [Facility(**f) for f in raw_sorted]

    # Step 5: speech 생성 — 사용자 질문을 같이 넘겨 LIST/NEAREST 모드 자동 선택
    speech = await _generate_speech(
        raw_sorted, category, language,
        user_message=req.message,
        user_id=user_id,
    )

    return ConvenienceResponse(
        speech=speech,
        category=category,
        facilities=facilities,
        language=language,
    )
