package cn.edu.bit.bitmart.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VerifyTicketStoreTest : FunSpec({

    test("issue 后可用正确 studentId 消费一次") {
        val store = VerifyTicketStore(ttlMinutes = 15)
        val ticket = store.issue("1120201234")
        store.consume(ticket, "1120201234") shouldBe true
    }

    test("消费后立即失效，第二次消费失败") {
        val store = VerifyTicketStore(ttlMinutes = 15)
        val ticket = store.issue("1120201234")
        store.consume(ticket, "1120201234") shouldBe true
        store.consume(ticket, "1120201234") shouldBe false
    }

    test("studentId 不匹配时拒绝消费，且不消耗票据") {
        val store = VerifyTicketStore(ttlMinutes = 15)
        val ticket = store.issue("1120201234")
        store.consume(ticket, "9999999999") shouldBe false
        // 原 studentId 仍可消费，证明上一步未误消费。
        store.consume(ticket, "1120201234") shouldBe true
    }

    test("未知票据消费失败") {
        val store = VerifyTicketStore(ttlMinutes = 15)
        store.consume("nonexistent-ticket", "1120201234") shouldBe false
    }

    test("每张票据互不相同") {
        val store = VerifyTicketStore(ttlMinutes = 15)
        store.issue("a") shouldNotBe store.issue("a")
    }
})
