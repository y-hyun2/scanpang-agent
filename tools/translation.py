"""
tools/translation.py
장소 데이터 필드별 영문화 — 두 단계 전략.

1차: Google Places (name) / Google Geocoding (addr) — 공식 영문명/로마자 주소
2차: Google Cloud Translation API — 1차 실패 or 그 외 필드 fallback

translate_fields() 가 메인 진입점.
반환값은 translations JSONB에 바로 저장 가능한 dict.
예: {"name": "Halal Restaurant", "addr": "14 Myeongdong-gil...", "open_hours": "Daily 11:00~22:00"}
"""

import asyncio
import os
import re
import httpx

GOOGLE_MAPS_API_KEY = os.getenv("GOOGLE_MAPS_API_KEY", "")

_HTTP_TIMEOUT = 5.0

_RE_KOREAN = re.compile(r"[가-힣㄰-㆏ᄀ-ᇿ]")


def _has_korean(text: str) -> bool:
    return bool(_RE_KOREAN.search(text))


# ── Google Places ─────────────────────────────────────────────────────────────

async def _google_place_name(store_name: str, lat: float, lng: float) -> str | None:
    """Google Places Find Place → 반경 100m 내 매칭 → 영문 이름.
    반환값에 한국어가 포함되면 None 처리해 Translation API로 fallback."""
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
            name = candidates[0].get("name")
            # 한국어 문자가 섞인 결과는 Translation API로 재시도
            if name and not _has_korean(name):
                return name
    except Exception:
        pass
    return None


# ── Google Geocoding ──────────────────────────────────────────────────────────

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


# ── Google Cloud Translation ──────────────────────────────────────────────────

async def _google_translate(text: str) -> str | None:
    """Google Cloud Translation Basic API ko → en."""
    if not GOOGLE_MAPS_API_KEY or not text or not text.strip():
        return None
    try:
        async with httpx.AsyncClient(timeout=_HTTP_TIMEOUT) as client:
            r = await client.post(
                "https://translation.googleapis.com/language/translate/v2",
                params={"key": GOOGLE_MAPS_API_KEY},
                json={"q": text, "source": "ko", "target": "en", "format": "text"},
            )
        return r.json()["data"]["translations"][0]["translatedText"]
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
    - "name"  : Google Places → Google Translate
    - "addr"  : Google Geocoding → Google Translate
    - 나머지   : Google Translate (open_hours, closed_days, description 등)

    번역 실패 or 빈 값인 필드는 결과에 포함되지 않음.
    호출자는 결과를 translations[lang]에 병합해 저장.
    """
    if lang == "ko":
        return {}

    async def _translate_field(field: str, text: str) -> tuple[str, str | None]:
        if not text or not text.strip():
            return field, None
        if field == "name":
            translated = await _google_place_name(text, lat, lng) or await _google_translate(text)
        elif field == "addr":
            translated = await _google_geocode_addr(lat, lng) or await _google_translate(text)
        else:
            translated = await _google_translate(text)
        return field, translated

    pairs = await asyncio.gather(*[_translate_field(f, t) for f, t in fields.items()])
    return {f: t for f, t in pairs if t}


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
