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
