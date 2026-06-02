import json
import re
from datetime import datetime, timezone

from dotenv import load_dotenv

from core.db import get_pool
from schemas.place import PlaceRequest
from tools.building_raycast import fetch_building_by_key
from tools.llm_client import call_llm
from tools.tour_api_client import fetch_tour_info

load_dotenv()

_STALE_DAYS = 30


async def _record_user_scan(user_id: str, building_key: str) -> None:
    """
    사용자가 AR 카메라로 건물을 스캔(=`/place/query` 호출) 한 사실을
    user_building_scans 테이블에 누적 기록.
    """
    if not user_id or not building_key:
        return
    try:
        pool = await get_pool()
        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO user_building_scans (user_id, building_key)
                VALUES ($1, $2)
                ON CONFLICT (user_id, building_key) DO UPDATE
                   SET scan_count = user_building_scans.scan_count + 1,
                       last_scanned_at = now()
                """,
                user_id, building_key,
            )
    except Exception as e:
        print(f"[place_insight] 스캔 기록 실패 (무시): {e}")


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


async def _fetch_place_info(building_key: str) -> dict:
    """placeinfo 테이블에서 building_key로 직접 조회."""
    try:
        pool = await get_pool()
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT * FROM placeinfo WHERE building_key = $1", building_key
            )
        return dict(row) if row else {}
    except Exception as e:
        print(f"[place_insight] Supabase 조회 실패: {e}")
        return {}


# ── LLM: docent 해설 생성 ──────────────────────────────────────────────────────

async def llm_generate_docent(context: str, language: str, user_id: str = "", model: str = "gpt-5.4-mini") -> str:
    lang_map = {"ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese"}
    response_lang_label = lang_map.get(language, language)

    system_prompt = (
        "You are a friendly AR tour guide for foreign visitors in Seoul. "
        "Respond in 2-3 short sentences suitable for text-to-speech. "
        f"Always respond in {response_lang_label}. "
        "Use the provided building data for specifics (floors, hours, halal info). "
        "For well-known landmarks, you MAY add brief interesting background "
        "(history, cultural significance) from your general knowledge to enrich the narration. "
        "If the place is NOT a well-known landmark, do not add background knowledge — "
        "describe only from the provided data. "
        "But do NOT invent specifics that are not provided — especially exact admission "
        "fees, prices, or opening hours. If a specific number is not given, do not state one. "
        "If halal_info is provided, always mention it. "
        "Be warm, concise, and helpful for a solo traveler."
    )

    return await call_llm(
        user_id=user_id,
        purpose="docent",
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": context},
        ],
        max_tokens=300,
    )


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

async def run_place_insight_agent(req: PlaceRequest, progress_cb=None, user_id: str = "") -> dict:
    async def _progress(pct: int, msg: str):
        if progress_cb:
            await progress_cb(pct, msg)

    # 1) 바라보는 건물 식별 — frontend 가 건물 핀 탭 시 ufid 전달. ufid 없으면
    # 식별 불가 (raycast 경로는 제거됨, frontend 는 항상 ufid 를 보내야 한다).
    await _progress(5, "건물 식별 중...")
    vworld_meta = await fetch_building_by_key(req.ufid) if req.ufid else None
    await _progress(25, "건물 정보 조회 중...")

    place_data           = {}
    bld_name_from_vworld = ""

    if vworld_meta:
        bld_name_from_vworld = vworld_meta.get("bld_nm") or "주변 건물"
        building_key = vworld_meta.get("building_key", "")

        if building_key:
            await _record_user_scan(user_id, building_key)

        # 2) placeinfo 직접 조회 (building_key PK)
        if building_key:
            place_data = await _fetch_place_info(building_key)
        await _progress(45, "데이터 처리 중...")

        # 3) cache miss → 백그라운드 파이프라인 트리거
        if not place_data and building_key:
            _trigger_background_pipeline(building_key)
            return _partial_response(bld_name_from_vworld, building_key)

        # 4) cache hit — 오래된 데이터면 백그라운드 갱신 예약
        if place_data and building_key and _is_stale(place_data.get("last_updated")):
            print(f"[place_insight] stale 데이터 — 백그라운드 갱신 예약: {building_key}")
            _trigger_background_pipeline(building_key)

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

    await _progress(65, "AR 오버레이 구성 중...")
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
        "ufid":              place_data.get("building_key", "") or (vworld_meta.get("building_key", "") if vworld_meta else ""),
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

    await _progress(80, "도슨트 생성 중...")
    try:
        speech = await llm_generate_docent(context, req.language, user_id=user_id)
    except Exception as e:
        print(f"[place_insight] docent 생성 실패 (fallback 사용): {e}")
        fallback = {
            "ko": f"{place_data.get('name_ko', '이 장소')}입니다.",
            "en": f"This is {place_data.get('name_ko', 'this place')}.",
            "ar": f"هذا هو {place_data.get('name_ko', 'هذا المكان')}.",
            "ja": f"こちらは{place_data.get('name_ko', 'この場所')}です。",
            "zh": f"这里是{place_data.get('name_ko', '此地点')}。",
        }
        speech = fallback.get(req.language, fallback["en"])
    await _progress(95, "완료 처리 중...")
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


def _trigger_background_pipeline(building_key: str) -> None:
    try:
        from rag.automation.worker import enqueue
        if enqueue(building_key):
            print(f"[place_insight] 자동화 파이프라인 enqueue: {building_key}")
    except Exception as e:
        print(f"[place_insight] worker enqueue 실패 (무시): {e}")


# ── Chat 진입점 (orchestrator 라우팅용) ────────────────────────────────────
# `run_place_insight_agent` 는 /place/store endpoint 가 ufid 와 함께 호출하는
# building scan 전용. orchestrator 채팅 흐름은 ufid 없이 lat/lng + 메시지만
# 들고 오므로 별도 진입점이 필요하다. 매장 정보(상호명 발화)는 여기서 다루지
# 않고 search_agent 로 라우팅된다 — 이 함수는 관광지/랜드마크/
# 지역 정보에만 답한다.

_PLACE_CHAT_SYSTEM = (
    "You are a friendly Seoul tour guide for foreign visitors. "
    "Answer questions about tourist attractions, landmarks, neighborhoods, "
    "historic sites, and famous places in Seoul using your general knowledge. "
    "Respond in 2-3 short sentences suitable for text-to-speech. "
    "Be warm, concise, and informative. "
    "Do NOT assert specific numbers you are not certain of — especially exact admission fees, "
    "ticket prices, or opening hours. For such details, give general guidance and suggest checking "
    "the official website or on-site information instead of stating a precise figure. "
    "If you don't recognize the place, say so honestly rather than guessing."
)


# 입장료·요금·운영시간 등 "사실 수치"를 묻는 질의 감지 → 출처 기반 grounding 트리거
_FACT_RE = re.compile(
    r"입장료|관람료|이용료|요금|가격|얼마|운영\s*시간|영업\s*시간|개장|폐장|몇\s*시|휴무|"
    r"fee|price|admission|cost|ticket|hour|opening|close",
    re.I,
)


async def _extract_landmark_name(message: str, model: str) -> str:
    """사실 질의에서 장소/랜드마크 고유명사 1개만 추출 (TourAPI 검색용 클린 키워드)."""
    try:
        content = await call_llm(
            user_id="", purpose="place_name_extract", model=model, record=False,
            response_format={"type": "json_object"}, temperature=0, max_tokens=40,
            messages=[
                {"role": "system", "content":
                 '사용자 문장에서 장소/랜드마크 고유명사 1개만 추출. '
                 '{"place_name": "<이름 또는 빈문자열>"} JSON으로만 답하라.'},
                {"role": "user", "content": message},
            ],
        )
        return (json.loads(content or "{}").get("place_name") or "").strip()
    except Exception:
        return ""


async def _ground_place_facts(name: str) -> tuple[str, dict]:
    """TourAPI 우선, 없으면 store_details 에서 요금/시간/공식링크 확보.

    Returns: (LLM 주입용 검증데이터 블록, raw 소스 dict). 못 찾으면 ("", {}).
    """
    facts: list[str] = []
    src: dict = {}

    # 1) TourAPI (관광 랜드마크 1순위 출처)
    try:
        info = await fetch_tour_info(name)
    except Exception as e:
        print(f"[place_chat] TourAPI 실패: {e}")
        info = {}
    if info:
        if info.get("admission_fee"): facts.append(f"입장료/요금: {info['admission_fee']}")
        if info.get("open_hours"):    facts.append(f"운영시간: {info['open_hours']}")
        if info.get("closed_days"):   facts.append(f"휴무: {info['closed_days']}")
        if facts:
            src = {"source": "TourAPI", "source_url": info.get("homepage") or "",
                   "admission_fee": info.get("admission_fee", ""),
                   "open_hours": info.get("open_hours", "")}

    # 2) storedetails fallback (영업시간은 store_hours 정규화 테이블에서)
    if not facts:
        try:
            from tools.open_hours_parser import format_schedule_text
            pool = await get_pool()
            async with pool.acquire() as conn:
                row = await conn.fetchrow(
                    "SELECT id, details, homepage, place_url "
                    "FROM storedetails WHERE store_name ILIKE $1 LIMIT 1",
                    f"%{name}%",
                )
                hrs = ""
                if row:
                    hrs_rows = await conn.fetch(
                        "SELECT day_of_week, open_time, close_time, last_order "
                        "FROM store_hours WHERE store_id = $1",
                        row["id"],
                    )
                    hrs = format_schedule_text(hrs_rows)
            if row:
                details = row["details"] or {}
                if isinstance(details, str):
                    details = json.loads(details or "{}")
                fee = (details or {}).get("admission_fee", "")
                if fee: facts.append(f"입장료/요금: {fee}")
                if hrs: facts.append(f"운영시간: {hrs}")
                if facts:
                    src = {"source": "storedetails",
                           "source_url": row["homepage"] or row["place_url"] or "",
                           "admission_fee": fee, "open_hours": hrs}
        except Exception as e:
            print(f"[place_chat] storedetails 조회 실패: {e}")

    if not facts:
        return "", {}
    block = ("검증된 데이터 (아래 수치만 사용하라 — 요금/시간 등은 절대 임의로 만들지 말 것):\n"
             + "\n".join(facts) + "\n\n")
    return block, src


async def _fetch_store_brief(place_id: str) -> dict:
    """storedetails 에서 매장 기본정보(카테고리/영업시간) 조회. focus(매장 핀)용."""
    try:
        pool = await get_pool()
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT * FROM storedetails WHERE place_id = $1 LIMIT 1", place_id
            )
        return dict(row) if row else {}
    except Exception as e:
        print(f"[place_chat] storedetails 조회 실패: {e}")
        return {}


async def _build_focus_block(focus: dict) -> str:
    """화면에 보이는 핀 1개(focus) → '사용자가 지금 보는 건물/매장' 컨텍스트 블록.
    focus = {type: 'building'|'store', id, name}. DB 정보는 best-effort 보강."""
    fname = (focus.get("name") or "").strip()
    if not fname:
        return ""
    ftype = focus.get("type", "")
    fid = (focus.get("id") or "").strip()
    details: dict = {}
    if ftype == "building" and fid:
        details = await _fetch_place_info(fid)
    elif ftype == "store" and fid:
        details = await _fetch_store_brief(fid)

    extra = []
    if details.get("category"):   extra.append(f"카테고리: {details['category']}")
    if details.get("open_hours"): extra.append(f"영업시간: {details['open_hours']}")
    kind = "건물" if ftype == "building" else "매장"
    return (
        f"[사용자가 지금 화면에서 보고 있는 {kind}] {fname}"
        + (f" ({', '.join(extra)})" if extra else "")
        + f"\n사용자가 '이 {kind}' 또는 지시어로 물으면 바로 이 곳을 가리킨다. 이 곳을 설명하라. "
          "위 DB 정보(영업시간/카테고리)는 그대로 쓰고, 특징·배경은 일반지식으로 보강하되 "
          "가짜 구체수치(요금/시간/평점)는 만들지 말 것.\n\n"
    )


async def run_place_chat_agent(
    message: str,
    lat: float,
    lng: float,
    language: str = "ko",
    user_id: str = "",
    model: str = "gpt-5.4-mini",
    focus: dict | None = None,
) -> dict:
    """관광지/랜드마크/지역 정보 응답 — orchestrator chat 전용 진입점.
    focus(화면의 핀 1개)가 오면 '이 건물/매장'을 그 핀으로 특정해 설명한다.

    Returns:
        {"speech": str, "raw": dict}
    """
    lang_map = {"ko": "Korean", "en": "English", "ar": "Arabic", "ja": "Japanese", "zh": "Chinese"}
    response_lang_label = lang_map.get(language, language)

    # 화면에 보이는 핀 1개 → '이 건물/매장' 지시어 해소용 컨텍스트
    focus_block = await _build_focus_block(focus) if focus else ""

    # 사실 수치(요금/시간) 질의면 출처(TourAPI→store_details)에서 검증 데이터 확보 → 주입
    grounded_block, source = "", {}
    if _FACT_RE.search(message or ""):
        name = (focus.get("name") if focus else "") or await _extract_landmark_name(message, model)
        if name:
            grounded_block, source = await _ground_place_facts(name)

    system_prompt = f"{_PLACE_CHAT_SYSTEM} Always respond in {response_lang_label}."
    user_prompt   = (
        f"{focus_block}{grounded_block}User location: lat={lat}, lng={lng}\nUser question: {message}"
    )

    try:
        speech = await call_llm(
            user_id=user_id,
            purpose="place_chat",
            model=model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user",   "content": user_prompt},
            ],
            max_tokens=300,
        )
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

    return {"speech": speech, "raw": source}


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
