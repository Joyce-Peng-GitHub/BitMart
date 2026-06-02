# BitMart 架构设计

> 本文档基于 `docs/Request.md` 的需求与若干技术决策（详见各节）落地为可执行的架构方案。所有具体数值（过期天数上限、标签数量上限、分页大小等）均集中放置于配置文件中，本文档以"配置项"形式提及，不在正文写死。

---

## 1. 总览

```
┌────────────────────────────────────────────────────────────────────┐
│                        Android (Jetpack Compose)                   │
│  UI → ViewModel → UseCase → Repository → (Remote / Local / LLM)    │
└────────────────────────────────────────────────────────────────────┘
            │ HTTPS / JSON                       │ HTTPS
            ▼                                    ▼
┌──────────────────────────┐         ┌──────────────────────────┐
│   BitMart Backend (Ktor) │         │   外部 LLM Provider       │
│  Routes → Service → Repo │         │  (OpenAI-Compatible)     │
│       │           │       │         └──────────────────────────┘
│       ▼           ▼      │
│   FileStorage   PostgreSQL (+ pg_trgm + zhparser)
│   (LocalDisk     │
│    抽象接口)     ▼
│       │      ShowAPI / BIT101 (服务端代理)
└──────────────────────────┘
```

**核心思路**
- 后端轻量、单体部署；前后端通过 REST/JSON 通信，无 GraphQL/gRPC 等额外协议复杂度。
- 卖品（Sell）与求购（Buy）共用一张 `listing` 表，以 `type` 字段区分，业务层共享 Service/Repository。
- 图片、文件存储通过 `BlobStorage` 接口抽象，初期落到本地磁盘，未来切 MinIO/S3 仅替换实现。
- LLM 识图由 Android 客户端直连第三方，服务器不承担 LLM 流量与成本；ISBN/身份认证等少量外部 API 由服务端代理（含缓存与凭据保护）。

---

## 2. 技术选型

| 层级 | 选型 | 版本/说明 |
|------|------|---------|
| 后端语言 | Kotlin (JVM 21) | LTS |
| 后端框架 | **Ktor** | 最新稳定版，Netty 引擎 |
| ORM | **Exposed**（JetBrains 官方） | DSL + DAO 二选，本项目使用 DSL |
| 数据库迁移 | **Flyway** | SQL 脚本版本化 |
| 数据库 | **PostgreSQL 16** + 扩展 `pg_trgm`、`zhparser`（或 `pg_jieba`） | 中文分词 + 模糊匹配 |
| 数据库连接池 | HikariCP | Exposed 默认整合 |
| 验证/序列化 | `kotlinx.serialization` | 与 Ktor 原生整合 |
| 鉴权 | Opaque Token + 服务端会话表 | 通过 Ktor `Authentication` 插件接入 |
| 密码哈希 | **Argon2id** (`de.mkammerer:argon2-jvm`) | OWASP 推荐 |
| 日志 | SLF4J + Logback；MDC 注入 requestId/userId | JSON 输出便于聚合 |
| 配置 | HOCON (`application.conf`) + 环境变量覆盖 | Ktor 原生 |
| 本地缓存 | **Caffeine** | 进程内 TTL 缓存（verifyTicket、限流计数等） |
| 测试 | JUnit 5 + Kotest 断言 + Testcontainers (PostgreSQL) + Ktor `testApplication` | TDD |
| 推送 | **FCM**（主）+ 应用内 SSE 拉取（兜底） | 见 §10 |
| 文件 | 本地磁盘 + 抽象 `BlobStorage` | 见 §8 |
| Android UI | **Jetpack Compose** + Material 3 | Compose BOM 最新稳定 |
| Android 架构 | **Clean Architecture**（domain / data / presentation） | 见 §5 |
| Android DI | **Hilt** | 与 Compose/Navigation 集成成熟 |
| Android 网络 | Ktor Client + `kotlinx.serialization` | 与后端共享数据类 |
| Android 本地存储 | Room（结构化）+ DataStore（偏好/草稿） | |
| Android 图像 | Coil 3 | Compose 原生支持 |
| 条码扫描 | **ML Kit Barcode Scanning**（Bundled 模型，离线可用） | |

---

## 3. 模块划分

### 3.1 后端模块（单 Gradle 工程，多模块拆分）

```
bitmart-server/
├─ app/                     // Ktor 入口、装配、配置
├─ core/
│  ├─ auth/                 // 身份认证、会话、密码、BIT101 适配
│  ├─ user/                 // 用户、昵称、通知
│  ├─ listing/              // 卖/买（共用），含标签、图片
│  ├─ book/                 // ISBN 元数据、ShowAPI 缓存
│  ├─ tag/                  // 标签热门统计、归一化
│  ├─ notification/         // 站内通知 + 推送适配
│  └─ admin/                // 管理员动作（改进项预留）
├─ infra/
│  ├─ db/                   // Exposed 表、Flyway
│  ├─ storage/              // BlobStorage 接口与 LocalDisk 实现
│  ├─ http/                 // 共享 HTTP Client、错误模型
│  ├─ external/             // BIT101、ShowAPI 客户端
│  └─ search/               // 搜索查询构造（trgm/分词）
└─ shared/                  // DTO/常量/异常
```

### 3.2 Android 模块（Clean Architecture）

```
bitmart-android/
├─ app/                     // Hilt 入口、Navigation 顶层
├─ core/
│  ├─ designsystem/         // Compose 主题、色板、暗色/自定义
│  ├─ ui/                   // 通用组件
│  ├─ data/                 // Repository 实现、DataSource
│  ├─ domain/               // UseCase、领域模型、Repository 接口
│  └─ common/               // 工具、错误模型
├─ feature/
│  ├─ auth/                 // 登录、注册、找回密码
│  ├─ listing-feed/         // 卖品/求购列表（共用 UI 组件）
│  ├─ listing-detail/
│  ├─ publish/              // 发布卖品/求购（共用）
│  ├─ book-scan/            // 条码批量扫描 + 编辑
│  ├─ llm-import/           // LLM 拍照识图 + 编辑
│  ├─ profile/              // 昵称、通知、注销
│  └─ admin/                // 预留
└─ libs/
   ├─ llm-client/           // OpenAI 兼容客户端（response_format）
   └─ network/              // Ktor Client + 拦截器（鉴权/日志）
```

**依赖方向**（Clean Architecture）：`feature → domain ← data`，`feature → core/designsystem|ui`。`domain` 不依赖 Android/Ktor，纯 Kotlin，可单测。

---

## 4. 数据模型

> 所有时间字段使用 `TIMESTAMPTZ`；金额使用 `NUMERIC(10,2)`；软删除统一 `deleted_at TIMESTAMPTZ NULL`，索引上加 `WHERE deleted_at IS NULL` 部分索引。

### 4.1 用户与会话

```sql
user (
  id              BIGSERIAL PK,
  student_id      VARCHAR(20) UNIQUE NOT NULL,   -- 学号
  password_hash   TEXT NOT NULL,                  -- Argon2id
  nickname        VARCHAR(32) NULL,               -- 空 → "匿名"
  role            SMALLINT NOT NULL DEFAULT 0,    -- 0 普通 / 1 管理员
  status          SMALLINT NOT NULL DEFAULT 0,    -- 0 正常 / 1 封禁
  created_at      TIMESTAMPTZ NOT NULL,
  deleted_at      TIMESTAMPTZ NULL                -- 注销：仅打标，联系记录见 §11
)

session (
  token_hash      BYTEA PRIMARY KEY,              -- 仅存 SHA-256(token)
  user_id         BIGINT FK,
  created_at      TIMESTAMPTZ NOT NULL,
  last_used_at    TIMESTAMPTZ NOT NULL,
  expires_at      TIMESTAMPTZ NOT NULL,
  user_agent      TEXT NULL,
  revoked         BOOLEAN NOT NULL DEFAULT FALSE
)
INDEX session_user_idx ON session(user_id) WHERE NOT revoked;

notification (
  id              BIGSERIAL PK,
  user_id         BIGINT FK,          -- NULL 表示全员公告
  category        SMALLINT NOT NULL,  -- ANNOUNCEMENT/EXPIRY_WARN/...
  title           VARCHAR(120) NOT NULL,
  body            TEXT NOT NULL,
  payload         JSONB NULL,         -- 跳转 listingId 等
  read_at         TIMESTAMPTZ NULL,
  created_at      TIMESTAMPTZ NOT NULL
)
INDEX notification_user_cursor_idx ON notification(user_id, created_at DESC);
INDEX notification_announce_idx ON notification(created_at DESC) WHERE user_id IS NULL;

push_token (
  id              BIGSERIAL PK,
  user_id         BIGINT FK,
  token           TEXT NOT NULL,
  platform        SMALLINT NOT NULL,  -- 0 ANDROID_FCM / 1 UNIFIED_PUSH / ...
  created_at      TIMESTAMPTZ NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL,
  UNIQUE(user_id, token)
)
INDEX push_token_user_idx ON push_token(user_id);
```

### 4.2 商品/需求（同表 + type）

```sql
listing (
  id              BIGSERIAL PK,
  type            SMALLINT NOT NULL,         -- 0 SELL / 1 BUY
  category        SMALLINT NOT NULL,         -- 0 GENERAL / 1 BOOK
  user_id         BIGINT FK,
  title           TEXT NOT NULL,
  description     TEXT NOT NULL DEFAULT '',
  unit_price      NUMERIC(10,2) NULL,        -- NULL = 面议/带价联系
  quantity_total  INT NOT NULL CHECK (quantity_total >= 1),
  quantity_sold   INT NOT NULL DEFAULT 0
                  CHECK (quantity_sold BETWEEN 0 AND quantity_total),
  pickup_location TEXT NULL,
  contact         JSONB NOT NULL,            -- {channel:"WECHAT|QQ|PHONE|...", value:""}+
  expires_at      TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL,
  deleted_at      TIMESTAMPTZ NULL,
  search_tsv      TSVECTOR,                  -- 触发器维护，title+description 经 zhparser
  source          SMALLINT NOT NULL DEFAULT 0 -- 0 USER / 1 NAPCAT_BOT（预留，见 §14）
)
INDEX listing_active_idx
  ON listing(type, category, expires_at)
  WHERE deleted_at IS NULL;
INDEX listing_user_idx ON listing(user_id) WHERE deleted_at IS NULL;
INDEX listing_tsv_idx ON listing USING GIN(search_tsv);
INDEX listing_title_trgm_idx ON listing USING GIN(title gin_trgm_ops);
INDEX listing_desc_trgm_idx  ON listing USING GIN(description gin_trgm_ops);

listing_image (
  id           BIGSERIAL PK,
  listing_id   BIGINT FK,
  blob_key     TEXT NOT NULL,           -- 由 BlobStorage 解释
  ord          SMALLINT NOT NULL,
  width        INT, height INT,
  UNIQUE(listing_id, ord)
)

tag (
  id           BIGSERIAL PK,
  name         VARCHAR(20) UNIQUE NOT NULL,    -- 归一化（小写、去空白）
  usage_count  INT NOT NULL DEFAULT 0          -- 用于"热门标签"建议
)

listing_tag (
  listing_id   BIGINT FK,
  tag_id       BIGINT FK,
  PRIMARY KEY (listing_id, tag_id)
)

-- 书籍专属
book_meta (              -- 服务端缓存的 ISBN 信息（不可变事实）
  isbn         VARCHAR(20) PRIMARY KEY,
  title        TEXT, authors TEXT, publisher TEXT, edition TEXT,
  raw          JSONB NOT NULL,         -- ShowAPI 原始返回
  fetched_at   TIMESTAMPTZ NOT NULL
)

listing_book (           -- 与 listing 1:1（仅 category=BOOK）
  listing_id   BIGINT PK FK,
  isbn         VARCHAR(20) NULL,
  title        TEXT NULL, authors TEXT NULL,
  publisher    TEXT NULL, edition TEXT NULL
)
```

**搜索策略**：
- 主路径 `search_tsv @@ plainto_tsquery('zhparser', :q)`，按 `ts_rank` 排序。
- 兜底/容错：当 tsquery 命中数低于阈值，叠加 `title % :q OR description % :q`（pg_trgm 模糊）。
- 中英混合：zhparser 对英文按空白切分，已可工作；如对短英文 query 召回不足，再走 trgm 兜底。
- 价格区间、标签、`include_sold`、`include_no_price` 等过滤条件以 WHERE 拼接；按时间排序为 `ORDER BY created_at DESC`。
- 分页采用 **keyset**（`(created_at, id) < (:cursor_t, :cursor_id)`），不使用 `OFFSET`。

---

## 5. Android 客户端

### 5.1 Clean Architecture 分层

| 层 | 职责 | 依赖 |
|---|---|---|
| `presentation` (Compose + ViewModel) | UI 状态、事件、导航 | `domain` |
| `domain` (UseCase + Model + Repo 接口) | 业务规则，纯 Kotlin | — |
| `data` (Repo 实现 + DataSource) | 远端 API、本地 Room/DataStore、缓存 | `domain` |

ViewModel 持有 `UseCase`，通过 `StateFlow` 暴露 UI 状态；副作用（导航、Toast）走一次性 `SharedFlow`。

### 5.2 共享买/卖 UI

发布、列表、详情三类页面均以 `ListingType` 作为参数，UI 组件按文案表（"售价/期望价"、"卖家/求购者"）切换。底层调用同一组 `PublishListingUseCase`、`SearchListingsUseCase`、`GetListingDetailUseCase`，后端走同一 REST 资源 `/listings?type=...`。

### 5.3 本地数据

- **登录态**：DataStore 保存 Opaque Token；启动时挂载到 Ktor Client 拦截器。
- **常用联系方式**：Room 表 `recent_contact(channel, value, last_used_at)`。
- **草稿/未上传批次**：Room 表 `draft_listing(...)`，含图片本地 URI；用户离开"批量发布"页时自动落盘，未上传清单不会丢失。
- **标签缓存**：服务端"热门标签"接口结果做 10 分钟内存缓存。

### 5.4 LLM 识图（客户端直连）

- 用户在设置中填入 OpenAI-Compatible 的 base URL + API Key + Model；保存在 EncryptedDataStore（Tink/Jetpack Security）。
- 用户在拍照前选择「书籍 / 通用商品」，对应两套提示词模板（资源文件）；用户可在设置覆盖。
- 调用使用 `response_format: { type: "json_schema", json_schema: {...} }` 强约束输出结构（不支持 json_schema 的服务降级为 `json_object` + 提示词约束 + 客户端 JSON Schema 校验）。
- 解析后填入与"批量扫描书籍"相同的编辑界面，由用户审核后再上传。
- **风险隔离**：LLM Key 永不离开设备；后端不感知用户配置的 LLM。

### 5.5 条码扫描

- ML Kit Bundled 模型（离线），相机预览 + 防抖（同 ISBN 1 秒内只识别一次）。
- 识别到 ISBN 后调用 `POST /books/lookup`（服务端代理 ShowAPI），失败回退到用户手动填写。
- 多本书放入"待上传清单"，可逐条编辑、删除、重排，最后批量提交。

### 5.6 顶层导航与页面结构

> 落地自 `docs/Android-App-UI.md`，作为前述各 feature 的组织方式。

底部导航两个入口：**买卖** 与 **我的**。

- **买卖**：内含「商品」「收购」两个 tab（界面基本相同，以 `ListingType` 区分文案）。
  - 顶部搜索框 + 按钮；列表下滑无限加载（keyset）。
  - 登录后可进入详情；**仅详情页**显示取货地点与联系方式，列表页不含（贴合 §6.3 摘要不返回 `contact`）。
  - 右下角两个悬浮按钮：筛选、发布新项。
    - 筛选唤起弹窗，设置标签/价格区间/是否含面议/是否含售罄等条件，支持清空、取消、确认。
    - 发布需登录，进入批量发布页；先选「书籍 / 一般商品」，按类型展示不同字段；可拍照交 LLM 识别（每次一张，合并进本地暂存清单），未配置 LLM 则跳转 LLM 设置页；展示热门标签并允许自定义，标签个数上限编译期配置。
- **我的**：
  - 右上角邮件状图标进入通知页（公告、过期提醒），对应 `GET /me/notifications`。
  - 顶部显示昵称、学号、ID；未登录则显示"未登录"并可点击进入登录页。
  - 栏目：常用联系方式（本地）、我的商品、我的收购（均可删/查/改，含调整已售出数量，对应 `GET /me/listings` + `PATCH/DELETE /listings/{id}`）、设置、关于。

**设置存储**：除账号信息外的设置以 JSON 持久化于 DataStore（唯 LLM API Key 例外，存于 EncryptedDataStore，见 §5.4）。
- 账号设置：改昵称、改密码（经统一身份认证）、退出登录、注销账号（未登录则跳转登录页）。
- LLM 设置：协议（暂仅 OpenAI Compatible）、Base URL、API Key（提示不上传服务器）、模型名、超时阈值、书籍/一般商品识别提示词；可保存或清空（详见 §5.4，Key 存 EncryptedDataStore）。
- 语言设置、主题设置：预留多语言与自定义色板（见 §14）。

---

## 6. 后端 API

> 全部基于 REST/JSON；统一前缀 `/api/v1`。所有写操作要求 `Authorization: Bearer <opaque-token>`。错误响应 `{ "error": { "code": "...", "message": "..." } }`。

### 6.1 认证 `/auth`
- `POST /auth/bit101/verify`：入参 `{studentId, password}`；服务端走 BIT101 校验通过后，签发**短时验证票** `verifyTicket`（仅用于下一步）。
- `POST /auth/register`：`{verifyTicket, password, nickname?}` → `{token, user}`。
- `POST /auth/login`：`{studentId, password}` → `{token, user}`。
- `POST /auth/reset-password`：`{verifyTicket, newPassword}`。
- `DELETE /auth/session`：登出（吊销当前会话）。
- `DELETE /auth/sessions`：全部登出（吊销该用户所有会话）。
- `DELETE /auth/account`：注销账号（软删用户 + 级联软删 `listing` + 全部吊销会话）。

### 6.2 用户 `/me`
- `GET /me`、`PATCH /me`（昵称）、`GET /me/notifications?cursor=...`（返回结果合并个人通知与全员公告，按 `created_at` 统一排序）、`POST /me/notifications/{id}/read`、`POST /me/fcm-token`（注册推送 token）、`GET /me/stream`（SSE 长连接，推送轻量更新信号，鉴权同 REST）。
- `GET /me/listings?type=&cursor=`：当前用户**自己**发布的列表，供"我的商品/我的收购"管理。`type` 可选（省略则返回买卖两类）。与公开 `GET /listings` 不同：**包含已售罄与已过期项**（但仍排除软删除），按 `created_at` 倒序 keyset 分页；摘要字段同 `GET /listings`。需登录。

### 6.3 列表 `/listings`
- `GET /listings`：query 参数 `type, category?, q?, tagIds?, minPrice?, maxPrice?, includeNoPrice?, includeSold?, cursor?`。返回摘要列表（含 `id, type, category, title, unitPrice, quantityTotal, quantitySold, nickname, firstImageUrl, tags, createdAt`；**不含** `contact`、完整 `description`、`pickupLocation`（取货地点仅详情页可见，贴合需求"列表不显示交易地点和联系方式"）），无论是否登录均可访问。
- `GET /listings/{id}`：详情（含 `contact`、完整 `description`、所有图片）；**未登录返回 401**（贴合需求"不对未登录的客户端显示详情"）。
- `POST /listings`：单条发布。
- `POST /listings/batch`：批量发布；**全部成功或全部回滚**（单事务）；响应中返回所有校验错误列表供用户逐条修正后重新提交。
- `PATCH /listings/{id}`：修改字段、`quantitySold`、延期 `expiresAt`。
- `DELETE /listings/{id}`：软删除。
- `GET /tags/popular?limit=...`：热门标签。

### 6.4 书籍 `/books`
- `POST /books/lookup`：`{isbn}` → `book_meta`（命中缓存直接返回；否则调用 ShowAPI 后回写缓存）。

### 6.5 上传 `/uploads`
- `POST /uploads/images`：multipart，校验 MIME、尺寸、单文件大小（配置项）；返回 `{blobKey, width, height}`，前端在创建 listing 时携带。

### 6.6 管理 `/admin`（预留）
- `DELETE /admin/listings/{id}`、`POST /admin/users/{id}/ban`、`DELETE /admin/users/{id}/ban`。

---

## 7. 鉴权与身份

### 7.1 BIT101 校验流程

**注册**
```
Android         Backend                BIT101
  │  /verify     │                      │
  │─────────────▶│  POST /login (按官方 API) ─▶
  │              │◀──── 200/401 ────────│
  │◀── ticket ───│  发 verifyTicket(15min, 含 studentId)
  │  /register   │
  │─────────────▶│  校验 ticket → 写 user + 签发 session token
  │◀── token ────│
```

**重置密码**（与注册共用 `/auth/bit101/verify` 获取 ticket）
```
Android         Backend                BIT101
  │  /verify     │                      │
  │─────────────▶│  POST /login (按官方 API) ─▶
  │              │◀──── 200/401 ────────│
  │◀── ticket ───│  发 verifyTicket(15min, 含 studentId)
  │ /reset-password │
  │─────────────▶│  校验 ticket + studentId 匹配已有用户 → 更新 password_hash + 吊销所有会话
  │◀── 200 ─────│
```
- 入站的统一身份认证密码**仅用于一次直连 BIT101**，不落盘、不入日志。
- `verifyTicket` 存储在 **Caffeine** 进程内缓存（TTL 15 分钟，单次使用即失效——使用后立即 `cache.invalidate(ticket)`）。Ticket 内嵌 `studentId` 绑定，校验时比对请求中的 studentId 与 ticket 中的 studentId 一致，防止截获后用于不同学号。单实例部署下无需 Redis；未来多实例可升级为 Redis。
- BIT101 客户端按其[官方文档](https://bit101-api.apifox.cn/)实现；密码字段按其要求加密后传输。

### 7.2 会话管理
- Token = 32 字节 CSPRNG → Base64URL；服务端只存 `SHA-256(token)`。
- 默认 30 天过期；每次使用更新 `last_used_at`；支持单点登出与全部登出（封禁/改密时全部吊销）。

### 7.3 密码策略
- Argon2id（m=64MB, t=3, p=1 起步；版本号写入 hash 字串便于未来调参）。
- 注册/重置时强制最小长度与字符多样性（配置项）。

---

## 8. 存储抽象（图片）

```kotlin
interface BlobStorage {
    suspend fun put(key: String, bytes: ByteArray, contentType: String): BlobRef
    suspend fun get(key: String): BlobRef?
    suspend fun delete(key: String)
    fun publicUrl(key: String): String   // 给前端直接拉取
}
```

- **LocalDiskBlobStorage**：根目录 `storage.root`；按 `yyyy/mm/dd/<uuid>.<ext>` 落盘；通过 Ktor `staticFiles` 暴露在 `/static/<key>`；写入时校验 magic-bytes 防止伪造扩展名。
- **未来 MinIOBlobStorage**：实现同一接口，`publicUrl` 返回预签名 URL；切换只需修改 DI 装配 + 配置；数据迁移用一次性脚本搬运现有目录到 bucket，DB 中 `blob_key` 兼容。
- 上传链路统一为「前端→后端→存储」，避免预签名直传带来的逻辑差异；待规模上升再切到直传。

---

## 9. 业务规则细节

- **过期**：`min_days/max_days/default_days` 写入 `application.conf`（创建与延期同源）。延期校验：新 `expiresAt` 必须满足 `now + min_days <= expiresAt <= now + max_days`。后台定时任务每小时检查"24h 内到期"产生通知；推送和站内信同时发出。
- **数量**：`quantity_sold` 受 CHECK 约束；前端"调整售出数量"仅允许 `>=` 历史值（不可"反悔"），后端 UPDATE 语句使用 `SET quantity_sold = :new WHERE quantity_sold <= :new` 保证单调递增（管理员如需修正可直接操作数据库）。若 UPDATE 影响 0 行，返回 409 Conflict。
- **updated_at**：由应用层在每次 UPDATE 时显式设置为 `now()`（Exposed DSL 中统一处理），不使用数据库触发器。
- **删除**：`listing.deleted_at` 打标；查询统一加 `deleted_at IS NULL`。
- **注销**：事务内 `UPDATE user SET deleted_at=now()`，并对该用户的 listing 全部软删；其会话全部吊销；`notification` 不删（保留站内审计）。
- **联系方式存储**：服务端 `contact` 为 JSONB 数组以兼容多渠道；应用层校验：数组长度 >= 1，每项 `channel` 必须为枚举值、`value` 非空。详情接口对未登录拒绝。
- **隐私提示**：前端在「填写联系方式」与「展示联系方式」两处插入风险提示文案（资源文件，多语言友好）。

---

## 10. 通知与推送

- **应用内通知**：`notification` 表 + `/me/notifications`；ViewModel 启动时拉取未读计数；详情进入时调 `mark-read`。
- **推送**：FCM 主路径；客户端登录后注册 `fcm_token`（按设备一行，可多端）。
- **非 GMS 设备兜底**：进入应用时主动拉取 + 应用内 SSE 长连接（`/me/stream`）。SSE 仅推送轻量"有更新"信号，不传载荷，鉴权同 REST。服务端每 30 秒发送 `:keepalive` 注释帧；客户端超时未收到则自动重连。
- **后端推送适配**：`NotificationDispatcher` 接口 + `FcmDispatcher` 实现；新增渠道（UnifiedPush 等）不动业务代码。

---

## 11. 安全、日志、配置

- **HTTPS**：内网调试可用明文，公网部署强制 TLS（朋友的服务器或我方加 caddy 反代）。
- **HSTS、CSP、X-Content-Type-Options** 在反代层加。
- **请求 ID**：`X-Request-ID`，无则后端生成；进入 MDC，贯穿日志。
- **审计日志**：登录、注销、删除、封禁、修改密码 → 单独 logger 输出。
- **避免敏感信息入日志**：拦截器对 `password / token / authorization / contact.value` 字段脱敏。
- **配置文件**：`application.conf` 集中所有阈值；环境变量覆盖；CI 用单独 profile。

---

## 12. 测试策略（TDD）

| 层 | 工具 | 覆盖重点 |
|----|------|---------|
| 后端单元 | JUnit 5 + Kotest assertions + MockK | UseCase/Service 业务规则、边界（价格 NULL、quantity 边界、过期窗口） |
| 后端集成 | Ktor `testApplication` + Testcontainers PostgreSQL | 路由、鉴权、搜索 SQL（含 zhparser）、事务、级联软删 |
| 外部依赖 | WireMock | BIT101、ShowAPI 的成功/失败/超时 |
| Android domain | JUnit 5 + Turbine | UseCase 纯逻辑 |
| Android data | MockWebServer（Ktor Client） | Repository/DataSource |
| Android UI | Compose UI Test | 关键流程：登录、批量发布、列表搜索、详情可见性 |

**先写测试**：每个 API 端点先有 Ktor `testApplication` 用例（含未登录、过期 token、参数边界、空结果），再实现路由。

---

## 13. 部署

### 13.1 本地调试
- `docker-compose`：自定义 PostgreSQL 镜像（基于 `postgres:16`，编译安装 zhparser + SCWS 词库；Dockerfile 随项目维护）+ `bitmart-server`（开发镜像，挂载源码热重载或 Gradle 持续构建）。Testcontainers 复用同一自定义镜像。
- CI 环境：将自定义 PostgreSQL 镜像推送到私有 registry（或 GitHub Container Registry），CI pipeline 直接拉取，确保搜索相关集成测试可运行。
- 备选：如 zhparser 编译困难，可切换为 `pg_jieba`（CMake 构建，社区有现成 Docker 镜像）。
- 客户端 BASE_URL 通过 Build Variant 区分 `dev/staging/prod`。

### 13.2 上线
- 后端打包 `./gradlew installDist` → 系统 `systemd` unit 守护；或装入精简 OCI 镜像通过 `podman/docker` 运行。
- 部署位置：**默认部署在朋友的公网 IP 服务器**（避免每次回源到内网增加延迟）；如需将服务保留在内网，则朋友服务器跑 caddy/Nginx 反代 + Tailscale 后端。
- 备份：`pg_dump` 定时（配置项）+ 存储目录定时打包到对象存储（朋友方或第三方均可）。

---

## 14. 改进方向的预留挂点

| 方向 | 预留方式 |
|------|----------|
| 多语言 | 文案集中在 `strings.xml` / 后端使用 `Accept-Language` 路由文案模板；通知与错误消息走 i18n |
| 亮暗主题 / 自定义色板 | `core/designsystem` 已封装 `BitMartTheme(colorScheme=...)`，DataStore 持久化用户偏好 |
| 频率限制 | Ktor 拦截器 + 滑动窗口（Caffeine 内存版起步，升级到 Redis）；账号注销-注册窗口期记录在 `user_lifecycle_event` 表（待引入） |
| 权限管理 | `user.role` 字段已就位；`/admin/*` 路由前置 Role 校验；前端 `feature/admin` 模块预留 |
| NapCat 导入 | `listing.source` 字段已就位（DEFAULT 0=USER）；联系方式仅 QQ；详情页对 NAPCAT 来源加显式警告横幅 |

---

## 15. 开放问题（无碍开工，可在迭代中确认）

1. 推送是否必须支持非 GMS 设备？当前方案以 FCM 为主、SSE 兜底；若需替换为 UnifiedPush 仅需新增 Dispatcher 实现。
2. ShowAPI 调用配额与缓存策略——当前缓存永久有效（ISBN 元数据视为不可变），如需 TTL 后续在 `book_meta.fetched_at` 上加策略。
3. 是否需要"举报"流程？目前未在 Request 中提及，未做表结构预留；如需要后续增 `report` 表。
4. 注销后窗口期长度（频率限制改进项）——尚无具体值，留待引入限流时确定。
