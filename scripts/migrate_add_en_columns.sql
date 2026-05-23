-- 다국어 지원: translations JSONB 컬럼 추가
-- 실행: psql $SUPABASE_DATABASE_URL -f scripts/migrate_add_en_columns.sql
--
-- 구조: {"en": {"name": "...", "addr": "...", "open_hours": "...", "closed_days": "...", "description": "..."}}
-- 언어 추가해도 스키마 변경 없이 같은 컬럼에 key 추가하면 됨.

-- store_details: name_en/addr_en 컬럼 제거 후 translations로 통일
ALTER TABLE store_details
    DROP COLUMN IF EXISTS name_en,
    DROP COLUMN IF EXISTS addr_en,
    ADD COLUMN IF NOT EXISTS translations JSONB DEFAULT '{}'::jsonb;

ALTER TABLE halal_restaurants
    ADD COLUMN IF NOT EXISTS translations JSONB DEFAULT '{}'::jsonb;

ALTER TABLE vegan_restaurants
    ADD COLUMN IF NOT EXISTS translations JSONB DEFAULT '{}'::jsonb;

ALTER TABLE prayer_rooms
    ADD COLUMN IF NOT EXISTS translations JSONB DEFAULT '{}'::jsonb;

ALTER TABLE accommodation_places
    ADD COLUMN IF NOT EXISTS translations JSONB DEFAULT '{}'::jsonb;

ALTER TABLE tourist_places
    ADD COLUMN IF NOT EXISTS translations JSONB DEFAULT '{}'::jsonb;

ALTER TABLE public_restrooms
    ADD COLUMN IF NOT EXISTS translations JSONB DEFAULT '{}'::jsonb;