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

**为什么这样**：业务数据（数据库 + 图片）放你自己服务器，朋友的机器只做 TLS 终结与转发，等于 0 信任暴露。Caddy 一行 `reverse_proxy 100.x.x.x:8080` 即可。如果将来朋友撤掉服务，把 Caddy 搬过来就行，应用层零改动。

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
-- 用户
CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id    VARCHAR(16)  UNIQUE NOT NULL,
  nickname      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,            -- Argon2id
  qq            VARCHAR(16),
  wechat        VARCHAR(64),
  phone         VARCHAR(16),
  avatar_url    TEXT,
  is_admin      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 出售单
CREATE TABLE listings (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  seller_user_id    UUID REFERENCES users(id) ON DELETE SET NULL, -- 注销时应用层先下架，再删用户；NULL = 已注销 或 NapCat 来源
  seller_qq         VARCHAR(16),                  -- NapCat 来源 或 备用联系方式
  source            VARCHAR(16)  NOT NULL,        -- USER | NAPCAT
  category          VARCHAR(32)  NOT NULL,        -- BOOK | ELECTRONICS | OTHER
  title             VARCHAR(255) NOT NULL,
  description       TEXT,
  price_cents       INTEGER,                      -- NULL = 面议
  currency          CHAR(3) NOT NULL DEFAULT 'CNY',
  condition_rating  SMALLINT,                     -- 1~5，可空
  total_quantity    INTEGER NOT NULL DEFAULT 1,
  available_qty     INTEGER NOT NULL DEFAULT 1,
  status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | SOLD_OUT | DELISTED
  isbn              VARCHAR(20),
  book_meta         JSONB,                        -- {author, publisher, pubdate, edition, price, gist, ...}
  search_text       TEXT GENERATED ALWAYS AS
                    (coalesce(title,'') || ' ' || coalesce(description,'') || ' ' ||
                     coalesce(book_meta->>'author','')) STORED,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK ((seller_user_id IS NOT NULL) OR (seller_qq IS NOT NULL))
);

-- 模糊搜索索引（中英混合直接走 trigram，无需分词器）
CREATE INDEX idx_listings_search_trgm ON listings USING gin (search_text gin_trgm_ops);
CREATE INDEX idx_listings_status_created ON listings (status, created_at DESC);
CREATE INDEX idx_listings_isbn ON listings (isbn) WHERE isbn IS NOT NULL;

-- 出售单内的单品（多本/多件分别售卖）
CREATE TABLE listing_items (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  listing_id  UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  item_index  INTEGER NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE | RESERVED | SOLD
  sold_at     TIMESTAMPTZ,
  UNIQUE (listing_id, item_index)
);

-- 图片
CREATE TABLE listing_images (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  listing_id  UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  object_key  TEXT NOT NULL,        -- MinIO 中的 key
  sort_order  INTEGER NOT NULL DEFAULT 0,
  is_cover    BOOLEAN NOT NULL DEFAULT FALSE,
  width       INTEGER, height INTEGER
);

-- 标签
CREATE TABLE tags (
  id          SERIAL PRIMARY KEY,
  name        VARCHAR(32) UNIQUE NOT NULL,
  usage_count INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE listing_tags (
  listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  tag_id     INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (listing_id, tag_id)
);

-- ISBN 元数据缓存（校园场景下同一课本被反复查询，缓存命中率高）
CREATE TABLE isbn_cache (
  isbn        VARCHAR(20) PRIMARY KEY,
  title       VARCHAR(255),
  author      VARCHAR(255),
  publisher   VARCHAR(255),
  pubdate     VARCHAR(16),              -- "2006-05" 格式
  edition     VARCHAR(8),               -- 版次，教材场景下很重要
  price       VARCHAR(16),              -- 原价/定价，供买家参考
  cover_url   TEXT,                     -- 封面图链接
  gist        TEXT,                     -- 内容简介
  raw_json    JSONB,                    -- 保留完整 API 响应以备扩展
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 求购（进阶功能）
CREATE TABLE wishes (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  keyword      VARCHAR(255) NOT NULL,
  max_price    INTEGER,
  tag_filter   TEXT[],
  active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at   TIMESTAMPTZ
);
CREATE INDEX idx_wishes_active ON wishes (active) WHERE active;

-- 通知
CREATE TABLE notifications (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type       VARCHAR(32) NOT NULL,     -- WISH_HIT | SYSTEM | ...
  payload    JSONB NOT NULL,
  read_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

-- refresh token（支持注销）
CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(255) NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked     BOOLEAN NOT NULL DEFAULT FALSE
);
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
    │   ├── MinioClient.kt
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
| `POST /auth/register` | body: `studentId, bitPassword, platformPassword, nickname, qq?, wechat?` → 调 BIT101 校验 → 写库 → 返回 `{accessToken, refreshToken}` |
| `POST /auth/login` | body: `studentId, platformPassword` |
| `POST /auth/refresh` | |
| `POST /auth/logout` | 撤销 refresh token |
| `GET /users/me`, `PATCH /users/me` | |
| `DELETE /users/me` | 注销账号：下架全部 ACTIVE 商品、删除求购/通知/refresh token、删除用户行 |
| `GET /listings?q=&category=&tags=&minPrice=&maxPrice=&sort=&cursor=` | 游标分页 |
| `POST /listings` | 创建出售单（含子项目数量） |
| `GET /listings/{id}` | 详情；seller 为 null 时显示"卖家已注销"；NapCat 来源显示 QQ + 风险提示 |
| `PATCH /listings/{id}` | |
| `POST /listings/{id}/items/{idx}/sold` | 标记已售一件 |
| `DELETE /listings/{id}` | 软下架 |
| `POST /uploads/images` | multipart 直传，返回 `{objectKey, url}`；或 `POST /uploads/presign` 返回 PUT URL |
| `GET /books/lookup?isbn=` | 先查 DB 缓存，未命中再调外部 ISBN API；结果入库供后续复用 |
| `GET /tags?prefix=` | 标签自动补全 |
| `POST /wishes`, `GET /wishes`, `DELETE /wishes/{id}` | |
| `GET /notifications?unread=true` | |
| `WS /ws` | 鉴权后推送通知，心跳 30s |

**WebSocket 生命周期**：
- 连接时通过 query param `?token=<accessToken>` 鉴权，token 无效立即关闭（4001）。
- 服务端每 30s 发 ping，客户端 10s 内未 pong 则断开。
- Access token 过期时服务端发 `{"type":"TOKEN_EXPIRED"}` 后关闭连接（4002），客户端自动 refresh 后重连。
- 客户端重连时携带 `?lastEventId=<timestamp>`，服务端补发该时间点之后的未读通知（最多 50 条）。
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

### 4.6 LLM 调用（纯客户端）

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

### 4.7 NapCat 进阶接入（可选）

独立 worker 进程订阅 NapCat 的 OneBot WebSocket → 收到群消息 → 拉取附图 → 调用 LLM 提取结构化信息 → 写库时 `source='NAPCAT'`、`seller_user_id=NULL`、`seller_qq=<发送者 QQ>`。详情页上对此类商品加红色风险提示。

**注意**：NapCat worker 需要自己的 LLM key 配置（环境变量），独立于用户侧 LLM 调用。

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
services:
  postgres:
    image: postgres:16-alpine
    environment: { POSTGRES_DB: bitmart, POSTGRES_PASSWORD: ... }
    volumes: ["./data/pg:/var/lib/postgresql/data"]
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    volumes: ["./data/minio:/data"]
  app:
    build: .
    depends_on: [postgres, minio]
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/bitmart
      MINIO_ENDPOINT: http://minio:9000
      JWT_SECRET: ${JWT_SECRET}
      BIT101_BASE: https://bit101.cn/api
    ports: ["127.0.0.1:8080:8080"]   # 仅监听 Tailscale 接口可改 0.0.0.0 + 防火墙
```

### 6.2 朋友服务器的 Caddyfile

```
bitmart.example.com {
  reverse_proxy your-tailscale-host:8080
  encode gzip
  request_body { max_size 20MB }   # 配合图片上传
}
```

### 6.3 图片访问说明

图片通过 MinIO presigned URL 直接下载，经 Tailscale + Caddy 两跳。初期依赖 Coil 客户端缓存降低重复请求。

**升级路径**：当带宽成为瓶颈时，在 Caddy 前加 CDN（Cloudflare 免费层），或将 MinIO 迁移到 S3 + CloudFront。

### 6.4 备份

每日 cron：`pg_dump | zstd | rsync` 到第二个磁盘 + Tailscale 到朋友机器一份。MinIO 可启 versioning。

## 七、安全要点（按优先级）

1. **BIT 密码不落盘不入日志**：网络层 TLS、内存中处理完即清空、日志中间件白名单。
2. **平台密码 Argon2id**（参数 `m=64MB, t=3, p=1`）。
3. **JWT 短 access（15min）+ refresh（30d）**，refresh 哈希入库以便撤销。
4. **图片上传校验**：MIME 嗅探 + 大小限制 + EXIF GPS 清除。
5. **通用限流**：内存令牌桶（Ktor 插件），对 `/auth/login`、`/auth/register` 等敏感接口按 IP 限流（如 5次/分钟），防暴力破解。
6. **联系方式仅登录后可见**；NapCat 来源商品在详情页加风险提示横幅，符合"避免交易"的要求。
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
- 验收：能注册（真打 BIT101）、登录、上传一张图；日志 JSON 输出正常

**Week 2 — 核心业务**
- 后端：listings CRUD + 搜索（pg_trgm）+ tags + ISBN 查询
- Android：发布流（扫码 → ISBN 预填 → 表单提交）、列表、详情、搜索
- 验收：完整一次卖书流程跑通

**Week 3 — 智能与体验**
- Android：批量拍照 → LLM（vision，客户端直连）→ 草稿审核 → 批量提交；筛选器；联系方式展示
- 后端：批量提交接口优化
- 验收：拍三本书一次发布成功（使用用户自己的 LLM key）

**Week 4 — 进阶 + 收尾**
- 后端：wishes + notifications + WebSocket
- Android：求购页 + 通知页；UI 打磨；中英文核校；崩溃修复
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