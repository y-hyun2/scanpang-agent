-- user_building_scans
-- 사용자가 AR 카메라로 건물을 스캔한 기록 (집계형).
-- 매 스캔마다 row 만들지 않고 (user_id, ufid) 조합당 1 row 로 압축 — UPSERT 가
-- scan_count 를 누적하고 last_scanned_at 만 갱신한다.
--
-- 용도:
-- · 운영 — 어떤 건물이 인기 있는지 → 그 건물 enrichment 우선순위
-- · 추천 — 협업 필터링(같은 사용자가 본 건물), 카테고리 선호 추정
-- · 사용자 분석 — 활동량/패턴
--
-- 사용자한테는 직접 노출 X (recently_viewed_places 가 그 역할). 이 테이블은 분석용.

CREATE TABLE IF NOT EXISTS user_building_scans (
    user_id            UUID         NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    ufid               TEXT         NOT NULL,
    scan_count         INT          NOT NULL DEFAULT 1,
    first_scanned_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_scanned_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, ufid)
);

-- ufid 단위 인기 건물 집계 ("이 건물 누가 봤나" / "TOP N 건물") 용
CREATE INDEX IF NOT EXISTS idx_ubs_ufid ON user_building_scans (ufid);

-- 사용자별 최근 스캔 timeline 조회 ("이 사용자 무엇을 봤나") 용
CREATE INDEX IF NOT EXISTS idx_ubs_user_last_scanned ON user_building_scans (user_id, last_scanned_at DESC);
