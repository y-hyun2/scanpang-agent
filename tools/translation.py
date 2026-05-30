"""
tools/translation.py
장소 데이터 필드별 영문화

- addr  : 주소기반산업지원서비스 영문주소 API → DeepL fallback
- 나머지 : DeepL API Free (name, category, open_hours 등)
"""

import asyncio
import os
import re
import httpx

JUSO_ADDR_API_KEY = os.getenv("JUSO_ADDR_API_KEY", "")
DEEPL_API_KEY     = os.getenv("DEEPL_API_KEY", "")

_HTTP_TIMEOUT = 6.0

_RE_KOREAN = re.compile(r"[가-힣㄰-㆏ᄀ-ᇿ]")


def _has_korean(text: str) -> bool:
    return bool(_RE_KOREAN.search(text))


# ── 주소기반산업지원서비스 영문주소 API ────────────────────────────────────────

async def _juso_addr(addr_ko: str) -> str | None:
    """주소기반산업지원서비스 영문주소 API → 공식 영문 도로명주소."""
    if not JUSO_ADDR_API_KEY or not addr_ko or not addr_ko.strip():
        return None
    try:
        async with httpx.AsyncClient(timeout=_HTTP_TIMEOUT) as client:
            r = await client.get(
                "https://business.juso.go.kr/addrlink/addrEngApi.do",
                params={
                    "confmKey":     JUSO_ADDR_API_KEY,
                    "currentPage":  1,
                    "countPerPage": 1,
                    "keyword":      addr_ko,
                    "resultType":   "json",
                },
            )
        juso_list = r.json().get("results", {}).get("juso", [])
        if juso_list:
            road = juso_list[0].get("roadAddr") or juso_list[0].get("engAddr") or ""
            if road and not _has_korean(road):
                return road
    except Exception:
        pass
    return None


# ── DeepL ─────────────────────────────────────────────────────────────────────

async def _deepl_translate(text: str) -> str | None:
    """DeepL API Free ko → en-US."""
    if not DEEPL_API_KEY or not text or not text.strip():
        return None
    try:
        async with httpx.AsyncClient(timeout=_HTTP_TIMEOUT) as client:
            r = await client.post(
                "https://api-free.deepl.com/v2/translate",
                headers={"Authorization": f"DeepL-Auth-Key {DEEPL_API_KEY}"},
                json={
                    "text":        [text],
                    "source_lang": "KO",
                    "target_lang": "EN-US",
                },
            )
        return r.json()["translations"][0]["text"]
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
    - "addr" : 주소기반산업지원서비스 영문주소 API → DeepL fallback
    - 나머지  : DeepL (name, category, open_hours, closed_days 등)

    번역 실패 or 빈 값인 필드는 결과에 포함되지 않음.
    """
    if lang == "ko":
        return {}

    async def _translate_field(field: str, text: str) -> tuple[str, str | None]:
        if not text or not text.strip():
            return field, None
        if field == "addr":
            translated = await _juso_addr(text) or await _deepl_translate(text)
        else:
            translated = await _deepl_translate(text)
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
