"""
tools/translation.py
장소 데이터 필드별 영문화 — 두 단계 전략.

1차: Google Places (name) / Google Geocoding (addr) — 공식 영문명/로마자 주소
2차: Naver Papago NMT — 1차 실패 or API 키 없을 때 fallback

translate_fields() 가 메인 진입점.
반환값은 translations JSONB에 바로 저장 가능한 dict.
예: {"name": "Halal Restaurant", "addr": "14 Myeongdong-gil...", "open_hours": "Daily 11:00~22:00"}
"""

import os
import httpx

GOOGLE_MAPS_API_KEY = os.getenv("GOOGLE_MAPS_API_KEY", "")
NAVER_CLIENT_ID     = os.getenv("NAVER_CLIENT_ID", "")
NAVER_CLIENT_SECRET = os.getenv("NAVER_CLIENT_SECRET", "")

_HTTP_TIMEOUT = 5.0


# ── Google APIs ──────────────────────────────────────────────────────────────

async def _google_place_name(store_name: str, lat: float, lng: float) -> str | None:
    """Google Places Find Place → 반경 100m 내 매칭 → 영문 이름."""
    if not GOOGLE_MAPS_API_KEY:
        return None
    try:
        async with httpx.AsyncClient(timeout=_HTTP_TIMEOUT) as client:
            r = await client.get(
                "https://maps.googleapis.com/maps/api/place/findplacefromtext/json",
                params={
                    "input":        store_name,
                    "inputtype":    "textquery",
                    "locationbias": f"circle:100@{lat},{lng}",
                    "fields":       "name",
                    "language":     "en",
                    "key":          GOOGLE_MAPS_API_KEY,
                },
            )
        candidates = r.json().get("candidates", [])
        if candidates:
            return candidates[0].get("name")
    except Exception:
        pass
    return None


async def _google_geocode_addr(lat: float, lng: float) -> str | None:
    """Google Geocoding API → 좌표 기반 영문(로마자) 주소."""
    if not GOOGLE_MAPS_API_KEY:
        return None
    try:
        async with httpx.AsyncClient(timeout=_HTTP_TIMEOUT) as client:
            r = await client.get(
                "https://maps.googleapis.com/maps/api/geocode/json",
                params={
                    "latlng":   f"{lat},{lng}",
                    "language": "en",
                    "key":      GOOGLE_MAPS_API_KEY,
                },
            )
        results = r.json().get("results", [])
        if results:
            return results[0].get("formatted_address")
    except Exception:
        pass
    return None


# ── Papago ───────────────────────────────────────────────────────────────────

async def _papago(text: str) -> str | None:
    """Naver Papago NMT ko → en. 빈 텍스트·키 없으면 None."""
    if not NAVER_CLIENT_ID or not NAVER_CLIENT_SECRET:
        return None
    if not text or not text.strip():
        return None
    try:
        async with httpx.AsyncClient(timeout=_HTTP_TIMEOUT) as client:
            r = await client.post(
                "https://naveropenapi.apigw.ntruss.com/nmt/v1/translation",
                headers={
                    "X-NCP-APIGW-API-KEY-ID": NAVER_CLIENT_ID,
                    "X-NCP-APIGW-API-KEY":    NAVER_CLIENT_SECRET,
                    "Content-Type":           "application/json",
                },
                json={"source": "ko", "target": "en", "text": text},
            )
        return r.json()["message"]["result"]["translatedText"]
    except Exception:
        pass
    return None


# ── 메인 진입점 ───────────────────────────────────────────────────────────────

async def translate_fields(
    fields: dict[str, str],
    lat: float = 0.0,
    lng: float = 0.0,
    lang: str = "en",
) -> dict[str, str]:
    """
    {field_name: 한국어_텍스트} → {field_name: 번역된_텍스트}

    필드별 전략:
    - "name"  : Google Places → Papago
    - "addr"  : Google Geocoding → Papago
    - 나머지   : Papago (open_hours, closed_days, description 등)

    번역 실패 or 빈 값인 필드는 결과에 포함되지 않음.
    호출자는 결과를 translations[lang]에 병합해 저장.
    """
    if lang == "ko":
        return {}

    result: dict[str, str] = {}

    for field, text in fields.items():
        if not text or not text.strip():
            continue

        translated: str | None = None

        if field == "name":
            translated = await _google_place_name(text, lat, lng)
            if not translated:
                translated = await _papago(text)

        elif field == "addr":
            translated = await _google_geocode_addr(lat, lng)
            if not translated:
                translated = await _papago(text)

        else:
            translated = await _papago(text)

        if translated:
            result[field] = translated

    return result


def apply_lang(
    original: str,
    translations: dict,
    lang: str,
    field: str,
) -> str:
    """
    DB의 translations JSONB에서 lang의 field 번역값을 꺼냄.
    없으면 original(한국어) 그대로 반환.
    """
    if lang == "ko" or not translations:
        return original
    return translations.get(lang, {}).get(field) or original