package cn.edu.bit.bitmart.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * AuthService 占位哈希（登录时序抹平）的单元测试。
 *
 * H1：用户不存在时用于「抹平时序」的占位哈希若参数弱于真实密码哈希，反而会让
 * 不存在的学号校验更快，可据响应时间枚举已注册学号。修复后占位哈希必须与真实
 * 密码哈希使用相同的 argon2 参数，故此处用一组刻意区别于任何历史硬编码常量的参数
 * （2048/2/1，既非旧常量 1024/1/1，也非生产 65536/3/1）来锁定该不变量。
 */
class AuthServiceTest : FunSpec({

    // argon2 编码格式：$argon2id$v=19$m=<>,t=<>,p=<>$<salt>$<hash>，取参数段。
    // argon2-jvm 始终输出 v= 段，故参数段固定位于 split 后索引 3。
    fun argon2Params(encoded: String) = encoded.split("$")[3]

    val hasher = PasswordHasher(memoryKb = 2048, iterations = 2, parallelism = 1)

    test("占位哈希与真实密码哈希使用相同的 argon2 参数") {
        val placeholder = AuthService.placeholderHash(hasher)
        val real = hasher.hash("Secret123")
        argon2Params(placeholder) shouldBe argon2Params(real) // 均为 m=2048,t=2,p=1
    }

    test("任何密码都不会匹配占位哈希") {
        val placeholder = AuthService.placeholderHash(hasher)
        hasher.verify(placeholder, "Secret123") shouldBe false
        hasher.verify(placeholder, "") shouldBe false
    }

    test("每次生成的占位哈希互不相同（随机盐 + 随机明文）") {
        val a = AuthService.placeholderHash(hasher)
        val b = AuthService.placeholderHash(hasher)
        (a == b) shouldBe false
    }
})
