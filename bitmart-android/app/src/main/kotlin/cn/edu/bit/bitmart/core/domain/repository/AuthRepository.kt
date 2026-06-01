package cn.edu.bit.bitmart.core.domain.repository

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User
import kotlinx.coroutines.flow.Flow

/** 认证仓储接口（domain 层，纯 Kotlin，不依赖框架）。 */
interface AuthRepository {
    /** BIT101 校验，成功返回一次性 verifyTicket。 */
    suspend fun verify(studentId: String, password: String): DomainResult<String>

    /** 用 verifyTicket 注册，成功后令牌已持久化。 */
    suspend fun register(verifyTicket: String, studentId: String, password: String, nickname: String?): DomainResult<User>

    /** 登录，成功后令牌已持久化。 */
    suspend fun login(studentId: String, password: String): DomainResult<User>

    /** 用 verifyTicket 重置密码。 */
    suspend fun resetPassword(verifyTicket: String, studentId: String, newPassword: String): DomainResult<Unit>

    /** 登出当前会话并清除本地令牌。 */
    suspend fun logout(): DomainResult<Unit>

    /** 注销账号。 */
    suspend fun deleteAccount(): DomainResult<Unit>

    /** 当前是否已登录（基于本地令牌存在性）。 */
    val isLoggedIn: Flow<Boolean>
}
