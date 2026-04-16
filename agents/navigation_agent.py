import json
import re
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from tools.navigation_tools import search_poi, get_pedestrian_route
from schemas.navigation import NavRequest, RouteRequest

load_dotenv()

llm = ChatOpenAI(model="gpt-4o", temperature=0)

# ── Step 0: 의도 파악 + 키워드 추출 ───────────────────────────────────────────
INTENT_PROMPT = """Extract navigation intent from the user's message. Return valid JSON only, no explanation.

{
  "keyword": "<place name or category keyword only, no filler words>",
  "intent": "specific_place" or "category_search",
  "language": "ko" or "en" or "ar"
}

Keyword rules:
- If the user mentions a well-known landmark, monument, or institution by its common/short name, expand it to the official full name for better search accuracy.
  Examples: "명동성당" → "명동대성당", "남대문" → "숭례문", "동대문" → "흥인지문", "63빌딩" → "63스퀘어"
- If the place is a regular shop/restaurant, keep the name as-is.
- For category searches (할랄 식당, 편의점, 카페 등), keep the category keyword as-is.

Examples:
- "명동성당 어떻게 가?" → {"keyword": "명동대성당", "intent": "specific_place", "language": "ko"}
- "캄풍쿠 어떻게 가?" → {"keyword": "캄풍쿠", "intent": "specific_place", "language": "ko"}
- "주변 할랄 식당 알려줘" → {"keyword": "할랄 식당", "intent": "category_search", "language": "ko"}
- "How do I get to Kampungku?" → {"keyword": "Kampungku", "intent": "specific_place", "language": "en"}
- "How to get to Myeongdong Cathedral?" → {"keyword": "명동대성당", "intent": "specific_place", "language": "en"}
- "مطعم حلال قريب" → {"keyword": "할랄 식당", "intent": "category_search", "language": "ar"}
"""

# ── Step 1 (specific_place): LLM이 추천 POI 1개 선택 ─────────────────────────
SELECT_POI_PROMPT = """The user is looking for: "{keyword}"
Current location: lat={lat}, lng={lng}

Choose the single best matching POI from the list below.
Return only the index number (0-based integer), nothing else.

POI list:
{poi_list}
"""

# ── Step 2: 경로 턴포인트별 TTS 안내 문구 생성 ────────────────────────────────
SPEECH_PROMPT = """Generate short TTS navigation instructions for each turn point.
Language: {language} — ko=Korean, en=English, ar=Arabic. Respond in that language.
Respond with a JSON array of strings — one string per turn point, in order. No explanation, no markdown.

Rules:
- SP (start point): departure announcement including total distance and time
- GP (guidance point): 1-sentence turn instruction
  - Use nearPoiName as landmark if available → "GS25 명동점에서 우회전하세요."
  - Else use intersectionName → "명동사거리에서 좌회전하세요."
  - Else use description only → "우회전 후 직진하세요."
  - facilityType 125=육교, 126=지하보도, 127=계단 → 반드시 언급
- EP (end point): arrival announcement

Turn points:
{turn_points_json}

Destination: {destination_name}
Total: {total_distance_m}m, {total_time_min} minutes
"""


async def run_search_agent(req: NavRequest) -> dict:
    """
    1단계: 메시지 파싱 → POI 검색 → 후보 목록 + 추천 반환
    사용자가 앱에서 확인/선택 후 /navigation/route 호출
    """
    # Step 0: 키워드 + 의도 + 언어 추출
    intent_resp = llm.invoke([
        {"role": "system", "content": INTENT_PROMPT},
        {"role": "user", "content": req.message},
    ])
    try:
        raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", intent_resp.content.strip())
        intent = json.loads(raw)
    except json.JSONDecodeError:
        intent = {"keyword": req.message, "intent": "specific_place", "language": "ko"}

    keyword = intent.get("keyword", req.message)
    intent_type = intent.get("intent", "specific_place")
    language = intent.get("language", "ko")

    # Step 1: POI 검색 (5km → 전국 fallback)
    pois = await search_poi(keyword, req.lat, req.lng)
    if not pois:
        msg = "목적지를 찾지 못했어요. 다시 말씀해주세요." if language == "ko" else "Sorry, I couldn't find that destination. Please try again."
        return {"speech": msg, "candidates": [], "intent": intent_type, "language": language}

    # Step 2: 후보 목록 구성 + 추천 선택
    recommended_idx = 0

    if intent_type == "specific_place" and len(pois) > 1:
        # LLM이 가장 적합한 POI 선택
        poi_list_str = "\n".join(
            f"{i}. {p['name']} | {p['address']}" for i, p in enumerate(pois)
        )
        select_resp = llm.invoke([
            {"role": "user", "content": SELECT_POI_PROMPT.format(
                keyword=keyword, lat=req.lat, lng=req.lng, poi_list=poi_list_str,
            )},
        ])
        try:
            recommended_idx = int(select_resp.content.strip())
            recommended_idx = max(0, min(recommended_idx, len(pois) - 1))
        except ValueError:
            recommended_idx = 0
    # category_search: 거리순 첫 번째(가장 가까운 곳)를 recommended로 표시

    candidates = [
        {
            "poi_id": p["id"],
            "name": p["name"],
            "address": p["address"],
            "pns_lat": float(p["pnsLat"]),
            "pns_lon": float(p["pnsLon"]),
            "recommended": (i == recommended_idx),
        }
        for i, p in enumerate(pois)
    ]

    # 확인 요청 speech 생성
    rec = candidates[recommended_idx]
    if language == "ko":
        if intent_type == "specific_place":
            speech = f"'{rec['name']}' 찾았어요. {rec['address']}. 이 곳으로 안내할까요?"
        else:
            speech = f"근처 {keyword} {len(candidates)}곳을 찾았어요. 가고 싶은 곳을 선택해주세요."
    else:
        if intent_type == "specific_place":
            speech = f"Found '{rec['name']}' at {rec['address']}. Shall I navigate there?"
        else:
            speech = f"Found {len(candidates)} nearby {keyword}. Please select your destination."

    return {
        "speech": speech,
        "candidates": candidates,
        "intent": intent_type,
        "language": language,
    }


async def run_route_agent(req: RouteRequest) -> dict:
    """
    2단계: 사용자가 확정한 POI → 보행자 경로 계산 → 턴별 TTS 포함 응답
    """
    dest = req.destination

    # Step 3: 보행자 경로 계산
    route = await get_pedestrian_route(
        start_lat=req.lat,
        start_lng=req.lng,
        end_poi_id=dest.poi_id,
        end_lat=dest.pns_lat,
        end_lng=dest.pns_lon,
        end_name=dest.name,
        search_option=0,
    )

    # Step 4: 턴포인트별 TTS 문구 생성 (LLM 1회 호출)
    turn_points_for_llm = [
        {
            "index": i,
            "pointType": tp["pointType"],
            "turnType": tp["turnType"],
            "description": tp["description"],
            "nearPoiName": tp["nearPoiName"],
            "intersectionName": tp["intersectionName"],
            "facilityType": tp["facilityType"],
            "segment_distance_m": tp["segment_distance_m"],
        }
        for i, tp in enumerate(route["turn_points"])
    ]

    speech_resp = llm.invoke([
        {"role": "user", "content": SPEECH_PROMPT.format(
            language=req.language,
            turn_points_json=json.dumps(turn_points_for_llm, ensure_ascii=False, indent=2),
            destination_name=dest.name,
            total_distance_m=route["total_distance_m"],
            total_time_min=route["total_time_min"],
        )},
    ])
    try:
        raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", speech_resp.content.strip())
        speeches = json.loads(raw)
        if not isinstance(speeches, list):
            speeches = [""] * len(route["turn_points"])
    except json.JSONDecodeError:
        speeches = [""] * len(route["turn_points"])

    enriched_turn_points = [
        {**tp, "speech": speeches[i] if i < len(speeches) else ""}
        for i, tp in enumerate(route["turn_points"])
    ]

    # SP speech = 출발 즉시 재생할 안내
    departure_speech = next(
        (tp["speech"] for tp in enriched_turn_points if tp["pointType"] == "SP"),
        f"출발합니다. {dest.name}까지 {route['total_distance_m']}m, 약 {route['total_time_min']}분 소요됩니다." if req.language == "ko"
        else f"Starting navigation to {dest.name}. {route['total_distance_m']}m, about {route['total_time_min']} minutes.",
    )

    return {
        "speech": departure_speech,
        "ar_command": {
            "type": "start_navigation",
            "route_line": route["route_line"],
            "turn_points": enriched_turn_points,
            "destination": {
                "lat": dest.pns_lat,
                "lng": dest.pns_lon,
                "name": dest.name,
            },
            "total_distance_m": route["total_distance_m"],
            "total_time_min": route["total_time_min"],
        },
    }
