import json
import re
from dotenv import load_dotenv
import h3
from core.db import get_pool
from tools.navigation_tools import search_poi, get_pedestrian_route
from tools.llm_client import call_llm
from schemas.navigation import NavRequest, RouteRequest

load_dotenv()


def _haversine_m(lat1, lng1, lat2, lng2):
    """두 좌표 간 거리(m). 계산 불가 시 None."""
    from math import radians, sin, cos, atan2, sqrt
    try:
        R = 6371000
        dlat = radians(float(lat2) - float(lat1))
        dlng = radians(float(lng2) - float(lng1))
        a = sin(dlat/2)**2 + cos(radians(float(lat1))) * cos(radians(float(lat2))) * sin(dlng/2)**2
        return R * 2 * atan2(sqrt(a), sqrt(1-a))
    except Exception:
        return None


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
Selection rule:
- Prefer the POI whose name matches the keyword most exactly (the main place itself, not a parking lot/annex/station).
- When several POIs match the name similarly, choose the CLOSEST one (smallest distance).
Each line is "index. name | address | distance".
Return only the index number (0-based integer), nothing else.

POI list:
{poi_list}
"""

# ── Step 2: 경로 턴포인트별 TTS 안내 문구 생성 ────────────────────────────────
SPEECH_PROMPT = """Generate short TTS navigation instructions for each turn point.
IMPORTANT: You MUST respond entirely in the language specified below. Do not mix languages.
Output language: {language} (ko=Korean, en=English, ar=Arabic)

Respond with a JSON array of strings — one string per turn point, in order. No explanation, no markdown.

Rules:
- SP (start point): departure announcement including total distance and time
- GP (guidance point): 1-sentence turn instruction
  - Use nearPoiName as landmark if available (translate direction words to output language)
  - Else use intersectionName
  - Else use description only
  - facilityType 125=overpass, 126=underpass, 127=stairs — must mention in output language
- EP (end point): arrival announcement in output language

Turn points:
{turn_points_json}

Destination: {destination_name}
Total: {total_distance_m}m, {total_time_min} minutes
"""


async def _fetch_nearby_buildings(lat: float, lng: float) -> list[dict]:
    """
    사용자 위치 기준 H3 resolution-10 셀 + 1-ring(7개 셀) 내 건물을 조회한다.
    place_info 테이블과 LEFT JOIN해 탐색모드와 동일한 카테고리·층별 매장 데이터를 포함한다.
    """
    try:
        cell = h3.latlng_to_cell(lat, lng, 10)
        cells = list(h3.grid_disk(cell, 1))

        pool = await get_pool()
        async with pool.acquire() as conn:
            rows = await conn.fetch(
                """
                SELECT
                    b.ufid,
                    b.bld_nm,
                    ST_Y(ST_Centroid(b.geom)) AS lat,
                    ST_X(ST_Centroid(b.geom)) AS lng,
                    p.name_ko,
                    p.category,
                    p.floor_info
                FROM buildings b
                LEFT JOIN place_info p ON b.ufid = p.ufid
                WHERE b.h3_index_10 = ANY($1::varchar[])
                  AND b.bld_nm IS NOT NULL
                LIMIT 20
                """,
                cells,
            )

        return [
            {
                "ufid": r["ufid"],
                "bld_nm": r["name_ko"] or r["bld_nm"] or "",
                "category": r["category"] or "",
                "floor_info": r["floor_info"] or [],
                "lat": float(r["lat"]),
                "lng": float(r["lng"]),
            }
            for r in rows
        ]
    except Exception as e:
        print(f"[navigation] 주변 건물 조회 실패 (무시): {e}")
        return []


async def run_route_search(req: NavRequest, user_id: str = "", language_override: str | None = None) -> dict:
    """
    경로 안내 1단계: 메시지 파싱 → POI 검색 → 후보 목록 + 추천 반환.
    사용자가 앱에서 확인/선택 후 /navigation/route 호출.
    (이전 이름: run_search_agent — 시설 검색 search_agent 와 구분 위해 개명)
    """
    # Step 0: 키워드 + 의도 + 언어 추출
    intent_content = await call_llm(
        user_id=user_id,
        purpose="nav_intent",
        messages=[
            {"role": "system", "content": INTENT_PROMPT},
            {"role": "user", "content": req.message},
        ],
        model="gpt-5.4-mini",
        temperature=0,
    )
    try:
        raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", intent_content)
        intent = json.loads(raw)
    except json.JSONDecodeError:
        intent = {"keyword": req.message, "intent": "specific_place", "language": "ko"}

    keyword = intent.get("keyword", req.message)
    intent_type = intent.get("intent", "specific_place")
    language = language_override or intent.get("language", "ko")

    # Step 1: POI 검색 (5km → 전국 fallback)
    pois = await search_poi(keyword, req.lat, req.lng)
    if not pois:
        msg = "목적지를 찾지 못했어요. 다시 말씀해주세요." if language == "ko" else "Sorry, I couldn't find that destination. Please try again."
        return {"speech": msg, "candidates": [], "intent": intent_type, "language": language}

    # Step 2: 후보 목록 구성 + 추천 선택
    recommended_idx = 0

    if intent_type == "specific_place" and len(pois) > 1:
        # LLM이 가장 적합한 POI 선택
        def _poi_line(i, p):
            d = _haversine_m(req.lat, req.lng, p.get("pnsLat"), p.get("pnsLon"))
            dist = f" | {int(d)}m" if d is not None else ""
            return f"{i}. {p['name']} | {p['address']}{dist}"
        poi_list_str = "\n".join(_poi_line(i, p) for i, p in enumerate(pois))
        select_content = await call_llm(
            user_id=user_id,
            purpose="nav_poi_select",
            messages=[
                {"role": "user", "content": SELECT_POI_PROMPT.format(
                    keyword=keyword, lat=req.lat, lng=req.lng, poi_list=poi_list_str,
                )},
            ],
            model="gpt-5.4-mini",
            temperature=0,
        )
        try:
            recommended_idx = int(select_content)
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
        # 프론트(ArExploreScreen.extractNavDest) 가 raw_data["lat"]/["lng"]/["name"]
        # 를 1순위로 본다. candidates 안의 pns_lat/pns_lon 은 미스매치라 버튼 렌더링
        # 실패. 추천 후보 좌표를 top-level 로 같이 노출해 "X(으)로 안내" 버튼이 뜨게.
        "lat": rec["pns_lat"],
        "lng": rec["pns_lon"],
        "name": rec["name"],
    }


async def run_route_agent(req: RouteRequest, user_id: str = "") -> dict:
    """
    2단계: 사용자가 확정한 POI → 보행자 경로 계산 → 턴별 TTS 포함 응답
    """
    dest = req.destination

    # Step 3: 보행자 경로 계산 + 주변 건물 조회 (병렬)
    import asyncio
    route, nearby_buildings = await asyncio.gather(
        get_pedestrian_route(
            start_lat=req.lat,
            start_lng=req.lng,
            end_poi_id=dest.poi_id,
            end_lat=dest.pns_lat,
            end_lng=dest.pns_lon,
            end_name=dest.name,
            search_option=0,
        ),
        _fetch_nearby_buildings(req.lat, req.lng),
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

    speech_content = await call_llm(
        user_id=user_id,
        purpose="nav_speech",
        messages=[
            {"role": "user", "content": SPEECH_PROMPT.format(
                language=req.language,
                turn_points_json=json.dumps(turn_points_for_llm, ensure_ascii=False, indent=2),
                destination_name=dest.name,
                total_distance_m=route["total_distance_m"],
                total_time_min=route["total_time_min"],
            )},
        ],
        model="gpt-5.4-mini",
        temperature=0,
    )
    try:
        raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", speech_content)
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
            # T-map raw GeoJSON features (프론트 parseTmapRoute용 — 원본 인터리브 순서 보존)
            "tmap_features": route.get("tmap_features", []),
            "destination": {
                "lat": dest.pns_lat,
                "lng": dest.pns_lon,
                "name": dest.name,
            },
            "total_distance_m": route["total_distance_m"],
            "total_time_min": route["total_time_min"],
            # 탐색모드와 동일한 건물 인식 — H3 셀 기반 주변 건물 + place_info 데이터
            "nearby_buildings": nearby_buildings,
        },
    }



# ── AR 길안내 중 어시스턴트 (nav_guide) ─────────────────────────────────────
# 사용자가 길안내 진행 중 채팅으로 "여기서 좌회전 맞아?" "거의 다 왔어?" 같이
# 현재 경로 상태에 대해 묻는 케이스. frontend 가 nav_context (현재 턴/거리/
# 목적지/남은 시간) 를 같이 보내고 LLM 이 그걸 보고 응답.

_NAV_GUIDE_SYSTEM = """당신은 AR 도보 길안내 중인 사용자의 음성/채팅 어시스턴트입니다.
사용자가 현재 진행 중인 경로에 대해 묻습니다. 아래 컨텍스트를 보고 짧게(1-2문장,
TTS 친화) 답하세요. 친근하고 안심되는 톤.

응답 언어: {language}  (ko=한국어, en=English, ar=Arabic, ja=日本語, zh=中文)

길안내 상태:
- 길안내 진행 중: {is_routing}
- 목적지: {destination}
- 다음 동작: {direction} ({current_distance_m}m 앞 — {current_speech})
- 그 다음 동작: {next_direction} ({next_distance_m}m 앞)
- 목적지까지 남은 거리: {remaining_distance_m}m
- 예상 소요: 약 {remaining_time_min}분

규칙:
- "여기서 좌회전 맞아?" → 다음 동작의 direction 과 비교해 yes/no + 거리 명시.
  예: "네, 80m 앞에서 좌회전이에요." / "아뇨, 직진하시다 다음 갈림길에서 우회전이에요."
- "거의 다 왔어?" / "얼마나 남았어?" → remaining_distance_m + remaining_time_min 기준.
- "다음은?" / "이 갈림길에서?" → current_speech 또는 next 안내.
- 길안내 진행 중이 아니면(is_routing=false) 정중하게 "지금 길안내 중이 아니에요" 안내.
- 컨텍스트 없는 일반 질문이 섞여 들어오면 길안내 관점에서만 답하고 더 일반적인 질문은
  검색 메뉴로 안내."""


async def run_nav_guide_agent(
    message: str,
    nav_context: dict | None,
    language: str = "ko",
    user_id: str = "",
) -> dict:
    """길안내 중 어시스턴트 — nav_context + 사용자 발화 → 짧은 응답.
    nav_context 없으면 안내 메시지 1줄 반환 (LLM 호출 X)."""
    ctx = nav_context or {}
    if not ctx.get("is_routing"):
        msg = {
            "ko": "지금 길안내 중이 아니에요. 검색에서 목적지를 먼저 정해주세요.",
            "en": "You're not currently in navigation. Please choose a destination first.",
            "ar": "أنت لست في وضع التنقل حاليًا. الرجاء اختيار وجهة أولاً.",
            "ja": "現在ナビゲーション中ではありません。先に目的地を選んでください。",
            "zh": "您目前未在导航中。请先选择目的地。",
        }.get(language, "You're not currently in navigation.")
        return {"speech": msg, "raw": ctx}

    lang_label = {"ko": "ko", "en": "en", "ar": "ar", "ja": "ja", "zh": "zh"}.get(language, "ko")

    # 프론트가 보낸 remaining_time_min 이 0 인데 remaining_distance_m 가 양수면
    # int 잘림 또는 미설정으로 본다. 도보 평균 80m/min(=4.8km/h) 기준으로 재계산.
    # 1분 미만이면 "약 1분" 으로 표시되도록 ceil.
    remaining_distance_m = int(ctx.get("remaining_distance_m", 0) or 0)
    remaining_time_min = int(ctx.get("remaining_time_min", 0) or 0)
    if remaining_distance_m > 0 and remaining_time_min == 0:
        import math
        remaining_time_min = max(1, math.ceil(remaining_distance_m / 80))

    system = _NAV_GUIDE_SYSTEM.format(
        language          = lang_label,
        is_routing        = ctx.get("is_routing", False),
        destination       = ctx.get("destination_name", "") or "(없음)",
        direction         = ctx.get("direction", "") or "(미정)",
        current_distance_m = ctx.get("current_distance_m", 0),
        current_speech    = ctx.get("current_speech", "") or "(없음)",
        next_direction    = ctx.get("next_direction", "") or "(없음)",
        next_distance_m   = ctx.get("next_distance_m", 0),
        remaining_distance_m = remaining_distance_m,
        remaining_time_min   = remaining_time_min,
    )
    try:
        speech = await call_llm(
            user_id=user_id,
            purpose="nav_guide",
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": message},
            ],
            model="gpt-5.4-mini",
            temperature=0,
        )
        return {"speech": speech, "raw": ctx}
    except Exception as e:
        return {"speech": f"답변 생성 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.", "raw": {"error": str(e)}}
