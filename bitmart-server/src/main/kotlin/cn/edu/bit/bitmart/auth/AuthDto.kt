package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.domain.User
import kotlinx.serialization.Serializable

/** /auth 端点的请求/响应 DTO。 */

@Serializable
data class VerifyRequest(val studentId: String, val password: String)

@Serializable
data class VerifyResponse(val verifyTicket: String)

@Serializable
data class RegisterRequest(
    val verifyTicket: String,
    val studentId: String,
    val password: String,
    val nickname: String? = null,
)

@Serializable
data class LoginRequest(val studentId: String, val password: String)

@Serializable
data class ResetPasswordRequest(
    val verifyTicket: String,
    val studentId: String,
    val newPassword: String,
)

/** 鉴权成功响应：令牌 + 用户摘要。 */
@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class UserDto(
    val id: Long,
    val studentId: String,
    val nickname: String? = null,
    val displayName: String,
    val role: String,
) {
    companion object {
        fun from(user: User) = UserDto(
            id = user.id,
            studentId = user.studentId,
            nickname = user.nickname,
            displayName = user.displayName,
            role = user.role.name,
        )
    }
}
