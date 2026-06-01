-- 商品/需求（卖与买共用一张表，以 type 区分）、标签、书籍（架构 §4.2）。

CREATE TABLE listing (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            SMALLINT      NOT NULL,                 -- 0 SELL / 1 BUY
    category        SMALLINT      NOT NULL,                 -- 0 GENERAL / 1 BOOK
    user_id         BIGINT        NOT NULL REFERENCES app_user(id),
    title           TEXT          NOT NULL,
    description     TEXT          NOT NULL DEFAULT '',
    unit_price      NUMERIC(10,2) NULL,                     -- NULL = 面议/带价联系
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
