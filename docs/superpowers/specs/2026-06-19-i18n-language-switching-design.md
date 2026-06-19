# 中英文切换功能设计（跟随系统 / 中文 / English）

- 日期：2026-06-19
- 范围：bitmart-android（Jetpack Compose，主体工作）+ bitmart-server（最小改动）
- 状态：待用户评审（核心设计已批准 A1 + B1；本稿含 LLM 多语言 + 默认英文兜底两处增补）
- 关联：复用 [主题切换](2026-06-19-theme-switching-design.md) 的设置项基础设施模式

## 目标

让 App 全部面向用户的文案支持**中文 / English** 两种语言，并在设置页提供切换：**跟随系统 / 中文 / English** 三选项。选择即时全局生效（无需重启 Activity），跨重启持久化。后端做最小改动以消除"无法在客户端本地化"的少量服务端中文文案。

## 关键架构决策（已批准）

- **A1：字符串走 Android 资源**。`res/values/strings.xml`（**英文，默认/兜底**）+ `res/values-zh/strings.xml`（中文）。Compose 用 `stringResource(...)`。
- **B1：运行时切换走 `CompositionLocal` 语言覆盖**。根布局据偏好构造本地化 `Context`，在 NavHost 外层用 `CompositionLocalProvider` 覆盖 `LocalContext` / `LocalConfiguration`。**即时切换、不重启、不引入 AppCompat 依赖**，与主题"根布局应用设置"的方式一致。

## UI 决策

替换 `SettingsScreen` 中现有的"语言设置 / 敬请期待"占位行：改为可点击行，副标题显示当前语言标签（跟随系统 / 中文 / English），点击 → 弹出 `AlertDialog` 单选框（三项），选中即生效并关闭。与 `ThemeModeDialog` 完全同构。

## 架构与数据流

单一数据源为 DataStore，结构照搬主题：

```
LanguagePrefsStore (DataStore) ──flow──> LanguageViewModel ──> MainActivity: 构造本地化 Context
        ▲                                      │                  └─> CompositionLocalProvider(LocalContext/LocalConfiguration)
        │                                      └──> SettingsScreen: 行副标题 + 单选对话框                 └─> BitMartTheme { BitMartNavHost() }
        └──────── setLanguage() ◄── 用户在对话框选择
```

根布局与设置页各持有自己的 `LanguageViewModel` 实例，但都读写同一个单例 `LanguagePrefsStore`。用户选中 → 写 store → store 的 Flow 推送 → 根布局重组、用新 locale 构造 `Context` 并下发 → 全树 `stringResource` 即时按新语言解析。符合项目"每屏独立 VM、共享单例 Store"约定。

## 组件清单 —— Android

### 1. `AppLanguage`（`core/domain/model/AppLanguage.kt`，纯 Kotlin）

```kotlin
enum class AppLanguage {
    SYSTEM, ZH, EN;

    /** 解析为实际使用的 BCP-47 语言标签；SYSTEM 跟随设备：系统为中文→zh，否则（含英文及其它任何语言）→en。 */
    fun resolveLanguageTag(systemTag: String): String = when (this) {
        ZH -> "zh"
        EN -> "en"
        SYSTEM -> if (systemTag.startsWith("zh")) "zh" else "en"
    }
}
```

中文/英文标签映射放在 UI 层（与 `ThemeMode.label()`、`ContactChannel` 约定一致），域层不含 UI 文案。`SYSTEM` 的解析在 UI 层用设备当前 locale 喂入 `systemTag`。

### 2. `LanguagePrefsStore`（`core/data/local/LanguagePrefsStore.kt`）

照搬 `ThemePrefsStore` 结构：

```kotlin
interface LanguagePrefsStore {
    val languageFlow: Flow<AppLanguage>
    suspend fun setLanguage(lang: AppLanguage)
}

class DataStoreLanguagePrefsStore(dataStore: DataStore<Preferences>) : LanguagePrefsStore {
    // key = stringPreferencesKey("app_language"); 存 lang.name
    // 解析失败/无值 → SYSTEM
}
```

在 `AppModule` 用 Hilt `@Provides @Singleton` 提供（新增一段，紧邻 `provideThemePrefsStore`）。

### 3. `LanguageViewModel`（`feature/settings/LanguageViewModel.kt`，`@HiltViewModel`）

```kotlin
val language: StateFlow<AppLanguage>  // store.languageFlow.stateIn(scope, WhileSubscribed(5000), SYSTEM)
fun setLanguage(lang: AppLanguage)    // viewModelScope.launch { store.setLanguage(lang) }
```

### 4. 语言应用层（`core/designsystem/` 或 `core/ui/` 新增 `AppLocale.kt`）

提供一个用本地化 `Context` 包裹内容的 composable：

```kotlin
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    // 设备当前首选语言：取自资源配置的 locale 列表首项
    val systemTag = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?.toLanguageTag().orEmpty()
    val locale = Locale.forLanguageTag(language.resolveLanguageTag(systemTag))
    val localizedContext = remember(locale) {
        val config = Configuration(baseContext.resources.configuration).apply { setLocale(locale) }
        baseContext.createConfigurationContext(config)
    }
    val localizedConfig = remember(locale) { localizedContext.resources.configuration }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig,
    ) { content() }
}
```

### 5. `MainActivity`

```kotlin
setContent {
    val themeVm: ThemeViewModel = hiltViewModel()
    val langVm: LanguageViewModel = hiltViewModel()
    val mode by themeVm.themeMode.collectAsStateWithLifecycle()
    val lang by langVm.language.collectAsStateWithLifecycle()
    ProvideAppLocale(language = lang) {
        BitMartTheme(themeMode = mode) { BitMartNavHost() }
    }
}
```

### 6. `SettingsScreen`

注入 `LanguageViewModel`（`hiltViewModel()`），收集当前 language：
- "语言设置"行副标题 = 当前语言标签（跟随系统 / 中文 / English），不再"敬请期待"。
- 点击 → `AlertDialog`，三个 `RadioButton` 行，选中即 `langVm.setLanguage(it)` 并 dismiss（复用主题对话框的写法）。

## 字符串迁移（全量覆盖）

### 资源文件

- `res/values/strings.xml`：**英文**为默认（兜底：任何非中文系统语言都回落英文，符合"非中/英→英文"要求）。
- `res/values-zh/strings.xml`：中文（简体）。
- 运行时总是显式下发具体 locale（"zh" 或 "en"，见 `ProvideAppLocale`），故 `stringResource` 实际只解析中/英两套；默认英文文件兼作非中文系统的兜底。
- 两文件 key 集合必须完全一致（由测试守护，见下）。

### 命名约定

`<screen_or_area>_<element>`，跨屏复用的放 `common_*`。例：`common_cancel`、`common_delete`、`auth_student_id`、`publish_title_label`、`settings_language`。

### 三类棘手用例的处理

1. **字符串插值**（`"调整$noun"`、`"还没有$title"`、`"删除后无法恢复，确定要删除这条${noun}吗？"`）→ 带格式参数的资源：
   `<string name="adjust_qty_title">调整%1$s</string>` + `stringResource(R.string.adjust_qty_title, noun)`。
2. **按枚举取标签**（`商品/收购`、`期望价/售价`、`已收/已售`、`已收购数量/已售出数量`）→ `when` 返回 `stringResource`，或在 UI 层为枚举加 `@StringRes` 映射扩展（如 `ListingType.titleRes()`）。
3. **复数/计数**（如存在"N 条""N 小时"）→ 必要处用 `<plurals>`；英文与中文规则不同，按各自语言定义。

### 非 Compose 层文案（ViewModel / data 层）

ViewModel 与 data 层无 `Context`，引入轻量 `UiText`（`core/ui/UiText.kt`）：

```kotlin
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    @Composable fun asString(): String = when (this) {
        is Raw -> value
        is Res -> stringResource(id, *args.toTypedArray())
    }
}
```

- ViewModel 的报错/校验文案改为发 `UiText`（如 `UiText.Res(R.string.error_network)`），屏幕渲染时 `.asString()`。
- data 层两处兜底（`ApiResponseMapper` 的"无法解析服务器响应"、`BitMartApi` 的"网络异常"）改为返回错误码 / `UiText`，由 UI 解析。
- **API 错误本地化**：服务端错误信封的顶层 `code`（`VALIDATION_FAILED` / `UNAUTHORIZED` / `FORBIDDEN` / `NOT_FOUND` / `CONFLICT` / `RATE_LIMITED` / ...）是稳定英文枚举，客户端将其映射为本地化文案展示；服务端 `message` 仅作开发者兜底。表单字段级反馈由**客户端在提交前做基础校验**（非空/长度/价格范围等）给出本地化提示，避免改动服务端校验错误信封。

### 执行方式（用子代理并行）

按 feature 模块拆分并行迁移，每个子代理迁移自己负责的文件、产出其新增的 key 列表（遵循统一命名约定避免冲突），最后一次"汇总 pass"把所有 key 合并进两个 `strings.xml`，再跑全量测试。`core/ui/` 共享 composable 先迁移（复用杠杆最大）。

## 组件清单 —— 后端（最小改动）

### 1. 通知结构化（消除持久化的中文文案）

`ExpiryWarningJob` 目前把中文 `title`/`body` 写库。改为在既有 JSONB `payload` 中补充结构化字段：

```json
{ "listingId": 123, "expiresAt": "...", "templateKey": "EXPIRY_WARNING",
  "listingTitle": "线性代数", "hours": 24, "listingType": "SELL" }
```

- 客户端对 `category == 1`（到期提醒）且 `templateKey` 已知者，用 payload 变量渲染本地化文案；否则回落 `title`/`body`。
- 服务端**仍照常填中文 `title`/`body`** 作为旧客户端兼容兜底 → **无破坏性迁移**（`payload` 是 JSONB，加字段不需改表）。
- 管理员自由文本公告（category 0）不变（按管理员输入语言原样展示）。

涉及：`job/ExpiryWarningJob.kt`、`user/NotificationRepository.kt`（payload 组装）、`user/MeRoutes.kt` / `NotificationDto`（确认 payload 透传）。

### 2. 去掉 `"匿名"` 兜底

`domain/User.kt` 的 `displayName` 不再回落 `"匿名"`，返回原始 `nickname`（空/未设为 null/空串）；客户端在 `nickname` 为空时渲染本地化"匿名 / Anonymous"。涉及 `auth/AuthDto.kt`（`UserDto`）。

## 组件清单 —— LLM 提示词多语言

目标：当应用语言为英文时，识图走**英文**提示词（system prompt + user 文本 + JSON schema 描述），从而让模型产出英文的标题/描述/标签；中文时走中文。跟随 `AppLanguage` 偏好（`SYSTEM` 按设备 locale 解析为 zh/en）。**用户在「LLM 设置」中自定义的提示词按原文保留、不做翻译**——多语言只作用于"内置默认"提示词。

### 1. `Prompts.kt`：语言成对的默认提示词

- 拆出 `DEFAULT_BOOK_PROMPT_ZH` / `DEFAULT_BOOK_PROMPT_EN`、`DEFAULT_GENERAL_PROMPT_ZH` / `DEFAULT_GENERAL_PROMPT_EN`。
- 选择器 `fun defaultBookPrompt(tag: String): String`（`tag` 以 `"zh"` 开头 → ZH，否则 → EN）、`defaultGeneralPrompt(tag)`。
- 保留 `DEFAULT_BOOK_PROMPT` / `DEFAULT_GENERAL_PROMPT` 作为 ZH 别名，兼容现有引用。

### 2. `LlmConfig`：空串表示"按语言用默认"

`bookPrompt` / `generalPrompt` 默认值改为空串 `""`（新装存空，识别时按当前语言取默认）。已保存的旧配置按需清空即可。

### 3. 识别时按语言解析（`OpenAiCompatibleLlmClient`）

`recognize(...)` 增加 `languageTag: String` 入参。`buildRequest` 内：

```kotlin
val bookPrompt = config.bookPrompt.ifBlank { defaultBookPrompt(languageTag) }
// 空 → 跟随语言的内置默认；非空 → 用户自定义，原样使用
```

- `userText`（"请识别这张图片中所有书本的信息…"）→ 按 `languageTag` 选 ZH/EN。
- `BOOK_SCHEMA` / `GENERAL_SCHEMA` 的 `description` 文案 → 提供 ZH/EN 两份常量，按 `languageTag` 选取（描述语言与提示词同向，才能稳定拿到目标语言的字段值）。
- 因"改动前数据可清空"，无需对已 baked-in 的旧默认做兼容探测：清空 LLM 配置、或在表单清空提示词字段后，即恢复"跟随语言"。

### 4. 语言传入识别流程（`PublishViewModel`）

`PublishViewModel`（调用点 `PublishViewModel.kt:282`）注入 `LanguagePrefsStore`，识别前读取当前 `AppLanguage`，用 `Locale.getDefault().toLanguageTag()` 解析 `SYSTEM`，把 `languageTag` 传给 `recognize(...)`。

### 5. 「LLM 设置」表单

- 提示词输入框默认值为空；用 `placeholder` 显示**当前语言的默认提示词**（灰字预览），并加说明文案"留空则按当前语言使用默认提示词"。
- `save()` 不再 `ifBlank { DEFAULT_... }`——空串原样保存（保留"跟随语言"语义）；自定义文本原样保存。
- `clear()` 把提示词字段恢复为空。

### 6. LLM 报错文案

`OpenAiCompatibleLlmClient` 的 `DomainResult` 文案（"识别服务返回错误（…）"、"无法解析识别服务的响应结构"、"识别结果不是预期的 JSON 格式"、"网络异常"）并入"错误码 → 本地化"策略：保留稳定 `code`（如 `LLM_HTTP_xxx`），UI 按 code 显示本地化文案。

## 测试

### Android（JVM 单测，`./gradlew :app:testDebugUnitTest`）

| 测试 | 覆盖 |
| --- | --- |
| `AppLanguageTest` | `resolveLanguageTag`：ZH→zh、EN→en、SYSTEM 随 systemTag（zh→zh，其余含 en 与任何其它语言→en） |
| `DataStoreLanguagePrefsStoreTest` | 默认 SYSTEM；setLanguage 持久化；非法存储值兜底 SYSTEM（`InMemoryPreferencesDataStore`） |
| `FakeLanguagePrefsStore` | 测试替身（照搬 `FakeThemePrefsStore`） |
| `LanguageViewModelTest` | setLanguage 后 `language` flow 更新 |
| `UiTextTest` | `Res`/`Raw` 解析（可用 Robolectric 或纯逻辑断言 args 透传） |
| `StringsParityTest` | `values/strings.xml`（英文）与 `values-zh/strings.xml`（中文）的 key 集合完全相等（防止漏翻） |
| `PromptsTest` | `defaultBookPrompt`/`defaultGeneralPrompt`：zh→ZH，其余→EN |
| `OpenAiCompatibleLlmClient` 识别测试（更新/新增） | 空提示词 + en → 请求体用 EN 提示词/userText/schema；空提示词 + zh → 中文；自定义提示词 → 原样 |

### 后端（Kotest/JUnit，`./gradlew test`）

| 测试 | 覆盖 |
| --- | --- |
| `ExpiryWarningJob` 既有测试更新 | payload 含 `templateKey` 及变量；`title`/`body` 仍存在 |
| 通知 DTO 测试 | payload 结构化字段正确透传到 `GET /me/notifications` |
| `displayName` 相关断言 | 去掉 `"匿名"` 后的预期（返回 null/空） |

全绿方可进入评审。

## 边界与决策

- 未知/损坏的存储语言值 → 兜底 `SYSTEM`。
- 首次无值 → `SYSTEM`（跟随设备）：系统为中文→中文，其余（含英文及任何其它语言）→英文。
- 切换为即时重组，不重启 Activity；`createConfigurationContext` + 覆盖 `LocalContext`/`LocalConfiguration` 后 `stringResource` 立刻取新语言。
- 默认语言资源（`res/values/`）为英文，任何未匹配 locale 一律回落英文，符合"非中/英→英文"。
- 改动前已有的数据（旧通知行、已保存 LLM 配置等）按需直接清空（用户已确认），无需回填/迁移。

## 范围外（YAGNI）

- 用户在「LLM 设置」自定义的提示词不自动翻译（按原文使用）；多语言只作用于内置默认提示词。
- 管理员公告自动翻译。
- 第三种及以上语言、地区变体（zh-TW、en-GB 等）。
- 服务端 `Accept-Language` 协商与服务端消息资源包。
- 服务端校验错误信封结构化（改由客户端提交前校验覆盖表单反馈）。
