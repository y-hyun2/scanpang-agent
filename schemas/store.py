from pydantic import BaseModel


class StoreRequest(BaseModel):
    place_id: str       # 어느 건물 소속인지
    store_name: str     # 매장명 (소상공인 API의 bizesNm)


class StoreDetail(BaseModel):
    store_name: str
    place_id: str
    name_ko: str = ""
    category: str = ""
    addr: str = ""
    phone: str = ""
    place_url: str = ""
