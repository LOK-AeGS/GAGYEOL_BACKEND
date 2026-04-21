-- =============================================
-- V4: 스키마 보완 - 결재/증빙서류/그룹 구조 개선
-- =============================================

-- 1. approval_requests에 form_id, parent_request_id 추가
ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS form_id           BIGINT REFERENCES form(id),
    ADD COLUMN IF NOT EXISTS parent_request_id BIGINT REFERENCES approval_requests(id);

-- 2. approval_steps UNIQUE 조건 변경
--    기존: UNIQUE(request_id, approver_id) → 같은 사람이 여러 단계 불가
--    변경: UNIQUE(request_id, approver_id, approval_order) → 단계별로 허용
ALTER TABLE approval_steps
    DROP CONSTRAINT IF EXISTS approval_steps_request_id_approver_id_key;

ALTER TABLE approval_steps
    ADD CONSTRAINT approval_steps_request_approver_order_key
    UNIQUE (request_id, approver_id, approval_order);

-- 3. evidence_forms 중간 테이블 (selected_form_ids TEXT 정규화)
CREATE TABLE IF NOT EXISTS evidence_forms (
    evidence_id BIGINT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    form_id     BIGINT NOT NULL REFERENCES form(id)     ON DELETE CASCADE,
    PRIMARY KEY (evidence_id, form_id)
);

-- 4. groups에 active_policy_id 추가 (그룹이 현재 사용하는 규정책)
ALTER TABLE groups
    ADD COLUMN IF NOT EXISTS active_policy_id BIGINT REFERENCES policy(id);
