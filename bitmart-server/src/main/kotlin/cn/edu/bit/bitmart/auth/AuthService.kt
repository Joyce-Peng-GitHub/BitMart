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

    // 占位哈希：启动时以与真实密码完全相同的 argon2 参数对一个随机串哈希一次。
    // 登录时若学号不存在，用它做一次等价开销的校验，使「用户不存在」与「密码错误」两条失败
    // 分支耗时一致，抹平据响应时间枚举已注册学号的时序侧信道。任何密码都不会匹配它。
    private val dummyPasswordHash: String = placeholderHash(passwordHasher)

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
            return RegisterResult.PasswordPolicyViolation(policy.errors)
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
            // 即使用户不存在也执行一次等价开销的哈希校验以抹平时序侧信道（用启动期生成的占位哈希）。
            val matches = if (hash != null) passwordHasher.verify(hash, password) else {
                passwordHasher.verify(dummyPasswordHash, password); false
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
            return ResetPasswordResult.PasswordPolicyViolation(policy.errors)
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
        /**
         * 以传入 [hasher] 的配置参数对一个随机串哈希一次，作为登录时序抹平用的占位哈希。
         * 关键在于其 argon2 参数与真实密码哈希一致，使「用户不存在」分支的校验开销不再偏快。
         * 随机明文不保留，任何密码都不会匹配返回值。
         */
        internal fun placeholderHash(hasher: PasswordHasher): String = hasher.hash(OpaqueToken.generate())
    }
}
