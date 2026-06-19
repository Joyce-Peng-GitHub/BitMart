package cn.edu.bit.bitmart.core.domain.model

/**
 * 主题模式：跟随系统 / 亮色 / 暗色。纯领域模型，不含任何 Android 依赖。
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    /** 解析为是否使用暗色板。[systemInDark] 为系统当前是否暗色。 */
    fun resolveDark(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }
}
