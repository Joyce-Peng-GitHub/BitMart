# BitMart 代码审查记录（2026-06-20）

> 本文档记录一次全量潜在问题审查的结果。审查由 4 个并行子代理分别覆盖
> **后端安全/认证**、**后端数据/并发**、**Android 数据层**、**Android 功能层**，
> 共覆盖 155 个源文件。高危项已逐一回到源码核实。
>
> 本文档仅记录问题与建议，**不代表已修复**。修复进度见每条末尾的「状态」。

## 审查范围

| 维度 | 范围 |
| --- | --- |
| 后端安全/认证 | `auth/` · `storage/` · `external/` · `config/` · `shared/ApiError.kt` · `Application.kt` · `AppComponents.kt` |
| 后端数据/并发 | `listing/` · `db/` · `domain/` · `job/` · `user/` · `db/migration/*.sql` |
| Android 数据层 | `core/data/**` · `core/domain/**` · `core/di/` · `llm/` |
| Android 功能层 | `feature/**` · `core/ui/**` · 导航 · `MainActivity` |

**严重级别**：🔴 高危（确凿 bug / 安全风险，优先修） · 🟡 中危 · 🟢 低危/可记录

---

## 🔴 高危

### H1 · 登录时序侧信道 → 可枚举学号
- **位置**：`bitmart-server/.../auth/AuthService.kt:84-86,155`
- **问题**：用户不存在时校验的占位哈希 `DUMMY_HASH` 参数为 `m=1024,t=1,p=1`，而真实密码哈希按配置使用 `m=65536,t=3,p=1`（`application.conf:48-51`），验证开销相差约 190 倍。
- **影响**：不存在的学号返回明显更快，反噬了「抹平时序」的本意，攻击者可凭响应时间枚举已注册学号。
- **修法**：启动时用 `passwordHasher` 以**相同配置参数**对一个随机串哈希一次作为占位哈希，替换硬编码的弱参数串。
- **状态**：✅ 已修复（`AuthService.placeholderHash` 以 `passwordHasher` 配置参数生成占位哈希；测试 `AuthServiceTest` + `AuthRoutesTest`）。

### H2 · `updateQuantitySold` 无锁盲写 → 并发售出丢失更新
- **位置**：`bitmart-server/.../listing/ListingRepository.kt:176-180`；`listing/ListingService.kt:139-145`
- **问题**：`UPDATE listing SET quantity_sold=:new WHERE id=? AND deleted_at IS NULL`，缺少 `AND quantity_sold=:expectedOld` 的 CAS 条件。Service 层「读 `current.quantitySold` → 校验 → 写」存在经典读-改-写竞态。
- **影响**：两个并发 PATCH 都读到 5，一个写 6 一个写 4，后者静默覆盖前者；`affected==0 → QuantityConflict` 对真正的并发**永不触发**，与设计宣称的「并发约束」相悖。
- **修法**：WHERE 增加 `AND quantity_sold=:expectedOld`（此时 `affected==0` 即真实冲突），或在事务开头对该行 `SELECT … FOR UPDATE`。
- **状态**：⬜ 待修复

### H3 · 后端返回未知枚举值 → Android 闪退
- **位置**：`bitmart-android/.../core/data/repository/ListingRepositoryImpl.kt:121-122,129-130`；`core/domain/DomainResult.kt:44-47`
- **问题**：`enumValueOf<ListingType>(type)` / `enumValueOf<ListingCategory>(category)` 在 `DomainResult.map{}` 内执行，而该 `map` 跑在 `BitMartApi` 的 `safe{}` try-catch **之外**，`DomainResult.map` 自身无异常捕获。
- **影响**：服务端新增类别或版本漂移返回客户端未知枚举时，抛 `IllegalArgumentException` 直接崩溃，而非降级为 `InvalidResponse`。
- **修法**：用 `enumValues().firstOrNull { it.name == type } ?: 兜底值`，或将 DTO→domain 映射移入 `safe{}` 边界内。
- **状态**：⬜ 待修复

### H4 · 出站 HTTP 客户端未配置超时
- **位置**：后端 `AppComponents.kt:105-107`；Android `core/di/AppModule.kt:82-92`
- **问题**：后端 `HttpClient(CIO)` 仅装 `ContentNegotiation`，配置中的 `bit101/showapi.requestTimeoutMs`（15s，`ConfigSections.kt:150-166`）未接进 `Bit101Client`/`ShowApiClient`；Android 主 backend 客户端也未装 `HttpTimeout`（仅 LLM 客户端装了）。
- **影响**：上游卡住时调用协程无限期挂起；`/auth/bit101/verify`、`/books/lookup` 易触达，可造成资源堆积。
- **修法**：两端均 `install(HttpTimeout){ requestTimeoutMillis = … }` 并把配置值接入。
- **状态**：⬜ 待修复

---

## 🟡 中危

| # | 问题 | 位置 | 说明 / 修法 | 状态 |
| --- | --- | --- | --- | --- |
| M1 | `original_price` 完全未校验 | `domain/ListingValidator.kt`；`listing/ListingRepository.kt:50,137` | 仅校验了 `unitPrice`；超 `99999999.99` 触发 PG numeric overflow → 裸 500，负数被静默存入。对 `originalPrice` 复用 `validatePrice`。 | ⬜ |
| M2 | 上传先整体读入内存再判大小 | `storage/UploadRoutes.kt:46-53`；`UploadService.kt:25` | `readBytes()` 全量入堆后才查 5MiB 限制，限额未在传输层生效。应在 multipart/引擎层设硬上限或边读边限。 | ⬜ |
| M3 | 认证/上传端点无限流 | `auth/AuthRoutes.kt:34-104`；`Application.kt:99-113` | login / bit101 verify / register / reset / uploads 均无 RateLimit，可在线撞库与刷接口。装 Ktor `RateLimit`，按 IP/学号限速。 | ⬜ |
| M4 | `imageKeys` 客户端原样入库无校验 | `listing/ListingRepository.kt:73-79,146-155` | 未校验是否本服务签发、未绑定上传者，存在 IDOR / 返回 URL 注入隐患（磁盘读取本身被 Ktor `staticFiles` 规范化挡住）。按签发格式 `yyyy/MM/dd/<uuid>.<ext>` 校验。 | ⬜ |
| M5 | 表单 Scaffold 缺 `WindowInsets.safeDrawing` | `LlmSettingsScreen:72`、`AccountSettingsScreen:74`、`ContactsScreen:50` 等 9 处 | 项目无 `adjustResize`/`imePadding()`，键盘遮挡底部输入框。按约定补 `contentWindowInsets = WindowInsets.safeDrawing`。 | ⬜ |
| M6 | 注册/改密表单用 `remember` 而非 `rememberSaveable` | `AuthScreen.kt:55-57`；`ChangePasswordScreen.kt:55-58` | 旋转 / **切换语言** 触发重组时已输入内容全丢，`registerMode` 退回登录态。至少对 `registerMode` 及非密码字段用 `rememberSaveable`。 | ⬜ |
| M7 | 图片解码+压缩 / base64 在主线程 | `feature/publish/PublishScreen.kt:1047-1072`；`llm/OpenAiCompatibleLlmClient.kt:96` | activity-result 回调里同步 `decodeStream`+`compress`，大图卡 UI/ANR；LLM base64 编码同样未切 IO。包入 `Dispatchers.IO` 并对 `decodeStream` 判空。 | ⬜ |
| M8 | `quantityTotal.toInt()` 可抛异常 | `feature/publish/PublishViewModel.kt:666,698` | 提交时用会抛的 `toInt()` 重解析未 trim 字段，而校验用宽松 `toLongOrNull()`。复用已校验的解析结果并 trim。 | ⬜ |

---

## 🟢 低危 / 可记录

| # | 问题 | 位置 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| L1 | keyset 分页过滤可变谓词 | `listing/ListingRepository.kt:211,229` | `expires_at>now()` 与 `sold<total` 在翻页间变化，可能漏/重少量行。campus 规模可接受，或在 cursor 内带快照时间戳。 | ⬜ |
| L2 | `ExpiryWarningJob` 去重无 DB 唯一约束 | `job/ExpiryWarningJob.kt:107-113` | 靠应用层 `NOT EXISTS`，**多实例部署**会重复发通知（单实例顺序执行无虞）。多实例时加咨询锁或部分唯一索引。 | ⬜ |
| L3 | Tag lookup-then-insert 非并发安全 | `listing/TagRepository.kt` | 并发创建同名新标签会触发 `tag.name` 唯一冲突 500（罕见、可重试自愈）。建议 `INSERT … ON CONFLICT DO NOTHING RETURNING`。 | ⬜ |
| L4 | token / LLM key 明文存 DataStore | `core/data/local/TokenStore.kt:20-35`；`LlmConfigStore.kt:32-53` | `LlmConfigStore` 已有 `TODO(security)`；token 建议同样迁 EncryptedDataStore。 | ⬜ |
| L5 | 一次性事件用 sticky state 建模 | `AccountSettingsScreen.kt:89-90`；`PublishViewModel.kt:139,280` 等 | snackbar/message 重组时重显；`SharedFlow` 建议 `extraBufferCapacity=1`，或显式消费后清空。 | ⬜ |
| L6 | `ContentTransformationException` 回显解析细节 | `Application.kt:84-89` | 400 响应内含 `cause.message`，泄漏字段名/序列化内部。返回静态文案即可。 | ⬜ |
| L7 | 过期的 IDE 构建产物入了 classpath 风险 | `bitmart-server/bin/main/db/migration/`（缺 V5） | 若误入运行时 classpath 可能引发 Flyway 校验和/版本冲突。**建议删除该目录并加入 `.gitignore`**。 | ⬜ |
| L8 | `ImageViewer`/`ImageCarousel` 越界访问未防 | `core/ui/ImageViewer.kt:98`；`feature/detail/ListingDetailScreen.kt:458` | 重载后 `imageUrls` 变短时 `imageUrls[page]` 可能越界（窗口极小）。改用 `getOrNull`。 | ⬜ |
| L9 | `delete` 无 in-flight 守卫 | `feature/listing/ListingListViewModel.kt:161-168` | 快速双击滑动删除会发两次 DELETE（第二次 404）。删除中禁用该动作。 | ⬜ |

---

## ✅ 已核实正确（无需处理）

审查同时确认以下实现正确、无需改动：

- **Token**：32 字节 `SecureRandom` + Base64URL，仅存 `SHA-256(token)`，按哈希查找（等价常量时间）。
- **Argon2id**：参数 64MiB / t=3 / p=1，达 OWASP 推荐；密码/LLM key 不入日志。
- **会话生命周期**：过期、吊销、登出、改密、销户全链路撤销会话；每次登录签发新 token，无固定化。
- **授权 / IDOR**：listing 改删校验 owner-or-admin；通知/未读数按 `user_id` 限定；过期 listing 对非属主隐藏存在性；`/me/*` 全部经 `authenticate` 且以 `principal.userId` 为准。
- **SQL 注入**：原生全文检索 SQL 全参数化（含游标/标签/价格/查询）；`bitmart_search_config()::regconfig` 无用户输入。
- **文件安全**：图片魔数校验（JPEG/PNG/WebP 白名单，拒 SVG/HTML）；`LocalDiskBlobStorage.resolveSafely` 规范化并 `require(startsWith(root))`；静态服务经 Ktor `staticFiles`。
- **事务**：`publish`/`publishBatch`/`update`/`delete`/`deleteAccount` 均单事务原子写；外部 HTTP 调用置于事务外。
- **软删除**：所有 listing/user 读写路径一致带 `deleted_at IS NULL`；V5 `student_id` 部分唯一索引正确。
- **Android 架构**：`core/domain/**` 无 Android/Ktor 导入；LLM key 仅发往用户配置端点，不经 BitMart 后端；401 统一清 token；客户端校验镜像后端边界。
- **Compose / i18n**：`ProvideAppLocale` 启动崩溃陷阱已规避（`ContextWrapper` over Activity + `hiltViewModel` 顺序在外）；`isbn_result` 在 `LaunchedEffect` 消费并 `remove` 清除；全量使用 `collectAsStateWithLifecycle`；`LazyColumn` 均带稳定 `key=`。

---

## 修复优先级建议

1. **先修 H1 / H2 / H3**：分别是时序泄漏、丢失更新、线上闪退，均为确凿问题。
2. **再修 H4 与 M1–M4**：稳定性与安全加固。
3. M5–M8 为 UX / 健壮性改进，可批量处理。
4. L 级按需排期；其中 **L7（删除 `bin/` 目录）** 成本极低，可顺手处理。

> 修复时请遵循 `.claude/CLAUDE.md` 的 impl-test-review 流程（含补测试）。
