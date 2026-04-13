"""
halal_agent.py
무슬림 관광객을 위한 Halal Agent.
카테고리별 라우팅: prayer_time | qibla | restaurant | prayer_room
"""

import json
import os
from datetime import datetime, timezone, timedelta

from openai import AsyncOpenAI
from dotenv import load_dotenv

from schemas.halal import (
    HalalRequest, HalalResponse,
    PrayerTimeData, QiblaData, HalalRestaurant, PrayerRoomDetail,
)
from tools.halal_tools import (
    fetch_prayer_times, fetch_qibla_direction,
    halal_restaurant_search, halal_prayer_room_search,
    DEFAULT_RADIUS,
)

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
openai_client = AsyncOpenAI(api_key=OPENAI_API_KEY)


# ── LLM: 카테고리 추출 ──────────────────────────────────────────────────────

async def _extract_category(message: str) -> tuple:
    """자유 텍스트 → (category, language, halal_type) 추출."""
    system = """You are a classifier for a Muslim tourist assistant app.
Given a user message, return JSON with:
- "category": one of "prayer_time", "qibla", "restaurant", "prayer_room"
- "language": detected language code ("en", "ko", "ar", "ja", "zh")
- "halal_type": if restaurant, one of "HALAL_MEAT", "SEAFOOD", "VEGGIE", or "" if not specified

Examples:
- "When is the next prayer?" → {"category":"prayer_time","language":"en","halal_type":""}
- "메카 방향 알려줘" → {"category":"qibla","language":"ko","halal_type":""}
- "Find halal Korean food" → {"category":"restaurant","language":"en","halal_type":"HALAL_MEAT"}
- "근처 해산물 식당" → {"category":"restaurant","language":"ko","halal_type":"SEAFOOD"}
- "Where can I pray?" → {"category":"prayer_room","language":"en","halal_type":""}
- "أين يمكنني الصلاة؟" → {"category":"prayer_room","language":"ar","halal_type":""}

Return ONLY valid JSON, no explanation."""

    try:
        resp = await openai_client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": message},
            ],
            max_tokens=100,
            temperature=0,
        )
        text = resp.choices[0].message.content.strip()
        # JSON 파싱 (```json ... ``` 감싸져 있을 수 있음)
        if text.startswith("```"):
            text = text.split("```")[1].replace("json", "", 1).strip()
        result = json.loads(text)
        return (
            result.get("category", "prayer_time"),
            result.get("language", "en"),
            result.get("halal_type", ""),
        )
    except Exception as e:
        print(f"[Halal] 카테고리 추출 오류: {e}")
        return "prayer_time", "en", ""


# ── LLM: 음성 생성 ──────────────────────────────────────────────────────────

async def _generate_speech(
    data: dict, category: str, language: str
) -> str:
    """카테고리별 데이터를 기반으로 자연어 음성 생성."""
    lang_map = {"ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese"}
    lang_label = lang_map.get(language, "English")

    kst = timezone(timedelta(hours=9))
    current_time = datetime.now(kst).strftime("%H:%M")

    if category == "prayer_time":
        context = f"""Current time in Seoul (KST): {current_time}
Prayer times today:
- Fajr: {data.get('fajr', '?')}
- Dhuhr: {data.get('dhuhr', '?')}
- Asr: {data.get('asr', '?')}
- Maghrib: {data.get('maghrib', '?')}
- Isha: {data.get('isha', '?')}
Hijri date: {data.get('hijri_date', '?')}

Announce the NEXT upcoming prayer first, then briefly mention the remaining prayers."""

    elif category == "qibla":
        direction = data.get("direction", 0)
        # 방향을 자연어로 변환
        compass = ""
        if 337.5 <= direction or direction < 22.5: compass = "North"
        elif 22.5 <= direction < 67.5: compass = "Northeast"
        elif 67.5 <= direction < 112.5: compass = "East"
        elif 112.5 <= direction < 157.5: compass = "Southeast"
        elif 157.5 <= direction < 202.5: compass = "South"
        elif 202.5 <= direction < 247.5: compass = "Southwest"
        elif 247.5 <= direction < 292.5: compass = "West"
        elif 292.5 <= direction < 337.5: compass = "Northwest"
        context = f"The Qibla direction from the user's location is {direction:.1f}° ({compass})."

    elif category == "restaurant":
        if isinstance(data, list) and data:
            items = []
            for r in data[:3]:
                items.append(
                    f"- {r.get('name_en', r.get('name_ko', '?'))} "
                    f"({r.get('halal_type', '?')}, {r.get('distance_m', 0):.0f}m away, "
                    f"hours: {r.get('opening_hours', '?')})"
                )
            context = f"Found {len(data)} halal restaurants nearby:\n" + "\n".join(items)
        else:
            context = "No halal restaurants found within the search radius."

    elif category == "prayer_room":
        if isinstance(data, list) and data:
            items = []
            for r in data[:3]:
                fac = r.get("facilities", {})
                fac_str = ", ".join(k for k, v in fac.items() if v)
                items.append(
                    f"- {r.get('name_en', r.get('name', '?'))} "
                    f"({r.get('distance_m', 0):.0f}m, {r.get('floor', '?')}, "
                    f"facilities: {fac_str or 'N/A'})"
                )
            context = f"Found {len(data)} prayer rooms nearby:\n" + "\n".join(items)
        else:
            context = "No prayer rooms found within the search radius."
    else:
        context = "No data available."

    system = f"""You are a friendly Muslim travel assistant for tourists visiting Seoul, Korea.
Generate a concise 2-3 sentence response suitable for text-to-speech.
Always respond in {lang_label}.
Be warm, helpful, and respectful of Islamic practices."""

    try:
        resp = await openai_client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": context},
            ],
            max_tokens=300,
        )
        return resp.choices[0].message.content.strip()
    except Exception as e:
        print(f"[Halal] 음성 생성 오류: {e}")
        return "Sorry, I couldn't generate a response at this time."


# ── Main Agent ───────────────────────────────────────────────────────────────

async def run_halal_agent(req: HalalRequest) -> dict:
    """Halal Agent 메인 함수."""

    # 1) 카테고리 결정
    category = req.category
    language = req.language
    halal_type = req.halal_type

    if not category and req.message:
        category, language, halal_type = await _extract_category(req.message)
    category = category or "prayer_time"

    # 2) 반경 결정
    radius = req.radius if req.radius > 0 else DEFAULT_RADIUS.get(category, 1000)

    # 3) 도구 라우팅
    prayer_times = None
    qibla = None
    restaurants = []
    prayer_rooms = []

    if category == "prayer_time":
        data = await fetch_prayer_times(req.lat, req.lng)
        if data:
            prayer_times = PrayerTimeData(**data)

    elif category == "qibla":
        data = await fetch_qibla_direction(req.lat, req.lng)
        qibla = QiblaData(**data)

    elif category == "restaurant":
        data = halal_restaurant_search(req.lat, req.lng, radius, halal_type)
        restaurants = [HalalRestaurant(**r) for r in data]

    elif category == "prayer_room":
        data = halal_prayer_room_search(req.lat, req.lng, radius)
        prayer_rooms = [PrayerRoomDetail(**r) for r in data]

    else:
        data = {}

    # 4) 음성 생성
    speech_data = data if not isinstance(data, list) else data
    speech = await _generate_speech(speech_data, category, language)

    # 5) 응답 구성
    return HalalResponse(
        speech=speech,
        category=category,
        language=language,
        prayer_times=prayer_times,
        qibla=qibla,
        restaurants=restaurants,
        prayer_rooms=prayer_rooms,
    ).model_dump()
