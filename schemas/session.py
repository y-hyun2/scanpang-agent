"""schemas/session.py — 대화 세션 관련 Pydantic 모델"""

from typing import List, Optional
from pydantic import BaseModel


class ConversationTurn(BaseModel):
    role: str           # "user" | "assistant"
    content: str
    agent: str = ""     # 어떤 서브에이전트가 응답했는지
    ts: int = 0         # Unix timestamp


class SessionContext(BaseModel):
    session_id: str
    turns: List[ConversationTurn] = []

    def to_prompt_text(self, max_turns: int = 5) -> str:
        """intent_classifier 프롬프트에 삽입할 대화 이력 텍스트 생성."""
        recent = self.turns[-max_turns:]
        if not recent:
            return ""
        lines = ["[이전 대화 이력]"]
        for t in recent:
            prefix = "사용자" if t.role == "user" else f"AI({t.agent})"
            lines.append(f"{prefix}: {t.content}")
        return "\n".join(lines)
