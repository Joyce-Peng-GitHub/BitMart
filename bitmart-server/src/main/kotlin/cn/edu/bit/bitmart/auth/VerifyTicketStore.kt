package cn.edu.bit.bitmart.auth

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import java.util.Base64
import java.security.SecureRandom

/**
 * 身份验证票（verifyTicket）存储。
 *
 * BIT101 校验通过后签发，仅用于紧接着的注册或重置密码（见架构 §7.1）：
 * - 进程内 Caffeine 缓存，TTL 15 分钟（配置项）。
 * - 单次使用：消费成功后立即失效。
 * - 绑定 studentId：消费时比对调用方传入的 studentId，防止票据被挪用到其他学号。
 *
 * 单实例部署足够；未来多实例可替换为 Redis 实现同一接口。
 */
class VerifyTicketStore(ttlMinutes: Long) {

    private val random = SecureRandom()
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
        .build<String, String>()   // ticket -> studentId

    /** 为已通过校验的 studentId 签发一张新票，返回票据字符串。 */
    fun issue(studentId: String): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        cache.put(ticket, studentId)
        return ticket
    }

    /**
     * 消费票据：仅当票据存在且其绑定的 studentId 与传入值一致时成功。
     * 成功后票据立即失效（单次使用）。返回是否消费成功。
     */
    fun consume(ticket: String, studentId: String): Boolean {
        val bound = cache.getIfPresent(ticket) ?: return false
        if (bound != studentId) return false
        cache.invalidate(ticket)
        return true
    }
}
