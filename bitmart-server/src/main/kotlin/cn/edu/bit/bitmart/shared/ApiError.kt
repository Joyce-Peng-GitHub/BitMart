package cn.edu.bit.bitmart.shared

import kotlinx.serialization.Serializable

/**
 * 统一错误响应：{ "error": { "code": "...", "message": "..." } }（见架构 §6）。
 * code 为稳定的机器可读标识，message 面向开发者/可本地化展示。
 */
@Serializable
data class ApiError(val error: ErrorBody) {
    @Serializable
    data class ErrorBody(val code: String, val message: String)

    companion object {
        fun of(code: ErrorCode, message: String): ApiError =
            ApiError(ErrorBody(code.name, message))
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
