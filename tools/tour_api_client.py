"""
tools/tour_api_client.py
한국관광공사 TourAPI 4.0 클라이언트.

rag/build_place_db.py 에서 분리 — chromadb/sentence_transformers 없이
어디서든 import 가능하도록.
"""
import os
from typing import Optional

import httpx
from dotenv import load_dotenv

load_dotenv()

TOUR_API_KEY = os.getenv("TOUR_API_KEY", "")

# 12:관광지, 14:문화시설, 15:행사/공연/축제, 25:여행코스, 28:레포츠, 32:숙박
TOUR_CONTENT_TYPES = [12, 14, 15, 25, 28, 32]

INTRO_FIELD_MAP: dict[int, dict[str, str]] = {
    12: {"open_hours": "usetime",         "closed_days": "restdate",         "parking_info": "parking",         "admission_fee": ""},
    14: {"open_hours": "usetimeculture",   "closed_days": "restdateculture",  "parking_info": "parkingculture",  "admission_fee": "usefee"},
    15: {"open_hours": "playtime",         "closed_days": "",                 "parking_info": "",                "admission_fee": "usetimefestival"},
    25: {"open_hours": "taketime",         "closed_days": "",                 "parking_info": "",                "admission_fee": ""},
    28: {"open_hours": "usetimeleports",   "closed_days": "restdateleports",  "parking_info": "parkingleports",  "admission_fee": "usefeeleports"},
    32: {"open_hours": "checkintime",      "closed_days": "checkouttime",     "parking_info": "parkinghotel",    "admission_fee": ""},
    38: {"open_hours": "opentime",         "closed_days": "restdateshopping", "parking_info": "parkingshopping", "admission_fee": ""},
    39: {"open_hours": "opentimefood",     "closed_days": "restdatefood",     "parking_info": "parkingfood",     "admission_fee": ""},
}


async def fetch_tour_info(
    place_name: str,
    tour_keyword: Optional[str] = None,
    sigungu_code: Optional[int] = None,
) -> dict:
    """
    TourAPI searchKeyword2 → detailCommon2 + detailIntro2 순서로 호출.
    type 12(관광지)는 detailInfo2도 추가 호출해 admission_fee 보강.

    Returns:
        {title, addr, phone, overview, image_url, homepage,
         open_hours, closed_days, parking_info, admission_fee,
         content_id, content_type_id}
        매칭 실패 시 빈 dict.
    """
    base = "https://apis.data.go.kr/B551011/KorService2"
    common = {
        "serviceKey": TOUR_API_KEY,
        "MobileOS": "ETC",
        "MobileApp": "ScanPang",
        "_type": "json",
    }

    keyword = tour_keyword or place_name
    content_id = None
    content_type_id = None
    image_url = ""
    title = ""
    addr = ""
    phone = ""

    async with httpx.AsyncClient() as client:
        for ctype in TOUR_CONTENT_TYPES:
            params = {**common, "keyword": keyword, "contentTypeId": ctype, "numOfRows": 100}
            if sigungu_code:
                params["lDongRegnCd"] = 11
                params["lDongSignguCd"] = sigungu_code
            resp = await client.get(f"{base}/searchKeyword2", params=params)
            if resp.status_code != 200 or not resp.text.strip():
                return {}
            items_raw = resp.json().get("response", {}).get("body", {}).get("items", {})
            items = items_raw.get("item", []) if isinstance(items_raw, dict) else []
            if isinstance(items, dict):
                items = [items]
            if not items:
                continue
            matched = next((it for it in items if it.get("title", "") == keyword), None)
            selected = matched or items[0]
            content_id      = selected.get("contentid")
            content_type_id = ctype
            image_url       = selected.get("firstimage", "")
            title           = selected.get("title", "")
            addr            = selected.get("addr1", "")
            phone           = selected.get("tel", "")
            break

        if not content_id:
            return {}

        resp = await client.get(
            f"{base}/detailCommon2",
            params={**common, "contentId": content_id, "overviewYN": "Y"},
        )
        detail_raw = resp.json().get("response", {}).get("body", {}).get("items", {})
        detail = detail_raw.get("item", {}) if isinstance(detail_raw, dict) else {}
        if isinstance(detail, list):
            detail = detail[0] if detail else {}
        overview = detail.get("overview", "") if isinstance(detail, dict) else ""
        homepage = detail.get("homepage", "") if isinstance(detail, dict) else ""

        field_map = INTRO_FIELD_MAP.get(content_type_id, {})
        resp2 = await client.get(f"{base}/detailIntro2", params={
            **common, "contentId": content_id, "contentTypeId": content_type_id,
        })
        intro_raw = resp2.json().get("response", {}).get("body", {}).get("items", {})
        intro = intro_raw.get("item", {}) if isinstance(intro_raw, dict) else {}
        if isinstance(intro, list):
            intro = intro[0] if intro else {}

        open_hours    = intro.get(field_map.get("open_hours", ""), "")    if isinstance(intro, dict) else ""
        closed_days   = intro.get(field_map.get("closed_days", ""), "")   if isinstance(intro, dict) else ""
        parking_info  = intro.get(field_map.get("parking_info", ""), "")  if isinstance(intro, dict) else ""
        admission_fee = intro.get(field_map.get("admission_fee", ""), "") if isinstance(intro, dict) else ""

        # type 12(관광지)는 intro에 admission_fee 필드 없음 → detailInfo2에서 보강
        if content_type_id == 12 and not admission_fee:
            resp3 = await client.get(f"{base}/detailInfo2", params={
                **common, "contentId": content_id, "contentTypeId": content_type_id,
            })
            info_raw = resp3.json().get("response", {}).get("body", {}).get("items", {})
            info_items = info_raw.get("item", []) if isinstance(info_raw, dict) else []
            if isinstance(info_items, dict):
                info_items = [info_items]
            for item in info_items:
                if "입장" in (item.get("infoname") or ""):
                    admission_fee = item.get("infotext", "")
                    break

    return {
        "title":           title,
        "addr":            addr,
        "phone":           phone,
        "overview":        overview,
        "image_url":       image_url,
        "homepage":        homepage,
        "open_hours":      open_hours,
        "closed_days":     closed_days,
        "parking_info":    parking_info,
        "admission_fee":   admission_fee,
        "content_id":      content_id,
        "content_type_id": content_type_id,
    }