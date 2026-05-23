-- user_preferences 에 "최근 본 장소" 컬럼 추가.
-- frontend RecentlyViewedStore (PlaceDetail / AR 매장 오버레이 진입 시 자동 기록)
-- 의 SharedPreferences 가 multi-device 동기화되도록 backend 미러링.
--
-- 항목 형식 (saved_places 와 동일 패턴):
--   { id, name, category, target, viewedAt, lat, lng }
--
-- 길이 캡 (20개) 은 frontend 가 관리. backend 는 전체 list 받아 replace.

ALTER TABLE user_preferences
  ADD COLUMN IF NOT EXISTS recently_viewed_places JSONB NOT NULL DEFAULT '[]'::jsonb;
