package cn.edu.bit.bitmart.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class PasswordHasherTest : FunSpec({

    // 使用较小参数加速测试，正确性与参数大小无关。
    val hasher = PasswordHasher(memoryKb = 1024, iterations = 1, parallelism = 1)

    test("hash 产生 argon2id 编码字串") {
        val hash = hasher.hash("Secret123")
        hash shouldStartWith "\$argon2id\$"
    }

    test("verify 对正确密码返回 true") {
        val hash = hasher.hash("Secret123")
        hasher.verify(hash, "Secret123") shouldBe true
    }

    test("verify 对错误密码返回 false") {
        val hash = hasher.hash("Secret123")
        hasher.verify(hash, "WrongPass") shouldBe false
    }

    test("相同密码因随机盐产生不同哈希") {
        val a = hasher.hash("Secret123")
        val b = hasher.hash("Secret123")
        (a == b) shouldBe false
        hasher.verify(a, "Secret123") shouldBe true
        hasher.verify(b, "Secret123") shouldBe true
    }
})
