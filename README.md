# BitMart：为北理工开发的跳蚤市场——《Android技术开发基础》课程设计

> 班级：<u>114514</u>	学号：<u>1919810</u>	姓名：<u>田所浩二</u>

## 运行与开发环境

### 后端

#### 运行环境

- JDK 21
- PostgreSQL 16，需启用扩展 `pg_trgm`；建议安装 `zhparser`，未安装时迁移脚本会降级为内置 `simple` 分词，中文搜索效果会下降
- 数据库默认 `jdbc:postgresql://localhost:5432/bitmart`，账号/密码 `bitmart/bitmart`
- 服务器监听端口默认 `8080`
- 无需 Docker
- 查询 ISBN 依赖环境变量 `BITMART_SHOWAPI_APP_KEY`，需要从 [ShowAPI](https://www.showapi.com/apiGateway/view/1626) 申请密钥（注册即有免费额度 50 次/天）

#### 部署方法



#### 开发环境

- IntelliJ IDEA 2026.1.2
- JDK 21
- Kotlin 2.3.21
- Gradle 9.5.0
- Claude Code
- Codex

### Android 端

#### 运行环境

- Android 7.0 ~ 16 系统手机。未测试对平板电脑的兼容性。
- LLM 识图功能需要提供 OpenAI Compatible 协议的 API 接口和密钥。
- ISBN 扫码功能要求设备有摄像头并授权。

#### 部署方法

- **因为没有域名且受限于校园网环境，Android 端连接后端必须通过 `adb reverse tcp:8080 tcp:8080`。**
- 直接安装 APK 即可。

#### 开发环境

- Android Studio Panda 4 | 2025.3.4 Patch 1
- JDK 17
- Gradle 9.4.1
- AGP/Kotlin/KSP 9.2.1/2.3.21/2.3.9
- Claude Code

## App 需求分析

### 需求分析

目前我校跳蚤市场建立在 QQ 群上。在群里，卖家通常都是将商品拍照后附价格，而附文字描述较少，对于二手书尤甚。卖方这样很方便，但买方就比较麻烦，需要一张张图片找而不能搜索，而且很可能找到别人已经售出的商品。

因此我想开发这样一个跳蚤市场应用：卖方可以扫描书本条形码并填写售价、书况等信息，也可以对一本或多本书封面或书脊拍照后调用 LLM 批量添加，然后将信息上传到服务器数据库；对其他商品也同样可以提供拍照识别等功能，填写信息后上传到服务器。买方则可以搜索和筛选自己需要的商品，并获取卖方联系方式。相应地，买方也可以发布“收购”需求，等待卖方联系。

### 功能分解

- 账号
  - 调用 BIT101 API 转接统一身份认证系统进行身份验证后注册
  - 忘记/修改密码：身份认证后可以重置
  - 设置昵称
  - 修改密码
  - 注销账号
- 发布（出售/求购）
  - 填写名称、件数、单价、标签、描述、图片、联系方式、取货地点、过期时间
  - 书籍专项：ISBN、作者、出版社、版本；条形码扫描自动查书
  - 本地调用 LLM API，将图片压缩后发送识图
  - 草稿编辑、批量发布
- 公共列表
  - 商品/需求列表
  - 按时间排序、标签筛选、价格区间筛选、文字模糊搜索、是否展示售罄
  - 详情页显示卖家昵称、联系方式等详细信息（未登录不返回详情，以保护信息安全）
  - 不显示过期项
- 通知消息
  - 服务端通知：公告、临期提醒等
  - 支持通知拉取、未读计数与标记已读
- 我的
  - 我的发布列表（编辑、调整已售/收数量、删除）
  - 常用联系方式管理（本地存储）
  - 临期通知
- 设置
  - 多语言切换
  - 亮暗主题切换
  - LLM API 配置（本地存储）

### UI 设计与人机交互

参考 BIT 101 界面，采用经典 Material 3 主题。

## 架构设计及技术实现方案

### 后端

```
bitmart-server/src/
├── main
│   ├── kotlin
│   │   └── cn
│   │       └── edu
│   │           └── bit
│   │               └── bitmart
│   │                   ├── AppComponents.kt
│   │                   ├── Application.kt
│   │                   ├── auth
│   │                   │   ├── AuthDto.kt
│   │                   │   ├── AuthResults.kt
│   │                   │   ├── AuthRoutes.kt
│   │                   │   ├── AuthService.kt
│   │                   │   ├── Bit101PasswordCipher.kt
│   │                   │   ├── OpaqueToken.kt
│   │                   │   ├── PasswordHasher.kt
│   │                   │   ├── SessionRepository.kt
│   │                   │   ├── TokenAuthenticator.kt
│   │                   │   └── VerifyTicketStore.kt
│   │                   ├── config
│   │                   │   ├── BitmartConfig.kt
│   │                   │   ├── ConfigReader.kt
│   │                   │   └── ConfigSections.kt
│   │                   ├── db
│   │                   │   ├── DatabaseFactory.kt
│   │                   │   └── Tables.kt
│   │                   ├── domain
│   │                   │   ├── DomainTypes.kt
│   │                   │   ├── Listing.kt
│   │                   │   ├── ListingValidator.kt
│   │                   │   ├── PasswordPolicy.kt
│   │                   │   ├── TagNormalizer.kt
│   │                   │   ├── User.kt
│   │                   │   └── ValidationResult.kt
│   │                   ├── external
│   │                   │   ├── Bit101Client.kt
│   │                   │   ├── Bit101Dto.kt
│   │                   │   ├── ShowApiClient.kt
│   │                   │   └── ShowApiDto.kt
│   │                   ├── job
│   │                   │   └── ExpiryWarningJob.kt
│   │                   ├── listing
│   │                   │   ├── BookMetaRepository.kt
│   │                   │   ├── ListingDto.kt
│   │                   │   ├── ListingInputs.kt
│   │                   │   ├── ListingRepository.kt
│   │                   │   ├── ListingRequestMapper.kt
│   │                   │   ├── ListingRoutes.kt
│   │                   │   ├── ListingService.kt
│   │                   │   ├── ListingServiceResults.kt
│   │                   │   └── TagRepository.kt
│   │                   ├── shared
│   │                   │   └── ApiError.kt
│   │                   ├── storage
│   │                   │   ├── BlobStorage.kt
│   │                   │   ├── ImageTypeDetector.kt
│   │                   │   ├── LocalDiskBlobStorage.kt
│   │                   │   ├── UploadRoutes.kt
│   │                   │   └── UploadService.kt
│   │                   └── user
│   │                       ├── MeRoutes.kt
│   │                       ├── NotificationRepository.kt
│   │                       ├── UserRepository.kt
│   │                       └── UserService.kt
│   └── resources
│       ├── application.conf
│       ├── db
│       │   └── migration
│       │       └── V1__baseline.sql
│       └── logback.xml
└── test
    └── kotlin
        └── cn
            └── edu
                └── bit
                    └── bitmart
                        ├── ApiPrefixContractTest.kt
                        ├── HealthRouteTest.kt
                        ├── ProjectConfig.kt
                        ├── auth
                        │   ├── AuthRoutesTest.kt
                        │   ├── AuthServiceTest.kt
                        │   ├── AuthTestSupport.kt
                        │   ├── Bit101PasswordCipherTest.kt
                        │   ├── OpaqueTokenTest.kt
                        │   ├── PasswordHasherTest.kt
                        │   └── VerifyTicketStoreTest.kt
                        ├── config
                        │   └── BitmartConfigTest.kt
                        ├── db
                        │   ├── EmbeddedPostgresSupport.kt
                        │   └── MigrationIntegrationTest.kt
                        ├── domain
                        │   ├── ListingValidatorTest.kt
                        │   ├── PasswordPolicyTest.kt
                        │   └── TagNormalizerTest.kt
                        ├── external
                        │   ├── Bit101ClientTest.kt
                        │   ├── MockHttpSupport.kt
                        │   └── ShowApiClientTest.kt
                        ├── job
                        │   └── ExpiryWarningJobTest.kt
                        ├── listing
                        │   └── ListingRoutesTest.kt
                        ├── storage
                        │   ├── ImageTypeDetectorTest.kt
                        │   ├── UploadRoutesTest.kt
                        │   └── UploadServiceTest.kt
                        └── user
                            └── MeRoutesTest.kt
```

- 整体架构
  - 按业务领域划分（`auth`/`listing`/`user`/`storage`），主要业务包按 Routes $\to$ Service $\to$ Repository 分层，`external` 封装外部 API 客户端
  - `domain` 提供数据模型与校验逻辑
  - `config` 读取 `application.conf` 配置
- Web 框架：Ktor 3.5 + Netty，添加插件 Authentication、CallId、CallLogging、ContentNegotiation、StatusPages（提供统一错误响应）
- 登录认证
  - 采用 Opaque Token 方案，登录签发随机令牌存数据库
  - Ktor Auth 插件校验 Bearer token
  - 密码用 Argon2id 哈希
  - 注册/重置密码前先经 BIT 101 验证，签发短时效 VerifyTicket，用 Caffeine 保存
- 数据库
  - PostgreSQL 16 + pg_trgm + zhparser（提供中文搜索）；`zhparser` 不可用时自动降级到 `simple` 分词
  - DDL 由 Flyway SQL 迁移管理（开发结束后压到了一个迁移文件里）
  - 应用层用 Exposed 库读写数据库
  - 用 HikariCP 库管理连接池
  - 用数据库触发器维护 `search_tsv` 全文检索列
  - 列表接口用 keyset 分页
- 外部 API 集成
  - `Bit101Client` 调用 BIT 101 API 转接学校的统一身份认证系统，验证学号密码（密码先本地加密）
  - `ShowApiClient` 调万维易源（ShowAPI） ISBN 查询 API，结果缓存至 `book_meta` 表，以减少 API 调用次数（学校交易的书籍多为课程相关，大多重复）
- 文件存储：直接作为文件存在磁盘，图片上传经 MIME 白名单校验，客户端可通过 `/static` 路径访问
- `job` 定时任务：`ExpiryWarningJob` 协程定时扫描即将过期的项，创建站内通知（`NotificationRepository`）
- JSON 解析：Exposed JSONB
  - 通知
  - Show API 响应解析

- 日志：Logback
- 测试
  - Kotest + JUnit 5
  - 用 zonky embedded-postgres 调用 PG
  - Ktor TestHost 路由级测试
  - Ktor MockEngine 模拟客户端

### Android 端

```
bitmart-android/app/src/
├── main
│   ├── AndroidManifest.xml
│   ├── kotlin
│   │   └── cn
│   │       └── edu
│   │           └── bit
│   │               └── bitmart
│   │                   ├── BitMartApplication.kt
│   │                   ├── BitMartNavHost.kt
│   │                   ├── BitMartShell.kt
│   │                   ├── MainActivity.kt
│   │                   ├── core
│   │                   │   ├── data
│   │                   │   │   ├── local
│   │                   │   │   │   ├── ContactPrefsStore.kt
│   │                   │   │   │   ├── LanguagePrefsStore.kt
│   │                   │   │   │   ├── LlmConfigStore.kt
│   │                   │   │   │   ├── ThemePrefsStore.kt
│   │                   │   │   │   └── TokenStore.kt
│   │                   │   │   ├── remote
│   │                   │   │   │   ├── ApiResponseMapper.kt
│   │                   │   │   │   ├── BitMartApi.kt
│   │                   │   │   │   └── Dtos.kt
│   │                   │   │   └── repository
│   │                   │   │       ├── AuthRepositoryImpl.kt
│   │                   │   │       ├── ListingRepositoryImpl.kt
│   │                   │   │       └── ProfileRepositoryImpl.kt
│   │                   │   ├── designsystem
│   │                   │   │   └── Theme.kt
│   │                   │   ├── di
│   │                   │   │   └── AppModule.kt
│   │                   │   ├── domain
│   │                   │   │   ├── DomainResult.kt
│   │                   │   │   ├── model
│   │                   │   │   │   ├── AppLanguage.kt
│   │                   │   │   │   ├── Models.kt
│   │                   │   │   │   ├── PublishConfig.kt
│   │                   │   │   │   └── ThemeMode.kt
│   │                   │   │   └── repository
│   │                   │   │       ├── AuthRepository.kt
│   │                   │   │       ├── ListingRepository.kt
│   │                   │   │       └── ProfileRepository.kt
│   │                   │   └── ui
│   │                   │       ├── AdjustQuantityDialog.kt
│   │                   │       ├── AppLocale.kt
│   │                   │       ├── ImageViewer.kt
│   │                   │       ├── ListingCard.kt
│   │                   │       ├── ListingFilterDialog.kt
│   │                   │       ├── ListingTimeInfo.kt
│   │                   │       ├── ListingTypeLabels.kt
│   │                   │       ├── MediaUrls.kt
│   │                   │       ├── OwnedListingRow.kt
│   │                   │       ├── PasswordField.kt
│   │                   │       ├── SearchDialog.kt
│   │                   │       ├── SwipeRevealRow.kt
│   │                   │       ├── TimeFormats.kt
│   │                   │       └── UiText.kt
│   │                   ├── feature
│   │                   │   ├── about
│   │                   │   │   └── AboutScreen.kt
│   │                   │   ├── auth
│   │                   │   │   ├── AppAuthViewModel.kt
│   │                   │   │   ├── AuthScreen.kt
│   │                   │   │   └── AuthViewModel.kt
│   │                   │   ├── bookscan
│   │                   │   │   └── BookScanScreen.kt
│   │                   │   ├── detail
│   │                   │   │   ├── ListingDetailScreen.kt
│   │                   │   │   └── ListingDetailViewModel.kt
│   │                   │   ├── feed
│   │                   │   │   └── ListingFeedScreen.kt
│   │                   │   ├── listing
│   │                   │   │   └── ListingListViewModel.kt
│   │                   │   ├── notifications
│   │                   │   │   ├── ExpiryWarningPayload.kt
│   │                   │   │   ├── NotificationsScreen.kt
│   │                   │   │   └── NotificationsViewModel.kt
│   │                   │   ├── profile
│   │                   │   │   ├── ContactsScreen.kt
│   │                   │   │   ├── ContactsViewModel.kt
│   │                   │   │   ├── MyListingsScreen.kt
│   │                   │   │   ├── ProfileScreen.kt
│   │                   │   │   └── ProfileViewModel.kt
│   │                   │   ├── publish
│   │                   │   │   ├── PublishScreen.kt
│   │                   │   │   └── PublishViewModel.kt
│   │                   │   ├── settings
│   │                   │   │   ├── AccountSettingsScreen.kt
│   │                   │   │   ├── AccountSettingsViewModel.kt
│   │                   │   │   ├── ChangePasswordScreen.kt
│   │                   │   │   ├── LanguageViewModel.kt
│   │                   │   │   ├── LlmSettingsScreen.kt
│   │                   │   │   ├── LlmSettingsViewModel.kt
│   │                   │   │   ├── SettingsScreen.kt
│   │                   │   │   └── ThemeViewModel.kt
│   │                   │   └── trade
│   │                   │       └── TradeScreen.kt
│   │                   └── llm
│   │                       ├── LlmClient.kt
│   │                       ├── LlmConfig.kt
│   │                       ├── LlmRecognition.kt
│   │                       ├── OpenAiCompatibleLlmClient.kt
│   │                       └── Prompts.kt
│   └── res
│       ├── values
│       │   ├── strings.xml
│       │   └── themes.xml
│       ├── values-zh
│       │   └── strings.xml
│       └── xml
│           └── file_paths.xml
└── test
    └── kotlin
        └── cn
            └── edu
                └── bit
                    └── bitmart
                        ├── AuthRouteArgumentsTest.kt
                        ├── RoutesNavTest.kt
                        ├── core
                        │   ├── data
                        │   │   ├── AuthRepositoryImplTest.kt
                        │   │   ├── BitMartApiTimeoutTest.kt
                        │   │   ├── DataStoreLanguagePrefsStoreTest.kt
                        │   │   ├── FakeContactPrefsStore.kt
                        │   │   ├── FakeLanguagePrefsStore.kt
                        │   │   ├── FakeLlmConfigStore.kt
                        │   │   ├── FakeThemePrefsStore.kt
                        │   │   ├── FakeTokenStore.kt
                        │   │   ├── InMemoryPreferencesDataStore.kt
                        │   │   ├── ListingRepositoryBatchTest.kt
                        │   │   ├── ListingRepositoryImplTest.kt
                        │   │   ├── LlmConfigStoreTest.kt
                        │   │   ├── ProfileRepositoryImplTest.kt
                        │   │   ├── TestApiSupport.kt
                        │   │   ├── ThemePrefsStoreTest.kt
                        │   │   └── remote
                        │   │       └── ApiResponseMapperTest.kt
                        │   ├── domain
                        │   │   └── model
                        │   │       ├── AppLanguageTest.kt
                        │   │       ├── ListingTypeTest.kt
                        │   │       └── ThemeModeTest.kt
                        │   └── ui
                        │       ├── ErrorCodeResTest.kt
                        │       ├── TimeFormatsTest.kt
                        │       └── UiTextTest.kt
                        ├── feature
                        │   ├── auth
                        │   │   ├── AppAuthViewModelTest.kt
                        │   │   └── AuthViewModelTest.kt
                        │   ├── detail
                        │   │   └── ListingDetailViewModelTest.kt
                        │   ├── edit
                        │   ├── feed
                        │   ├── listing
                        │   │   └── ListingListViewModelTest.kt
                        │   ├── notifications
                        │   │   ├── ExpiryWarningPayloadTest.kt
                        │   │   └── NotificationsViewModelTest.kt
                        │   ├── profile
                        │   │   ├── ContactsViewModelTest.kt
                        │   │   └── ProfileViewModelTest.kt
                        │   ├── publish
                        │   │   └── PublishViewModelTest.kt
                        │   └── settings
                        │       ├── AccountSettingsViewModelTest.kt
                        │       ├── LanguageViewModelTest.kt
                        │       ├── LlmSettingsViewModelTest.kt
                        │       └── ThemeViewModelTest.kt
                        ├── i18n
                        │   └── StringsParityTest.kt
                        └── llm
                            ├── OpenAiCompatibleLlmClientLangTest.kt
                            ├── OpenAiCompatibleLlmClientTest.kt
                            └── PromptsTest.kt
```

- 整体架构：**采用 clean architecture**
  - domain
  - data（Repository 实现 + 远程/本地数据源）
  - feature（Compose UI + ViewModel）；
  - 用 Hilt 库进行依赖注入
- 网络层
  - Ktor Client 3.5（OkHttp 引擎），`BitMartApi` 封装后端调用
  - `ApiResponseMapper` 统一将 HTTP 响应映射为 `DomainResult<T>(sealed class)`；Token 由 `TokenStore` 持久化，请求时自动附带
- 本地存储：DataStore Preferences 存 token 和主题、语言、LLM 配置、常用联系方式等本地设置
- LLM 识图
  - 本地调用用户配置的 API（key 仅存本地 DataStore）
  - 用 OpenAI Compatible 协议的 `response_format` 约束 JSON 输出，配合提示词工程兜底（防止~~反代的~~ API 不支持 `response_format` ~~我用的就是反代的~~）
  - 识别结果可再次统一或分别编辑
- 条形码扫描：CameraX + ML Kit Barcode，扫到 ISBN 后发送给后端查询书籍信息
- 站内通知：进入“我的”页面时从服务器拉取
- UI
  - Jetpack Compose + Material 3
  - 支持亮/暗/跟随系统主题（ThemePrefsStore + Theme.kt）
  - 多语言切换（AppLocale + LanguagePrefsStore，ProvideAppLocale CompositionLocal）
  - `collectAsStateWithLifecycle` 订阅状态
  - Coil 显示图片
- 测试
  - JUnit 4 + kotlinx-coroutines-test + Turbine
  - Ktor MockEngine 模拟后端与 LLM API
  - InMemory Preferences DataStore 验证本地存储

## 技术亮点、技术难点和解决方案

### 识图功能

最开始的设想是采用 OCR + LLM 整理，但考虑到实际中直接 OCR 容易将多本书的文本混杂在一起，决定改为直接用 LLM 识图，代价是对 LLM API 的要求变高（需要支持多模态）。出于成本，没有在服务端提供 LLM 功能，而是由用户自行接入。

实测准确率可以接受。

### 大量使用 Agent 代码工具

项目超过 95% 的代码由采用 Claude Opus 4.7/4.8 模型的 Claude Code 完成，我仅在初期 demo 阶段、调试 LLM 功能、调试 UI 以及进行简单修改时手工修改过代码。

> 我还少量使用了**可能**基于 GPT-5.5 的 Codex。我在使用过程中发现 OpenAI 官方掺水 2024 年 6 月的老旧模型，可能是因为即将发布 GPT-5.6。

最基本的问题是成本问题，直接订阅 Claude 是我这个穷学生承担不起的。多亏我的挚友为我提供了极低成本的中转中转中转站资源，使我得以燃烧 token。当然，也是有代价的，中转中转中转站的 Claude 有一部分来自 Kiro 等编辑器反代，导致 token 不纯，有一小段时间不管发甚么都跟我说法语，当时出 Fable 5 的时候还用 DeepSeek 掺水（而且水分比例相当高）。中转中转中转站当然也有不稳定问题，我在日志上见识了 500、502、504 等各种 HTTP 错误码。

在开始写代码前，我与 CC 就架构和需求问题进行了长达多日的（其实是因为同时在忙别的事情）讨论，写成文档形式。我本以为像这样先把需求和架构明确为文档，后续直接指派 agent 根据文档实现，可以有效抑制 agent 的瞎写行为；然而事实上还是与文档出现了不少偏离。我不得不手工测试效果并人工干预。

为了约束其行为，我引入了 superpowers 技能，并要求其对于任何代码修改都调用按照“impl-test-review”的流程进行循环，直至没有问题。然而，实操中出现了包括如不遵守工作流、调用子代理失败等问题。这主要是因为我对 CC 本身不熟悉（例如不知道 `CLAUDE.md` 只有在开新对话时才注入），其次是因为 CC 本身的 bug。

CC 最重大的 bug 是这个 auto mode 老是出问题。其设想是好的：当 agent 需要编辑文件或调用命令时，由特定规则或另一个模型评估其命令的危险程度，存在风险的交由用户决断。实操中，不知道是有甚么 bug，一切编辑和命令行都给我决定，然后还回头告诉 agent 说他的命令被 auto mode block 了，污染上下文。我不得不开 `--dangerously-skip-permissions`，但开这个模式本身都经历了一些挫折——这个模式只对新对话生效，不在该模式下创建的对话仍不允许跳过权限审核。其他的诸如输入框 cursor 消失、列表不显示 session 这样的小 bug 我都不说了。该说到底是 vibe coding 产物吗？可隔壁 Codex 的体验感好得多。

## 改进方向

受限于课业压力和其他事务，我没办法将该项目做到尽善尽美。下面是我预想完成但在提交项目前并未做到的改进方向。

### 容器化

配置 `docker-compose.yml`，使配置 PostgresQL 数据库的过程自动化。

### MinIO

目前上传的图片直接作为文件保存，很不优雅。应当引入 MinIO 对象存储。

### NapCat + LLM

这样一个跳蚤市场应用在初期的最大问题就是“用户少 $\to$ 商品少 $\to$ 用户少 $\to\cdots$”的恶性循环。如果跳蚤市场管理员同意，通过 NapCat 框架接入跳蚤市场 QQ 群，通过 LLM 分析群内消息，自动化提交到数据库，将可以极大丰富商品数量，从而打破循环，实现从 QQ 群到 BitMart 的数据和用户迁移。

我已经在我的内网服务器上部署了 NapCat + AstrBot 的 QQ 聊天机器人，用以验证 NapCat 的功能和 LLM 对 QQ 消息的理解。经过一个多月的使用，我认为技术上这一方案这是可行的，可惜时间不允许。

### 通知

现在的“通知”功能局限在应用内。期望能够实现 FCM 或 SSE 通知。

### 管理员

管理员应当可以删除普通用户发布的买卖需求，封禁用户以及发布公告。

### 公网部署

租赁域名或内网穿透。考虑到当前项目完成度有限，我并没有这么做。

## 简要开发过程
