package cn.edu.bit.bitmart.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import cn.edu.bit.bitmart.core.domain.model.ThemeMode

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * BitMart 主题。封装亮/暗色板，依据持久化的 [ThemeMode] 决定明暗：
 * 跟随系统时回退到 [isSystemInDarkTheme]，否则强制亮/暗。
 */
@Composable
fun BitMartTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDark(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
