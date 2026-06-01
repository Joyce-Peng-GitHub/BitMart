package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.domain.UserRole
import cn.edu.bit.bitmart.user.UserRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime

/** 鉴权后注入的用户主体。 */
data class UserPrincipal(
    val userId: Long,
    val role: UserRole,
    val token: String,
)

/**
 * 校验不透明令牌：查会话表，要求未吊销且未过期；命中则刷新 last_used_at 并返回主体。
 * 抽出为纯函数便于单测，不依赖 Ktor。
 */
class TokenAuthenticator(
    private val database: Database,
    private val sessionRepository: SessionRepository,
    private val userRepository: cn.edu.bit.bitmart.user.UserRepository,
) {
    fun authenticate(token: String): UserPrincipal? = transaction(database) {
        val hash = OpaqueToken.hash(token)
        val session = sessionRepository.findByTokenHash(hash) ?: return@transaction null
        if (session.revoked) return@transaction null
        if (session.expiresAt.isBefore(OffsetDateTime.now())) return@transaction null
        val user = userRepository.findById(session.userId) ?: return@transaction null
        sessionRepository.touch(hash)
        UserPrincipal(userId = user.id, role = user.role, token = token)
    }
}
