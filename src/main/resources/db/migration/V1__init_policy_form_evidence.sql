-- =============================================
-- V1: 초기 스키마 - 규정책 / 양식지 / 증빙서류
-- 담당: 공용 (초기 세팅)
-- =============================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS policy (
    id          BIGSERIAL PRIMARY KEY,
    policy_name VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS policy_chunk (
    id          BIGSERIAL PRIMARY KEY,
    policy_id   BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    chunk_index INT    NOT NULL,
    content     TEXT   NOT NULL,
    embedding   vector(1536)
);

CREATE INDEX IF NOT EXISTS policy_chunk_embedding_idx
    ON policy_chunk USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE TABLE IF NOT EXISTS form (
    id           BIGSERIAL PRIMARY KEY,
    form_name    VARCHAR(255) NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    description  TEXT,
    form_fields  TEXT,
    payment_type VARCHAR(10) DEFAULT 'BOTH',
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evidence (
    id                BIGSERIAL PRIMARY KEY,
    policy_id         BIGINT REFERENCES policy(id),
    selected_form_ids TEXT,
    file_path         VARCHAR(500) NOT NULL,
    file_name         VARCHAR(255),
    extracted_text    TEXT,
    select_reason     TEXT,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
