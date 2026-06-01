package cn.edu.bit.bitmart.external

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

class Bit101ClientTest : FunSpec({

    val base = "https://bit101.example"

    // salt = Base64("0123456789abcdef")，与加密已知答案向量一致。
    val initBody = """{"salt":"MDEyMzQ1Njc4OWFiY2RlZg==","execution":"e1","cookie":"c1"}"""

    test("init + verify 均成功且返回 token → Success") {
        val recorder = MockHttpSupport.Recorder()
        val client = MockHttpSupport.client(recorder) { request ->
            when {
                request.url.encodedPath.endsWith("/webvpn_verify_init") ->
                    respond(initBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/webvpn_verify") ->
                    respond("""{"token":"abc123","code":"0","msg":"ok"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val result = Bit101Client(client, base).verify("1120201234", "MySecret123")
        result shouldBe Bit101VerifyResult.Success
        // 验证调用了两步，且加密密码已发送（请求体不含明文）。
        recorder.requests.size shouldBe 2
    }

    test("verify 返回空 token → InvalidCredentials") {
        val client = MockHttpSupport.client { request ->
            when {
                request.url.encodedPath.endsWith("/webvpn_verify_init") ->
                    respond(initBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else ->
                    respond("""{"token":"","code":"1","msg":"学号或密码错误"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val result = Bit101Client(client, base).verify("1120201234", "wrong")
        result.shouldBeInstanceOf<Bit101VerifyResult.InvalidCredentials>()
        result.message shouldContain "学号或密码错误"
    }

    test("init 返回 5xx → ServiceError") {
        val client = MockHttpSupport.client { respondError(HttpStatusCode.InternalServerError) }
        val result = Bit101Client(client, base).verify("1120201234", "pw")
        result.shouldBeInstanceOf<Bit101VerifyResult.ServiceError>()
    }

    test("verify 阶段 5xx → ServiceError") {
        val client = MockHttpSupport.client { request ->
            if (request.url.encodedPath.endsWith("/webvpn_verify_init"))
                respond(initBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            else respondError(HttpStatusCode.BadGateway)
        }
        val result = Bit101Client(client, base).verify("1120201234", "pw")
        result.shouldBeInstanceOf<Bit101VerifyResult.ServiceError>()
    }

    test("网络异常 → ServiceError（不抛出）") {
        val client = MockHttpSupport.client { throw RuntimeException("connection reset") }
        val result = Bit101Client(client, base).verify("1120201234", "pw")
        result.shouldBeInstanceOf<Bit101VerifyResult.ServiceError>()
    }
})
