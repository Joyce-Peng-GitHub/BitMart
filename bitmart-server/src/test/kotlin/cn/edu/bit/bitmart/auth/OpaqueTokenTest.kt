package cn.edu.bit.bitmart.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize

class OpaqueTokenTest : FunSpec({

    test("generate 产生 URL 安全且无填充的令牌") {
        val token = OpaqueToken.generate()
        // 32 字节 Base64URL 无填充 = 43 字符。
        token.length shouldBe 43
        token.contains('=') shouldBe false
        token.contains('+') shouldBe false
        token.contains('/') shouldBe false
    }

    test("generate 每次产生不同令牌") {
        val tokens = (1..1000).map { OpaqueToken.generate() }.toSet()
        tokens shouldHaveSize 1000
    }

    test("hash 对同一令牌确定性，长度为 32 字节（SHA-256）") {
        val token = OpaqueToken.generate()
        val h1 = OpaqueToken.hash(token)
        val h2 = OpaqueToken.hash(token)
        h1.toList() shouldBe h2.toList()
        h1.size shouldBe 32
    }

    test("hash 对不同令牌产生不同摘要") {
        val a = OpaqueToken.hash(OpaqueToken.generate())
        val b = OpaqueToken.hash(OpaqueToken.generate())
        a.toList() shouldNotBe b.toList()
    }
})
