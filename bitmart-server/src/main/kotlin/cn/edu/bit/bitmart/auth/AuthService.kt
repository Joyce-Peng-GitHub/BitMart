package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.config.SessionConfig
import cn.edu.bit.bitmart.db.Listings
import cn.edu.bit.bitmart.domain.PasswordPolicy
import cn.edu.bit.bitmart.domain.UserStatus
import cn.edu.bit.bitmart.external.Bit101Client
import cn.edu.bit.bitmart.external.Bit101VerifyResult
import cn.edu.bit.bitmart.user.UserRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

/**
 * 认证服务：编排 BIT101 校验、注册、登录、重置密码、登出、注销。
 *
 * - 统一身份认证密码仅经 [Bit101Client] 直连校验，不落盘、不入日志。
 * - 会话采用不透明令牌，仅存 SHA-256；改密/注销时吊销全部会话（架构 §7）。
 */
class AuthService(
    private val database: Database,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val bit101Client: Bit101Client,
    private val verifyTicketStore: VerifyTicketStore,
    private val passwordHasher: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val sessionConfig: SessionConfig,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)
    private val audit = LoggerFactory.getLogger("bitmart.audit")

    /** 通过 BIT101 校验学号与统一身份认证密码，成功则签发一次性 verifyTicket。 */
    suspend fun verify(studentId: String, password: String): VerifyResult =
        when (val r = bit101Client.verify(studentId, password)) {
            is Bit101VerifyResult.Success -> {
                val ticket = verifyTicketStore.issue(studentId)
                VerifyResult.Success(ticket)
            }
            is Bit101VerifyResult.InvalidCredentials -> VerifyResult.InvalidCredentials
            is Bit101VerifyResult.ServiceError -> VerifyResult.ServiceUnavailable(r.message)
        }

    /** 用 verifyTicket 完成注册，设置 BitMart 密码并签发会话。 */
    fun register(
        verifyTicket: String,
        studentId: String,
        password: String,
        nickname: String?,
        userAgent: String?,
    ): RegisterResult {
        val policy = passwordPolicy.validate(password)
        if (!policy.isValid) {
            return RegisterResult.PasswordPolicyViolation(policy.errors.map { it.message })
        }
        if (!verifyTicketStore.consume(verifyTicket, studentId)) {
            return RegisterResult.InvalidTicket
        }
        val hash = passwordHasher.hash(password)
        return transaction(database) {
            if (userRepository.findByStudentId(studentId) != null) {
                RegisterResult.StudentAlreadyRegistered
            } else {
                val userId = userRepository.create(studentId, hash, nickname)
                val token = issueSession(userId, userAgent)
                val user = userRepository.findById(userId)!!
                audit.info("register success studentId={} userId={}", studentId, userId)
                RegisterResult.Success(token, user)
            }
        }
    }

    /** 用学号 + BitMart 密码登录。 */
    fun login(studentId: String, password: String, userAgent: String?): LoginResult =
        transaction(database) {
            val hash = userRepository.findPasswordHash(studentId)
            val user = userRepository.findByStudentId(studentId)
            // 即使用户不存在也执行一次哈希校验以降低时序侧信道（用固定假哈希）。
            val matches = if (hash != null) passwordHasher.verify(hash, password) else {
                passwordHasher.verify(DUMMY_HASH, password); false
            }
            when {
                hash == null || user == null || !matches -> {
                    audit.info("login failed studentId={}", studentId)
                    LoginResult.InvalidCredentials
                }
                user.status == UserStatus.BANNED -> LoginResult.Banned
                else -> {
                    val token = issueSession(user.id, userAgent)
                    audit.info("login success userId={}", user.id)
                    LoginResult.Success(token, user)
                }
            }
        }

    /** 用 verifyTicket 重置密码：更新哈希并吊销全部会话。 */
    fun resetPassword(verifyTicket: String, studentId: String, newPassword: String): ResetPasswordResult {
        val policy = passwordPolicy.validate(newPassword)
        if (!policy.isValid) {
            return ResetPasswordResult.PasswordPolicyViolation(policy.errors.map { it.message })
        }
        if (!verifyTicketStore.consume(verifyTicket, studentId)) {
            return ResetPasswordResult.InvalidTicket
        }
        val hash = passwordHasher.hash(newPassword)
        return transaction(database) {
            val user = userRepository.findByStudentId(studentId)
                ?: return@transaction ResetPasswordResult.UserNotFound
            userRepository.updatePasswordHash(studentId, hash)
            sessionRepository.revokeAllForUser(user.id)
            audit.info("password reset userId={}", user.id)
            ResetPasswordResult.Success
        }
    }

    /** 登出当前会话。 */
    fun logout(token: String): Boolean = transaction(database) {
        sessionRepository.revoke(OpaqueToken.hash(token)) > 0
    }

    /** 全部登出。 */
    fun logoutAll(userId: Long): Int = transaction(database) {
        sessionRepository.revokeAllForUser(userId)
    }

    /** 注销账号：软删用户 + 级联软删其 listing + 吊销全部会话（架构 §9）。 */
    fun deleteAccount(userId: Long): Boolean = transaction(database) {
        val affected = userRepository.softDelete(userId)
        if (affected == 0) return@transaction false
        // 级联软删该用户所有未删除的 listing。
        Listings.update({ (Listings.userId eq userId) and Listings.deletedAt.isNull() }) {
            it[deletedAt] = OffsetDateTime.now()
            it[updatedAt] = OffsetDateTime.now()
        }
        sessionRepository.revokeAllForUser(userId)
        audit.info("account deleted userId={}", userId)
        true
    }

    /** 生成会话令牌并入库，返回明文令牌。 */
    private fun issueSession(userId: Long, userAgent: String?): String {
        val token = OpaqueToken.generate()
        val expiresAt = OffsetDateTime.now().plusDays(sessionConfig.ttlDays)
        sessionRepository.create(OpaqueToken.hash(token), userId, expiresAt, userAgent)
        return token
    }

    companion object {
        // 用户不存在时校验的占位哈希（任何密码都不会匹配），抹平时序差异。
        private const val DUMMY_HASH =
            "\$argon2id\$v=19\$m=1024,t=1,p=1\$YWJjZGVmZ2hpamtsbW5vcA\$Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6cXV4MDAwMA"
    }
}
