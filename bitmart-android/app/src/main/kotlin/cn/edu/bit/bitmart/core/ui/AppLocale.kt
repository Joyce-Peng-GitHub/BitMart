package cn.edu.bit.bitmart.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
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
 *
 * 关键：下发的 Context 必须仍是一个「能向上 unwrap 到 Activity」的 [ContextWrapper]，
 * 否则其内部的 `hiltViewModel()` 会因 `HiltViewModelFactory` 找不到 Activity 而抛
 * `IllegalStateException: Expected an activity context`。因此这里用一个包住「原始
 * （Activity）Context」的 [ContextWrapper]，仅改写 resources/assets 为本地化版本，
 * 而不是直接下发 `createConfigurationContext()` 的结果（后者是一个不含 Activity 的全新 Context）。
 */
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    // 设备当前首选语言（在覆盖前读取，保证 SYSTEM 解析正确）
    val systemTag = ConfigurationCompat.getLocales(baseConfig)[0]?.toLanguageTag().orEmpty()
    val locale = Locale.forLanguageTag(language.resolveLanguageTag(systemTag))

    val localizedContext = remember(baseContext, locale) {
        localizedContext(baseContext, locale)
    }
    val localizedConfig = remember(localizedContext) { localizedContext.resources.configuration }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig,
    ) { content() }
}

/**
 * 构造一个 base 仍为 [base]（通常是 Activity）的 [ContextWrapper]，但其 resources/assets
 * 采用按 [locale] 本地化后的版本。保留 Activity 在 ContextWrapper 链上，使 `hiltViewModel()`
 * 等依赖「向上查找 Activity」的 API 正常工作。
 */
private fun localizedContext(base: Context, locale: Locale): ContextWrapper {
    val cfg = Configuration(base.resources.configuration).apply { setLocale(locale) }
    val configContext = base.createConfigurationContext(cfg)
    return object : ContextWrapper(base) {
        override fun getResources(): Resources = configContext.resources
        override fun getAssets(): AssetManager = configContext.assets
    }
}
