import json
import os

import chromadb
from openai import AsyncOpenAI
from dotenv import load_dotenv

from schemas.place import PlaceRequest

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
openai_client = AsyncOpenAI(api_key=OPENAI_API_KEY)

_chroma_client = None
_collection = None

# VWorld / Kakao 역지오코딩이 반환하는 건물명 → place_id 매핑
BUILDING_NAME_MAP = {
    "명동대성당": "myeongdong_cathedral",
    "명동성당": "myeongdong_cathedral",
    "롯데백화점 본점": "lotte_dept_myeongdong",
    "롯데백화점 명동본점": "lotte_dept_myeongdong",
    "신세계백화점 본점": "shinsegae_myeongdong",
    "신세계백화점": "shinsegae_myeongdong",
    "눈스퀘어": "noon_square_myeongdong",
    "명동 눈스퀘어": "noon_square_myeongdong",
    "명동예술극장": "myeongdong_art_theater",
    "국립극단 명동예술극장": "myeongdong_art_theater",
    "N서울타워": "n_seoul_tower",
    "남산서울타워": "n_seoul_tower",
    "서울타워": "n_seoul_tower",
    "롯데시티호텔 명동": "lotte_city_hotel_myeongdong",
    "유네스코회관": "unesco_hall_myeongdong",
    "유네스코회관빌딩": "unesco_hall_myeongdong",
    "포스트타워": "post_tower_myeongdong",
    "서울중앙우체국": "post_tower_myeongdong",
    "대신파이낸스센터": "daishin_finance_center",
    "Daishin343": "daishin_finance_center",
}


def _get_collection():
    global _chroma_client, _collection
    if _collection is None:
        from chromadb.utils.embedding_functions import DefaultEmbeddingFunction
        _chroma_client = chromadb.PersistentClient(path="./chroma_db")
        _collection = _chroma_client.get_or_create_collection("place_info", embedding_function=DefaultEmbeddingFunction())
    return _collection


# ── LLM: docent 해설 생성 ──────────────────────────────────────────────────────

async def llm_generate_docent(context: str, language: str) -> str:
    lang_map = {"ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese"}
    response_lang_label = lang_map.get(language, language)

    system_prompt = (
        "You are a friendly AR tour guide for foreign visitors in Seoul. "
        "Respond in 2-3 short sentences suitable for text-to-speech. "
        f"Always respond in {response_lang_label}. "
        "If halal_info is provided, always mention it. "
        "Be warm, concise, and helpful for a solo traveler."
    )

    response = await openai_client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": context},
        ],
        max_tokens=300,
    )
    return response.choices[0].message.content.strip()


# ── Follow-up 질문 생성 ────────────────────────────────────────────────────────

def generate_follow_ups(user_message: str, place_data: dict) -> list[str]:
    suggestions = []
    msg_lower = user_message.lower()

    has_floor_info   = bool(place_data.get("floor_info"))
    has_halal        = bool(place_data.get("halal_info"))
    has_admission    = bool(place_data.get("admission_fee"))
    has_parking      = bool(place_data.get("parking_info"))

    if "floor" not in msg_lower and has_floor_info:
        suggestions.append("What's on each floor?")
    if "halal" not in msg_lower and has_halal:
        suggestions.append("Where can I find halal food nearby?")
    if "fee" not in msg_lower and "price" not in msg_lower and has_admission:
        suggestions.append("How much is the admission fee?")
    if "park" not in msg_lower and has_parking:
        suggestions.append("Is there parking available?")
    if "eat" not in msg_lower and "restaurant" not in msg_lower:
        suggestions.append("What's nearby to eat?")
    if "prayer" not in msg_lower:
        suggestions.append("Is there a prayer room nearby?")

    return suggestions[:3]


# ── GPT-4V: 이미지에서 건물명 추출 ────────────────────────────────────────────

async def gpt4v_extract_building_name(image_base64: str) -> str:
    """
    이미지에서 건물명(간판, 외관 텍스트)만 추출.
    Kakao 검색에 쓸 수 있도록 한국어 건물명 우선 반환.
    인식 불가 시 빈 문자열 반환.
    """
    response = await openai_client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "Look at this image and identify the building or location name shown. "
                            "Read any visible signs or text. "
                            "Return ONLY the building name in Korean if possible, otherwise in the language shown. "
                            "If you cannot identify any building name, return an empty string. "
                            "Return nothing else — just the name."
                        ),
                    },
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:image/jpeg;base64,{image_base64}", "detail": "high"},
                    },
                ],
            }
        ],
        max_tokens=50,
    )
    return response.choices[0].message.content.strip()


# ── Kakao로 건물명 → place_id 매핑 시도 ────────────────────────────────────────

async def resolve_place_id_from_name(building_name: str, user_lat: float, user_lng: float) -> str:
    """
    건물명으로 Kakao 키워드 검색 → BUILDING_NAME_MAP 매핑 → place_id 반환.
    매핑 실패 시 빈 문자열 반환.
    """
    import httpx, os
    KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {
        "query": building_name,
        "x": str(user_lng),
        "y": str(user_lat),
        "radius": 2000,
        "size": 5,
    }
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers, params=params)
        docs = resp.json().get("documents", [])

    for doc in docs:
        kakao_name = doc.get("place_name", "")
        # Kakao 반환 건물명으로 BUILDING_NAME_MAP 매핑
        if kakao_name in BUILDING_NAME_MAP:
            return BUILDING_NAME_MAP[kakao_name]
        # 부분 문자열 매핑 (예: "롯데백화점 명동본점" → "롯데백화점 본점" 키에 포함)
        for map_key, pid in BUILDING_NAME_MAP.items():
            if map_key in kakao_name or kakao_name in map_key:
                return pid

    return ""


# ── GPT-4V 최종 폴백: LLM 지식 기반 응답 ──────────────────────────────────────

async def gpt4v_fallback_response(image_base64: str, user_message: str, language: str) -> dict:
    """
    Chroma에도 없고 Kakao 매핑도 안 될 때 최후 수단.
    GPT-4V 지식 기반 응답. is_estimated=True 표시.
    """
    lang_map = {"ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese"}
    response_lang = lang_map.get(language, "English")

    response = await openai_client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "system",
                "content": (
                    "You are an AR tour guide assistant. "
                    "Analyze the image to identify the building or location. "
                    f"Respond in {response_lang}. "
                    "Be honest if uncertain — use 'This appears to be...' phrasing. "
                    "2-3 sentences for TTS. Include any practically useful info visible."
                ),
            },
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": f"User's question: {user_message}"},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:image/jpeg;base64,{image_base64}", "detail": "high"},
                    },
                ],
            },
        ],
        max_tokens=300,
    )

    return {
        "ar_overlay": {
            "name": "", "category": "", "floor_info": [],
            "halal_info": "", "image_url": "", "homepage": "",
            "open_hours": "", "closed_days": "", "parking_info": "", "admission_fee": "",
            "is_estimated": True,
        },
        "docent": {
            "speech": response.choices[0].message.content.strip(),
            "follow_up_suggestions": [],
        },
    }


# ── Main agent ────────────────────────────────────────────────────────────────

async def run_place_insight_agent(req: PlaceRequest) -> dict:
    collection = _get_collection()

    # 1. place_id 결정: 직접 전달 > building_name 매핑
    place_id = req.place_id
    if not place_id and req.building_name:
        place_id = BUILDING_NAME_MAP.get(req.building_name, "")

    # Chroma에서 place_id로 직접 조회
    result = collection.get(ids=[place_id]) if place_id else {"metadatas": []}
    if not result["metadatas"]:
        # GPT-4V fallback: 이미지 → 건물명 추출 → Kakao 매핑 → Chroma 재조회 → 최후 LLM 폴백
        if req.image_base64:
            building_name = await gpt4v_extract_building_name(req.image_base64)
            if building_name:
                resolved_id = await resolve_place_id_from_name(building_name, req.user_lat, req.user_lng)
                if resolved_id:
                    place_id = resolved_id
                    result = collection.get(ids=[place_id])
                    if not result["metadatas"]:
                        return await gpt4v_fallback_response(req.image_base64, req.user_message, req.language)
                    # result["metadatas"] 있음 → 아래 RAG 플로우로 계속
                else:
                    return await gpt4v_fallback_response(req.image_base64, req.user_message, req.language)
            else:
                return await gpt4v_fallback_response(req.image_base64, req.user_message, req.language)
        else:
            return {
                "ar_overlay": {
                    "name":          req.place_id,
                    "category":      "",
                    "floor_info":    [],
                    "halal_info":    "",
                    "image_url":     "",
                    "homepage":      "",
                    "open_hours":    "",
                    "closed_days":   "",
                    "parking_info":  "",
                    "admission_fee": "",
                    "is_estimated":  False,
                },
                "docent": {
                    "speech": "Sorry, I don't have information about this place yet.",
                    "follow_up_suggestions": [],
                },
            }

    place_data = result["metadatas"][0]

    # Chroma에서 꺼낸 JSON 문자열 → 파이썬 객체 역직렬화
    floor_info = json.loads(place_data.get("floor_info", "[]"))

    # 2. halal_info
    halal_info = place_data.get("halal_info", "")

    # 3. ar_overlay (LLM 없이 RAG 데이터 그대로)
    ar_overlay = {
        "name":          place_data.get("name_ko", ""),
        "category":      place_data.get("category", ""),
        "floor_info":    floor_info,
        "halal_info":    halal_info,
        "image_url":     place_data.get("image_url", ""),
        "homepage":      place_data.get("homepage", ""),
        "open_hours":    place_data.get("open_hours", ""),
        "closed_days":   place_data.get("closed_days", ""),
        "parking_info":  place_data.get("parking_info", ""),
        "admission_fee": place_data.get("admission_fee", ""),
        "is_estimated":  False,  # 공공 API 검증 데이터
    }

    # 4. docent: LLM 자연어 해설 생성
    context = f"""
Place: {place_data.get('name_ko', '')}
Category: {place_data.get('category', '')}
Description: {place_data.get('description_en', '')}
Open hours: {place_data.get('open_hours', '')}
Closed days: {place_data.get('closed_days', '')}
Admission fee: {place_data.get('admission_fee', '')}
Halal info: {halal_info}
User's question: {req.user_message}
Language: {req.language}
""".strip()

    speech = await llm_generate_docent(context, req.language)
    follow_ups = generate_follow_ups(req.user_message, {
        "floor_info":   floor_info,
        "halal_info":   halal_info,
        "admission_fee": place_data.get("admission_fee", ""),
        "parking_info":  place_data.get("parking_info", ""),
    })

    return {
        "ar_overlay": ar_overlay,
        "docent": {
            "speech": speech,
            "follow_up_suggestions": follow_ups,
        },
    }
