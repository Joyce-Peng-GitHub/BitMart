package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.db.Sessions
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/** 会话的查找结果（仅鉴权所需字段）。 */
data class SessionInfo(
    val userId: Long,
    val expiresAt: OffsetDateTime,
    val revoked: Boolean,
)

/**
 * 会话仓储。仅持久化 SHA-256(token)，明文令牌只返回客户端一次（架构 §7.2）。
 * 须在 `transaction { }` 内调用。
 */
class SessionRepository {

    fun create(
        tokenHash: ByteArray,
        userId: Long,
        expiresAt: OffsetDateTime,
        userAgent: String?,
    ) {
        val now = OffsetDateTime.now()
        Sessions.insert {
            it[Sessions.tokenHash] = tokenHash
            it[Sessions.userId] = userId
            it[createdAt] = now
            it[lastUsedAt] = now
            it[Sessions.expiresAt] = expiresAt
            it[Sessions.userAgent] = userAgent
            it[revoked] = false
        }
    }

    /** 按令牌哈希查找会话。 */
    fun findByTokenHash(tokenHash: ByteArray): SessionInfo? =
        Sessions.selectAll()
            .where { Sessions.tokenHash eq tokenHash }
            .singleOrNull()
            ?.let {
                SessionInfo(
                    userId = it[Sessions.userId],
                    expiresAt = it[Sessions.expiresAt],
                    revoked = it[Sessions.revoked],
                )
            }

    /** 刷新最近使用时间。 */
    fun touch(tokenHash: ByteArray): Int =
        Sessions.update({ Sessions.tokenHash eq tokenHash }) {
            it[lastUsedAt] = OffsetDateTime.now()
        }

    /** 吊销单个会话（登出）。 */
    fun revoke(tokenHash: ByteArray): Int =
        Sessions.update({ Sessions.tokenHash eq tokenHash }) {
            it[revoked] = true
        }

    /** 吊销某用户的全部会话（全部登出 / 改密 / 封禁 / 注销）。 */
    fun revokeAllForUser(userId: Long): Int =
        Sessions.update({ (Sessions.userId eq userId) and (Sessions.revoked eq false) }) {
            it[revoked] = true
        }
}
