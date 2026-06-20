-- ============================================================================
-- BitMart 数据库基线（单文件 baseline）。
-- 由原 V1–V5 增量迁移合并而来，直接定义最新版本的 schema：
--   * listing.original_price（原 V4）已并入 listing 定义；
--   * app_user 学号唯一性（原 V5）以"部分唯一索引"形式直接定义，不再用内联 UNIQUE。
-- Flyway SQL 是表结构的唯一事实来源；Exposed 仅用于查询。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 扩展与中文搜索配置。
-- pg_trgm 用于模糊匹配（随 PostgreSQL contrib 提供，官方镜像已含）。
-- ----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 中文分词：优先使用 zhparser；若部署镜像未安装该扩展，则降级为内置 'simple'
-- 配置（按空白切分，英文仍可用）。通过 DO 块容错，使本迁移在 stock postgres:16
-- 与自定义 zhparser 镜像上均可成功执行（见架构 §13）。
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS zhparser;
        IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'bitmart_zh') THEN
            CREATE TEXT SEARCH CONFIGURATION bitmart_zh (PARSER = zhparser);
            -- 名词/动词/形容词/成语/简称等词性映射到 simple 词典。
            ALTER TEXT SEARCH CONFIGURATION bitmart_zh
                ADD MAPPING FOR n, v, a, i, e, l, j, t WITH simple;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'zhparser 不可用，全文搜索降级为 simple 配置: %', SQLERRM;
    END;
END
$$;

-- 解析当前可用的文本搜索配置：有 zhparser 配置则用之，否则回退 simple。
-- 注意：'name'::regconfig 的转换在函数解析期即校验，若 bitmart_zh 尚不存在会直接报错；
-- 因此这里返回 text 并在调用期由调用方动态转换，仅对实际命中的分支求值。
CREATE OR REPLACE FUNCTION bitmart_search_config() RETURNS text AS $$
    SELECT CASE
        WHEN EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'bitmart_zh')
        THEN 'bitmart_zh'
        ELSE 'simple'
    END;
$$ LANGUAGE sql STABLE;

-- ----------------------------------------------------------------------------
-- 用户与会话（架构 §4.1）。
-- 注意：表名用 app_user 而非 user —— user 是 PostgreSQL 保留字，避免到处加引号。
-- ----------------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id    VARCHAR(20)  NOT NULL,                  -- 学号（唯一性见下方部分唯一索引）
    password_hash TEXT         NOT NULL,                  -- Argon2id
    nickname      VARCHAR(32)  NULL,                      -- 空 → 展示为"匿名"
    role          SMALLINT     NOT NULL DEFAULT 0,        -- 0 普通 / 1 管理员
    status        SMALLINT     NOT NULL DEFAULT 0,        -- 0 正常 / 1 封禁
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ  NULL                       -- 注销：仅打标
);

-- 注销采用软删（仅给 deleted_at 打标，保留行）。若对 student_id 做全表唯一约束，
-- "注销后用同一学号重新注册" 会与遗留软删行冲突。改为部分唯一索引：仅对未注销
-- （deleted_at IS NULL）的行强制学号唯一，允许已注销行保留其学号，从而支持同一
-- 学号重新注册（生成新的用户 id）。
CREATE UNIQUE INDEX app_user_active_student_id_uk
    ON app_user (student_id)
    WHERE deleted_at IS NULL;

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

-- ----------------------------------------------------------------------------
-- 商品/需求（卖与买共用一张表，以 type 区分）、标签、书籍（架构 §4.2）。
-- ----------------------------------------------------------------------------
CREATE TABLE listing (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            SMALLINT      NOT NULL,                 -- 0 SELL / 1 BUY
    category        SMALLINT      NOT NULL,                 -- 0 GENERAL / 1 BOOK
    user_id         BIGINT        NOT NULL REFERENCES app_user(id),
    title           TEXT          NOT NULL,
    description     TEXT          NOT NULL DEFAULT '',
    unit_price      NUMERIC(10,2) NULL,                     -- NULL = 面议/带价联系
    original_price  NUMERIC(10,2) NULL,                     -- 原价/划线价，NULL = 不展示
    quantity_total  INT           NOT NULL CHECK (quantity_total >= 1),
    quantity_sold   INT           NOT NULL DEFAULT 0
                    CHECK (quantity_sold BETWEEN 0 AND quantity_total),
    pickup_location TEXT          NULL,
    contact         JSONB         NOT NULL,                 -- [{channel, value}, ...]，应用层校验非空
    expires_at      TIMESTAMPTZ   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ   NULL,                     -- 软删除
    search_tsv      TSVECTOR,                               -- 触发器维护
    source          SMALLINT      NOT NULL DEFAULT 0        -- 0 USER / 1 NAPCAT_BOT（预留）
);

-- 活跃列表主查询路径：按类型/品类/过期时间，仅未删除。
CREATE INDEX listing_active_idx ON listing(type, category, expires_at) WHERE deleted_at IS NULL;
-- 按用户查（"我的发布"），仅未删除。
CREATE INDEX listing_user_idx ON listing(user_id) WHERE deleted_at IS NULL;
-- keyset 分页排序键。
CREATE INDEX listing_created_idx ON listing(created_at DESC, id DESC) WHERE deleted_at IS NULL;
-- 全文搜索与模糊匹配。
CREATE INDEX listing_tsv_idx ON listing USING GIN(search_tsv);
CREATE INDEX listing_title_trgm_idx ON listing USING GIN(title gin_trgm_ops);
CREATE INDEX listing_desc_trgm_idx ON listing USING GIN(description gin_trgm_ops);

-- 维护 search_tsv：标题权重 A，描述权重 B；分词配置运行期解析（zhparser 或 simple）。
-- bitmart_search_config() 返回配置名(text)，在此处转换为 regconfig（调用期解析，避免函数解析期失败）。
CREATE OR REPLACE FUNCTION listing_tsv_update() RETURNS trigger AS $$
DECLARE
    cfg regconfig := bitmart_search_config()::regconfig;
BEGIN
    NEW.search_tsv :=
        setweight(to_tsvector(cfg, coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector(cfg, coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER listing_tsv_trigger
    BEFORE INSERT OR UPDATE OF title, description ON listing
    FOR EACH ROW EXECUTE FUNCTION listing_tsv_update();

CREATE TABLE listing_image (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    listing_id BIGINT   NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    blob_key   TEXT     NOT NULL,                          -- 由 BlobStorage 解释
    ord        SMALLINT NOT NULL,
    width      INT      NULL,
    height     INT      NULL,
    UNIQUE(listing_id, ord)
);

CREATE TABLE tag (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(20) NOT NULL UNIQUE,               -- 归一化（小写、去空白）
    usage_count INT         NOT NULL DEFAULT 0             -- 热门标签建议
);

CREATE TABLE listing_tag (
    listing_id BIGINT NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    tag_id     BIGINT NOT NULL REFERENCES tag(id),
    PRIMARY KEY (listing_id, tag_id)
);
CREATE INDEX listing_tag_tag_idx ON listing_tag(tag_id);

-- 服务端缓存的 ISBN 元数据（不可变事实，命中即免再调 ShowAPI）。
CREATE TABLE book_meta (
    isbn       VARCHAR(20) PRIMARY KEY,
    title      TEXT NULL,
    authors    TEXT NULL,
    publisher  TEXT NULL,
    edition    TEXT NULL,
    raw        JSONB       NOT NULL,                        -- ShowAPI 原始返回
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 与 listing 1:1（仅 category = BOOK）。
CREATE TABLE listing_book (
    listing_id BIGINT PRIMARY KEY REFERENCES listing(id) ON DELETE CASCADE,
    isbn       VARCHAR(20) NULL,
    title      TEXT NULL,
    authors    TEXT NULL,
    publisher  TEXT NULL,
    edition    TEXT NULL
);
