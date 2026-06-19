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
