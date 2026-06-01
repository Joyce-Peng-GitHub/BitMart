-- 扩展与中文搜索配置。
-- pg_trgm 用于模糊匹配（随 PostgreSQL contrib 提供，官方镜像已含）。
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
