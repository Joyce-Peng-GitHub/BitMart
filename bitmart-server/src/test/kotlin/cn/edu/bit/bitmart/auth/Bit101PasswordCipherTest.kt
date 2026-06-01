package cn.edu.bit.bitmart.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Base64

class Bit101PasswordCipherTest : FunSpec({

    // 由 openssl 与 python cryptography 两个独立工具交叉验证生成的已知答案向量，
    // 确保与 BIT101 前端（CryptoJS AES-ECB-PKCS7）字节级一致。
    test("encrypt 匹配独立生成的已知答案向量") {
        val salt = "MDEyMzQ1Njc4OWFiY2RlZg=="   // Base64("0123456789abcdef")
        val plaintext = "MySecret123"
        Bit101PasswordCipher.encrypt(plaintext, salt) shouldBe "j9dlTvA7mnYVQK7mOXba1Q=="
    }

    test("相同输入确定性输出（ECB 无 IV）") {
        val salt = Base64.getEncoder().encodeToString("0123456789abcdef".toByteArray())
        val a = Bit101PasswordCipher.encrypt("hello", salt)
        val b = Bit101PasswordCipher.encrypt("hello", salt)
        a shouldBe b
    }

    test("支持 256 位密钥（32 字节 salt）") {
        val key = "0123456789abcdef0123456789abcdef"   // 32 bytes → AES-256
        val salt = Base64.getEncoder().encodeToString(key.toByteArray())
        // 不抛异常即可（依赖 JDK 默认已解除强度限制；JDK 21 默认无限制）。
        Bit101PasswordCipher.encrypt("pw", salt).isNotEmpty() shouldBe true
    }
})
