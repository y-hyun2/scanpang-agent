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
