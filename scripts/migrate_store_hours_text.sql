-- store_hours 스키마 변경: TIME → TEXT, 하루 1 row, last_order 추가
--   open_time/close_time : 단일 구간이면 "11:00"/"20:00",
--                          브레이크 2구간이면 "11:00-15:00"/"16:00-20:00"
--   close_time 자정넘김   : "26:00"(24+) 텍스트로
--   last_order            : 구간별 콤마 나열 "14:00, 19:00" (없으면 NULL)
--   PK                    : (store_id, day_of_week) — 하루 1 row 보장
-- 기존 데이터는 normalize_store_hours 재실행으로 복구되므로 TRUNCATE.

TRUNCATE store_hours;

-- 기존 PK(이름 불명) 제거
DO $$
DECLARE pk text;
BEGIN
  SELECT conname INTO pk
  FROM pg_constraint
  WHERE conrelid = 'store_hours'::regclass AND contype = 'p';
  IF pk IS NOT NULL THEN
    EXECUTE format('ALTER TABLE store_hours DROP CONSTRAINT %I', pk);
  END IF;
END $$;

ALTER TABLE store_hours
  ALTER COLUMN open_time  TYPE text USING open_time::text,
  ALTER COLUMN close_time TYPE text USING close_time::text;

ALTER TABLE store_hours ADD COLUMN IF NOT EXISTS last_order text;

ALTER TABLE store_hours ADD PRIMARY KEY (store_id, day_of_week);
