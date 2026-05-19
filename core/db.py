import os

import asyncpg
from dotenv import load_dotenv

load_dotenv()

_pool: asyncpg.Pool | None = None
_building_pool: asyncpg.Pool | None = None


async def get_pool() -> asyncpg.Pool:
    global _pool
    if _pool is None:
        url = os.getenv("SUPABASE_DATABASE_URL", "")
        if not url:
            raise RuntimeError("SUPABASE_DATABASE_URL이 .env에 없습니다.")
        _pool = await asyncpg.create_pool(url, min_size=1, max_size=5, max_inactive_connection_lifetime=60.0, statement_cache_size=0)
    return _pool


async def get_building_pool() -> asyncpg.Pool:
    global _building_pool
    if _building_pool is None:
        url = os.getenv("SUPABASE_BUILDING_DATABASE_URL", "")
        if not url:
            raise RuntimeError("SUPABASE_BUILDING_DATABASE_URL이 .env에 없습니다.")
        _building_pool = await asyncpg.create_pool(url, min_size=1, max_size=5, max_inactive_connection_lifetime=60.0, statement_cache_size=0)
    return _building_pool


async def close_pool():
    global _pool, _building_pool
    if _pool:
        await _pool.close()
        _pool = None
    if _building_pool:
        await _building_pool.close()
        _building_pool = None
