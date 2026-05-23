"""
tools/tour_api_place_client.py
관광지(12) · 문화시설(14) · 숙박(32) 전용 TourAPI 4.0 클라이언트.

제공 함수:
    location_based_list  — locationBasedList2 (좌표+반경으로 장소 목록)
    fetch_detail_common  — detailCommon2 (homepage, overview)
    fetch_detail_intro   — detailIntro2 (카테고리별 상세)
"""

import os

import httpx
from dotenv import load_dotenv

load_dotenv()

TOUR_API_KEY = os.getenv("TOUR_API_KEY", "")
_BASE = "https://apis.data.go.kr/B551011/KorService2"
_COMMON_PARAMS: dict[str, str] = {
    "MobileOS":  "ETC",
    "MobileApp": "ScanPang",
    "_type":     "json",
}

# contentTypeId별 detailIntro2 추출 필드
INTRO_FIELDS: dict[int, list[str]] = {
    12: ["infocenter", "parking", "restdate", "usetime"],
    14: ["infocenterculture", "parkingculture", "parkingfee", "restdateculture", "usefee", "usetimeculture"],
    32: ["checkintime", "checkouttime", "infocenterlodging", "parkinglodging", "reservationlodging", "reservationurl"],
}


def _params(**extra) -> dict:
    return {**_COMMON_PARAMS, "serviceKey": TOUR_API_KEY, **extra}


def _items(data: dict) -> list[dict]:
    raw = data.get("response", {}).get("body", {}).get("items", {})
    if not raw:
        return []
    items = raw.get("item", [])
    if isinstance(items, dict):
        return [items]
    return items or []


async def location_based_list(
    lat: float,
    lng: float,
    radius: int,
    content_type_id: int,
    max_items: int = 100,
) -> list[dict]:
    """
    locationBasedList2 — 좌표+반경으로 관광정보 목록 조회.

    Returns list of dicts:
        contentid, contenttypeid, title, addr1, mapx, mapy, tel, firstimage
    """
    if not TOUR_API_KEY:
        print("[tour_api_place] TOUR_API_KEY 없음")
        return []

    results: list[dict] = []
    page = 1
    async with httpx.AsyncClient(timeout=15) as client:
        while len(results) < max_items:
            try:
                resp = await client.get(
                    f"{_BASE}/locationBasedList2",
                    params=_params(
                        mapX=lng,
                        mapY=lat,
                        radius=radius,
                        contentTypeId=content_type_id,
                        numOfRows=100,
                        pageNo=page,
                    ),
                )
                resp.raise_for_status()
                items = _items(resp.json())
            except Exception as e:
                print(f"[tour_api_place] locationBasedList2 실패 (page={page}): {e}")
                break

            if not items:
                break

            for item in items:
                if len(results) >= max_items:
                    break
                cid = str(item.get("contentid", ""))
                if not cid:
                    continue
                results.append({
                    "contentid":     cid,
                    "contenttypeid": int(item.get("contenttypeid") or content_type_id),
                    "title":         (item.get("title") or "").strip(),
                    "addr1":         item.get("addr1", ""),
                    "mapx":          float(item.get("mapx") or 0),
                    "mapy":          float(item.get("mapy") or 0),
                    "tel":           item.get("tel", ""),
                    "firstimage":    item.get("firstimage", ""),
                })

            if len(items) < 100:
                break
            page += 1

    return results


async def fetch_detail_common(content_id: str) -> dict:
    """
    detailCommon2 — homepage, overview 조회.

    Returns {"homepage": str, "overview": str}
    """
    if not TOUR_API_KEY or not content_id:
        return {}
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.get(
                f"{_BASE}/detailCommon2",
                params=_params(contentId=content_id, overviewYN="Y", defaultYN="Y"),
            )
            resp.raise_for_status()
            items = _items(resp.json())
    except Exception as e:
        print(f"[tour_api_place] detailCommon2 실패 ({content_id}): {e}")
        return {}

    if not items:
        return {}
    item = items[0]
    # homepage 는 HTML 형태로 옴: <a href="http://...">...</a> → URL만 추출
    raw_homepage = item.get("homepage", "") or ""
    import re as _re
    href_match = _re.search(r'href=["\']([^"\']+)["\']', raw_homepage)
    homepage = href_match.group(1) if href_match else raw_homepage.strip()
    return {
        "homepage": homepage,
        "overview": item.get("overview", ""),
    }


async def fetch_detail_intro(content_id: str, content_type_id: int) -> dict:
    """
    detailIntro2 — 카테고리별 소개 정보 조회.

    Returns dict with fields defined in INTRO_FIELDS[content_type_id].
    """
    if not TOUR_API_KEY or not content_id:
        return {}
    fields = INTRO_FIELDS.get(content_type_id, [])
    if not fields:
        return {}
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.get(
                f"{_BASE}/detailIntro2",
                params=_params(contentId=content_id, contentTypeId=content_type_id),
            )
            resp.raise_for_status()
            items = _items(resp.json())
    except Exception as e:
        print(f"[tour_api_place] detailIntro2 실패 ({content_id}): {e}")
        return {}

    if not items:
        return {}
    item = items[0]
    return {f: item.get(f, "") for f in fields}