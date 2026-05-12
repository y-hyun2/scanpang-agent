"""
llm_extractor.py
검색 결과에서 GPT-4o-mini로 매장 정보를 추출하고 verbatim_quote 검증으로 환각을 제거한다.
LLM은 "수집된 텍스트에서 매장명 파싱"만 담당하며 학습 지식 사용을 프롬프트로 금지한다.
"""

import json
import os

from openai import AsyncOpenAI
from dotenv import load_dotenv

load_dotenv()

_client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))

_SYSTEM_PROMPT = """\
당신은 텍스트 파서입니다. 주어진 검색 결과 텍스트에서 건물에 입점한 매장 정보를 추출합니다.

반드시 지켜야 할 규칙:
1. 학습된 지식(훈련 데이터)은 절대 사용 금지. 오직 아래 입력 텍스트에 명시된 내용만 사용.
2. 입력에 명시적으로 등장하는 매장만 출력. 추론 금지.
3. 각 매장마다 verbatim_quote 필수: 입력 텍스트에서 그대로 가져온 30자 이내 substring.
4. 층 정보(floor): "3F", "3층", "지하1층", "B1", "3rd floor" 형태로 본문에 명시된 경우에만 채움. 아니면 반드시 null.
5. 추정, 유추, 가정 절대 금지. "카페니까 1층일 것" 같은 추론 불허.

출력 형식 (JSON):
{
  "stores": [
    {
      "name": "매장명",
      "floor": "3층" | null,
      "category": "카테고리" | null,
      "source_url": "출처 URL",
      "source_intent": "intent 값",
      "verbatim_quote": "입력 텍스트에서 그대로 따온 30자 이내 문자열"
    }
  ]
}
"""


def _pack_results(search_results: list[dict], max_items: int = 30) -> tuple[str, str]:
    """
    검색 결과를 인덱스 붙인 텍스트로 패킹.
    Returns: (packed_text, raw_text_for_validation)
    """
    lines = []
    raw_parts = []
    for i, r in enumerate(search_results[:max_items]):
        title   = r.get("title", "")
        snippet = r.get("snippet", "")
        url     = r.get("url", "")
        intent  = r.get("intent", "")

        line = f"[{i}] ({intent}) {title} — {snippet} | url={url}"
        lines.append(line)
        raw_parts.append(f"{title} {snippet}")

    return "\n".join(lines), " ".join(raw_parts)


def _validate_quote(verbatim_quote: str, raw_text: str) -> bool:
    """verbatim_quote가 raw_text의 실제 substring인지 검증."""
    if not verbatim_quote or not verbatim_quote.strip():
        return False
    # 공백 정규화 후 검사
    norm_quote = " ".join(verbatim_quote.split())
    norm_text  = " ".join(raw_text.split())
    return norm_quote in norm_text


_BUILDING_INFO_SYSTEM_PROMPT = """\
당신은 텍스트 파서입니다. 주어진 검색 결과에서 건물/장소의 운영 정보를 추출합니다.

반드시 지켜야 할 규칙:
1. 학습된 지식(훈련 데이터)은 절대 사용 금지. 오직 아래 입력 텍스트에 명시된 내용만 사용.
2. 입력에 명시적으로 등장하는 정보만 출력. 추론 금지.
3. 각 필드마다 _quote 필드 필수: 입력 텍스트에서 그대로 가져온 30자 이내 substring.
4. 정보가 없으면 null.

출력 형식 (JSON):
{
  "open_hours":        "운영시간" | null,
  "open_hours_quote":  "verbatim_quote" | null,
  "closed_days":       "휴무일" | null,
  "closed_days_quote": "verbatim_quote" | null,
  "parking_info":       "주차 정보" | null,
  "parking_info_quote": "verbatim_quote" | null,
  "admission_fee":       "입장료" | null,
  "admission_fee_quote": "verbatim_quote" | null,
  "homepage":       "공식 홈페이지 URL" | null,
  "homepage_quote": "verbatim_quote" | null
}
"""

_BUILDING_FIELDS = ("open_hours", "closed_days", "parking_info", "admission_fee", "homepage")


async def extract_building_info(
    building_name: str,
    search_results: list[dict],
) -> dict:
    """
    검색 결과에서 LLM으로 건물 운영정보를 추출하고 verbatim_quote로 검증한다.

    Returns:
        {open_hours, closed_days, parking_info, admission_fee, homepage}
        verbatim_quote 검증 실패 필드는 빈 문자열.
    """
    if not search_results:
        return {}

    packed_text, raw_text = _pack_results(search_results)
    building_label = building_name if building_name else "이름 없는 건물"
    user_content = (
        f"건물명: {building_label}\n\n"
        f"=== 검색 결과 (이 텍스트만 참고) ===\n{packed_text}"
    )

    try:
        response = await _client.chat.completions.create(
            model="gpt-4o-mini",
            temperature=0,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": _BUILDING_INFO_SYSTEM_PROMPT},
                {"role": "user",   "content": user_content},
            ],
            max_tokens=800,
        )
        data = json.loads(response.choices[0].message.content)
    except Exception as e:
        print(f"[llm_extractor] extract_building_info 실패: {e}")
        return {}

    result: dict = {}
    for field in _BUILDING_FIELDS:
        value = data.get(field) or ""
        quote = data.get(f"{field}_quote") or ""
        result[field] = value if (value and _validate_quote(quote, raw_text)) else ""

    print(f"[llm_extractor] building_info 추출: "
          f"{[f for f in _BUILDING_FIELDS if result.get(f)]}")
    return result


async def extract_stores(
    building_name: str,
    search_results: list[dict],
) -> list[dict]:
    """
    검색 결과에서 LLM으로 매장 정보를 추출하고 verbatim_quote로 검증한다.

    Args:
        building_name: 건물명 (빈 문자열 가능)
        search_results: search_collector.collect() 반환값

    Returns:
        verbatim_quote 검증 통과한 매장 dict 리스트
        각 항목: {name, floor, category, source_url, source_intent, verbatim_quote}
    """
    if not search_results:
        return []

    packed_text, raw_text = _pack_results(search_results)

    building_label = building_name if building_name else "이름 없는 건물"
    user_content = (
        f"건물명: {building_label}\n\n"
        f"=== 검색 결과 (이 텍스트만 참고) ===\n{packed_text}"
    )

    try:
        response = await _client.chat.completions.create(
            model="gpt-4o-mini",
            temperature=0,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": _SYSTEM_PROMPT},
                {"role": "user",   "content": user_content},
            ],
            max_tokens=2000,
        )
        data = json.loads(response.choices[0].message.content)
        raw_stores = data.get("stores", [])
    except Exception as e:
        print(f"[llm_extractor] LLM 호출 실패: {e}")
        return []

    # verbatim_quote 검증 — 실패한 매장은 폐기
    validated: list[dict] = []
    discarded = 0
    for store in raw_stores:
        quote = store.get("verbatim_quote", "")
        if _validate_quote(quote, raw_text):
            validated.append(store)
        else:
            discarded += 1
            name_repr  = repr(store.get("name", "?"))
            quote_repr = repr(quote[:40])
            print(f"[llm_extractor] verbatim_quote 검증 실패 → 폐기: "
                  f"{name_repr} (quote={quote_repr})")

    print(f"[llm_extractor] 추출={len(raw_stores)}, 검증통과={len(validated)}, 폐기={discarded}")
    return validated


_HOMEPAGE_FLOOR_SYSTEM_PROMPT = """\
당신은 텍스트 파서입니다. 건물 공식 홈페이지 텍스트에서 층별 매장 목록을 추출합니다.

반드시 지켜야 할 규칙:
1. 학습된 지식 절대 사용 금지. 입력 텍스트에 명시된 내용만 사용.
2. 층(floor)과 매장명(name)이 텍스트에서 명확히 연결된 경우만 추출.
3. 각 매장마다 verbatim_quote 필수: 입력 텍스트의 30자 이내 substring.
4. 카테고리가 명시된 경우만 채움. 없으면 빈 문자열.

출력 형식 (JSON):
{
  "floor_info": [
    {
      "floor": "B2",
      "stores": [
        {"name": "매장명", "category": "", "verbatim_quote": "원문 30자 이내"}
      ]
    }
  ]
}
"""


async def extract_floor_info_from_homepage(
    building_name: str,
    homepage_text: str,
) -> list[dict]:
    """
    홈페이지 텍스트에서 LLM으로 층별 매장 목록을 추출한다.

    Returns:
        [{"floor": str, "stores": [{"name": str, "category": str}]}]
        verbatim_quote 검증 실패 매장은 제외.
    """
    if not homepage_text:
        return []

    truncated = homepage_text[:6_000]
    user_content = (
        f"건물명: {building_name or '이름 없는 건물'}\n\n"
        f"=== 홈페이지 텍스트 ===\n{truncated}"
    )

    try:
        response = await _client.chat.completions.create(
            model="gpt-4o-mini",
            temperature=0,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": _HOMEPAGE_FLOOR_SYSTEM_PROMPT},
                {"role": "user",   "content": user_content},
            ],
            max_tokens=2000,
        )
        data = json.loads(response.choices[0].message.content)
        raw_floor_info = data.get("floor_info", [])
    except Exception as e:
        print(f"[llm_extractor] extract_floor_info_from_homepage 실패: {e}")
        return []

    validated: list[dict] = []
    for floor_item in raw_floor_info:
        valid_stores = []
        for store in floor_item.get("stores", []):
            quote = store.get("verbatim_quote", "")
            if _validate_quote(quote, truncated):
                valid_stores.append({
                    "name":     store.get("name", ""),
                    "category": store.get("category", ""),
                })
        if valid_stores:
            validated.append({"floor": floor_item.get("floor", ""), "stores": valid_stores})

    total = sum(len(f["stores"]) for f in validated)
    print(f"[llm_extractor] homepage floor_info: {len(validated)}개 층, {total}개 매장")
    return validated
