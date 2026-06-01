package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.domain.User

/** 认证相关操作的结果类型。 */

/** BIT101 身份校验结果（/auth/bit101/verify）。 */
sealed interface VerifyResult {
    /** 校验通过，签发一次性 verifyTicket（供注册/重置密码）。 */
    data class Success(val verifyTicket: String) : VerifyResult
    data object InvalidCredentials : VerifyResult
    data class ServiceUnavailable(val message: String) : VerifyResult
}

/** 注册结果。 */
sealed interface RegisterResult {
    data class Success(val token: String, val user: User) : RegisterResult
    data object InvalidTicket : RegisterResult
    data object StudentAlreadyRegistered : RegisterResult
    data class PasswordPolicyViolation(val messages: List<String>) : RegisterResult
}

/** 登录结果。 */
sealed interface LoginResult {
    data class Success(val token: String, val user: User) : LoginResult
    data object InvalidCredentials : LoginResult
    data object Banned : LoginResult
}

/** 重置密码结果。 */
sealed interface ResetPasswordResult {
    data object Success : ResetPasswordResult
    data object InvalidTicket : ResetPasswordResult
    data object UserNotFound : ResetPasswordResult
    data class PasswordPolicyViolation(val messages: List<String>) : ResetPasswordResult
}
