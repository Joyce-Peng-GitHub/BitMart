package cn.edu.bit.bitmart.auth

import de.mkammerer.argon2.Argon2Factory

/**
 * BitMart 账号密码哈希：Argon2id（OWASP 推荐）。
 *
 * 参数（内存 KiB / 迭代次数 / 并行度）来自配置，版本号随哈希字串保存，便于未来调参而不破坏旧哈希。
 */
class PasswordHasher(
    private val memoryKb: Int,
    private val iterations: Int,
    private val parallelism: Int,
) {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    /** 生成密码哈希。内部使用 char[] 并在结束后由库清零，降低明文驻留内存的风险。 */
    fun hash(plainPassword: String): String {
        val chars = plainPassword.toCharArray()
        return try {
            argon2.hash(iterations, memoryKb, parallelism, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }

    /** 校验明文密码是否匹配给定哈希。 */
    fun verify(hash: String, plainPassword: String): Boolean {
        val chars = plainPassword.toCharArray()
        return try {
            argon2.verify(hash, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }
}
