-- =============================================
-- V3: 결재 프로세스 테이블 추가
-- 담당: 파트 C
-- =============================================

CREATE TABLE IF NOT EXISTS approval_requests (
    id                     BIGSERIAL PRIMARY KEY,
    group_id               BIGINT NOT NULL REFERENCES groups(id),
    requester_id           BIGINT NOT NULL REFERENCES users(id),
    evidence_id            BIGINT REFERENCES evidence(id),
    filled_fields          TEXT,
    current_approval_order INT NOT NULL DEFAULT 0,
    status                 VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS approval_steps (
    id             BIGSERIAL PRIMARY KEY,
    request_id     BIGINT NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    approver_id    BIGINT NOT NULL REFERENCES users(id),
    approval_order INT NOT NULL,
    action         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment        TEXT,
    acted_at       TIMESTAMP,
    UNIQUE (request_id, approver_id)
);

CREATE TABLE IF NOT EXISTS approval_edit_history (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    editor_id     BIGINT NOT NULL REFERENCES users(id),
    before_fields TEXT,
    after_fields  TEXT,
    edited_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
