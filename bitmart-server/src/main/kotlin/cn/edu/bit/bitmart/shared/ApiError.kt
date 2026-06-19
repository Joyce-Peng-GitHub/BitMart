package cn.edu.bit.bitmart.shared

import kotlinx.serialization.Serializable

/**
 * 统一错误响应：{ "error": { "code": "...", "message": "...", "details": [...] } }（见架构 §6）。
 * code 为稳定的机器可读标识，message 面向开发者/日志兜底；
 * details 为结构化校验明细（稳定 code + 参数），供客户端本地化，非校验类错误时省略。
 */
@Serializable
data class ApiError(val error: ErrorBody) {
    @Serializable
    data class ErrorBody(
        val code: String,
        val message: String,
        val details: List<ErrorDetail>? = null,
    )

    /** 结构化错误明细：稳定 code + 参数，供客户端本地化（message 仅作开发者/日志兜底）。 */
    @Serializable
    data class ErrorDetail(
        val field: String,
        val code: String,
        val params: Map<String, String> = emptyMap(),
    )

    companion object {
        fun of(code: ErrorCode, message: String): ApiError =
            ApiError(ErrorBody(code.name, message))

        /** 带结构化明细的错误；details 为空时省略（落为 null）。 */
        fun of(code: ErrorCode, message: String, details: List<ErrorDetail>): ApiError =
            ApiError(ErrorBody(code.name, message, details.ifEmpty { null }))
    }
}

/** 错误码枚举，避免散落的魔法字符串。 */
enum class ErrorCode {
    VALIDATION_FAILED,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    EXTERNAL_SERVICE_ERROR,
    INTERNAL_ERROR,
}
