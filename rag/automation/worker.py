"""
worker.py
asyncio 기반 백그라운드 워커. FastAPI lifespan에서 start/stop을 호출한다.
building_key 큐를 소비하며 pipeline.process_one_building을 순차 실행한다.
"""

import asyncio
import time
from typing import Optional

from rag.automation.pipeline import process_one_building

# building_key → 처리 시작 timestamp (1시간 dedupe)
_in_progress: dict[str, float] = {}
_queue: asyncio.Queue = asyncio.Queue()
_worker_task: Optional[asyncio.Task] = None

_DEDUPE_TTL_SEC = 3600  # 1시간 이내 동일 building_key 재처리 방지


def enqueue(building_key: str) -> bool:
    """
    building_key를 워커 큐에 추가한다.
    1시간 이내에 이미 처리 중이거나 처리 완료된 building_key는 무시한다.

    Returns:
        True: 큐에 추가됨
        False: dedupe로 무시됨
    """
    now = time.time()
    last = _in_progress.get(building_key, 0)
    if now - last < _DEDUPE_TTL_SEC:
        return False

    _in_progress[building_key] = now
    _queue.put_nowait(building_key)
    print(f"[worker] enqueue: {building_key}")
    return True


async def _worker_loop() -> None:
    print("[worker] 워커 루프 시작")
    while True:
        try:
            building_key = await _queue.get()
        except asyncio.CancelledError:
            print("[worker] 워커 루프 종료")
            break

        try:
            await process_one_building(building_key)
        except Exception as e:
            print(f"[worker] process_one_building 예외 ({building_key}): {e}")
        finally:
            _queue.task_done()

        # 만료된 dedupe 항목 정리 (메모리 누수 방지)
        now = time.time()
        expired = [u for u, ts in _in_progress.items() if now - ts >= _DEDUPE_TTL_SEC]
        for u in expired:
            del _in_progress[u]


async def start_worker() -> None:
    """FastAPI startup 시 호출. 워커 태스크를 생성한다."""
    global _worker_task
    if _worker_task is None or _worker_task.done():
        _worker_task = asyncio.create_task(_worker_loop())
        print("[worker] 백그라운드 워커 시작")


async def stop_worker() -> None:
    """FastAPI shutdown 시 호출. 워커 태스크를 취소하고 큐를 비운다."""
    global _worker_task
    if _worker_task and not _worker_task.done():
        _worker_task.cancel()
        try:
            await _worker_task
        except asyncio.CancelledError:
            pass
    _worker_task = None
    print("[worker] 백그라운드 워커 종료")
