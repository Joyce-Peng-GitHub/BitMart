-- 用户与会话（架构 §4.1）。
-- 注意：表名用 app_user 而非 user —— user 是 PostgreSQL 保留字，避免到处加引号。

CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id    VARCHAR(20)  NOT NULL UNIQUE,           -- 学号
    password_hash TEXT         NOT NULL,                  -- Argon2id
    nickname      VARCHAR(32)  NULL,                      -- 空 → 展示为"匿名"
    role          SMALLINT     NOT NULL DEFAULT 0,        -- 0 普通 / 1 管理员
    status        SMALLINT     NOT NULL DEFAULT 0,        -- 0 正常 / 1 封禁
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ  NULL                       -- 注销：仅打标
);

CREATE TABLE session (
    token_hash   BYTEA       PRIMARY KEY,                 -- 仅存 SHA-256(token)
    user_id      BIGINT      NOT NULL REFERENCES app_user(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    user_agent   TEXT        NULL,
    revoked      BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX session_user_idx ON session(user_id) WHERE NOT revoked;

CREATE TABLE notification (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NULL REFERENCES app_user(id),  -- NULL 表示全员公告
    category   SMALLINT    NOT NULL,                      -- 0 ANNOUNCEMENT / 1 EXPIRY_WARN / ...
    title      VARCHAR(120) NOT NULL,
    body       TEXT        NOT NULL,
    payload    JSONB       NULL,                          -- 跳转 listingId 等
    read_at    TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX notification_user_cursor_idx ON notification(user_id, created_at DESC);
CREATE INDEX notification_announce_idx ON notification(created_at DESC) WHERE user_id IS NULL;

CREATE TABLE push_token (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user(id),
    token      TEXT        NOT NULL,
    platform   SMALLINT    NOT NULL,                      -- 0 ANDROID_FCM / 1 UNIFIED_PUSH / ...
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, token)
);
CREATE INDEX push_token_user_idx ON push_token(user_id);
