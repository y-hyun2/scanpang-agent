"""
seoul_openapi.py
화장실 / 물품보관함 fetcher.

화장실(restroom):
  data.go.kr 행정안전부 공중화장실정보 (전국 53k+건, 단일 출처)
물품보관함(locker):
  서울시 OpenAPI OA-22731 (subwayLockerInfo)
"""

from tools.convenience_tools import seoul_locker_search
from tools.details_fetchers._public_restroom_cache import find_nearest as find_public_restroom

_SEARCH_RADIUS = 500


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """매장명/카테고리로 화장실/물품보관함 어느 쪽인지 판별 후 적절한 source 순회."""
    if not (lat and lng):
        return {}

    is_locker   = any(kw in store_name for kw in ("보관", "락커", "보관함"))
    is_restroom = ("화장실" in store_name or "화장실" in (category_name or ""))

    if is_locker:
        rows = await seoul_locker_search(lat, lng, _SEARCH_RADIUS)
        return _pick_locker(rows, store_name)

    if is_restroom:
        # data.go.kr 행정안전부 공중화장실 (전국 53k+건 단일 출처)
        public_rows = await find_public_restroom(lat, lng, radius_m=_SEARCH_RADIUS)
        return _pick_public_restroom(public_rows, store_name)

    return {}


def _name_match(rows: list[dict], store_name: str, key: str = "name") -> dict | None:
    """매장명 부분일치 1건 우선, 없으면 첫 번째(가장 가까운)."""
    if not rows:
        return None
    norm = (store_name or "").strip().lower()
    if norm:
        for r in rows:
            if norm in (r.get(key, "") or "").lower():
                return r
    return rows[0]


def _pick_public_restroom(rows: list[dict], store_name: str) -> dict:
    """data.go.kr 공중화장실 1건 → fetcher 표준 출력. RSTRM_NM으로 매칭."""
    if not rows:
        return {}
    sel = _name_match(rows, store_name, key="RSTRM_NM") or rows[0]

    # boolean 필드는 "Y"/"N" 문자열
    def yn(v): return (v or "").upper() == "Y"

    return {
        "phone":       sel.get("TELNO", "") or "",
        "addr":        sel.get("LCTN_ROAD_NM_ADDR", "") or sel.get("LCTN_LOTNO_ADDR", "") or "",
        "homepage":    "",
        "open_hours":  sel.get("OPN_HR_DTL", "") or sel.get("OPN_HR", ""),
        "image_urls":  [],
        "details": {
            "name":              sel.get("RSTRM_NM", ""),
            "type":              sel.get("SE_NM", ""),               # 공중화장실/개방화장실
            "mng_inst":          sel.get("MNG_INST_NM", ""),         # 관리기관
            "male_toilt_cnt":    sel.get("MALE_TOILT_CNT", ""),
            "female_toilt_cnt":  sel.get("FEMALE_TOILT_CNT", ""),
            "has_disabled":      yn(sel.get("MALE_FRDBL_TOILT_CNT")) or yn(sel.get("FEMALE_FRDBL_TOILT_CNT")),
            "has_child":         (int(sel.get("MALE_CHLD_TOILT_CNT") or 0) > 0
                                   or int(sel.get("FEMALE_CHLD_TOILT_CNT") or 0) > 0),
            "has_diaper_table":  yn(sel.get("DIAP_EXCHCON_EN")),     # 기저귀 교환대
            "has_emergency_bell":yn(sel.get("EMRGNCBLL_INSTL_YN")),  # 비상벨
            "has_cctv":          yn(sel.get("RSTRM_ENTRAN_CCTV_INSTL_EN")),
            "waste_method":      sel.get("WSTE_PRCS_MTH_NM", ""),    # 수세식/...
            "distance_m":        sel.get("_distance_m"),
        },
        "source": "public_restroom",
    }


def _pick_locker(rows: list[dict], store_name: str) -> dict:
    sel = _name_match(rows, store_name, key="name")
    if not sel:
        return {}
    extra = sel.get("extra", {}) or {}
    return {
        "phone":       sel.get("phone", ""),
        "addr":        sel.get("address", ""),
        "homepage":    "",
        "open_hours":  sel.get("open_hours", ""),
        "image_urls":  [],
        "details": {
            "small":           extra.get("small", ""),
            "medium":          extra.get("medium", ""),
            "large":           extra.get("large", ""),
            "location_detail": extra.get("location_detail", ""),
            "distance_m":      sel.get("distance_m"),
        },
        "source": "seoul_openapi",
    }
