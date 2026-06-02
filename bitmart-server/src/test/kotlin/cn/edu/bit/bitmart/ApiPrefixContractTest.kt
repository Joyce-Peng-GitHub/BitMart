package cn.edu.bit.bitmart

import cn.edu.bit.bitmart.auth.AuthTestSupport
import cn.edu.bit.bitmart.auth.VerifyRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/**
 * 路由前缀契约测试：业务 API 必须挂在 /api/v1 下（架构 §6），与 Android 客户端一致。
 * 防止再次出现"客户端带前缀、服务端不带"导致的 404（曾导致注册 404）。
 */
class ApiPrefixContractTest : FunSpec({

    fun app(block: suspend (io.ktor.client.HttpClient) -> Unit) = testApplication {
        application { configureApp(AuthTestSupport.components()) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        block(client)
    }

    test("业务 API 在 /api/v1 前缀下可达") {
        app { client ->
            // verify 端点存在即可（凭据是否有效不影响路由是否命中），不应是 404。
            val resp = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest("112020" + (100000..999999).random(), "pw"))
            }
            (resp.status != HttpStatusCode.NotFound) shouldBe true
        }
    }

    test("不带前缀的旧路径返回 404") {
        app { client ->
            client.post("/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest("1120201234", "pw"))
            }.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("健康检查在根路径（不带前缀）") {
        app { client ->
            client.get("/health").status shouldBe HttpStatusCode.OK
        }
    }
})
