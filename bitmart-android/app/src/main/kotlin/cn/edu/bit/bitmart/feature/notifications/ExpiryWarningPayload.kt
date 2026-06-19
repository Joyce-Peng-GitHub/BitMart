package cn.edu.bit.bitmart.feature.notifications

import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.Notification
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 后端为「到期提醒」通知（category == 1）写入的结构化 payload（见后端 ExpiryWarningJob）。
 * 客户端据此本地化渲染标题/正文，不再展示服务端存储的中文 title/body。
 * 字段与后端契约严格对应；[hours] 是预警窗口大小（如 24），文案应表述为「N 小时内到期」。
 */
@Serializable
data class ExpiryWarningPayload(
    val listingId: Long,
    val expiresAt: String,
    val templateKey: String,
    val listingTitle: String,
    val hours: Int,
    val listingType: String,
)

/** 解析 payload 时容忍后端新增字段，避免老/新版本互不兼容。 */
private val payloadJson = Json { ignoreUnknownKeys = true }

/** 到期提醒所用的 templateKey。其余（含未知）一律回落服务端 title/body。 */
private const val EXPIRY_WARNING_TEMPLATE = "EXPIRY_WARNING"

/**
 * 仅当该通知确为「到期提醒」且可完整渲染时返回解析后的 payload，否则返回 null
 * （调用方据此回落到存储的中文 title/body）：
 * - 必须是个人提醒（category == 1）；
 * - payload 非空且可解析；
 * - templateKey == "EXPIRY_WARNING"；
 * - listingType 是已知枚举（SELL/BUY），避免 UI 侧 valueOf 抛异常。
 * 任何解析异常（缺字段、非法 JSON、未知枚举、老数据无 payload）均吞掉并返回 null，保证渲染健壮。
 */
fun Notification.expiryWarningPayload(): ExpiryWarningPayload? {
    if (category != 1) return null
    val raw = payload ?: return null
    return runCatching { payloadJson.decodeFromString<ExpiryWarningPayload>(raw) }
        .getOrNull()
        ?.takeIf { it.templateKey == EXPIRY_WARNING_TEMPLATE && it.listingType.toListingTypeOrNull() != null }
}

/** 把 payload 里的字符串安全转为枚举，未知值返回 null（不抛异常）。 */
fun String.toListingTypeOrNull(): ListingType? =
    ListingType.entries.firstOrNull { it.name == this }
