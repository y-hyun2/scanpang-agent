"""
koreaexim.py
환전소(exchange) 상세 — 한국수출입은행 환율 OpenAPI.

https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON
    ?authkey={KEY}
    &searchdate=YYYYMMDD
    &data=AP01    (환율; AP02=대출금리, AP03=국제금리)

환율은 매장별이 아니라 글로벌 데이터이고 영업일에만 갱신되므로 일자별 1회만
fetch + 메모리·디스크 캐시. 영업외 시간/주말에는 마지막 영업일 값 사용.
"""

import asyncio
import json
import os
from datetime import datetime, timedelta
from typing import Optional

import httpx

_API_URL    = "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON"
_CACHE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "rag", "data", "exchange_rates_cache.json",
)

# 메모리 캐시: {"date": "20260513", "rates": [...]}
_MEM_CACHE: Optional[dict] = None

# 사용자에게 보여줄 주요 통화 (없으면 전체 반환)
_MAJOR_CURRENCIES = ["USD", "JPY(100)", "EUR", "CNH", "GBP", "AUD", "HKD", "SGD", "THB", "VND"]

# 통화별 국기 이모지 (UI 표시용)
_FLAG_BY_CCY = {
    "USD": "🇺🇸", "JPY(100)": "🇯🇵", "EUR": "🇪🇺", "CNH": "🇨🇳", "GBP": "🇬🇧",
    "AUD": "🇦🇺", "HKD": "🇭🇰", "SGD": "🇸🇬", "THB": "🇹🇭", "VND": "🇻🇳",
    "CAD": "🇨🇦", "CHF": "🇨🇭", "NZD": "🇳🇿", "TWD": "🇹🇼", "PHP": "🇵🇭",
    "MYR": "🇲🇾", "IDR(100)": "🇮🇩", "INR": "🇮🇳", "RUB": "🇷🇺", "AED": "🇦🇪",
    "SAR": "🇸🇦",
}


async def _fetch_rates(searchdate: Optional[str] = None) -> list[dict]:
    """한국수출입은행 API 호출. searchdate=YYYYMMDD, 없으면 오늘."""
    key = os.getenv("KOREAEXIM_API_KEY", "")
    if not key:
        print("[koreaexim] KOREAEXIM_API_KEY 없음")
        return []

    params = {"authkey": key, "data": "AP01"}
    if searchdate:
        params["searchdate"] = searchdate

    try:
        async with httpx.AsyncClient(timeout=15) as c:
            r = await c.get(_API_URL, params=params)
            r.raise_for_status()
            # API는 list 또는 None 반환 (영업외 시간/주말 = None)
            data = r.json()
    except Exception as e:
        print(f"[koreaexim] 호출 실패: {e}")
        return []

    if not isinstance(data, list):
        return []
    return data


async def _load_today_rates() -> tuple[str, list[dict]]:
    """오늘 환율을 메모리·디스크 캐시 우선으로 반환. 영업외시간엔 직전 영업일 시도."""
    global _MEM_CACHE
    today = datetime.now().strftime("%Y%m%d")

    # 메모리 캐시 hit (오늘 데이터)
    if _MEM_CACHE and _MEM_CACHE.get("date") == today and _MEM_CACHE.get("rates"):
        return today, _MEM_CACHE["rates"]

    # 디스크 캐시 hit
    if os.path.exists(_CACHE_PATH):
        try:
            with open(_CACHE_PATH, encoding="utf-8") as f:
                cache = json.load(f)
            if cache.get("date") == today and cache.get("rates"):
                _MEM_CACHE = cache
                return today, cache["rates"]
        except Exception as e:
            print(f"[koreaexim] 디스크 캐시 손상: {e}")

    # API 호출 (오늘 → 빈 결과면 어제·그제 시도, 주말/공휴일 대응)
    for offset in range(0, 4):
        date = (datetime.now() - timedelta(days=offset)).strftime("%Y%m%d")
        rates = await _fetch_rates(date)
        if rates:
            cache = {"date": date, "rates": rates}
            _MEM_CACHE = cache
            try:
                os.makedirs(os.path.dirname(_CACHE_PATH), exist_ok=True)
                with open(_CACHE_PATH, "w", encoding="utf-8") as f:
                    json.dump(cache, f, ensure_ascii=False)
            except Exception:
                pass
            print(f"[koreaexim] {date} 환율 {len(rates)}개 통화 로드")
            return date, rates
        await asyncio.sleep(0.3)

    return today, []


async def fetch(
    store_name: str,
    lat: float,
    lng: float,
    building_ufid: str = "",
    category_name: str = "",
) -> dict:
    """환전소 매장에 오늘의 환율 정보 첨부 — 글로벌 데이터라 매장명·좌표 무관."""
    date, raw = await _load_today_rates()
    if not raw:
        return {}

    # 주요 통화 우선, 없으면 전체 반환
    by_ccy = {r.get("cur_unit", ""): r for r in raw if r.get("result") == 1}
    primary = [by_ccy[c] for c in _MAJOR_CURRENCIES if c in by_ccy]
    selected = primary or list(by_ccy.values())[:10]

    rates_today = []
    for r in selected:
        ccy = r.get("cur_unit", "")
        rates_today.append({
            "ccy":           ccy,
            "name":           r.get("cur_nm", ""),
            "flag":           _FLAG_BY_CCY.get(ccy, ""),
            "base_rate":      r.get("deal_bas_r", ""),   # 매매기준율 (핵심)
            "buy_telegram":   r.get("ttb", ""),           # 송금 받을때
            "sell_telegram":  r.get("tts", ""),           # 송금 보낼때
        })

    return {
        "phone":       "",
        "addr":        "",
        "homepage":    "",
        "open_hours":  "",
        "image_urls":  [],
        "details": {
            "fetched_date":   date,
            "currency_count": len(rates_today),
            "rates_today":    rates_today,
        },
        "source": "koreaexim",
    }
