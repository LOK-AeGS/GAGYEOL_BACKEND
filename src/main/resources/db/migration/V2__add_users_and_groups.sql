-- =============================================
-- V2: 회원 / 그룹 / 역할 테이블 추가
-- 담당: 파트 A
-- =============================================

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) UNIQUE NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    invite_code VARCHAR(20) UNIQUE NOT NULL,
    owner_id    BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS group_roles (
    id             BIGSERIAL PRIMARY KEY,
    group_id       BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    role_name      VARCHAR(100) NOT NULL,
    approval_order INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, approval_order)
);

CREATE TABLE IF NOT EXISTS group_members (
    id        BIGSERIAL PRIMARY KEY,
    group_id  BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id   BIGINT NOT NULL REFERENCES group_roles(id),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, user_id)
);

-- 기존 테이블에 user_id, group_id FK 추가
ALTER TABLE policy  ADD COLUMN IF NOT EXISTS user_id  BIGINT REFERENCES users(id);
ALTER TABLE policy  ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES groups(id);
ALTER TABLE form     ADD COLUMN IF NOT EXISTS user_id  BIGINT REFERENCES users(id);
ALTER TABLE form     ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES groups(id);
ALTER TABLE evidence ADD COLUMN IF NOT EXISTS user_id  BIGINT REFERENCES users(id);
ALTER TABLE evidence ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES groups(id);
