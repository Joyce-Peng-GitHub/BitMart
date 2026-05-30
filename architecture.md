# BitMart 架构设计文档

## 一、整体拓扑

```
                 ┌──────────────────────────────┐
 用户手机 ──HTTPS─→│  朋友的公网服务器（Caddy）     │
                 │  域名 + Let's Encrypt 证书    │
                 └────────────┬─────────────────┘
                              │ Tailscale (内网，加密)
                              ▼
                 ┌──────────────────────────────┐
                 │  你的内网服务器（Docker Compose）│
                  │  ┌──────────┐  ┌──────────┐  │
                  │  │ Ktor App │  │ Postgres │  │
                  │  └──────────┘  └──────────┘  │
                  │  ┌──────────┐                │
                  │  │  MinIO   │                │
                  │  └──────────┘                │
                 └──────────────────────────────┘
```

**为什么这样**：业务数据（数据库 + 图片）都放你自己服务器，朋友的机器只做 TLS 终结与转发，落盘的持久化数据零暴露。Caddy 一行 `reverse_proxy 100.x.x.x:8080` 即可。如果将来朋友撤掉服务，把 Caddy 搬过来就行，应用层零改动。

> **信任边界提醒**：朋友的 Caddy 终结 HTTPS，转发段虽走 Tailscale 加密，但 Caddy 进程内存里能看到明文请求体（含登录密码、联系方式）——这并非"0 信任"。若要让明文也不经过朋友的机器，改用 L4 透传（如 Caddy `layer4` 插件按 SNI 转发原始 TLS，由你自己的服务器持证书终结），朋友的机器就退化为纯加密管道。当前阶段若可接受此边界，沿用 L7 反代即可。

---

## 二、技术栈选型

| 层 | 选型 | 备选/升级路径 |
|---|---|---|
| 后端框架 | **Ktor 2.x**（Kotlin 原生，轻量） | Spring Boot（团队大时） |
| ORM | **Exposed**（Kotlin DSL） | jOOQ / Hibernate |
| 数据库 | **PostgreSQL 16** + `pg_trgm`，可选 `zhparser` | 加 Meilisearch / Elasticsearch |
| 对象存储 | **MinIO**（S3 兼容，自托管） | 直接平迁到 AWS S3 / 阿里 OSS |
| 实时推送 | Ktor WebSocket + 轮询兜底 | FCM / UnifiedPush |
| 鉴权 | JWT (access + refresh) | OAuth2 |
| 限流 | **内存令牌桶**（单实例足够） | Redis 令牌桶（多实例时） |
| 日志/可观测 | **Logback + Structured JSON** + Ktor CallLogging | ELK / Grafana Loki |
| 数据库迁移 | **Flyway** | Liquibase |
| 反向代理 | Caddy（自动 HTTPS） | Nginx |
| 容器化 | Docker Compose | Kubernetes |
| Android UI | **Jetpack Compose + Material 3** | — |
| Android 架构 | MVVM + Repository + UseCase（可选） | MVI |
| Android DI | **Hilt** | Koin |
| Android 网络 | Retrofit + OkHttp + kotlinx.serialization | Ktor Client |
| Android 图片 | **Coil 3**（Compose 友好） | — |
| Android 本地存储 | Room + DataStore | — |
| 条码扫描 | **ML Kit Barcode Scanning** | ZXing |
| 相机 | **CameraX** | — |
| 国际化 | `strings.xml` + `values-zh-rCN/` | — |

## 三、数据库设计

### 3.1 表结构

```sql
-- ============================================================
-- 用户
-- ============================================================
CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id    VARCHAR(16)  UNIQUE NOT NULL,  -- 北理工学号，登录凭据之一
  nickname      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,         -- Argon2id，平台密码哈希
  qq            VARCHAR(16),
  wechat        VARCHAR(64),
  phone         VARCHAR(16),
  avatar_url    TEXT,
  is_admin      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- student_id 已有 UNIQUE 约束自带索引，用于登录查询

-- ============================================================
-- 出售单
-- ============================================================
CREATE TABLE listings (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  seller_user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
                    -- 注销时应用层先下架再删用户；NULL = 已注销 或 NapCat 来源
  seller_qq         VARCHAR(16),              -- NapCat 来源 或 备用联系方式
  source            VARCHAR(16)  NOT NULL,    -- USER | NAPCAT
  title             VARCHAR(255) NOT NULL,
  description       TEXT,
  price_cents       INTEGER,                  -- NULL = 议价
  currency          CHAR(3) NOT NULL DEFAULT 'CNY',
  condition_rating  SMALLINT,                 -- 1~5，可空
  quantity          INTEGER NOT NULL DEFAULT 1, -- 总件数，决定创建多少 listing_items
  status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    -- ACTIVE | SOLD_OUT | DELISTED | EXPIRED
  isbn              VARCHAR(20),
  book_meta         JSONB,                    -- {author, publisher, pubdate, edition, price, gist, ...}
  search_text       TEXT GENERATED ALWAYS AS
                    (coalesce(title,'') || ' ' || coalesce(description,'') || ' ' ||
                     coalesce(book_meta->>'author','')) STORED,
                    -- 自动拼接的搜索文本，供 pg_trgm 索引使用
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at        TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '6 months'),
                    -- 到期自动下架，用户可自定义（7天~1年）
  CHECK (source IN ('USER', 'NAPCAT')),
  CHECK (status IN ('ACTIVE', 'SOLD_OUT', 'DELISTED', 'EXPIRED')),
  CHECK (condition_rating IS NULL OR condition_rating BETWEEN 1 AND 5),
  CHECK (quantity >= 1),
  CHECK (price_cents IS NULL OR price_cents >= 0)
  -- 注意：故意不设 CHECK 强制 seller_user_id/seller_qq 至少一个非空。
  -- 原因：注销账号时 ON DELETE SET NULL 会把 seller_user_id 置空，而普通(USER)
  --   商品 seller_qq 本就为 NULL，二者同时为 NULL 会违反该 CHECK 使 DELETE 失败。
  -- "创建时必须有卖家来源" 的不变式改由应用层在 ListingService.create 校验（见 4.4）。
);

-- 搜索：pg_trgm GIN 索引支撑中英混合模糊搜索
CREATE INDEX idx_listings_search_trgm ON listings USING gin (search_text gin_trgm_ops);
-- 首页/列表：按状态+时间排序，覆盖"最新上架"查询
CREATE INDEX idx_listings_status_created ON listings (status, created_at DESC);
-- "我的发布"列表 + 注销时批量下架（partial，排除 NapCat 来源）
CREATE INDEX idx_listings_seller ON listings (seller_user_id) WHERE seller_user_id IS NOT NULL;
-- ISBN 精确查找：扫码后快速定位同书商品（partial，仅有 ISBN 的行）
CREATE INDEX idx_listings_isbn ON listings (isbn) WHERE isbn IS NOT NULL;
-- 过期任务扫描：定时任务每小时查询已到期但仍 ACTIVE 的记录
CREATE INDEX idx_listings_expires ON listings (expires_at) WHERE status = 'ACTIVE';

-- ============================================================
-- 出售单内的单品（多本/多件分别售卖）
-- 可售数量通过 COUNT(status='AVAILABLE') 实时计算，不冗余存储
-- 校园场景下单条 listing 最多十几件，partial index 扫描微秒级
-- ============================================================
CREATE TABLE listing_items (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  listing_id  UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  item_index  INTEGER NOT NULL,             -- 第几件，从 1 开始
  status      VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE | RESERVED | SOLD
  sold_at     TIMESTAMPTZ,
  UNIQUE (listing_id, item_index)           -- 同一 listing 内 item_index 唯一
);
-- 查询某 listing 剩余可售数量（partial，仅 AVAILABLE 行）
CREATE INDEX idx_listing_items_available ON listing_items (listing_id) WHERE status = 'AVAILABLE';

-- ============================================================
-- 图片
-- ============================================================
CREATE TABLE listing_images (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  listing_id  UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  object_key  TEXT NOT NULL,                -- MinIO 中的 key
  sort_order  INTEGER NOT NULL DEFAULT 0,   -- 展示顺序
  is_cover    BOOLEAN NOT NULL DEFAULT FALSE,
  width       INTEGER, height INTEGER
);
-- 详情页按顺序加载图片列表
CREATE INDEX idx_listing_images_listing ON listing_images (listing_id, sort_order);

-- ============================================================
-- 标签
-- ============================================================
CREATE TABLE tags (
  id          SERIAL PRIMARY KEY,
  name        VARCHAR(32) UNIQUE NOT NULL,  -- UNIQUE 自带索引，支撑自动补全查询
  usage_count INTEGER NOT NULL DEFAULT 0    -- 热门标签排序依据，应用层维护
);
-- usage_count 维护策略：
--   1. 应用层：listing 创建/编辑/删除时在同一事务中 ±1
--   2. 兜底：每日定时任务全量重算 UPDATE tags SET usage_count = (SELECT COUNT(*) FROM listing_tags WHERE tag_id = tags.id)
--   确保最终一致，避免触发器对 ORM 不透明的问题
CREATE TABLE listing_tags (
  listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  tag_id     INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (listing_id, tag_id)          -- 复合主键自带索引，listing→tags 方向查询可用
);
-- 按标签筛选商品：给定 tag_id 反查所有关联 listing
CREATE INDEX idx_listing_tags_tag ON listing_tags (tag_id);

-- ============================================================
-- ISBN 元数据缓存
-- 校园场景下同一课本被反复查询，缓存命中率高
-- ============================================================
CREATE TABLE isbn_cache (
  isbn        VARCHAR(20) PRIMARY KEY,      -- ISBN 即主键，精确查找
  title       VARCHAR(255),
  author      VARCHAR(255),
  publisher   VARCHAR(255),
  pubdate     VARCHAR(16),                  -- "2006-05" 格式
  edition     VARCHAR(8),                   -- 版次，教材场景下很重要
  price       VARCHAR(16),                  -- 原价/定价，供买家参考
  cover_url   TEXT,                         -- 封面图链接
  gist        TEXT,                         -- 内容简介
  raw_json    JSONB,                        -- 保留完整 API 响应以备扩展
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- 无需额外索引：所有查询都走主键精确匹配

-- ============================================================
-- 求购 / 求购广场（买家侧，核心需求）
-- ============================================================
-- 求购既是"可被全站浏览的求购帖"，也兼作"关键词订阅"（新商品上架时匹配通知）
CREATE TABLE wishes (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title        VARCHAR(255) NOT NULL,       -- 求购标题，如"求 CSAPP 第3版"，用于广场展示
  description  TEXT,                        -- 补充说明（可接受成色、版本范围等）
  keyword      VARCHAR(255) NOT NULL,       -- 用于匹配新上架商品的关键词（可与 title 相同）
  max_price    INTEGER,                     -- 期望最高价（分），NULL = 不限
  tag_filter   INTEGER[],                   -- 期望标签 ID 数组，NULL = 不限
  status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | FULFILLED | EXPIRED
  search_text  TEXT GENERATED ALWAYS AS
               (coalesce(title,'') || ' ' || coalesce(description,'') || ' ' ||
                coalesce(keyword,'')) STORED, -- 供求购广场的 pg_trgm 搜索使用
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at   TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '6 months'),
  CHECK (status IN ('ACTIVE', 'FULFILLED', 'EXPIRED')),
  CHECK (max_price IS NULL OR max_price >= 0)
);
-- 求购广场浏览/搜索：与 listings 同一套 pg_trgm 中英混合模糊搜索方案
CREATE INDEX idx_wishes_search_trgm ON wishes USING gin (search_text gin_trgm_ops);
-- 求购广场列表：按状态 + 时间排序
CREATE INDEX idx_wishes_status_created ON wishes (status, created_at DESC);
-- 新商品上架时扫描活跃求购做匹配通知
CREATE INDEX idx_wishes_active ON wishes (status) WHERE status = 'ACTIVE';
-- "我的求购"列表
CREATE INDEX idx_wishes_user ON wishes (user_id);
-- 过期任务扫描：定时任务查询已到期但仍 ACTIVE 的求购
CREATE INDEX idx_wishes_expires ON wishes (expires_at) WHERE status = 'ACTIVE';

-- ============================================================
-- 通知
-- ============================================================
CREATE TABLE notifications (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type       VARCHAR(32) NOT NULL,          -- WISH_HIT | LISTING_EXPIRED | WISH_EXPIRED | SYSTEM | ...
  payload    JSONB NOT NULL,                -- 通知详情，结构因 type 而异
  read_at    TIMESTAMPTZ,                   -- NULL = 未读
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- 未读通知角标 + 未读列表（partial，仅未读行，体积小速度快）
CREATE INDEX idx_notif_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
-- 通知历史分页（含已读）
CREATE INDEX idx_notif_user_all ON notifications (user_id, created_at DESC);

-- ============================================================
-- refresh token（支持注销）
-- ============================================================
CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(255) NOT NULL,        -- SHA-256(token)，不存明文
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked     BOOLEAN NOT NULL DEFAULT FALSE
);
-- 注销时按用户撤销所有 token；也用于登录时清理过期 token
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
```

### 3.2 中英混合模糊搜索

`pg_trgm` 在中英混合短文本（书名、商品名）上效果意外地好，因为它做的是 3-gram，对 CJK 也成立——"操作系统"会被切为 `操作系`、`作系统`。即使有错别字也能匹配。

```sql
SELECT id, title, similarity(search_text, '操作系统 csapp') AS s
FROM listings
WHERE search_text % '操作系统 csapp'
  AND status = 'ACTIVE'
ORDER BY s DESC
LIMIT 20;
```

升级路径：当数据量 >10w 或召回率不够时，引入 Meilisearch（对 CJK 原生支持），后端写 listings 时双写一份到 Meili，搜索接口切换数据源即可。

## 四、后端模块

### 4.1 项目结构

```
BitMartServer/
├── build.gradle.kts
├── docker-compose.yml
├── Dockerfile
└── src/main/kotlin/cn/edu/bit/bitmart/
    ├── Application.kt              # Ktor 入口、模块装配
    ├── config/                     # 配置加载（HOCON / 环境变量）
    ├── plugins/                    # ContentNeg、StatusPages、CORS、CallLogging、RateLimit
    ├── auth/
    │   ├── BitAuthClient.kt        # 调用 BIT101 验证接口
    │   ├── JwtService.kt
    │   ├── PasswordHasher.kt       # Argon2id
    │   └── AuthRoutes.kt           # /register /login /refresh /logout
    ├── users/
    ├── listings/
    │   ├── ListingService.kt
    │   ├── ListingRepository.kt
    │   └── ListingRoutes.kt
    ├── search/                     # 查询拼装、分页
    ├── uploads/
    │   ├── MinIOClient.kt
    │   └── UploadRoutes.kt         # 直传 + 预签名两种
    ├── books/
    │   ├── IsbnLookupService.kt    # 外部 ISBN API + DB 缓存
    │   └── BookRoutes.kt
    ├── wishes/
    ├── notifications/
    │   ├── NotificationService.kt  # 命中检测、入库
    │   └── WsRoutes.kt             # WebSocket 推送
    ├── napcat/                     # 进阶：QQ 群消息接入
    ├── db/
    │   ├── Database.kt             # 数据源、Flyway
    │   └── tables/                 # Exposed Table 定义
    └── util/
```

### 4.2 关键 API

所有路径前缀 `/api/v1`，JSON 请求/响应，统一错误格式 `{ code, message, details }`。

| Method & Path | 描述 |
|---|---|
| `GET /health` | 健康检查（Docker 存活探测 + Caddy 上游检测），返回 DB/MinIO 连通状态 |
| `POST /auth/register` | body: `studentId, bitPassword, platformPassword, nickname, qq?, wechat?` → 调 BIT101 校验 → 写库 → 返回 `{accessToken, refreshToken}` |
| `POST /auth/login` | body: `studentId, platformPassword` |
| `POST /auth/refresh` | |
| `POST /auth/logout` | 撤销 refresh token |
| `GET /users/me`, `PATCH /users/me` | |
| `DELETE /users/me` | 注销账号：下架全部 ACTIVE 商品、删除求购/通知/refresh token、删除用户行 |
| `GET /listings?q=&tags=&minPrice=&maxPrice=&sort=&cursor=` | 游标分页，tags 支持多选筛选 |
| `GET /listings/mine?status=&cursor=` | 当前用户的发布列表，可按状态筛选 |
| `POST /listings` | 创建出售单（含子项目数量、可选 expires_at，默认 6 个月） |
| `GET /listings/{id}` | 详情；seller 为 null 时显示"卖家已注销"；NapCat 来源显示 QQ + 风险提示。**联系方式仅当 status=ACTIVE 且请求者已登录时返回**，SOLD_OUT/DELISTED/EXPIRED 不返回（呼应"售出后免打扰"） |
| `PATCH /listings/{id}` | |
| `POST /listings/{id}/items/{idx}/sold` | 标记已售一件（事务内 `SELECT ... FOR UPDATE` 锁定该 item 防并发重复标记）；全部售出时同一事务把 listing status 置为 SOLD_OUT |
| `DELETE /listings/{id}` | 软下架 |
| `POST /listings/{id}/relist` | 重新上架已过期/已下架的商品，重置 expires_at |
| `POST /uploads/images` | multipart 直传（服务端经 `MINIO_INTERNAL_ENDPOINT` 落库），返回 `{objectKey, url}`；或 `POST /uploads/presign` 返回基于 `MINIO_PUBLIC_ENDPOINT` 的 PUT URL（客户端直传，host = cdn 域名） |
| `GET /books/lookup?isbn=` | 先查 DB 缓存，未命中再调外部 ISBN API；结果入库供后续复用 |
| `GET /tags?prefix=` | 标签自动补全 |
| `GET /wishes?q=&tags=&maxPrice=&sort=&cursor=` | 求购广场：浏览全站活跃求购帖，游标分页 + pg_trgm 搜索 |
| `GET /wishes/mine?status=&cursor=` | 当前用户的求购列表 |
| `GET /wishes/{id}` | 求购详情；**求购者联系方式仅登录后可见**，供卖家主动联系买家 |
| `POST /wishes` | 发布求购（title 必填，可选 description/max_price/tags/expires_at） |
| `PATCH /wishes/{id}`, `DELETE /wishes/{id}` | 编辑 / 删除（仅本人） |
| `POST /wishes/{id}/fulfill` | 标记已找到（status → FULFILLED），不再出现在求购广场 |
| `GET /notifications?unread=&cursor=` | 游标分页，可选仅未读 |
| `POST /notifications/read` | 批量标记已读，body: `{ids: [...]}` |
| `WS /ws` | 鉴权后推送通知，心跳 30s |

**WebSocket 生命周期**：
- 连接时在 WS 握手请求头携带 `Authorization: Bearer <accessToken>` 鉴权（Android 用 OkHttp 可直接给握手请求设头），token 无效立即关闭（4001）。**不要把 token 放在 query string**——URL 会进 Caddy/代理的访问日志，而脱敏白名单只覆盖请求体与 Authorization 头。
- 服务端每 30s 发 ping，客户端 10s 内未 pong 则断开。
- Access token 过期时服务端发 `{"type":"TOKEN_EXPIRED"}` 后关闭连接（4002），客户端自动 refresh 后重连。
- 客户端重连时携带 `?lastEventId=<timestamp>`，服务端补发该时间点之后的未读通知（最多 64 条）。
- 客户端从后台恢复时，先调 `GET /notifications?unread=true` 补全，再重建 WS。

### 4.3 BIT 身份验证流程

```
App ──{studentId, bitPwd, platformPwd, ...}──→ /auth/register
                                                    │
                          ┌─────────────────────────┘
                          ▼
                BitAuthClient.verify(studentId, bitPwd)
                          │ HTTP POST 到 BIT101 接口
                          ▼
                  成功？ ── 否 ──→ 返回 401
                    │ 是
                    ▼
            users 表新增（password_hash = Argon2id(platformPwd)）
                    │
                    ▼
              签发 JWT，返回客户端
```

**关键安全点**：BIT 密码绝对不入库不入日志。`BitAuthClient` 内部把密码字段标记为不可序列化，日志中间件加字段脱敏白名单。

### 4.4 账号注销流程

应用层按顺序执行（单事务）：
1. `UPDATE listings SET status = 'DELISTED', updated_at = NOW() WHERE seller_user_id = :id AND status = 'ACTIVE'`
2. `DELETE FROM users WHERE id = :id` — 触发 CASCADE 删除 wishes、notifications、refresh_tokens；listings 的 seller_user_id 被 SET NULL

商品保留（已下架状态）供历史参考，但不再出现在搜索结果中。求购和通知无保留价值，直接级联删除。

> **关于卖家来源约束**：listings 不再设 `CHECK (seller_user_id OR seller_qq)`。该不变式只在"创建时"有意义，由 `ListingService.create` 保证（USER 来源必须带 `seller_user_id`，NAPCAT 来源必须带 `seller_qq`）。注销后 `seller_user_id = NULL` 且 `seller_qq = NULL` 是合法的"卖家已注销"状态——若保留该 CHECK，上面第 2 步的 `ON DELETE SET NULL` 会因违反约束而使注销整体失败。这也沿用了本项目"约束尽量在应用层维护、不用触发器"的一贯做法。

### 4.5 ISBN 查询与缓存

校园场景下同一课本会被多人反复查询（如"高等数学"每学期都有人卖），DB 缓存可大幅减少外部 API 调用次数。

```
GET /books/lookup?isbn=978xxx
        │
        ▼
  isbn_cache 表命中？ ── 是 ──→ 直接返回
        │ 否
        ▼
  调用外部 ISBN API
        │
        ▼
  结果写入 isbn_cache
        │
        ▼
  返回客户端
```

缓存永不过期（书籍元数据不会变）。外部 API 有每日调用次数限制，缓存命中后不消耗配额。

**`book_meta` 与 `isbn_cache` 的关系**：通过 ISBN 创建 listing 时，从 `isbn_cache` 复制一份快照到 `listings.book_meta`。这样即使 isbn_cache 被清理或结构变更，已有 listing 的展示数据不受影响。`book_meta` 是 listing 的自有数据，`isbn_cache` 是全局共享的查询加速层。

### 4.6 自动过期机制

商品和求购默认有效期 6 个月，避免陈旧数据堆积干扰搜索。

**定时任务**（Ktor 启动时注册协程定时器，每小时执行一次）：

```sql
-- 过期商品下架
UPDATE listings SET status = 'EXPIRED', updated_at = NOW()
WHERE status = 'ACTIVE' AND expires_at <= NOW();

-- 过期求购关闭
UPDATE wishes SET status = 'EXPIRED'
WHERE status = 'ACTIVE' AND expires_at <= NOW();
```

过期后向用户发送通知（`type = 'LISTING_EXPIRED'` / `'WISH_EXPIRED'`），用户可选择重新上架（重置 `expires_at`）。

**用户侧**：
- 发布/创建求购时可自定义有效期（最短 7 天，最长 1 年，默认 6 个月）
- "我的发布"页面显示剩余有效期，临近过期（7 天内）时标黄提醒
- 已过期商品可一键"重新上架"（status → ACTIVE，expires_at 重置）

### 4.7 LLM 调用（纯客户端）

```
App ──图片 base64 + 用户自己的 key──→ 用户配置的 OpenAI 兼容端点
                                          │
                                          ▼
                                    返回结构化 JSON
                                          │
                                          ▼
                              App 解析 → 生成草稿 → 用户审核编辑
```

- Android 侧用 OkHttp 直接调用，API Key 存 EncryptedDataStore。
- 使用 vision 能力直接发送图片，由 LLM 同时完成文字识别和语义提取。
- 提示词使用 JSON Schema 强约束输出（OpenAI `response_format: json_schema`）。
- 返回结果落到 App 后让用户编辑确认再提交。
- 无需服务端参与，避免了服务端 LLM 限流、账单等复杂度。

**升级路径**：后续可引入本地 OCR（ML Kit）作为 LLM 的前置步骤（先 OCR 提取文本再发给 LLM，减少 token 消耗）或离线降级方案。如需服务端代理模式（平台提供默认 key），新增 `/ai/extract` 接口 + Redis 限流即可，客户端已有统一的 LLM 响应解析层，切换数据源无需改 ViewModel。

### 4.8 NapCat 进阶接入（可选）

独立 worker 进程订阅 NapCat 的 OneBot WebSocket → 收到群消息 → 拉取附图 → 调用 LLM 提取结构化信息 → 写库时 `source='NAPCAT'`、`seller_user_id=NULL`、`seller_qq=<发送者 QQ>`。详情页上对此类商品加红色风险提示。

**注意**：NapCat worker 需要自己的 LLM key 配置（环境变量），独立于用户侧 LLM 调用。

**隐私/合规**：抓取群消息转帖会涉及他人 QQ 与言论，需在群内公示收录规则并提供一键拒收/下架（如发送特定指令即不收录或删除已收录条目）；详情页只展示发帖者 QQ，不展示群内其他成员信息或原始聊天上下文。

### 4.9 求购（买家侧）

求购同时承担两个角色，覆盖"展示想买的东西"+"联系买家"这一侧需求：

- **可浏览的求购帖**：`GET /wishes` 是求购广场，列出全站活跃求购（pg_trgm 模糊搜索 + 标签/价格筛选）；`GET /wishes/{id}` 详情在登录后展示求购者联系方式（QQ/微信/手机，取自 users 表），卖家可据此主动联系买家。与 listings 对称，不在平台内成交。
- **关键词订阅（自动匹配通知）**：新 listing 创建时，对每条 ACTIVE wish 计算 `similarity(listing.search_text, wish.keyword) > 阈值`（pg_trgm），再叠加 `max_price`、`tag_filter` 过滤；命中则写一条 `WISH_HIT` 通知。校园量级下活跃求购有限，逐条扫描可接受；数据量大后可反向对 `wish.keyword` 建 trgm 索引、用 listing 文本反查。
- **免打扰**：求购者找到后调 `POST /wishes/{id}/fulfill`（status → FULFILLED），即从广场与匹配池中移除。

## 五、Android 客户端

### 5.1 模块与架构

单 module 项目即可（四周时间不必拆 multi-module）。简化版 Clean Architecture——保持三层分离和依赖方向正确，但不强制为每个 data 层组件抽 domain 接口（避免过度样板代码）：

```
ui (Compose)  ──→  ViewModel  ──→  UseCase(可选) ──→ Repository ──┬──→ Remote (Retrofit)
                                                                  ├──→ Local (Room / DataStore)
                                                                  └──→ Device (CameraX / MLKit / LLM)
```

```
BitMartApp/src/main/java/cn/edu/bit/bitmart/
├── BitMartApp.kt                  # @HiltAndroidApp
├── MainActivity.kt                # 单 Activity + NavHost
├── ui/
│   ├── theme/                     # Material3 + dynamic color
│   ├── auth/ (Login, Register)
│   ├── home/                      # 推荐 / 最新动态
│   ├── search/ (Search, Filter)
│   ├── listing/ (List, Detail)
│   ├── publish/                   # 关键流程：扫码 / 拍照 / LLM / 表单
│   │   ├── PublishScreen.kt
│   │   ├── BarcodeScanScreen.kt
│   │   ├── BatchCaptureScreen.kt  # 多张拍照后批量识别
│   │   └── PublishViewModel.kt
│   ├── wish/
│   ├── notifications/
│   ├── profile/
│   └── components/                # 通用组件：PriceText、TagChip、ImageCarousel
├── data/
│   ├── api/                       # Retrofit 接口 + DTO
│   ├── repo/
│   ├── local/                     # Room dao + entity（缓存 listings、tags）
│   ├── pref/                      # DataStore: token, language, llm settings
│   ├── ai/
│   │   └── LlmClient.kt           # OpenAI 兼容
│   └── upload/
├── domain/
│   ├── model/                     # 领域模型，与 DTO 解耦
│   └── usecase/                   # PublishListing, SearchListings, ...
├── di/                            # Hilt Modules
└── util/
```

### 5.2 发布商品的关键交互流

```
[选择类型]
    │
    ├─ 扫书条码 ──→ ML Kit Barcode → ISBN
    │                 │
    │                 ▼
    │           GET /books/lookup
    │                 │
    │                 ▼
    │           预填表单（书名、作者）
    │                 │
    │                 ▼
    │           用户补充（品相、数量、价格、tag、图片）
    │                 │
    │                 ▼
    │            提交
    │
    ├─ 批量拍书脊/封面 ──→ CameraX 多拍 ──→ 图片发送给 LLM（vision）
    │                                                │
    │                                                ▼
    │                                   返回 [{title, author, ...}]
    │                                                │
    │                                                ▼
    │                                   生成 N 条草稿，用户逐条审核 / 编辑
    │                                                │
    │                                                ▼
    │                                   批量提交（一次 HTTP 含图片 keys）
    │
    └─ 一般商品 ──→ 拍照 + LLM 提取 ──→ 表单
```

### 5.3 国际化

`res/values/strings.xml`（中文为默认）+ `res/values-en/strings.xml`。设置中提供"跟随系统 / 中文 / English"切换，`AppCompatDelegate.setApplicationLocales` 切换。

### 5.4 LLM 设置页

```
Settings → AI 推断
  LLM 配置：
  Base URL: https://...
  API Key:  ********  (EncryptedSharedPreferences / DataStore + MasterKey)
  Model:    gpt-4o-mini
  [测试连接]
```

## 六、部署与运维

### 6.1 docker-compose.yml（你的服务器）

```yaml
# ${TS_IP} = 本机 Tailscale IP（100.x.x.x）。端口绑定在 Tailscale 接口上，
# 既能让朋友的 Caddy 经 Tailscale 访问，又不暴露到公网/局域网。
services:
  postgres:
    image: postgres:16-alpine
    environment: { POSTGRES_DB: bitmart, POSTGRES_PASSWORD: ... }
    volumes: ["./data/pg:/var/lib/postgresql/data"]
    # 不发布端口，仅 compose 内网（app 经服务名 postgres:5432 访问）
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      # 让 MinIO 接受/校验以公网域名为 host 的请求（控制台跳转也用它）
      MINIO_SERVER_URL: https://cdn.bitmart.example.com
    volumes: ["./data/minio:/data"]
    ports:
      - "${TS_IP}:9000:9000"            # 经朋友的 Caddy(cdn 子域) → Tailscale → 这里
      - "127.0.0.1:9001:9001"           # 控制台仅本机/SSH 隧道访问
  app:
    build: .
    depends_on: [postgres, minio]
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/bitmart
      MINIO_INTERNAL_ENDPOINT: http://minio:9000             # 服务端上传/管理走 compose 内网
      MINIO_PUBLIC_ENDPOINT: https://cdn.bitmart.example.com # 生成预签名 URL 用，host 必须对外可达
      JWT_SECRET: ${JWT_SECRET}
      BIT101_BASE: https://bit101.cn/api
      ISBN_API_KEY: ${ISBN_API_KEY}
    ports:
      - "${TS_IP}:8080:8080"            # 绑定 Tailscale 接口；用 127.0.0.1 会让 Caddy 经 Tailscale 连不通
```

### 6.2 朋友服务器的 Caddyfile

```
# API：反代到你的 Ktor app（经 Tailscale）
bitmart.example.com {
  reverse_proxy your-tailscale-host:8080
  encode gzip
  request_body { max_size 20MB }   # 配合图片上传
}

# 图片：反代到你的 MinIO（经 Tailscale）。预签名 URL 的 host 必须等于此域名，
# 且需透传 Host（Caddy reverse_proxy 默认透传），MinIO 侧签名才能校验通过。
cdn.bitmart.example.com {
  reverse_proxy your-tailscale-host:9000
  encode gzip
  request_body { max_size 20MB }   # 客户端预签名直传也走这里
}
```

### 6.3 图片访问说明

图片通过 MinIO 预签名 URL 直接下载：客户端请求 `https://cdn.bitmart.example.com/...`（带签名）→ 朋友的 Caddy(cdn 子域) → Tailscale → 你的 MinIO。预签名 URL 由 app 用 `MINIO_PUBLIC_ENDPOINT` 生成，host 与 cdn 域名一致，Caddy 透传 Host 后 MinIO 才能校验签名通过；服务端自身上传/管理对象则走 `MINIO_INTERNAL_ENDPOINT`（compose 内网）。初期依赖 Coil 客户端缓存降低重复请求。

**升级路径**：当带宽成为瓶颈时，在 Caddy 前加 CDN（Cloudflare 免费层），或将 MinIO 迁移到 S3 + CloudFront。

### 6.4 备份

每日 cron：`pg_dump | zstd | rsync` 到第二个磁盘 + Tailscale 到朋友机器一份。MinIO 可启 versioning。

## 七、安全要点（按优先级）

1. **BIT 密码不落盘不入日志**：网络层 TLS、内存中处理完即清空、日志中间件白名单。
2. **平台密码 Argon2id**（参数 `m=64MB, t=3, p=1`）。
3. **JWT 短 access（15min）+ refresh（30d）**，refresh 哈希入库以便撤销。
4. **图片上传校验**：MIME 嗅探 + 大小限制 + EXIF GPS 清除。
5. **通用限流**：内存令牌桶（Ktor 插件），对 `/auth/login`、`/auth/register` 等敏感接口按 IP 限流（如 5次/分钟），防暴力破解。
6. **联系方式仅登录后可见，且仅当商品/求购处于 ACTIVE 时返回**（售出/下架/过期后不再暴露，呼应"售出后免打扰"）；NapCat 来源商品在详情页加风险提示横幅，符合"避免在平台交易"的要求。
7. **管理员后台**初期不做 UI，直接 SQL 操作 + 一个 `is_admin` 接口允许下架违规内容。

## 八、日志与可观测性（Day 1 必备）

### 8.1 日志框架

- **Logback** + **logstash-logback-encoder**：输出结构化 JSON 日志到 stdout。
- Docker Compose 中通过 `logging.driver: json-file` 收集，后续可切换到 Loki/ELK。

### 8.2 Ktor 日志配置

```kotlin
install(CallLogging) {
    level = Level.INFO
    format { call ->
        val status = call.response.status()
        val method = call.request.httpMethod.value
        val uri = call.request.uri
        val duration = call.processingTimeMillis()
        "$method $uri → $status (${duration}ms)"
    }
    // 敏感字段脱敏
    filter { !it.request.uri.contains("/health") }
    mdc("requestId") { it.request.header("X-Request-Id") ?: UUID.randomUUID().toString() }
    mdc("userId") { it.principal<JWTPrincipal>()?.subject }
}

install(StatusPages) {
    exception<Throwable> { call, cause ->
        logger.error("Unhandled exception on ${call.request.uri}", cause)
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(...))
    }
}
```

### 8.3 日志规范

| 级别 | 用途 | 示例 |
|---|---|---|
| ERROR | 不可恢复的异常、外部服务不可达 | DB 连接失败、BIT101 接口超时 |
| WARN | 可恢复但需关注 | 限流触发、JWT 校验失败、上传文件类型不合法 |
| INFO | 关键业务事件 | 用户注册、listing 创建/下架、ISBN 查询 |
| DEBUG | 开发调试（生产关闭） | SQL 语句、请求/响应 body |

### 8.4 脱敏白名单

以下字段在日志中**绝不输出**：
- `password`、`bitPassword`、`platformPassword`
- `Authorization` header 的 token 值（仅输出 `Bearer ***`）
- `apiKey`（用户 LLM key）

### 8.5 logback.xml 配置

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>requestId</includeMdcKeyName>
      <includeMdcKeyName>userId</includeMdcKeyName>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
  <logger name="org.jetbrains.exposed" level="WARN"/>
  <logger name="io.ktor" level="INFO"/>
  <logger name="cn.edu.bit.bitmart" level="DEBUG"/>
</configuration>
```

升级路径：接入 Grafana Loki（Docker Compose 加一个 Loki + Promtail 容器）或 Sentry（加 SDK 依赖即可）。

## 九、四周排期（建议）

**Week 1 — 地基**
- 后端：Ktor 骨架 + 结构化日志 + Postgres/MinIO 容器化 + Flyway + 用户/JWT/BIT 认证 + 上传接口 + 限流插件
- Android：Compose 脚手架 + 主题 + 导航 + 登录注册 + 设置页（含 LLM 配置）+ i18n 框架
- 验收：能注册、登录、上传一张图；日志 JSON 输出正常

**Week 2 — 核心业务**
- 后端：listings CRUD + 搜索（pg_trgm）+ tags + ISBN 查询 + wishes 增删改查与求购广场浏览（买家侧核心）
- Android：发布流（扫码 → ISBN 预填 → 表单提交）、列表、详情、搜索、求购广场（浏览 + 发布 + 查看求购者联系方式）
- 验收：完整一次卖书流程跑通；能发布求购、在广场看到、并看到求购者联系方式

**Week 3 — 智能与体验**
- Android：批量拍照 → LLM（vision，客户端直连）→ 草稿审核 → 批量提交；筛选器；联系方式展示
- 后端：批量提交接口优化
- 验收：拍三本书一次发布成功（使用用户自己的 LLM key）

**Week 4 — 进阶 + 收尾**
- 后端：求购自动匹配（WISH_HIT）+ notifications + WebSocket（wishes 基础 CRUD/广场已在 Week 2 完成）
- Android：求购匹配通知页；通知页；UI 打磨；中英文核校；崩溃修复
- 文档：README、架构图、API 文档（用 Ktor 自带 OpenAPI 插件或手写）
- 选做：NapCat 接入、推荐位

## 十、刻意延后的事项

为压缩工期，下面这些先不做但代码留口子：

- **服务端 LLM 代理**（`/ai/extract` + Redis 限流 + 平台 key）— 当前仅客户端直连
- **本地 OCR**（ML Kit Text Recognition v2）— 可作为 LLM 的前置步骤或离线降级方案
- 头像裁剪、富文本描述、视频
- 服务端图片缩略图生成（前期让客户端按需下载原图 + Coil 缓存即可）
- 反作弊、举报、信用分
- FCM 离线推送（先 WebSocket + 应用内 + 进程存活时本地通知）
- 国际化语言切换的 RTL 适配
- Redis（当前限流用内存令牌桶，单实例足够；多实例部署时引入）
- CDN / 图片加速