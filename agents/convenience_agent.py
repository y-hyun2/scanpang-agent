"""
convenience_agent.py
편의시설 검색 에이전트.

동작:
  1. category 파라미터 있음 → LLM 스킵, 바로 검색
  2. message만 있음 → LLM으로 category + language 추출
  3. category별 라우팅 → 결과 정렬 → speech 생성
"""

import json
import os

from openai import AsyncOpenAI
from dotenv import load_dotenv

from schemas.convenience import ConvenienceRequest, ConvenienceResponse, Facility
from tools.convenience_tools import (
    CATEGORY_CONFIG,
    DEFAULT_RADIUS,
    get_radius,
    kakao_category_search,
    kakao_keyword_search,
    prayer_room_search,
    seoul_locker_search,
    seoul_restroom_search,
)

load_dotenv()

openai_client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))

ALL_CATEGORIES = list(CATEGORY_CONFIG.keys()) + list(DEFAULT_RADIUS.keys())

CATEGORY_EXTRACT_PROMPT = f"""
You are a facility category classifier for a travel AR app.
Given a user message, return the most relevant facility category and detected language.

Categories:
convenience_store, cafe, restaurant, pharmacy, hospital,
bank, atm, shopping, parking, subway, tourist_info,
exchange, restroom, locker, prayer_room

Return JSON only:
{{"category": "<one of the above>", "language": "<ko|en|ar|ja|zh>"}}

If uncertain, default category to "convenience_store" and language to "ko".
"""

SPEECH_PROMPT = """
You are a helpful AR navigation assistant.
Generate a concise spoken response (2-3 sentences) about the nearest facility.
Respond in the language specified by the 'language' field.
Include: facility name, distance, open hours (if available), and any notable info (wheelchair access, locker sizes, etc.).
Keep it natural and friendly for TTS.
"""


async def _extract_category_and_language(message: str) -> tuple[str, str]:
    resp = await openai_client.chat.completions.create(
        model="gpt-4o",
        temperature=0,
        messages=[
            {"role": "system", "content": CATEGORY_EXTRACT_PROMPT},
            {"role": "user", "content": message},
        ],
        max_tokens=60,
        response_format={"type": "json_object"},
    )
    result = json.loads(resp.choices[0].message.content)
    category = result.get("category", "convenience_store")
    language = result.get("language", "ko")
    if category not in ALL_CATEGORIES:
        category = "convenience_store"
    return category, language


async def _generate_speech(facilities: list[dict], category: str, language: str) -> str:
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
    resp = await openai_client.chat.completions.create(
        model="gpt-4o",
        temperature=0.3,
        messages=[
            {"role": "system", "content": SPEECH_PROMPT},
            {"role": "user", "content": context},
        ],
        max_tokens=150,
    )
    return resp.choices[0].message.content.strip()


async def run_convenience_agent(req: ConvenienceRequest) -> ConvenienceResponse:
    # Step 1: category 결정
    category = req.category.strip()
    language = req.language

    if not category:
        if not req.message.strip():
            category = "convenience_store"
        else:
            category, language = await _extract_category_and_language(req.message)

    # Step 2: 반경 결정
    radius = get_radius(category, req.radius)

    # Step 3: category별 라우팅
    if category in CATEGORY_CONFIG:
        raw = await kakao_category_search(category, req.lat, req.lng, radius)
    elif category == "exchange":
        raw = await kakao_keyword_search("환전", req.lat, req.lng, radius)
    elif category == "restroom":
        raw = await seoul_restroom_search(req.lat, req.lng, radius)
    elif category == "locker":
        raw = await seoul_locker_search(req.lat, req.lng, radius)
    elif category == "prayer_room":
        raw = prayer_room_search(req.lat, req.lng, radius)
    else:
        raw = []

    # Step 4: 거리순 정렬 → 상위 5개
    raw_sorted = sorted(raw, key=lambda x: x["distance_m"])[:5]
    facilities = [Facility(**f) for f in raw_sorted]

    # Step 5: speech 생성
    speech = await _generate_speech(raw_sorted, category, language)

    return ConvenienceResponse(
        speech=speech,
        category=category,
        facilities=facilities,
        language=language,
    )
