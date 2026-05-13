"""
llm_extractor.py
공식 홈페이지 텍스트에서 GPT-4o-mini로 층별 매장 정보를 추출하고
verbatim_quote 검증으로 환각을 제거한다.
"""

import json
import os

from openai import AsyncOpenAI
from dotenv import load_dotenv

load_dotenv()

_client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))


def _validate_quote(verbatim_quote: str, raw_text: str) -> bool:
    """verbatim_quote가 raw_text의 실제 substring인지 검증."""
    if not verbatim_quote or not verbatim_quote.strip():
        return False
    norm_quote = " ".join(verbatim_quote.split())
    norm_text  = " ".join(raw_text.split())
    return norm_quote in norm_text


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
