package cn.edu.bit.bitmart.external

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 外部客户端测试支持：用 MockEngine 构造可断言、可脚本化响应的 HttpClient。 */
object MockHttpSupport {

    /** 记录收到的请求，便于断言 URL/参数/请求体。 */
    class Recorder {
        val requests = mutableListOf<HttpRequestData>()
    }

    /**
     * 构造一个用给定 handler 应答的 HttpClient。
     * handler 以 MockRequestHandleScope 为接收者（可直接调用 respond/respondError），
     * 接收每个请求并返回响应；同时把请求记录到 recorder。
     */
    fun client(
        recorder: Recorder = Recorder(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        val engine = MockEngine { request ->
            recorder.requests += request
            handler(request)
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            expectSuccess = false
        }
    }

    /** 便捷：始终返回给定 JSON 体与状态码。 */
    fun jsonClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
        recorder: Recorder = Recorder(),
    ): Pair<HttpClient, Recorder> {
        val c = client(recorder) {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return c to recorder
    }
}
