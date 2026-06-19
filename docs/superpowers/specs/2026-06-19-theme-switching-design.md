# 主题切换功能设计（跟随系统 / 亮 / 暗）

- 日期：2026-06-19
- 范围：bitmart-android（Jetpack Compose）
- 状态：已批准，待实现

## 目标

在设置页提供主题切换能力，三个选项：**跟随系统 / 亮色 / 暗色**。选择即时全局生效，并跨重启持久化。

## UI 决策

点击设置页"主题设置"行 → 弹出 `AlertDialog` 单选框（三项），选中即生效并关闭。设置行副标题显示当前模式标签（不再"敬请期待"）。语言设置仍保留"敬请期待"。

## 架构与数据流

单一数据源为 DataStore：

```
ThemePrefsStore (DataStore) ──flow──> ThemeViewModel ──> MainActivity: BitMartTheme(themeMode)
        ▲                                  └──> SettingsScreen: 行副标题 + 单选对话框
        └──────── setMode() ◄── 用户在对话框选择
```

根布局与设置页各持有自己的 `ThemeViewModel` 实例（Activity / nav-entry 作用域），但都读写同一个单例 `ThemePrefsStore`。用户选中 → 写 store → store 的 Flow 推送 → 根布局重组、全局即时换肤。符合项目"每屏独立 VM、共享单例 Store"约定。

## 组件清单

### 1. `ThemeMode`（`core/domain/model/ThemeMode.kt`，纯 Kotlin）

```kotlin
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    /** 解析为是否使用暗色板。[systemInDark] 为系统当前是否暗色。 */
    fun resolveDark(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }
}
```

中文标签映射放在 UI 层（与 `ContactChannel` 约定一致），域层不含 UI 文案。

### 2. `ThemePrefsStore`（`core/data/local/ThemePrefsStore.kt`）

照搬 `ContactPrefsStore` 结构：

```kotlin
interface ThemePrefsStore {
    val themeModeFlow: Flow<ThemeMode>
    suspend fun setMode(mode: ThemeMode)
}

class DataStoreThemePrefsStore(dataStore: DataStore<Preferences>) : ThemePrefsStore {
    // key = stringPreferencesKey("theme_mode"); 存 mode.name
    // 解析失败/无值 → SYSTEM
}
```

在 `AppModule` 用 Hilt `@Provides @Singleton` 提供。

### 3. `ThemeViewModel`（`feature/settings/ThemeViewModel.kt`，`@HiltViewModel`）

```kotlin
val themeMode: StateFlow<ThemeMode>  // store.themeModeFlow.stateIn(scope, WhileSubscribed(5000)/Eagerly, SYSTEM)
fun setMode(mode: ThemeMode)         // viewModelScope.launch { store.setMode(mode) }
```

### 4. `Theme.kt` 修改

`BitMartTheme` 签名由 `darkTheme: Boolean = isSystemInDarkTheme()` 改为：

```kotlin
@Composable
fun BitMartTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDark(isSystemInDarkTheme())
    ...
}
```

### 5. `MainActivity`

```kotlin
setContent {
    val vm: ThemeViewModel = hiltViewModel()
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    BitMartTheme(themeMode = mode) { BitMartNavHost() }
}
```

### 6. `SettingsScreen`

注入 `ThemeViewModel`（`hiltViewModel()`），收集当前 mode：
- "主题设置"行副标题 = 当前模式中文标签（跟随系统/亮色/暗色）。
- 点击 → `AlertDialog`，三个 `RadioButton` 行，选中即 `vm.setMode(it)` 并 dismiss。

## 测试（JVM 单测）

| 测试 | 覆盖 |
| --- | --- |
| `DataStoreThemePrefsStoreTest` | 默认 SYSTEM；setMode 持久化；非法存储值兜底 SYSTEM（用 `InMemoryPreferencesDataStore`） |
| `ThemeViewModelTest` | setMode 后 `themeMode` flow 更新（用内存 store 或 fake） |
| `ThemeModeTest` | `resolveDark` 三态：SYSTEM 随 systemInDark、LIGHT=false、DARK=true |

运行：`./gradlew :app:testDebugUnitTest`，全绿方可进入评审。

## 边界与决策

- 未知/损坏的存储字符串 → 兜底 `SYSTEM`。
- 无历史值（首次） → `SYSTEM`，与当前行为一致。
- 冷启动：DataStore 首次读出前以 `SYSTEM` 初值渲染；若已存暗色，可能极短暂浅色闪烁，可接受。

## 范围外（YAGNI）

- 动态取色 / Material You。
- 每色板自定义配色。
