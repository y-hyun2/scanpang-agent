"""
core/auth.py
Supabase Auth JWT 검증 + user_id 추출 dependency.

Supabase 가 발급한 access token (JWT) 을 검증해서 user_id (auth.users.id, UUID) 를
반환한다. 프론트엔드 Retrofit interceptor 가 `Authorization: Bearer <jwt>` 헤더로
보내주고 있음.

검증 방식: JWKS (asymmetric, RS256/ES256).
- Supabase 가 2025년에 Legacy HS256 → JWT Signing Keys (asymmetric) 로 마이그.
- 공개키는 `https://<project>.supabase.co/auth/v1/.well-known/jwks.json` 에서 받음.
- PyJWKClient 가 알아서 fetch + cache + kid 매칭 처리.

실패 시 401 거부.
"""

from __future__ import annotations

import os

import jwt
from dotenv import load_dotenv
from fastapi import Header, HTTPException, status
from jwt import PyJWKClient

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL", "").rstrip("/")
if not SUPABASE_URL:
    print("[auth] ⚠️ SUPABASE_URL 환경변수 미설정 — JWT 검증 호출 시 모두 401 처리됨")

JWKS_URL = f"{SUPABASE_URL}/auth/v1/.well-known/jwks.json" if SUPABASE_URL else ""

# PyJWKClient: 공개키 fetch + 1시간 캐싱. kid 매칭은 get_signing_key_from_jwt 가 알아서.
_jwks_client: PyJWKClient | None = (
    PyJWKClient(JWKS_URL, cache_keys=True, lifespan=3600) if JWKS_URL else None
)


async def get_current_user_id(authorization: str = Header(default="")) -> str:
    """
    Bearer JWT 검증 → Supabase user_id (UUID string) 반환.

    FastAPI dependency 로 사용:
        @app.post("/foo")
        async def foo(user_id: str = Depends(get_current_user_id)):
            ...

    실패 케이스 (모두 401):
    - Authorization 헤더 없음/형식 오류
    - JWT 서명 무효
    - 만료 (exp claim 초과)
    - audience 불일치
    """
    if _jwks_client is None:
        raise HTTPException(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "Auth not configured (SUPABASE_URL missing)",
        )
    if not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token")

    token = authorization[7:].strip()
    try:
        signing_key = _jwks_client.get_signing_key_from_jwt(token).key
        payload = jwt.decode(
            token,
            signing_key,
            algorithms=["RS256", "ES256"],
            audience="authenticated",
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token expired")
    except jwt.InvalidTokenError as e:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, f"Invalid token: {e}")

    user_id = payload.get("sub")
    if not user_id:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token missing sub claim")
    return user_id
