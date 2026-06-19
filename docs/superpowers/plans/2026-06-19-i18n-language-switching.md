# BitMart 中英文切换（i18n）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 BitMart Android App 全部面向用户文案支持中文 / English，设置页可在「跟随系统 / 中文 / English」间即时切换；配套最小后端改动与 LLM 提示词多语言。

**Architecture:** 字符串走 Android 资源（默认 `res/values/`=英文兜底，`res/values-zh/`=中文）；运行时用 `CompositionLocal` 覆盖 `LocalContext`/`LocalConfiguration` 即时换语言、不重启、不引入 AppCompat。语言偏好沿用主题切换的 `domain enum → DataStore store → ViewModel → UI` 模式（无 repository/usecase 层）。非 Compose 层（ViewModel/data）文案用 `UiText` 包装或错误码本地化。

**Tech Stack:** Kotlin 2.3.21 · Jetpack Compose (BOM 2026.05.01) · Hilt 2.59.2 · DataStore Preferences · Ktor Client 3.5.0 (MockEngine for tests) · Kotlinx Serialization · 后端 Ktor 3.5.0 + Exposed 1.3.0 + Kotest/JUnit5。

## Global Constraints

- Android 源根：`bitmart-android/app/src/main/kotlin`，测试根：`app/src/test/kotlin`。包名 `cn.edu.bit.bitmart`。
- Clean Architecture：`domain`（纯 Kotlin，零 Android/Ktor import）← `data` ← `feature`。语言偏好**不**新增 repository/usecase 层（照搬主题）。
- 默认语言解析规则（全局唯一真相）：系统为中文 → `zh`；否则（含英文及任何其它语言）→ `en`。
- 资源文件：`res/values/strings.xml` = **英文（默认/兜底）**；`res/values-zh/strings.xml` = **中文（简体）**。两文件 key 集合必须完全相等。
- key 命名：`<screen_or_area>_<element>`，跨屏复用用 `common_*`（如 `common_cancel`、`common_delete`、`common_save`）。
- Android JVM 单测命令：`cd bitmart-android && ./gradlew :app:testDebugUnitTest`。构建：`./gradlew assembleDebug`。
- 后端测试命令：`cd bitmart-server && ./gradlew test`。
- LLM 提示词多语言只作用于**内置默认**提示词；用户自定义提示词原样保留、不翻译。
- `llm/Prompts.kt` 之外的 `llm/**` 注释类中文可不动；只动会进入「发给模型/影响输出语言」的文案。
- 改动前已有数据（旧通知行、已存 LLM 配置）可按需清空，**无需**写迁移。
- 不自动提交/推送：每个 Task 的 commit 步骤由执行者按项目约定决定是否实际提交；本计划给出 commit 步骤是为标记完成边界。

> **关于 commit 步骤**：本项目 `.claude/CLAUDE.md` 要求不自动提交。各 Task 末尾的 commit 命令仅作为「任务完成」的边界标记；执行 Agent 若处于「禁止自动提交」模式，应改为 `git add -A && git status`（暂存并核对），把实际提交留给用户。

---

## 文件结构总览

**新增（Android）**
- `core/domain/model/AppLanguage.kt` — 语言枚举 + `resolveLanguageTag`
- `core/data/local/LanguagePrefsStore.kt` — 偏好 store（接口 + DataStore 实现 + `current()`）
- `feature/settings/LanguageViewModel.kt` — 暴露 `language: StateFlow`，`setLanguage`
- `core/ui/AppLocale.kt` — `ProvideAppLocale` composable（运行时换 locale）
- `core/ui/UiText.kt` — 非 Compose 层文案包装 + 错误码→`@StringRes` 映射
- `app/src/main/res/values/strings.xml` — 英文（默认）
- `app/src/main/res/values-zh/strings.xml` — 中文
- 测试：`AppLanguageTest`、`DataStoreLanguagePrefsStoreTest`、`FakeLanguagePrefsStore`、`LanguageViewModelTest`、`UiTextTest`、`StringsParityTest`、`PromptsTest`、`OpenAiCompatibleLlmClientLangTest`

**修改（Android）**
- `core/di/AppModule.kt` — 新增 `provideLanguagePrefsStore`
- `MainActivity.kt` — 注入 `LanguageViewModel`，`ProvideAppLocale` 包裹
- `feature/settings/SettingsScreen.kt` — 语言行 + 单选对话框；本屏字符串外提
- 约 24 个 `feature/**` 与 `core/ui/**` 文件 — 字符串外提为 `stringResource`
- 多个 ViewModel + `core/data/remote/{ApiResponseMapper,BitMartApi}.kt` — 文案改 `UiText`/错误码
- `llm/Prompts.kt`、`llm/OpenAiCompatibleLlmClient.kt`、`llm/LlmConfig.kt`、`feature/settings/LlmSettings*.kt`、`feature/publish/PublishViewModel.kt` — LLM 多语言

**修改（后端）**
- `job/ExpiryWarningJob.kt`、`user/NotificationRepository.kt`、`user/MeRoutes.kt`(/`NotificationDto`) — 结构化通知 payload
- `domain/User.kt`、`auth/AuthDto.kt` — 去掉 `"匿名"` 兜底

---

# 阶段 A — 基础设施（TDD，完整代码）

## Task 1: `AppLanguage` 域枚举

**Files:**
- Create: `app/src/main/kotlin/cn/edu/bit/bitmart/core/domain/model/AppLanguage.kt`
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/core/domain/model/AppLanguageTest.kt`

**Interfaces:**
- Produces: `enum class AppLanguage { SYSTEM, ZH, EN }`，`fun AppLanguage.resolveLanguageTag(systemTag: String): String`（返回 `"zh"` 或 `"en"`）。

- [ ] **Step 1: 写失败测试**

```kotlin
package cn.edu.bit.bitmart.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun explicit_zh_and_en() {
        assertEquals("zh", AppLanguage.ZH.resolveLanguageTag("en-US"))
        assertEquals("en", AppLanguage.EN.resolveLanguageTag("zh-CN"))
    }

    @Test fun system_follows_device_chinese_to_zh() {
        assertEquals("zh", AppLanguage.SYSTEM.resolveLanguageTag("zh-CN"))
        assertEquals("zh", AppLanguage.SYSTEM.resolveLanguageTag("zh-Hans-CN"))
    }

    @Test fun system_falls_back_to_english_for_non_chinese() {
        assertEquals("en", AppLanguage.SYSTEM.resolveLanguageTag("en-US"))
        assertEquals("en", AppLanguage.SYSTEM.resolveLanguageTag("fr-FR"))
        assertEquals("en", AppLanguage.SYSTEM.resolveLanguageTag(""))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppLanguageTest"`
Expected: FAIL（`AppLanguage` 未定义 / 编译错误）

- [ ] **Step 3: 写最小实现**

```kotlin
package cn.edu.bit.bitmart.core.domain.model

/** 应用语言偏好。SYSTEM 跟随设备；显式 ZH/EN 覆盖。纯 Kotlin，无 Android 依赖。 */
enum class AppLanguage {
    SYSTEM, ZH, EN;

    /**
     * 解析为实际使用的语言标签（"zh" / "en"）。
     * SYSTEM：设备语言以 "zh" 开头 → "zh"；否则（含 en 及任何其它语言）→ "en"。
     */
    fun resolveLanguageTag(systemTag: String): String = when (this) {
        ZH -> "zh"
        EN -> "en"
        SYSTEM -> if (systemTag.startsWith("zh")) "zh" else "en"
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppLanguageTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/cn/edu/bit/bitmart/core/domain/model/AppLanguage.kt app/src/test/kotlin/cn/edu/bit/bitmart/core/domain/model/AppLanguageTest.kt
git commit -m "feat(i18n): add AppLanguage domain enum with system-locale fallback"
```

---

## Task 2: `LanguagePrefsStore` + DI + Fake

**Files:**
- Create: `app/src/main/kotlin/cn/edu/bit/bitmart/core/data/local/LanguagePrefsStore.kt`
- Modify: `app/src/main/kotlin/cn/edu/bit/bitmart/core/di/AppModule.kt`（紧邻 `provideThemePrefsStore` 新增 provider；复用已注入的 `DataStore<Preferences>`）
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/core/data/DataStoreLanguagePrefsStoreTest.kt`
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/core/data/FakeLanguagePrefsStore.kt`（测试替身，照搬 `FakeThemePrefsStore`）

**Interfaces:**
- Consumes: `AppLanguage`（Task 1）；现有 `DataStore<Preferences>`（`AppModule` 已 `@Provides @Singleton`）。
- Produces: `interface LanguagePrefsStore { val languageFlow: Flow<AppLanguage>; suspend fun setLanguage(lang: AppLanguage) }`；`class DataStoreLanguagePrefsStore(dataStore)`；`suspend fun LanguagePrefsStore.current(): AppLanguage`；DI 绑定 `provideLanguagePrefsStore`。

- [ ] **Step 1: 写失败测试**（参照现有 `ThemePrefsStoreTest` 用的 `InMemoryPreferencesDataStore` 工具；若该工具是测试内私有 helper，则在本测试文件内同样构造一个内存 DataStore）

```kotlin
package cn.edu.bit.bitmart.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import cn.edu.bit.bitmart.core.data.local.DataStoreLanguagePrefsStore
import cn.edu.bit.bitmart.core.data.local.current
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreLanguagePrefsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(scopeFile: File): DataStoreLanguagePrefsStore {
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create { scopeFile }
        return DataStoreLanguagePrefsStore(ds)
    }

    @Test fun default_is_system() = runTest {
        val s = store(tmp.newFile("a.preferences_pb"))
        assertEquals(AppLanguage.SYSTEM, s.languageFlow.first())
    }

    @Test fun set_language_persists() = runTest {
        val s = store(tmp.newFile("b.preferences_pb"))
        s.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, s.current())
    }
}
```

> 注：若仓库已有 `InMemoryPreferencesDataStore`/类似 helper（见 `ThemePrefsStoreTest`），改用它以保持一致，删掉上面的 `TemporaryFolder` 构造。先打开 `ThemePrefsStoreTest.kt` 对齐写法。

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*DataStoreLanguagePrefsStoreTest"`
Expected: FAIL（`DataStoreLanguagePrefsStore` 未定义）

- [ ] **Step 3: 写实现**

```kotlin
package cn.edu.bit.bitmart.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 语言偏好本地存储抽象（结构照搬 ThemePrefsStore）。 */
interface LanguagePrefsStore {
    val languageFlow: Flow<AppLanguage>
    suspend fun setLanguage(lang: AppLanguage)
}

class DataStoreLanguagePrefsStore(
    private val dataStore: DataStore<Preferences>,
) : LanguagePrefsStore {

    private val key = stringPreferencesKey("app_language")

    override val languageFlow: Flow<AppLanguage> =
        dataStore.data.map { prefs -> decode(prefs[key]) }

    override suspend fun setLanguage(lang: AppLanguage) {
        dataStore.edit { it[key] = lang.name }
    }

    private fun decode(raw: String?): AppLanguage =
        if (raw.isNullOrBlank()) AppLanguage.SYSTEM
        else runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
}

/** 便于单测读取当前快照。 */
suspend fun LanguagePrefsStore.current(): AppLanguage = languageFlow.first()
```

DI（`AppModule.kt`，紧邻主题 provider 新增；签名按文件中既有风格）：

```kotlin
@Provides @Singleton
fun provideLanguagePrefsStore(dataStore: DataStore<Preferences>): LanguagePrefsStore =
    DataStoreLanguagePrefsStore(dataStore)
```

Fake（`FakeLanguagePrefsStore.kt`，照搬 `FakeThemePrefsStore`）：

```kotlin
package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.LanguagePrefsStore
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeLanguagePrefsStore(initial: AppLanguage = AppLanguage.SYSTEM) : LanguagePrefsStore {
    private val state = MutableStateFlow(initial)
    override val languageFlow: StateFlow<AppLanguage> = state
    override suspend fun setLanguage(lang: AppLanguage) { state.value = lang }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*DataStoreLanguagePrefsStoreTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/cn/edu/bit/bitmart/core/data/local/LanguagePrefsStore.kt app/src/main/kotlin/cn/edu/bit/bitmart/core/di/AppModule.kt app/src/test/kotlin/cn/edu/bit/bitmart/core/data/DataStoreLanguagePrefsStoreTest.kt app/src/test/kotlin/cn/edu/bit/bitmart/core/data/FakeLanguagePrefsStore.kt
git commit -m "feat(i18n): add LanguagePrefsStore (DataStore) with DI + fake"
```

---

## Task 3: `LanguageViewModel`

**Files:**
- Create: `app/src/main/kotlin/cn/edu/bit/bitmart/feature/settings/LanguageViewModel.kt`
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/feature/settings/LanguageViewModelTest.kt`

**Interfaces:**
- Consumes: `LanguagePrefsStore`（Task 2）、`FakeLanguagePrefsStore`（测试）。
- Produces: `class LanguageViewModel @Inject constructor(store)`，`val language: StateFlow<AppLanguage>`，`fun setLanguage(lang: AppLanguage)`。

- [ ] **Step 1: 写失败测试**（照搬 `ThemeViewModelTest` 的调度器/`runTest` 写法；若项目有 `MainDispatcherRule` 复用之）

```kotlin
package cn.edu.bit.bitmart.feature.settings

import cn.edu.bit.bitmart.core.data.FakeLanguagePrefsStore
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageViewModelTest {
    @Test fun set_language_updates_flow() = runTest {
        val vm = LanguageViewModel(FakeLanguagePrefsStore(AppLanguage.SYSTEM))
        vm.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, vm.language.first { it == AppLanguage.EN })
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*LanguageViewModelTest"`
Expected: FAIL（`LanguageViewModel` 未定义）

- [ ] **Step 3: 写实现**

```kotlin
package cn.edu.bit.bitmart.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.data.local.LanguagePrefsStore
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val store: LanguagePrefsStore,
) : ViewModel() {

    val language: StateFlow<AppLanguage> = store.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { store.setLanguage(lang) }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*LanguageViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/cn/edu/bit/bitmart/feature/settings/LanguageViewModel.kt app/src/test/kotlin/cn/edu/bit/bitmart/feature/settings/LanguageViewModelTest.kt
git commit -m "feat(i18n): add LanguageViewModel"
```

---

## Task 4: `UiText` + 错误码→`@StringRes` 映射

**Files:**
- Create: `app/src/main/kotlin/cn/edu/bit/bitmart/core/ui/UiText.kt`
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/core/ui/ErrorCodeResTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface UiText { data class Res(@StringRes id: Int, args: List<Any>); data class Raw(value: String); @Composable fun asString(): String }`
  - `fun errorMessageRes(code: String): Int` — 把后端/客户端稳定错误码映射到 `@StringRes`（纯函数，便于单测）。

> `asString()` 依赖 Compose（`stringResource`），不在 JVM 单测覆盖；把可测的纯逻辑放进 `errorMessageRes`。资源 id 用 Task 6 起在 `strings.xml` 中定义的 `R.string.error_*`；为让本任务可独立通过，先在 `strings.xml` 中加入本任务用到的 `error_*` 键（见下 Step 3 附带的 XML 片段），并和 Task 6 的 parity 测试保持同步。

- [ ] **Step 1: 写失败测试**

```kotlin
package cn.edu.bit.bitmart.core.ui

import cn.edu.bit.bitmart.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorCodeResTest {
    @Test fun known_codes_map_to_specific_strings() {
        assertEquals(R.string.error_unauthorized, errorMessageRes("UNAUTHORIZED"))
        assertEquals(R.string.error_validation_failed, errorMessageRes("VALIDATION_FAILED"))
        assertEquals(R.string.error_rate_limited, errorMessageRes("RATE_LIMITED"))
    }

    @Test fun unknown_code_maps_to_generic() {
        assertEquals(R.string.error_generic, errorMessageRes("SOMETHING_ELSE"))
        assertEquals(R.string.error_generic, errorMessageRes(""))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*ErrorCodeResTest"`
Expected: FAIL（`errorMessageRes`/`R.string.error_*` 未定义）

- [ ] **Step 3: 写实现 + 资源键**

`core/ui/UiText.kt`：

```kotlin
package cn.edu.bit.bitmart.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cn.edu.bit.bitmart.R

/** 非 Compose 层（ViewModel/data）产出的文案包装：要么资源 id+参数，要么已成型字符串。 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Raw -> value
        is Res -> stringResource(id, *args.toTypedArray())
    }
}

/** 稳定错误码 → 本地化字符串资源。未知码回落通用错误。 */
@StringRes
fun errorMessageRes(code: String): Int = when (code) {
    "UNAUTHORIZED" -> R.string.error_unauthorized
    "FORBIDDEN" -> R.string.error_forbidden
    "NOT_FOUND" -> R.string.error_not_found
    "CONFLICT" -> R.string.error_conflict
    "RATE_LIMITED" -> R.string.error_rate_limited
    "VALIDATION_FAILED" -> R.string.error_validation_failed
    "EXTERNAL_SERVICE_ERROR" -> R.string.error_external_service
    "NETWORK_ERROR" -> R.string.error_network
    "INVALID_RESPONSE" -> R.string.error_invalid_response
    else -> R.string.error_generic
}
```

在两个 strings.xml 中加入对应键（值占位，中文/英文各一份；Task 6 会扩充全量键，这里先放本任务所需）：

`res/values/strings.xml`（英文）：
```xml
<string name="error_unauthorized">Please sign in again</string>
<string name="error_forbidden">You don\'t have permission to do that</string>
<string name="error_not_found">Not found</string>
<string name="error_conflict">Conflict, please refresh and retry</string>
<string name="error_rate_limited">Too many requests, please try again later</string>
<string name="error_validation_failed">Please check your input</string>
<string name="error_external_service">External service error</string>
<string name="error_network">Network error</string>
<string name="error_invalid_response">Could not parse the server response</string>
<string name="error_generic">Something went wrong</string>
```

`res/values-zh/strings.xml`（中文）：
```xml
<string name="error_unauthorized">请重新登录</string>
<string name="error_forbidden">没有操作权限</string>
<string name="error_not_found">未找到</string>
<string name="error_conflict">数据冲突，请刷新后重试</string>
<string name="error_rate_limited">请求过于频繁，请稍后再试</string>
<string name="error_validation_failed">请检查输入</string>
<string name="error_external_service">外部服务错误</string>
<string name="error_network">网络异常</string>
<string name="error_invalid_response">无法解析服务器响应</string>
<string name="error_generic">出错了，请稍后再试</string>
```

> 若 Task 6 尚未创建 strings.xml，本任务先创建这两个文件（含上述键）；Task 6 在其上扩充。哪个先执行都行——后执行者「合并键、不覆盖」。

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*ErrorCodeResTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/cn/edu/bit/bitmart/core/ui/UiText.kt app/src/test/kotlin/cn/edu/bit/bitmart/core/ui/ErrorCodeResTest.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(i18n): add UiText wrapper + error-code to string-res mapping"
```

---

## Task 5: 字符串资源骨架 + `StringsParityTest`

**Files:**
- Create/Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/i18n/StringsParityTest.kt`

**Interfaces:**
- Produces: 两个 strings.xml 文件（至少含 `app_name` 与 Task 4 的 `error_*`）；`StringsParityTest` 断言两文件 `name` 集合相等。后续所有迁移任务都靠此测试守护「中英 key 对齐」。

- [ ] **Step 1: 写失败测试**

```kotlin
package cn.edu.bit.bitmart.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsParityTest {
    private fun names(path: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            .toSet()
    }

    @Test fun default_and_zh_have_identical_keys() {
        val en = names("src/main/res/values/strings.xml")
        val zh = names("src/main/res/values-zh/strings.xml")
        assertEquals("缺失/多余的 key（仅默认 values 有）", emptySet<String>(), en - zh)
        assertEquals("缺失/多余的 key（仅 values-zh 有）", emptySet<String>(), zh - en)
    }
}
```

> 工作目录说明：`:app` 模块测试的工作目录是 `bitmart-android/app`，故相对路径 `src/main/res/...` 成立。若运行环境不同导致找不到文件，改用绝对路径或 `System.getProperty("user.dir")` 拼接并在失败信息中打印解析到的路径。

- [ ] **Step 2: 运行确认失败 → 修正到通过**

先确保两个 XML 至少包含根节点与相同 key。骨架（若 Task 4 已建则只校验/补 `app_name`）：

`res/values/strings.xml`：
```xml
<resources>
    <string name="app_name">BitMart</string>
    <!-- Task 4 的 error_* 已在此；后续任务在此追加 -->
</resources>
```
`res/values-zh/strings.xml`：
```xml
<resources>
    <string name="app_name">BitMart</string>
</resources>
```

Run: `./gradlew :app:testDebugUnitTest --tests "*StringsParityTest"`
Expected: 先 FAIL（若 key 不齐），补齐后 PASS

- [ ] **Step 3: 运行全量基础设施测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppLanguageTest" --tests "*LanguagePrefsStore*" --tests "*LanguageViewModelTest" --tests "*ErrorCodeResTest" --tests "*StringsParityTest"`
Expected: 全 PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml app/src/test/kotlin/cn/edu/bit/bitmart/i18n/StringsParityTest.kt
git commit -m "feat(i18n): string-resource scaffolding + parity test guard"
```

---

## Task 6: `ProvideAppLocale` + `MainActivity` 接线（运行时换语言）

**Files:**
- Create: `app/src/main/kotlin/cn/edu/bit/bitmart/core/ui/AppLocale.kt`
- Modify: `app/src/main/kotlin/cn/edu/bit/bitmart/MainActivity.kt`

**Interfaces:**
- Consumes: `AppLanguage`（Task 1）、`LanguageViewModel`（Task 3）。
- Produces: `@Composable fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit)`。

- [ ] **Step 1: 写实现 — `AppLocale.kt`**

```kotlin
package cn.edu.bit.bitmart.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import java.util.Locale

/**
 * 按 [language] 用本地化 Context 覆盖 LocalContext/LocalConfiguration，
 * 使整棵子树的 stringResource 即时按目标语言解析（不重启 Activity）。
 */
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    // 设备当前首选语言（在覆盖前读取，保证 SYSTEM 解析正确）
    val systemTag = ConfigurationCompat.getLocales(baseConfig)[0]?.toLanguageTag().orEmpty()
    val locale = Locale.forLanguageTag(language.resolveLanguageTag(systemTag))

    val localized = remember(baseContext, locale) {
        val cfg = Configuration(baseContext.resources.configuration).apply { setLocale(locale) }
        baseContext.createConfigurationContext(cfg)
    }
    val localizedConfig = remember(localized) { localized.resources.configuration }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localizedConfig,
    ) { content() }
}
```

- [ ] **Step 2: 接线 `MainActivity`**（保留既有主题接线，外层加语言；只展示 setContent 内部）

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

补 import：`cn.edu.bit.bitmart.feature.settings.LanguageViewModel`、`cn.edu.bit.bitmart.core.ui.ProvideAppLocale`。

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 手动冒烟（记录到 PR 描述，非自动化）**

App 启动 → 设置（Task 7 后）切换语言 → 已迁移文案即时变化、无需重启。本步在 Task 7 完成后回归。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/cn/edu/bit/bitmart/core/ui/AppLocale.kt app/src/main/kotlin/cn/edu/bit/bitmart/MainActivity.kt
git commit -m "feat(i18n): runtime locale override via CompositionLocal"
```

---

## Task 7: 设置页语言切换 UI + 本屏字符串外提

**Files:**
- Modify: `app/src/main/kotlin/cn/edu/bit/bitmart/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`、`values-zh/strings.xml`

**Interfaces:**
- Consumes: `LanguageViewModel`、`AppLanguage`、`ProvideAppLocale`（已在根生效）。
- Produces: 可用的语言单选对话框；`AppLanguage` 的 UI 标签扩展 `@Composable fun AppLanguage.label(): String`。

- [ ] **Step 1: 加字符串键**（中/英两份）

英文 `values/strings.xml`：
```xml
<string name="settings_title">Settings</string>
<string name="settings_account">Account</string>
<string name="settings_llm">LLM settings</string>
<string name="settings_language">Language</string>
<string name="settings_theme">Theme</string>
<string name="language_system">Follow system</string>
<string name="language_zh">中文</string>
<string name="language_en">English</string>
<string name="common_back">Back</string>
```
中文 `values-zh/strings.xml`：
```xml
<string name="settings_title">设置</string>
<string name="settings_account">账号设置</string>
<string name="settings_llm">LLM 设置</string>
<string name="settings_language">语言设置</string>
<string name="settings_theme">主题设置</string>
<string name="language_system">跟随系统</string>
<string name="language_zh">中文</string>
<string name="language_en">English</string>
<string name="common_back">返回</string>
```

> `language_zh`/`language_en` 两语言下都展示其原生名（中文恒为「中文」，English 恒为「English」），故中英两份值相同——这是有意的。

- [ ] **Step 2: 改 `SettingsScreen`**

- 顶部 `TopAppBar` 标题 → `stringResource(R.string.settings_title)`；其它行标题用对应 `settings_*`。
- 替换「语言设置 / 敬请期待」占位行：副标题改为当前语言 `language.label()`，点击打开 `LanguageDialog`。
- 新增（本文件内私有）：

```kotlin
@Composable
fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ZH -> stringResource(R.string.language_zh)
    AppLanguage.EN -> stringResource(R.string.language_en)
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { lang ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected = lang == current,
                                role = Role.RadioButton,
                                onClick = { onSelect(lang); onDismiss() },
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == current, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(lang.label())
                    }
                }
            }
        },
    )
}
```

- 在 `SettingsScreen` 顶部注入 `languageViewModel: LanguageViewModel = hiltViewModel()`，`val lang by languageViewModel.language.collectAsStateWithLifecycle()`，用 `var showLangDialog by remember { mutableStateOf(false) }` 控制；选中调用 `languageViewModel.setLanguage(it)`。
- 删除该行原先的 `onComingSoon("语言设置")` 用法（若 `onComingSoon` 再无其它调用方，保留参数不动以免触达导航改动，超出本任务范围）。

补 import（按需）：`selectableGroup`/`selectable`/`Role`/`RadioButton`/`AlertDialog`/`stringResource`/`AppLanguage`/`LanguageViewModel` 等。

- [ ] **Step 3: 编译 + 解析校验**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest --tests "*StringsParityTest"`
Expected: BUILD SUCCESSFUL；parity PASS

- [ ] **Step 4: 手动冒烟**

设置页切换「中文 / English / 跟随系统」→ 标题/行文案即时切换；杀进程重启后保持上次选择。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(i18n): language switcher dialog in settings + localize settings strings"
```

---

# 阶段 B — 全量字符串迁移（按模块，子代理并行）

> **共享「字符串外提」流程**（每个迁移任务都执行，guard = 构建成功 + parity 测试）：
> 1. 打开目标文件，找出所有面向用户的中文字符串字面量（`Text("…")`、`label/placeholder/title/text = { Text("…") }`、`contentDescription = "…"`、形参默认值里的中文、ViewModel 里拼装给 UI 的中文）。**不动**：KDoc/`//` 注释、`Log` tag/日志、LLM 提示词（阶段 C 专门处理）。
> 2. 为每条字面量起 key（命名约定见 Global Constraints），中文值放 `values-zh/strings.xml`，英文译文放 `values/strings.xml`。复用已有 `common_*`/`error_*`，不要重复造键。
> 3. 用 `stringResource(R.string.key)` 替换；带参数的用 `stringResource(R.string.key, arg)`（资源里写 `%1$s`/`%1$d`）；`if/when` 取枚举标签的，改为 `when` 返回 `stringResource` 或加 `@StringRes` 扩展。
> 4. ViewModel/data 层（无 `Context`）不能直接 `stringResource`：把对外文案换成 `UiText.Res(R.string.key, args)` 或错误码，屏幕侧 `.asString()` 渲染。
> 5. Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest --tests "*StringsParityTest"` → 均通过。
> 6. `git add -A && git commit -m "i18n(<module>): externalize strings"`。
>
> **并行执行**：阶段 B 各任务文件集互不重叠，可并行子代理；唯一共享写入是两个 strings.xml——各任务**只追加自己的 key**（按字母序插入，减少冲突），最后由 Task 18 做一次合并去重 + 全量 parity。若并行产生 XML 冲突，以「并集、不丢键」解决。

## Task 8: `core/ui/**` 共享 Composable（最高复用杠杆，先做）
**Files（Modify）:** `core/ui/ListingCard.kt`、`ListingFilterDialog.kt`、`SearchDialog.kt`、`AdjustQuantityDialog.kt`、`OwnedListingRow.kt`、`ListingTimeInfo.kt`、`ImageViewer.kt`（以及 `core/ui/` 下其余含中文字面量的文件）。
**要点：** 枚举标签集中在此（`商品/收购`、`期望价/售价`、`已收/已售`、`已收购数量/已售出数量`）→ 建 `common_*` 与 `listing_*` 键，并为 `ListingType` 等加 `@StringRes` 标签扩展放在 `core/ui`（供 trade/detail 复用）。按共享流程 1–6。

## Task 9: `feature/auth/**`
**Files（Modify）:** `feature/auth/AuthScreen.kt`、`feature/auth/AuthViewModel.kt`。
**要点：** 表单标签（学号/密码/昵称）→ `auth_*`；ViewModel 校验/报错（`"请填写学号和密码"`、`"网络异常：…"`）→ `UiText.Res`（错误前缀用 `error_network` 等，拼接参数用 `%1$s`）。按共享流程。

## Task 10: `feature/trade/**`
**Files（Modify）:** `feature/trade/TradeScreen.kt`（tab `商品/收购` 等）+ 关联 VM 若有中文。复用 Task 8 的枚举标签扩展。按共享流程。

## Task 11: `feature/publish/**`（最大）
**Files（Modify）:** `feature/publish/PublishScreen.kt`、`feature/publish/PublishViewModel.kt`（注意：本任务只迁移 UI 文案；LLM 语言入参在阶段 C Task 21 处理，避免与本任务冲突——两者都改 `PublishViewModel`，**Task 21 依赖本任务先完成**）。按共享流程，插值用格式参数。

## Task 12: `feature/detail/**`
**Files（Modify）:** `feature/detail/ListingDetailScreen.kt`、`feature/detail/ListingDetailViewModel.kt`。
**要点：** `"删除后无法恢复，确定要删除这条${noun}吗？"` → 带 `%1$s` 的资源 + 删除/取消用 `common_*`；VM 报错走 `UiText`/错误码。按共享流程。

## Task 13: `feature/profile/**`
**Files（Modify）:** `feature/profile/ProfileScreen.kt`、`MyListingsScreen.kt`（`"还没有$title"`）、`ContactsScreen.kt`。客户端的「匿名/Anonymous」兜底（对应后端 Task 23）在此实现：`nickname` 为空显示 `stringResource(R.string.common_anonymous)`。新增 `common_anonymous`（en: `Anonymous` / zh: `匿名`）。按共享流程。

## Task 14: `feature/settings/**` 其余（账号 + LLM 设置标签）
**Files（Modify）:** `feature/settings/AccountSettingsScreen.kt`、`feature/settings/LlmSettingsScreen.kt`（标题/标签/按钮：`LLM 设置`、`Base URL`、`API Key`、`模型名称`、`超时阈值（秒）`、`书籍识别提示词`、`一般商品识别提示词`、`保存`、`清空`、`显示/隐藏`、API Key 提示句等）、`LlmSettingsViewModel.kt`（校验报错 `"请填写 Base URL"` 等 → `UiText`/资源）。
**要点：** 仅外提**界面**文案；提示词内容的多语言在阶段 C。按共享流程。

## Task 15: `feature/notifications/**`
**Files（Modify）:** `feature/notifications/NotificationsScreen.kt`、`NotificationsViewModel.kt`。
**要点：** 列表静态文案（`通知`、`暂无通知`）→ `notif_*`；到期提醒条目改为按后端结构化 payload（Task 22）的 `templateKey`+变量本地化渲染：新增 `notif_expiry_title`（en `"%1$s expiring soon"` / zh `"%1$s即将到期"`）与 `notif_expiry_body`（带 `%1$s` 标题、`%2$d` 小时、品类标签参数）。`templateKey` 未知时回落服务端 `title`/`body`。按共享流程。

## Task 16: `feature/bookscan/**`
**Files（Modify）:** `feature/bookscan/BookScanScreen.kt`（`扫描书籍条码`、`需要相机权限以扫描条码` 等）。按共享流程。

## Task 17: 跨层文案收尾（ViewModel/data/错误码）
**Files（Modify）:** `core/data/remote/ApiResponseMapper.kt`（`"无法解析服务器响应"` → `DomainResult.InvalidResponse` 带稳定 code，如 `INVALID_RESPONSE`）、`core/data/remote/BitMartApi.kt`（`"网络异常"` → code `NETWORK_ERROR`）、以及阶段 B 中各 VM 残留的对外中文。
**要点：** data 层不持 `Context`：统一改成「错误码 + 可选参数」，UI 侧用 `errorMessageRes(code)`/`UiText` 渲染。补齐所有 `error_*`/业务键到两份 XML。按共享流程。

## Task 18: 全量字符串清扫 + 合并校验
**Files（Modify）:** 两份 strings.xml（合并去重）；扫描遗漏。
- [ ] 跑全仓 CJK 字面量扫描（排除注释/Log/llm 提示词），确认 `feature/**`、`core/ui/**` 已无面向用户的中文字面量遗漏。
- [ ] 合并并按字母序整理两份 XML；Run parity + `assembleDebug`。
- [ ] Commit：`i18n: final sweep + merge string resources`。

---

# 阶段 C — LLM 提示词多语言

## Task 19: `Prompts.kt` 语言成对默认 + `PromptsTest`

**Files:**
- Modify: `app/src/main/kotlin/cn/edu/bit/bitmart/llm/Prompts.kt`
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/llm/PromptsTest.kt`

**Interfaces:**
- Produces: `DEFAULT_BOOK_PROMPT_ZH/EN`、`DEFAULT_GENERAL_PROMPT_ZH/EN`；`fun defaultBookPrompt(tag: String): String`、`fun defaultGeneralPrompt(tag: String): String`（`tag` 以 `"zh"` 开头→ZH，否则→EN）；保留 `DEFAULT_BOOK_PROMPT`/`DEFAULT_GENERAL_PROMPT` 作为 ZH 别名。

- [ ] **Step 1: 写失败测试**

```kotlin
package cn.edu.bit.bitmart.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    @Test fun zh_tag_selects_chinese() {
        assertEquals(DEFAULT_BOOK_PROMPT_ZH, defaultBookPrompt("zh-CN"))
        assertEquals(DEFAULT_GENERAL_PROMPT_ZH, defaultGeneralPrompt("zh"))
    }
    @Test fun non_zh_tag_selects_english() {
        assertEquals(DEFAULT_BOOK_PROMPT_EN, defaultBookPrompt("en-US"))
        assertEquals(DEFAULT_GENERAL_PROMPT_EN, defaultGeneralPrompt("fr"))
    }
    @Test fun english_prompt_is_actually_english() {
        assertTrue(DEFAULT_BOOK_PROMPT_EN.contains("JSON"))
        assertTrue(DEFAULT_BOOK_PROMPT_EN.none { it.code in 0x4E00..0x9FFF }) // 无中日韩统一表意文字
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*PromptsTest"`
Expected: FAIL

- [ ] **Step 3: 写实现**

把现有 `DEFAULT_BOOK_PROMPT`/`DEFAULT_GENERAL_PROMPT` 重命名为 `*_ZH`，新增 `*_EN`（英文等价提示词，约束「output a single pure JSON object `{"items":[...]}`, no markdown fences」、价格规则同义），并加别名与选择器：

```kotlin
const val DEFAULT_BOOK_PROMPT_ZH: String = /* 原 DEFAULT_BOOK_PROMPT 内容 */
const val DEFAULT_BOOK_PROMPT_EN: String =
    "You are a book recognition assistant. The user sends one photo that may contain multiple books; " +
        "identify each book's title, author, publisher, edition, and ISBN. " +
        "If a price is directly visible on the book (e.g. back-cover list price), fill originalPrice " +
        "(CNY, digits-only string); if no price is visible, set originalPrice to an empty string—do not guess, " +
        "and never output a selling price; set any other unrecognizable field to an empty string.\n" +
        "You must output exactly one pure JSON object of the form {\"items\":[{one element per book}]}, " +
        "an empty array if there are no books; output nothing else and do not wrap it in a Markdown code block."
const val DEFAULT_GENERAL_PROMPT_ZH: String = /* 原 DEFAULT_GENERAL_PROMPT 内容 */
const val DEFAULT_GENERAL_PROMPT_EN: String =
    "You are a second-hand goods listing assistant. The user sends one photo that may contain multiple items; " +
        "for each item produce a concise title, a short description, and a few search-friendly tags (string array, may be empty). " +
        "If a price is directly visible (price tag, label), fill originalPrice (CNY, digits-only string); " +
        "if no price is visible, set originalPrice to an empty string—do not guess, and do not produce a selling price.\n" +
        "You must output exactly one pure JSON object of the form {\"items\":[{one element per item}]}, " +
        "an empty array if there are no items; output nothing else and do not wrap it in a Markdown code block."

// 向后兼容别名（现有代码引用）
const val DEFAULT_BOOK_PROMPT: String = DEFAULT_BOOK_PROMPT_ZH
const val DEFAULT_GENERAL_PROMPT: String = DEFAULT_GENERAL_PROMPT_ZH

fun defaultBookPrompt(tag: String): String =
    if (tag.startsWith("zh")) DEFAULT_BOOK_PROMPT_ZH else DEFAULT_BOOK_PROMPT_EN
fun defaultGeneralPrompt(tag: String): String =
    if (tag.startsWith("zh")) DEFAULT_GENERAL_PROMPT_ZH else DEFAULT_GENERAL_PROMPT_EN
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*PromptsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/cn/edu/bit/bitmart/llm/Prompts.kt app/src/test/kotlin/cn/edu/bit/bitmart/llm/PromptsTest.kt
git commit -m "feat(i18n): bilingual default LLM prompts + selectors"
```

---

## Task 20: `OpenAiCompatibleLlmClient` 按语言组装请求

**Files:**
- Modify: `app/src/main/kotlin/cn/edu/bit/bitmart/llm/LlmClient.kt`（`recognize` 增 `languageTag`）、`llm/OpenAiCompatibleLlmClient.kt`
- Test: `app/src/test/kotlin/cn/edu/bit/bitmart/llm/OpenAiCompatibleLlmClientLangTest.kt`

**Interfaces:**
- Consumes: `defaultBookPrompt`/`defaultGeneralPrompt`（Task 19）。
- Produces: `suspend fun LlmClient.recognize(config, imageBytes, category, languageTag: String = "zh")`；EN/ZH 两套 `userText` 与 `*_SCHEMA`，按 `languageTag` 选取。
  - **`languageTag` 带默认值 `"zh"`**：使现有 3 参调用点（`PublishViewModel.kt:282`）在本任务后仍能编译、行为不变（保持中文），由 Task 21 再传入真实语言。**本任务不改 `PublishViewModel`**，避免与 Task 11 冲突。

- [ ] **Step 1: 写失败测试（Ktor MockEngine 捕获请求体）**

```kotlin
package cn.edu.bit.bitmart.llm

import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleLlmClientLangTest {
    private fun clientCapturing(sink: MutableList<String>): OpenAiCompatibleLlmClient {
        val engine = MockEngine { req ->
            sink.add((req.body as io.ktor.http.content.TextContent).text)
            respond(
                content = ByteReadChannel("""{"choices":[{"message":{"content":"{\"items\":[]}"}}]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return OpenAiCompatibleLlmClient(HttpClient(engine) { install(HttpTimeout) })
    }

    private val cfg = LlmConfig(baseUrl = "https://x", apiKey = "k", model = "m") // bookPrompt 默认空

    @Test fun blank_prompt_en_uses_english_default() = runTest {
        val bodies = mutableListOf<String>()
        clientCapturing(bodies).recognize(cfg, ByteArray(3), ListingCategory.BOOK, "en")
        assertTrue(bodies[0].contains("book recognition assistant"))
    }

    @Test fun blank_prompt_zh_uses_chinese_default() = runTest {
        val bodies = mutableListOf<String>()
        clientCapturing(bodies).recognize(cfg, ByteArray(3), ListingCategory.BOOK, "zh")
        assertTrue(bodies[0].contains("图书识别助手"))
    }

    @Test fun custom_prompt_used_verbatim() = runTest {
        val bodies = mutableListOf<String>()
        val custom = cfg.copy(bookPrompt = "MY CUSTOM PROMPT")
        clientCapturing(bodies).recognize(custom, ByteArray(3), ListingCategory.BOOK, "en")
        assertTrue(bodies[0].contains("MY CUSTOM PROMPT"))
    }
}
```

> 若项目已有 LLM 客户端测试（用别的 MockEngine helper），对齐其写法/依赖（`ktor-client-mock` 应已在 testImplementation；若否，在 `app/build.gradle.kts` 加 `testImplementation("io.ktor:ktor-client-mock:3.5.0")`）。

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*OpenAiCompatibleLlmClientLangTest"`
Expected: FAIL（`recognize` 仍是 3 参 / 无英文分支）

- [ ] **Step 3: 写实现**

`LlmClient.kt` 接口签名加 `languageTag: String = "zh"`（默认值保证旧 3 参调用点编译）。`OpenAiCompatibleLlmClient.recognize` 透传到 `buildRequest`，并：

```kotlin
// buildRequest 内
val bookPrompt = config.bookPrompt.ifBlank { defaultBookPrompt(languageTag) }
val generalPrompt = config.generalPrompt.ifBlank { defaultGeneralPrompt(languageTag) }
val prompt = if (category == ListingCategory.BOOK) bookPrompt else generalPrompt
val zh = languageTag.startsWith("zh")
val userText = when {
    category == ListingCategory.BOOK && zh -> "请识别这张图片中所有书本的信息（可能不止一本）。"
    category == ListingCategory.BOOK -> "Identify all books in this photo (there may be more than one)."
    zh -> "请识别这张照片中所有商品并分别生成挂牌信息（可能不止一件）。"
    else -> "Identify all items in this photo and generate a listing for each (there may be more than one)."
}
// responseFormat(category) -> responseFormat(category, languageTag)，按 zh 选 *_SCHEMA_ZH/EN
```

新增英文 schema 常量 `BOOK_SCHEMA_EN`/`GENERAL_SCHEMA_EN`（把现有 schema 的中文 `description` 译成英文，结构/字段名/`required` 不变），原中文常量重命名为 `*_SCHEMA_ZH`；`responseFormat(category, tag)` 据 `tag` 选取。

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*OpenAiCompatibleLlmClientLangTest"`
Expected: PASS（已有 LLM 测试若因签名变更失败，更新其调用补 `languageTag` 实参）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/cn/edu/bit/bitmart/llm/LlmClient.kt app/src/main/kotlin/cn/edu/bit/bitmart/llm/OpenAiCompatibleLlmClient.kt app/src/test/kotlin/cn/edu/bit/bitmart/llm/OpenAiCompatibleLlmClientLangTest.kt
git commit -m "feat(i18n): LLM request built per app language (prompt/userText/schema)"
```

---

## Task 21: `LlmConfig` 空默认 + 表单占位 + `PublishViewModel` 语言入参

> **依赖：Task 11（PublishScreen/PublishViewModel 字符串迁移）先完成**，避免同文件并行冲突。

**Files:**
- Modify: `llm/LlmConfig.kt`（`bookPrompt`/`generalPrompt` 默认 `""`）
- Modify: `feature/settings/LlmSettingsViewModel.kt`（`save()` 不再 `ifBlank{DEFAULT}`；`clear()`/init 提示词留空）
- Modify: `feature/settings/LlmSettingsScreen.kt`（提示词输入框 `placeholder` 显示当前语言默认 + 留空说明）
- Modify: `feature/publish/PublishViewModel.kt`（注入 `LanguagePrefsStore`，识别前解析 `languageTag` 并传入 `recognize`）

**Interfaces:**
- Consumes: `LanguagePrefsStore`、`defaultBookPrompt/defaultGeneralPrompt`、`recognize(..., languageTag)`。

- [ ] **Step 1: `LlmConfig` 默认改空**

`bookPrompt: String = ""`、`generalPrompt: String = ""`。（`isConfigured` 不变。）

- [ ] **Step 2: `LlmSettingsViewModel`**

`init` 载入 `c.bookPrompt`（可能空）；`save()` 直接 `bookPrompt = s.bookPrompt`（不再回填默认）；`clear()` 把两提示词置 `""`。校验报错文案改资源/`UiText`（与 Task 14 一致）。

- [ ] **Step 3: `LlmSettingsScreen`**

两个提示词 `OutlinedTextField` 加 `placeholder = { Text(stringResource(R.string.llm_prompt_hint)) }`；底部说明 `stringResource(R.string.llm_prompt_blank_note)`。新增键：
- en: `llm_prompt_hint`=`"Leave blank to use the default for the current language"`，`llm_prompt_blank_note` 同义；
- zh: `"留空则按当前语言使用默认提示词"`。
（如需在 placeholder 实呈现具体默认文，可用 `defaultBookPrompt(currentTag)`，`currentTag` 由 `LocalConfiguration` 解析；最简实现先用上面提示句。）

- [ ] **Step 4: `PublishViewModel` 语言入参**

构造注入 `private val languageStore: LanguagePrefsStore`。识别处（`:282` 附近）：

```kotlin
val lang = languageStore.current() // suspend，在已有协程作用域内
val tag = lang.resolveLanguageTag(Locale.getDefault().toLanguageTag())
when (val r = llmClient.recognize(config, imageBytes, category, tag)) { /* 不变 */ }
```

补 import `java.util.Locale`、`cn.edu.bit.bitmart.core.data.local.current`、`LanguagePrefsStore`、`AppLanguage`。

- [ ] **Step 5: 验证**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL；既有 + 新增测试全 PASS（含 LLM 测试，已配 `languageTag`）。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(i18n): LLM prompts follow app language; settings placeholder; publish passes languageTag"
```

---

# 阶段 D — 后端最小改动

## Task 22: 结构化到期通知

**Files:**
- Modify: `bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/job/ExpiryWarningJob.kt`
- Modify: `bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/user/NotificationRepository.kt`
- Verify/Modify: `bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/user/MeRoutes.kt`（`NotificationDto` payload 透传）
- Test: 更新 `ExpiryWarningJob` 既有测试 + 通知 DTO 测试

**Interfaces:**
- Produces: 到期通知的 JSONB `payload` 含 `{ templateKey:"EXPIRY_WARNING", listingId, expiresAt, listingTitle, hours, listingType }`；`title`/`body` 仍照常写中文兜底。

- [ ] **Step 1: 写/改失败测试**

在 `ExpiryWarningJob` 测试里断言：生成的通知 `payload` 经解析含 `templateKey == "EXPIRY_WARNING"`、`hours`、`listingTitle`、`listingType`；并仍有非空 `title`/`body`。（沿用项目内嵌 Postgres 测试夹具。）

Run: `cd bitmart-server && ./gradlew test --tests "*ExpiryWarning*"`
Expected: FAIL

- [ ] **Step 2: 实现**

`ExpiryWarningJob`：保留中文 `kind/title/body` 计算，新增结构化 payload 组装（用 `jsonbRaw()`/`buildJsonObject` 按项目既有方式），把 `templateKey` 与变量写入 payload；`NotificationRepository.create` 透传该 payload。`MeRoutes`/`NotificationDto` 确认把 `payload` 原样下发（payload 已是 DTO 字段则无需改）。

- [ ] **Step 3: 通过 + 全量后端测试**

Run: `cd bitmart-server && ./gradlew test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/job/ExpiryWarningJob.kt bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/user/NotificationRepository.kt bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/user/MeRoutes.kt
git add bitmart-server/src/test
git commit -m "feat(i18n): structured expiry-notification payload for client-side localization"
```

---

## Task 23: 去掉 `"匿名"` 服务端兜底

**Files:**
- Modify: `bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/domain/User.kt`（`displayName` 不再回落 `"匿名"`）
- Modify: `bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/auth/AuthDto.kt`（`UserDto` 暴露原始 `nickname`，可空/空串）
- Test: 调整相关断言

**Interfaces:**
- Produces: `UserDto.displayName`（或 `nickname`）为原始昵称，空/未设为 null/空串；客户端 Task 13 渲染本地化「匿名/Anonymous」。

- [ ] **Step 1: 改测试**

把断言「空昵称 → `"匿名"`」改为「空昵称 → null/空串」。
Run: `cd bitmart-server && ./gradlew test --tests "*User*" --tests "*Auth*"`
Expected: 先 FAIL

- [ ] **Step 2: 实现**

`User.kt`：`val displayName get() = nickname?.takeIf { it.isNotBlank() }`（去掉 `?: "匿名"`）。`AuthDto`/`UserDto` 据此序列化（字段保持 `nickname`/`displayName`，值可为 null/空）。客户端已在 Task 13 兜底。

- [ ] **Step 3: 通过 + 全量后端测试**

Run: `cd bitmart-server && ./gradlew test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/domain/User.kt bitmart-server/src/main/kotlin/cn/edu/bit/bitmart/auth/AuthDto.kt bitmart-server/src/test
git commit -m "feat(i18n): drop server-side 匿名 fallback; client localizes display name"
```

---

# 阶段 E — 收尾验收

## Task 24: 全量回归 + 评审

- [ ] **Step 1: Android 全量单测**

Run: `cd bitmart-android && ./gradlew :app:testDebugUnitTest`
Expected: 全 PASS（含 parity、UiText、Language*、Prompts、LLM lang 测试）。

- [ ] **Step 2: Android 构建**

Run: `cd bitmart-android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 后端全量测试**

Run: `cd bitmart-server && ./gradlew test`
Expected: 全 PASS。

- [ ] **Step 4: 手动冒烟清单**

- 跟随系统：设备中文→中文 UI；设备英文/其它→英文 UI。
- 显式切「中文」「English」即时全局生效、跨重启保持。
- 识图：英文模式下识别结果（标题/描述/标签）为英文；中文模式为中文；自定义提示词原样生效。
- 通知：到期提醒在中/英下分别本地化展示。
- 登录/发布/详情/账号/LLM 设置各屏无残留中文（英文模式下）。

- [ ] **Step 5: 最终评审（impl-test-review 退出条件）**

对照 `docs/superpowers/specs/2026-06-19-i18n-language-switching-design.md` 六维（需求/正确性/性能/安全/可维护/文档）逐项确认；记录到 PR 描述。

- [ ] **Step 6: 汇总提交（交由用户决定 push）**

```bash
git add -A && git status   # 核对；是否提交/推送由用户决定
```

---

## 自检：Spec 覆盖对照

- 语言模型/偏好/VM/UI 应用 → Task 1–3、6、7 ✅
- 字符串资源（默认英文 + values-zh）+ parity → Task 5、8–18 ✅
- 三类棘手用例（插值/枚举标签/复数）→ 共享流程 + Task 8/11/12/15 ✅
- 非 Compose 层 `UiText`/错误码 → Task 4、9、12、17 ✅
- LLM 多语言（Prompts/客户端/Config/表单/Publish 入参）→ Task 19–21 ✅
- 后端结构化通知 + 去 `匿名` → Task 22–23 ✅
- 测试矩阵（AppLanguage/Store/VM/UiText/Parity/Prompts/LLM/后端）→ 各 Task + Task 24 ✅
- 默认「非中/英→英文」、数据可清空（无迁移）→ 全局约束 + Task 1/5/22/23 ✅
