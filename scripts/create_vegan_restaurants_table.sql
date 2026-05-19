-- vegan_restaurants 테이블 생성
-- 구조: myeongdong_vegan.json 기준 (store_details 유사 구조)
-- details JSONB 안에 vegan_level, vegan_menu, restaurant_type 포함

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS vegan_restaurants (
    id              TEXT PRIMARY KEY,        -- UUID
    place_id        TEXT,                    -- Kakao place ID
    store_name      TEXT,
    category        TEXT,                    -- "채식전문 | 음식점"
    addr            TEXT,
    phone           TEXT,
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    geom            GEOGRAPHY(POINT, 4326),
    place_url       TEXT,
    category_key    TEXT,
    details         JSONB,                   -- {vegan_level, vegan_menu, restaurant_type}
    open_hours      TEXT,
    closed_days     TEXT,
    homepage        TEXT,
    image_urls      JSONB,
    floor           TEXT,
    source          TEXT,
    last_updated    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_vegan_restaurants_geom
    ON vegan_restaurants USING GIST(geom);

CREATE INDEX IF NOT EXISTS idx_vegan_restaurants_vegan_level
    ON vegan_restaurants ((details->>'vegan_level'));
