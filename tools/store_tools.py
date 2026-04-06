import json

import chromadb

from tools.place_tools import check_kakao_open_status

_chroma_client = None
_store_collection = None
_place_collection = None


def _get_store_collection():
    global _chroma_client, _store_collection
    if _store_collection is None:
        _chroma_client = chromadb.PersistentClient(path="./chroma_db")
        _store_collection = _chroma_client.get_or_create_collection("store_detail")
    return _store_collection


def _get_place_collection():
    global _chroma_client, _place_collection
    if _place_collection is None:
        _chroma_client = chromadb.PersistentClient(path="./chroma_db")
        _place_collection = _chroma_client.get_or_create_collection("place_info")
    return _place_collection


async def get_store_detail(place_id: str, store_name: str) -> dict:
    """
    개별 매장 상세 정보 조회.
    Chroma store_detail 컬렉션에서 캐시 hit이면 바로 반환,
    miss이면 place_info에서 건물 좌표를 꺼내 Kakao API 호출 후 캐싱.
    """
    collection = _get_store_collection()
    cache_id = f"{place_id}__{store_name}"

    result = collection.get(ids=[cache_id])
    if result["metadatas"]:
        return result["metadatas"][0]   # 캐시 hit

    # 캐시 miss → place_info에서 건물 좌표 조회
    place_result = _get_place_collection().get(ids=[place_id])
    if place_result["metadatas"]:
        meta = place_result["metadatas"][0]
        lat, lng = meta.get("lat", 37.5636), meta.get("lng", 126.9822)
    else:
        lat, lng = 37.5636, 126.9822  # 명동 기본값 fallback

    # Kakao API 호출
    kakao_data = await check_kakao_open_status(store_name, lat, lng)
    if not kakao_data:
        return {}

    # Chroma metadata는 str/int/float/bool만 허용
    metadata = {k: v for k, v in kakao_data.items() if isinstance(v, (str, int, float, bool))}
    metadata["place_id"] = place_id
    metadata["store_name"] = store_name

    collection.upsert(
        ids=[cache_id],
        documents=[store_name],
        metadatas=[metadata],
    )
    return metadata
