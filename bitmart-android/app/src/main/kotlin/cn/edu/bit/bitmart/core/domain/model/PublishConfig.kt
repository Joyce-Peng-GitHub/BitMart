package cn.edu.bit.bitmart.core.domain.model

/**
 * 发布相关的编译期配置常量。
 * 客户端强制上限（服务端也有独立的校验），保证良好 UX。
 */
object PublishConfig {
    /** 标签个数上限（用户可选择热门标签 + 自定义标签，总计不超过此数）。 */
    const val MAX_TAGS = 8

    /** 单条发布项的图片张数上限。 */
    const val MAX_IMAGES = 9

    /** 有效期（天）允许范围与默认值，与服务端 expiry 配置保持一致。 */
    const val EXPIRY_MIN_DAYS = 1
    const val EXPIRY_MAX_DAYS = 365
    const val EXPIRY_DEFAULT_DAYS = 30
}
