package cn.edu.bit.bitmart.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult

/** 非 Compose 层（ViewModel/data）产出的文案包装：要么资源 id+参数，要么已成型字符串。 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Raw -> value
        is Res -> stringResource(id, *resolvedArgs { it.asString() })
    }

    /**
     * 在非 Compose 作用域（如 LaunchedEffect 中弹 Toast）解析文案。
     * Compose 内优先用 [asString]；只有拿不到 composition 上下文时才用本方法。
     */
    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *resolvedArgs { it.resolve(context) })
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

/** [DomainResult.Error] → 本地化文案：业务错误按稳定 error code 映射，其余按失败类型回落到通用 error_* 资源。 */
fun DomainResult.Error.toUiText(): UiText = when (this) {
    is DomainResult.Failure -> UiText.Res(errorMessageRes(code))
    is DomainResult.InvalidResponse -> UiText.Res(R.string.error_invalid_response)
    is DomainResult.NetworkError -> UiText.Res(R.string.error_network)
}
