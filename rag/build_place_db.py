"""
build_place_db.py
서버 실행 전 1회만 실행하는 스크립트.
명동 주요 건물 8개의 정보를 수집하여 ChromaDB에 저장.

실행 방법:
    cd scanpang-navigation-agent
    python -m rag.build_place_db
"""

import asyncio
import json
import os
from typing import Optional

import httpx
from openai import AsyncOpenAI
from dotenv import load_dotenv
import chromadb
from sentence_transformers import SentenceTransformer

load_dotenv()

KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY", "")
TOUR_API_KEY = os.getenv("TOUR_API_KEY", "")
STORE_API_KEY = os.getenv("STORE_API_KEY", "")
JUSO_API_KEY = os.getenv("JUSO_API_KEY", "")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")

openai_client = AsyncOpenAI(api_key=OPENAI_API_KEY)

# building_key: 모두 None → Kakao 도로명주소 → Juso API로 bdMgtSn 자동 획득
# tour_keyword: TourAPI searchKeyword2 검색어 (None이면 name 그대로 사용)
TARGET_PLACES = [
    {"place_id": "myeongdong_cathedral",        "name": "명동성당",            "building_key": None, "tour_keyword": None},
    {"place_id": "lotte_dept_myeongdong",       "name": "롯데백화점 명동본점",  "building_key": None, "tour_keyword": "롯데백화점 본점"},
    {"place_id": "shinsegae_myeongdong",        "name": "신세계백화점 본점",    "building_key": None, "tour_keyword": None},
    {"place_id": "noon_square_myeongdong",      "name": "명동 눈스퀘어",       "building_key": None, "tour_keyword": "눈스퀘어"},
    {"place_id": "cgv_myeongdong",              "name": "CGV 명동",            "building_key": None, "tour_keyword": None},
    {"place_id": "myeongdong_art_theater",      "name": "명동예술극장",         "building_key": None, "tour_keyword": None},
    {"place_id": "n_seoul_tower",               "name": "N서울타워",            "building_key": None, "tour_keyword": "서울타워"},
    {"place_id": "lotte_city_hotel_myeongdong", "name": "롯데시티호텔 명동",    "building_key": None, "tour_keyword": None},
]

MYEONGDONG_LNG = 126.9822
MYEONGDONG_LAT = 37.5636


# ── Step 1: Kakao Local 기본 정보 ──────────────────────────────────────────────

async def fetch_kakao_info(place_name: str) -> dict:
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {
        "query": place_name,
        "x": str(MYEONGDONG_LNG),
        "y": str(MYEONGDONG_LAT),
        "radius": 2000,
        "size": 1,
    }
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers, params=params)
        resp.raise_for_status()
        data = resp.json()

    docs = data.get("documents", [])
    if not docs:
        return {}
    doc = docs[0]
    cat_parts = doc.get("category_name", "").split(" > ")
    return {
        "name_ko": doc.get("place_name", place_name),
        "category": cat_parts[1] if len(cat_parts) > 1 else cat_parts[0],
        "lat": float(doc.get("y", MYEONGDONG_LAT)),
        "lng": float(doc.get("x", MYEONGDONG_LNG)),
        "addr": doc.get("road_address_name", ""),
        "phone": doc.get("phone", ""),
    }


# ── Step 2: TourAPI description + image + 상세정보 ────────────────────────────

# 명동 확장 대응: 전체 contentTypeId 포함
TOUR_CONTENT_TYPES = [12, 14, 15, 25, 28, 32, 38, 39]
# 12:관광지, 14:문화시설, 15:행사/공연/축제, 25:여행코스,
# 28:레포츠, 32:숙박, 38:쇼핑, 39:음식점

# contentTypeId별 detailIntro2 필드 매핑
INTRO_FIELD_MAP = {
    12: {"open_hours": "usetime",         "closed_days": "restdate",         "parking_info": "parking",         "admission_fee": ""},
    14: {"open_hours": "usetimeculture",   "closed_days": "restdateculture",  "parking_info": "parkingculture",  "admission_fee": "usefee"},
    15: {"open_hours": "playtime",         "closed_days": "",                 "parking_info": "",                "admission_fee": "usetimefestival"},
    25: {"open_hours": "taketime",         "closed_days": "",                 "parking_info": "",                "admission_fee": ""},
    28: {"open_hours": "usetimeleports",   "closed_days": "restdateleports",  "parking_info": "parkingleports",  "admission_fee": "usefeeleports"},
    32: {"open_hours": "checkintime",      "closed_days": "checkouttime",     "parking_info": "parkinghotel",    "admission_fee": ""},
    38: {"open_hours": "opentime",         "closed_days": "restdateshopping", "parking_info": "parkingshopping", "admission_fee": ""},
    39: {"open_hours": "opentimefood",     "closed_days": "restdatefood",     "parking_info": "parkingfood",     "admission_fee": ""},
}

async def fetch_tour_info(place_name: str, tour_keyword: Optional[str] = None) -> dict:
    base = "https://apis.data.go.kr/B551011/KorService2"
    common_params = {
        "serviceKey": TOUR_API_KEY,
        "MobileOS": "ETC",
        "MobileApp": "ScanPang",
        "_type": "json",
    }

    keyword = tour_keyword or place_name
    content_id = None
    content_type_id = None
    image_url = ""

    async with httpx.AsyncClient() as client:
        for ctype in TOUR_CONTENT_TYPES:
            params = {**common_params, "keyword": keyword, "contentTypeId": ctype}
            resp = await client.get(f"{base}/searchKeyword2", params=params)
            items_raw = resp.json().get("response", {}).get("body", {}).get("items", {})
            items = items_raw.get("item", []) if isinstance(items_raw, dict) else []
            if items:
                first = items[0] if isinstance(items, list) else items
                content_id = first.get("contentid")
                content_type_id = ctype
                image_url = first.get("firstimage", "")
                break

        if not content_id:
            return {}

        # detailCommon2: overview + homepage (파라미터 없이 호출해야 overview/homepage 포함됨)
        resp = await client.get(f"{base}/detailCommon2", params={
            **common_params, "contentId": content_id
        })
        detail_raw = resp.json().get("response", {}).get("body", {}).get("items", {})
        detail = detail_raw.get("item", {}) if isinstance(detail_raw, dict) else {}
        if isinstance(detail, list):
            detail = detail[0] if detail else {}
        overview_ko = detail.get("overview", "") if isinstance(detail, dict) else ""
        homepage = detail.get("homepage", "") if isinstance(detail, dict) else ""

        # detailIntro2: contentTypeId별 운영시간, 휴무일, 주차, 입장료
        field_map = INTRO_FIELD_MAP.get(content_type_id, {})
        resp2 = await client.get(f"{base}/detailIntro2", params={
            **common_params, "contentId": content_id, "contentTypeId": content_type_id
        })
        intro_raw = resp2.json().get("response", {}).get("body", {}).get("items", {})
        intro = intro_raw.get("item", {}) if isinstance(intro_raw, dict) else {}
        if isinstance(intro, list):
            intro = intro[0] if intro else {}

        open_hours    = intro.get(field_map.get("open_hours", ""), "")
        closed_days   = intro.get(field_map.get("closed_days", ""), "")
        parking_info  = intro.get(field_map.get("parking_info", ""), "")
        admission_fee = intro.get(field_map.get("admission_fee", ""), "")

    description_en = await translate_to_english(overview_ko, place_name) if overview_ko else ""

    return {
        "description_en": description_en,
        "image_url": image_url,
        "homepage": homepage,
        "open_hours": open_hours,
        "closed_days": closed_days,
        "parking_info": parking_info,
        "admission_fee": admission_fee,
    }


async def translate_to_english(text_ko: str, place_name: str) -> str:
    response = await openai_client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": "You are a professional translator. Translate Korean to English concisely."},
            {"role": "user", "content": f"Translate this Korean description of {place_name} to English (2-3 sentences max):\n\n{text_ko}"},
        ],
        max_tokens=200,
    )
    return response.choices[0].message.content.strip()


async def gpt_generate_description(place_name: str) -> str:
    response = await openai_client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "user",
                "content": (
                    f"Write a 2-3 sentence description of {place_name} in Seoul "
                    "for a solo foreign traveler. "
                    "Focus on what it is, what you can find there, and why it's worth visiting. "
                    "Respond in English only."
                ),
            }
        ],
        max_tokens=200,
    )
    return response.choices[0].message.content.strip()


# ── Step 3: 소상공인 API floor_info ───────────────────────────────────────────

async def fetch_floor_info(building_key: str) -> list:
    url = "http://apis.data.go.kr/B553077/api/open/sdsc2/storeListInBuilding"
    params = {
        "serviceKey": STORE_API_KEY,
        "key": building_key,
        "numOfRows": 1000,
        "pageNo": 1,
        "type": "json",
    }
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, params=params)
        resp.raise_for_status()
        data = resp.json()

    items = data.get("body", {}).get("items", [])
    floor_map: dict[str, list] = {}
    for item in items:
        floor = item.get("flrNo", "").strip() or "기타"
        store_name = item.get("bizesNm", "").strip()
        biz_type = item.get("indsMclsNm", "").strip()
        if store_name:
            label = f"{store_name} ({biz_type})" if biz_type else store_name
            floor_map.setdefault(floor, []).append(label)

    return [{"floor": f, "stores": s} for f, s in floor_map.items()]


# ── Step 4: Juso API → building_key 자동 획득 ─────────────────────────────────

async def fetch_building_key(road_addr: str) -> Optional[str]:
    if not road_addr or not JUSO_API_KEY:
        return None
    url = "https://business.juso.go.kr/addrlink/addrLinkApi.do"
    params = {
        "confmKey": JUSO_API_KEY,
        "keyword": road_addr,
        "currentPage": 1,
        "countPerPage": 1,
        "resultType": "json",
    }
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, params=params)
        data = resp.json()
    juso_list = data.get("results", {}).get("juso", [])
    if juso_list:
        return juso_list[0].get("bdMgtSn")
    return None


# ── Main pipeline ─────────────────────────────────────────────────────────────

async def build_all_places() -> list[dict]:
    all_places = []

    for target in TARGET_PLACES:
        place_id = target["place_id"]
        name = target["name"]
        building_key = target["building_key"]
        tour_keyword = target.get("tour_keyword")
        print(f"[{place_id}] 수집 중...")

        # Step 1: Kakao 기본 정보
        kakao = await fetch_kakao_info(name)
        place = {
            "place_id": place_id,
            "name_ko": kakao.get("name_ko", name),
            "category": kakao.get("category", ""),
            "lat": kakao.get("lat", MYEONGDONG_LAT),
            "lng": kakao.get("lng", MYEONGDONG_LNG),
            "addr": kakao.get("addr", ""),
            "phone": kakao.get("phone", ""),
            "description_en": "",
            "image_url": "",
            "homepage": "",
            "open_hours": "",
            "closed_days": "",
            "parking_info": "",
            "admission_fee": "",
            "floor_info": [],
        }

        # Step 2: TourAPI description + 상세정보
        tour = await fetch_tour_info(name, tour_keyword)
        if tour.get("description_en"):
            place["description_en"] = tour["description_en"]
            place["image_url"]      = tour.get("image_url", "")
            place["homepage"]       = tour.get("homepage", "")
            place["open_hours"]     = tour.get("open_hours", "")
            place["closed_days"]    = tour.get("closed_days", "")
            place["parking_info"]   = tour.get("parking_info", "")
            place["admission_fee"]  = tour.get("admission_fee", "")
        else:
            place["description_en"] = await gpt_generate_description(name)

        # Step 3: 소상공인 API floor_info (building_key 없으면 Juso로 자동 획득)
        if not building_key and kakao.get("addr"):
            building_key = await fetch_building_key(kakao["addr"])

        if building_key:
            place["floor_info"] = await fetch_floor_info(building_key)

        all_places.append(place)
        print(f"  → 완료: {place['name_ko']}")

    return all_places


def save_to_chroma(all_places: list[dict]):
    print("\nChroma DB 임베딩 저장 중...")
    from chromadb.utils.embedding_functions import DefaultEmbeddingFunction
    ef = DefaultEmbeddingFunction()
    client = chromadb.PersistentClient(path="./chroma_db")
    collection = client.get_or_create_collection("place_info", embedding_function=ef)

    for place in all_places:
        text = " ".join([
            place["name_ko"],
            place["category"],
            place["description_en"],
        ])
        # Chroma metadata는 string/int/float/bool만 허용 → floor_info는 JSON 직렬화
        metadata = {k: v for k, v in place.items() if isinstance(v, (str, int, float, bool))}
        metadata["floor_info"] = json.dumps(place.get("floor_info", []), ensure_ascii=False)

        collection.upsert(
            documents=[text],
            metadatas=[metadata],
            ids=[place["place_id"]],
        )
        print(f"  저장: {place['place_id']}")

    print(f"\n총 {len(all_places)}개 장소 저장 완료 → ./chroma_db")


async def main():
    all_places = await build_all_places()
    save_to_chroma(all_places)


if __name__ == "__main__":
    asyncio.run(main())
