package cn.edu.bit.bitmart

import cn.edu.bit.bitmart.auth.AuthTestSupport
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

/** 冒烟测试：验证应用装配与 testApplication 测试链路可用。 */
class HealthRouteTest : FunSpec({
    test("GET /health 返回 200 与 ok 状态") {
        testApplication {
            application { module(AuthTestSupport.components()) }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"ok"}"""
        }
    }
})
