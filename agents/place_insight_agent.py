import json
import os
from datetime import datetime, timezone

from openai import AsyncOpenAI
from dotenv import load_dotenv

from core.db import get_pool
from schemas.place import PlaceRequest
from tools.building_raycast import fetch_building_by_ufid

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
openai_client  = AsyncOpenAI(api_key=OPENAI_API_KEY)

_SCAN_LOG_PATH = "logs/scan_events.jsonl"
_STALE_DAYS    = 30


def _log_scan_event(ufid: str, lat: float, lng: float) -> None:
    try:
        os.makedirs("logs", exist_ok=True)
        entry = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "ufid":      ufid,
            "lat":       lat,
            "lng":       lng,
        }
        with open(_SCAN_LOG_PATH, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except Exception as e:
        print(f"[place_insight] 스캔 로그 기록 실패: {e}")


def _compute_distance_m(
    user_lat: float | None, user_lng: float | None,
    poi_lat: float | None, poi_lng: float | None,
) -> float | None:
    """Haversine 거리(m). 한 좌표라도 None 이면 None 반환."""
    if user_lat is None or user_lng is None or poi_lat is None or poi_lng is None:
        return None
    import math
    R = 6371000.0
    lat1 = math.radians(float(user_lat))
    lat2 = math.radians(float(poi_lat))
    dlat = lat2 - lat1
    dlng = math.radians(float(poi_lng) - float(user_lng))
    a = math.sin(dlat/2)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlng/2)**2
    return round(2 * R * math.asin(math.sqrt(a)), 1)


def _is_stale(last_updated) -> bool:
    if not last_updated:
        return True
    try:
        dt = last_updated if isinstance(last_updated, datetime) else datetime.fromisoformat(last_updated)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return (datetime.now(timezone.utc) - dt).days >= _STALE_DAYS
    except Exception:
        return True


async def _fetch_place_info(ufid: str) -> dict:
    """Supabase place_info 테이블에서 ufid로 직접 조회."""
    try:
        pool = await get_pool()
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT * FROM place_info WHERE ufid = $1", ufid
            )
        return dict(row) if row else {}
    except Exception as e:
        print(f"[place_insight] Supabase 조회 실패: {e}")
        return {}


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
            {"role": "user",   "content": context},
        ],
        max_tokens=300,
    )
    return response.choices[0].message.content.strip()


# ── Follow-up 질문 생성 ────────────────────────────────────────────────────────

def generate_follow_ups(user_message: str, place_data: dict) -> list[str]:
    suggestions = []
    msg_lower = user_message.lower()

    if "floor" not in msg_lower and place_data.get("floor_info"):
        suggestions.append("What's on each floor?")
    if "halal" not in msg_lower and place_data.get("halal_info"):
        suggestions.append("Where can I find halal food nearby?")
    if "fee" not in msg_lower and "price" not in msg_lower and place_data.get("admission_fee"):
        suggestions.append("How much is the admission fee?")
    if "park" not in msg_lower and place_data.get("parking_info"):
        suggestions.append("Is there parking available?")
    if "eat" not in msg_lower and "restaurant" not in msg_lower:
        suggestions.append("What's nearby to eat?")
    if "prayer" not in msg_lower:
        suggestions.append("Is there a prayer room nearby?")

    return suggestions[:3]


# ── Main agent ────────────────────────────────────────────────────────────────

async def run_place_insight_agent(req: PlaceRequest) -> dict:
    # 1) 바라보는 건물 식별 — frontend 가 건물 핀 탭 시 ufid 전달. ufid 없으면
    # 식별 불가 (raycast 경로는 제거됨, frontend 는 항상 ufid 를 보내야 한다).
    vworld_meta = await fetch_building_by_ufid(req.ufid) if req.ufid else None

    place_data          = {}
    bld_name_from_vworld = ""

    if vworld_meta:
        bld_name_from_vworld = vworld_meta.get("bld_nm") or "주변 건물"
        ufid = vworld_meta.get("ufid", "")

        if ufid:
            _log_scan_event(ufid, req.user_lat, req.user_lng)

        # 2) Supabase place_info 직접 조회 (ufid PK)
        if ufid:
            place_data = await _fetch_place_info(ufid)

        # 3) cache miss → 백그라운드 파이프라인 트리거
        if not place_data and ufid:
            _trigger_background_pipeline(ufid)
            return _partial_response(bld_name_from_vworld, ufid)

        # 4) cache hit — 오래된 데이터면 백그라운드 갱신 예약
        if place_data and ufid and _is_stale(place_data.get("last_updated")):
            print(f"[place_insight] stale 데이터 — 백그라운드 갱신 예약: {ufid}")
            _trigger_background_pipeline(ufid)

    if not place_data and bld_name_from_vworld:
        return _partial_response(bld_name_from_vworld)

    if not place_data:
        return {
            "ar_overlay": {
                "name": "", "category": "", "floor_info": [],
                "halal_info": "", "image_url": "", "homepage": "",
                "open_hours": "", "closed_days": "", "parking_info": "",
                "admission_fee": "", "address": "", "phone": "",
                "is_estimated": False,
                "status": "partial", "floor_info_loading": False,
                "coverage_rate": None, "last_updated": None,
            },
            "docent": {
                "speech": "죄송합니다, 이 건물에 대한 정보가 아직 없습니다.",
                "follow_up_suggestions": [],
            },
        }

    # JSONB 컬럼 — asyncpg 가 환경(statement_cache_size=0 + jsonb codec 미설정)에
    # 따라 list 가 아니라 raw JSON 문자열로 반환할 때가 있다. 그대로 응답에 넣으면
    # frontend(List<FloorInfo> 기대)가 deserialize 실패해 빈 list 로 처리해 층별
    # 정보가 텅 빈 채 표시됨. str 면 json.loads 로 dict/list 복원.
    import json as _json_safe
    raw_floor = place_data.get("floor_info")
    if isinstance(raw_floor, str):
        try:
            floor_info = _json_safe.loads(raw_floor) or []
        except (ValueError, TypeError):
            floor_info = []
    else:
        floor_info = raw_floor or []
    halal_info = place_data.get("halal_info", "")

    # last_updated: asyncpg가 datetime 객체로 반환
    last_updated_val = place_data.get("last_updated")
    last_updated_str = last_updated_val.isoformat() if isinstance(last_updated_val, datetime) else last_updated_val

    ar_overlay = {
        "name":              place_data.get("name_ko", ""),
        "category":          place_data.get("category", ""),
        "floor_info":        floor_info,
        "halal_info":        halal_info,
        "image_url":         place_data.get("image_url", ""),
        "homepage":          place_data.get("homepage", ""),
        "open_hours":        place_data.get("open_hours", ""),
        "closed_days":       place_data.get("closed_days", ""),
        "parking_info":      place_data.get("parking_info", ""),
        "admission_fee":     place_data.get("admission_fee", ""),
        "address":           place_data.get("addr", ""),
        "phone":             place_data.get("phone", ""),
        "ufid":              place_data.get("ufid", "") or (vworld_meta.get("ufid", "") if vworld_meta else ""),
        "distance_m":        _compute_distance_m(
            req.user_lat, req.user_lng,
            place_data.get("lat") if place_data.get("lat") is not None
                else (vworld_meta.get("center_lat") if vworld_meta else None),
            place_data.get("lng") if place_data.get("lng") is not None
                else (vworld_meta.get("center_lng") if vworld_meta else None),
        ),
        "is_estimated":      False,
        "status":            "ready",
        "floor_info_loading": False,
        "coverage_rate":     place_data.get("coverage_rate"),
        "last_updated":      last_updated_str,
    }

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

    speech     = await llm_generate_docent(context, req.language)
    follow_ups = generate_follow_ups(req.user_message, {
        "floor_info":    floor_info,
        "halal_info":    halal_info,
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


def _trigger_background_pipeline(ufid: str) -> None:
    try:
        from rag.automation.worker import enqueue
        if enqueue(ufid):
            print(f"[place_insight] 자동화 파이프라인 enqueue: {ufid}")
    except Exception as e:
        print(f"[place_insight] worker enqueue 실패 (무시): {e}")


# ── Chat 진입점 (orchestrator 라우팅용) ────────────────────────────────────
# `run_place_insight_agent` 는 /place/store endpoint 가 ufid 와 함께 호출하는
# building scan 전용. orchestrator 채팅 흐름은 ufid 없이 lat/lng + 메시지만
# 들고 오므로 별도 진입점이 필요하다. 매장 정보(상호명 발화)는 여기서 다루지
# 않고 convenience_agent(=search) 로 라우팅된다 — 이 함수는 관광지/랜드마크/
# 지역 정보에만 답한다.

_PLACE_CHAT_SYSTEM = (
    "You are a friendly Seoul tour guide for foreign visitors. "
    "Answer questions about tourist attractions, landmarks, neighborhoods, "
    "historic sites, and famous places in Seoul using your general knowledge. "
    "Respond in 2-3 short sentences suitable for text-to-speech. "
    "Be warm, concise, and informative. "
    "If you don't recognize the place, say so honestly rather than guessing."
)


async def run_place_chat_agent(
    message: str,
    lat: float,
    lng: float,
    language: str = "ko",
) -> dict:
    """관광지/랜드마크/지역 정보 응답 — orchestrator chat 전용 진입점.

    Returns:
        {"speech": str, "raw": dict}
    """
    lang_map = {"ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese"}
    response_lang_label = lang_map.get(language, language)

    system_prompt = f"{_PLACE_CHAT_SYSTEM} Always respond in {response_lang_label}."
    user_prompt   = f"User location: lat={lat}, lng={lng}\nUser question: {message}"

    try:
        response = await openai_client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user",   "content": user_prompt},
            ],
            max_tokens=300,
        )
        speech = response.choices[0].message.content.strip()
    except Exception as e:
        print(f"[place_chat] LLM 호출 실패: {e}")
        fallback = {
            "ko": "죄송합니다, 잠시 후 다시 시도해 주세요.",
            "en": "Sorry, please try again in a moment.",
            "ar": "آسف، يرجى المحاولة مرة أخرى بعد قليل.",
            "ja": "申し訳ありません、しばらくしてからもう一度お試しください。",
            "zh": "抱歉，请稍后再试。",
        }
        speech = fallback.get(language, fallback["en"])

    return {"speech": speech, "raw": {}}


def _partial_response(name: str, ufid: str = "") -> dict:
    return {
        "ar_overlay": {
            "name":              name,
            "category":          "",
            "floor_info":        [],
            "halal_info":        "",
            "image_url":         "",
            "homepage":          "",
            "open_hours":        "",
            "closed_days":       "",
            "parking_info":      "",
            "admission_fee":     "",
            "address":           "",
            "phone":             "",
            "ufid":              ufid,
            "distance_m":        None,
            "is_estimated":      True,
            "status":            "partial",
            "floor_info_loading": True,
            "coverage_rate":     None,
            "last_updated":      None,
        },
        "docent": {
            "speech": f"{name}입니다. 매장 정보를 수집 중입니다. 잠시 후 다시 시도해 주세요.",
            "follow_up_suggestions": [],
        },
    }
