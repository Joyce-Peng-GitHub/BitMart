package cn.edu.bit.bitmart.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 会话不透明令牌（Opaque Token）。
 *
 * 令牌 = 32 字节 CSPRNG → Base64URL（无填充）；服务端只存 SHA-256(token)，
 * 原始令牌仅返回给客户端一次（见架构 §7.2）。
 */
object OpaqueToken {

    private const val TOKEN_BYTES = 32
    private val random = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()

    /** 生成新的随机令牌（返回给客户端的明文形式）。 */
    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }

    /** 计算令牌的 SHA-256 摘要，用于持久化与查找（避免明文落库）。 */
    fun hash(token: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
}
