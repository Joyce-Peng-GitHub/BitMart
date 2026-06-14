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

    /**
     * 单条发布的件数上限，与服务端 ListingValidator.MAX_QUANTITY 保持一致。
     * 面向校园二手场景的合理上界；客户端据此拦截，服务端有独立的同等校验。
     */
    const val MAX_QUANTITY = 9999

    /**
     * 单价上限，对齐服务端 DB 列 unit_price NUMERIC(10,2)（最大 99999999.99）。
     * 客户端提交前据此拦截，避免无谓的 400 往返；服务端有独立的同等校验。
     */
    const val MAX_UNIT_PRICE = "99999999.99"

    /** 有效期（天）允许范围与默认值，与服务端 expiry 配置保持一致。 */
    const val EXPIRY_MIN_DAYS = 1
    const val EXPIRY_MAX_DAYS = 365
    const val EXPIRY_DEFAULT_DAYS = 30

    /**
     * 临期提醒窗口（小时）：进入该窗口即视为“临近过期”（UI 橙色）。
     * 与服务端 BITMART_EXPIRY_WARN_WINDOW_HOURS 默认值一致——服务端在此窗口内发过期提醒通知。
     */
    const val EXPIRY_WARN_WINDOW_HOURS = 24L
}
