package cn.edu.bit.bitmart.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.ValidationDetail
import cn.edu.bit.bitmart.core.domain.model.PublishConfig

/** 非 Compose 层（ViewModel/data）产出的文案包装：要么资源 id+参数，要么已成型字符串。 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    /** 多条文案（如多条校验错误）按分隔符拼接。 */
    data class Multi(val parts: List<UiText>, val separator: String = "\n") : UiText

    @Composable
    fun asString(): String = when (this) {
        is Raw -> value
        is Res -> stringResource(id, *resolvedArgs { it.asString() })
        is Multi -> {
            val context = LocalContext.current
            parts.joinToString(separator) { it.resolve(context) }
        }
    }

    /**
     * 在非 Compose 作用域（如 LaunchedEffect 中弹 Toast）解析文案。
     * Compose 内优先用 [asString]；只有拿不到 composition 上下文时才用本方法。
     */
    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *resolvedArgs { it.resolve(context) })
        is Multi -> parts.joinToString(separator) { it.resolve(context) }
    }
}

/**
 * 解析 [UiText.Res.args]：嵌套的 [UiText] 用给定的 resolver 先转为字符串（支持「第 N 项：<错误>」这类
 * 由其他本地化文案拼接而成的消息），其余参数原样下发给 String.format。
 */
private inline fun UiText.Res.resolvedArgs(resolve: (UiText) -> String): Array<Any> =
    args.map { if (it is UiText) resolve(it) else it }.toTypedArray()

/** 稳定错误码 → 本地化字符串资源。未知码回落通用错误。 */
@StringRes
fun errorMessageRes(code: String): Int = when (code) {
    "UNAUTHORIZED" -> R.string.error_unauthorized
    "FORBIDDEN" -> R.string.error_forbidden
    "NOT_FOUND" -> R.string.error_not_found
    "CONFLICT" -> R.string.error_conflict
    "RATE_LIMITED" -> R.string.error_rate_limited
    "VALIDATION_FAILED" -> R.string.error_validation_failed
    "EXTERNAL_SERVICE_ERROR" -> R.string.error_external_service
    "INTERNAL_ERROR" -> R.string.error_generic
    "NETWORK_ERROR" -> R.string.error_network
    "INVALID_RESPONSE" -> R.string.error_invalid_response
    else -> R.string.error_generic
}

/** [DomainResult.Error] → 本地化文案：业务错误优先用结构化 details 本地化，否则按稳定 error code 映射，其余按失败类型回落到通用 error_* 资源。 */
fun DomainResult.Error.toUiText(): UiText = when (this) {
    is DomainResult.Failure ->
        if (details.isNotEmpty()) validationDetailsToUiText(details)
        else UiText.Res(errorMessageRes(code))
    is DomainResult.InvalidResponse -> UiText.Res(R.string.error_invalid_response)
    is DomainResult.NetworkError -> UiText.Res(R.string.error_network)
}

/**
 * 仅取首个问题的本地化文案：发布/编辑提交失败时，弹窗只展示「第一条」错误（已按字段定位、批量带条号），
 * 帮助用户快速定位最先需要修正的地方。无结构化 details 时回落到按 error code 映射。
 * 注意：密码等需要逐条展示的流程仍用 [toUiText]（[UiText.Multi]），本方法不影响其行为。
 */
fun DomainResult.Error.toFirstProblemUiText(): UiText = when (this) {
    is DomainResult.Failure -> details.firstOrNull()?.let { detailToUiText(it) } ?: UiText.Res(errorMessageRes(code))
    else -> toUiText()
}

/** 多条校验明细 → 本地化文案：单条直接返回，多条用 [UiText.Multi] 逐行拼接。 */
private fun validationDetailsToUiText(details: List<ValidationDetail>): UiText {
    val parts = details.map { detailToUiText(it) }
    return if (parts.size == 1) parts.first() else UiText.Multi(parts)
}

/** 批量发布字段前缀：`items[<n>].`，用于剥离并取出 0 基条目下标。 */
private val ITEMS_PREFIX = Regex("""^items\[(\d+)]\.""")

/**
 * 归并为基础字段名，便于按字段选模板：
 * - 先去除批量前缀 `items[<n>].`；
 * - 再丢弃带下标的子字段后缀（如 `tags[<i>]`、`contact[<i>].value`），只取下标前的字段名。
 */
private fun baseField(field: String): String =
    field.replace(ITEMS_PREFIX, "").substringBefore('[')

/**
 * 单条校验明细 → 本地化文案：按稳定 code（必要时结合字段）选模板并用 params 填充；未知 code 回落通用校验文案。
 * 若字段带批量前缀 `items[<n>].`，再包一层「第 n+1 条：<内层文案>」（嵌套 UiText 由 resolvedArgs 解析）。
 */
private fun detailToUiText(detail: ValidationDetail): UiText {
    // 批量发布的字段形如 items[0].unitPrice：剥离前缀得到基础字段，并记住条目下标（0 基）。
    val match = ITEMS_PREFIX.find(detail.field)
    val itemIndex = match?.groupValues?.get(1)?.toIntOrNull()
    val field = baseField(detail.field)
    val inner = detailBodyUiText(detail, field)
    return if (itemIndex != null) UiText.Res(R.string.publish_error_batch_item, listOf(itemIndex + 1, inner)) else inner
}

/** 按 (code, 基础字段) 映射到具体文案模板；数字一律取自 params（缺失才回落 [PublishConfig] 常量）。 */
private fun detailBodyUiText(detail: ValidationDetail, field: String): UiText {
    val p = detail.params
    return when (detail.code) {
        // —— 账号/密码（既有，保持不变）——
        "PASSWORD_TOO_SHORT" ->
            UiText.Res(R.string.error_password_too_short, listOf(p["minLength"] ?: ""))
        "PASSWORD_TOO_SIMPLE" ->
            UiText.Res(R.string.error_password_too_simple, listOf(p["minCharClasses"] ?: ""))
        // —— 发布/编辑校验 ——
        "TITLE_BLANK" -> UiText.Res(R.string.publish_error_title_required)
        "QUANTITY_TOTAL_INVALID", "QUANTITY_TOTAL_TOO_LARGE" ->
            UiText.Res(R.string.publish_error_quantity_range, listOf(p["max"] ?: PublishConfig.MAX_QUANTITY.toString()))
        "QUANTITY_TOTAL_BELOW_SOLD" ->
            UiText.Res(R.string.publish_error_quantity_below_sold, listOf(p["sold"] ?: ""))
        "PRICE_NEGATIVE", "PRICE_TOO_LARGE" -> {
            val max = p["max"] ?: PublishConfig.MAX_UNIT_PRICE
            if (field == "originalPrice") UiText.Res(R.string.publish_error_original_price_range, listOf(max))
            else UiText.Res(R.string.publish_error_unit_price_range, listOf(max))
        }
        "CONTACT_REQUIRED" -> UiText.Res(R.string.publish_error_contact_required)
        "CONTACT_VALUE_BLANK" -> UiText.Res(R.string.publish_error_contact_blank)
        "TAGS_TOO_MANY" ->
            UiText.Res(R.string.publish_error_tags_too_many, listOf(p["max"] ?: PublishConfig.MAX_TAGS.toString()))
        "TAG_BLANK" -> UiText.Res(R.string.publish_error_tag_blank)
        "TAG_TOO_LONG" ->
            UiText.Res(R.string.publish_error_tag_too_long, listOf(p["max"] ?: ""))
        "EXPIRY_TOO_SOON", "EXPIRY_TOO_LATE" ->
            UiText.Res(
                R.string.publish_error_expiry_range,
                listOf(
                    p["minDays"] ?: PublishConfig.EXPIRY_MIN_DAYS.toString(),
                    p["maxDays"] ?: PublishConfig.EXPIRY_MAX_DAYS.toString(),
                ),
            )
        "QUANTITY_SOLD_NEGATIVE", "QUANTITY_SOLD_EXCEEDS_TOTAL" ->
            UiText.Res(R.string.publish_error_sold_range, listOf(p["total"] ?: ""))
        else -> UiText.Res(R.string.error_validation_failed)
    }
}
