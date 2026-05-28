"""
halal_agent.py
무슬림 관광객을 위한 Halal Agent.
카테고리별 라우팅: prayer_time | qibla | restaurant | prayer_room

진입 경로:
  1. /halal/query 엔드포인트 (무슬림 허브 UI) — 4개 카테고리 모두 사용
  2. orchestrator chat — prayer_time / qibla 만. (restaurant/prayer_room 은 search_agent 로 분리)

호출자는 반드시 req.category 를 지정해야 한다. 빈 경우 prayer_time 으로 폴백.
"""

from datetime import datetime, timezone, timedelta

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
from tools.llm_client import call_llm

load_dotenv()


# ── LLM: 음성 생성 ──────────────────────────────────────────────────────────

async def _generate_speech(
    data: dict, category: str, language: str, user_id: str = "",
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
        return await call_llm(
            user_id=user_id,
            purpose="halal_speech",
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": context},
            ],
            max_tokens=300,
        )
    except Exception as e:
        print(f"[Halal] 음성 생성 오류: {e}")
        return "Sorry, I couldn't generate a response at this time."


# ── Main Agent ───────────────────────────────────────────────────────────────

async def run_halal_agent(req: HalalRequest, user_id: str = "") -> dict:
    """Halal Agent 메인 함수."""

    # 1) 카테고리 결정 — 호출자(orchestrator / 무슬림 허브 UI)가 명시한 값 사용.
    # 빈 경우 prayer_time 으로 폴백 (예전 LLM 기반 _extract_category 는 제거됨 —
    # orchestrator 가 이미 sub_category 를 결정해 전달하므로 dead path 였음).
    category = req.category or "prayer_time"
    language = req.language
    halal_type = req.halal_type

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
        data = await halal_restaurant_search(req.lat, req.lng, radius, halal_type)
        restaurants = [HalalRestaurant(**r) for r in data]

    elif category == "prayer_room":
        data = await halal_prayer_room_search(req.lat, req.lng, radius)
        prayer_rooms = [PrayerRoomDetail(**r) for r in data]

    else:
        data = {}

    # 4) 음성 생성
    speech_data = data if not isinstance(data, list) else data
    speech = await _generate_speech(speech_data, category, language, user_id=user_id)

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
