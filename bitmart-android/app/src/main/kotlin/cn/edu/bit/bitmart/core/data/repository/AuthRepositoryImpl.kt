package cn.edu.bit.bitmart.core.data.repository

import cn.edu.bit.bitmart.core.data.local.TokenStore
import cn.edu.bit.bitmart.core.data.remote.AuthResponse
import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.data.remote.LoginRequest
import cn.edu.bit.bitmart.core.data.remote.RegisterRequest
import cn.edu.bit.bitmart.core.data.remote.ResetPasswordRequest
import cn.edu.bit.bitmart.core.data.remote.UserDto
import cn.edu.bit.bitmart.core.data.remote.VerifyRequest
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.map
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** AuthRepository 实现：调用 API、映射结果、持久化令牌。 */
class AuthRepositoryImpl @Inject constructor(
    private val api: BitMartApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = tokenStore.tokenFlow.map { it != null }

    override suspend fun verify(studentId: String, password: String): DomainResult<String> =
        api.verify(VerifyRequest(studentId, password)).map { it.verifyTicket }

    override suspend fun register(
        verifyTicket: String, studentId: String, password: String, nickname: String?,
    ): DomainResult<User> =
        api.register(RegisterRequest(verifyTicket, studentId, password, nickname)).persistToken()

    override suspend fun login(studentId: String, password: String): DomainResult<User> =
        api.login(LoginRequest(studentId, password)).persistToken()

    override suspend fun resetPassword(
        verifyTicket: String, studentId: String, newPassword: String,
    ): DomainResult<Unit> =
        api.resetPassword(ResetPasswordRequest(verifyTicket, studentId, newPassword))

    override suspend fun logout(): DomainResult<Unit> {
        val result = api.logout()
        // 无论后端结果如何都清本地令牌（本地登出优先）。
        tokenStore.clear()
        return if (result is DomainResult.NetworkError) DomainResult.Success(Unit) else result
    }

    override suspend fun deleteAccount(): DomainResult<Unit> {
        val result = api.deleteAccount()
        if (result is DomainResult.Success) tokenStore.clear()
        return result
    }

    /** 成功响应时保存令牌并映射为领域 User。 */
    private suspend fun DomainResult<AuthResponse>.persistToken(): DomainResult<User> {
        if (this is DomainResult.Success) tokenStore.save(data.token)
        return map { it.user.toDomain() }
    }
}

internal fun UserDto.toDomain() = User(
    id = id, studentId = studentId, nickname = nickname, displayName = displayName, role = role,
)
