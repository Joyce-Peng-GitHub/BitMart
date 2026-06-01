package cn.edu.bit.bitmart.external

import kotlinx.serialization.Serializable

/** BIT101 webvpn 校验接口的请求/响应模型（见 docs/BIT101-Verification.md 与 demo）。 */

@Serializable
internal data class WebvpnVerifyInitRequest(val sid: String)

@Serializable
internal data class WebvpnVerifyInitResponse(
    val salt: String,
    val execution: String,
    val cookie: String,
    val captcha: String = "",
)

@Serializable
internal data class WebvpnVerifyRequest(
    val sid: String,
    val salt: String,
    val password: String,
    val execution: String,
    val cookie: String,
    val captcha: String = "",
)

@Serializable
internal data class WebvpnVerifyResponse(
    val token: String = "",
    val code: String = "",
    val msg: String = "",
)

/** BIT101 身份校验结果（领域层视图）。 */
sealed interface Bit101VerifyResult {
    /** 校验通过，学号确实属于该用户。 */
    data object Success : Bit101VerifyResult

    /** 学号或密码错误（凭据无效）。 */
    data class InvalidCredentials(val message: String) : Bit101VerifyResult

    /** 上游服务异常（网络、超时、5xx、响应格式异常等）。 */
    data class ServiceError(val message: String) : Bit101VerifyResult
}
