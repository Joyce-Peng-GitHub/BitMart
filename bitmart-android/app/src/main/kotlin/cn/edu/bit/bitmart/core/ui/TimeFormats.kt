package cn.edu.bit.bitmart.core.ui

import androidx.compose.ui.graphics.Color
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

/** 临近过期的橙色（亮/暗主题均可辨识）。已过期用主题 error 色。 */
val ExpiryWarnColor = Color(0xFFF57C00)

/**
 * 服务端 ISO-8601 时间戳（如 `2026-06-11T18:40:20.511027Z`，也可能带 `+08:00` 偏移）
 * → 本地时区、精确到分钟的可读文本（如 `2026-06-12 02:40`）。
 * 解析失败时原样返回，宁可显示原始串也不崩溃。
 * @param zone 目标时区，默认系统时区；注入以便测试。
 */
fun formatTimestampMinute(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(MINUTE_FORMATTER)
    }.getOrDefault(iso)

/** 过期状态：正常 / 临近过期（橙）/ 已过期（红）。 */
enum class ExpiryStatus { NORMAL, NEAR_EXPIRY, EXPIRED }

/**
 * 由过期时间推断展示状态。已过 → EXPIRED；距今 <= [warnWindowHours] 小时 → NEAR_EXPIRY；否则 NORMAL。
 * 解析失败 → NORMAL（不误报、不崩溃）。[nowEpochMs]/[warnWindowHours] 可注入以便测试。
 * 窗口默认与服务端过期提醒一致（见 [PublishConfig.EXPIRY_WARN_WINDOW_HOURS]）。
 */
fun expiryStatusOf(
    expiresAtIso: String,
    nowEpochMs: Long = System.currentTimeMillis(),
    warnWindowHours: Long = PublishConfig.EXPIRY_WARN_WINDOW_HOURS,
): ExpiryStatus {
    val expiresAtMs = runCatching { OffsetDateTime.parse(expiresAtIso).toInstant().toEpochMilli() }
        .getOrNull() ?: return ExpiryStatus.NORMAL
    return when {
        expiresAtMs <= nowEpochMs -> ExpiryStatus.EXPIRED
        expiresAtMs <= nowEpochMs + warnWindowHours * 3_600_000L -> ExpiryStatus.NEAR_EXPIRY
        else -> ExpiryStatus.NORMAL
    }
}
