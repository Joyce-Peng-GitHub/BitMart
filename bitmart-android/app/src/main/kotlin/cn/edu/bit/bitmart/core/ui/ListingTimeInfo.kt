package cn.edu.bit.bitmart.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * 列表/详情共用的“发布时间 + 过期时间”两行。过期时间按 [expiryStatusOf] 着色：
 * 临近过期橙色、已过期红色并注明“（已过期）”、正常为次要色。
 */
@Composable
fun ListingTimeInfo(
    createdAtIso: String,
    expiresAtIso: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val status = expiryStatusOf(expiresAtIso)
    val expiryColor = when (status) {
        ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.error
        ExpiryStatus.NEAR_EXPIRY -> ExpiryWarnColor
        ExpiryStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val expirySuffix = if (status == ExpiryStatus.EXPIRED) "（已过期）" else ""
    Column(modifier) {
        Text(
            "发布时间：${formatTimestampMinute(createdAtIso)}",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "过期时间：${formatTimestampMinute(expiresAtIso)}$expirySuffix",
            style = style,
            color = expiryColor,
        )
    }
}
