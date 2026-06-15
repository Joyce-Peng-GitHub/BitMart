package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.TokenStore
import cn.edu.bit.bitmart.core.data.remote.BitMartApi
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

/** 测试支持：构造 MockEngine 驱动的 BitMartApi，附带可控令牌。 */
object TestApiSupport {

    /** 用于测试的 no-op TokenStore；authBlock 在 401 时调用 clear()，忽略即可。 */
    private val noopTokenStore = object : TokenStore {
        override val tokenFlow: Flow<String?> = flowOf(null)
        override suspend fun current(): String? = null
        override suspend fun save(token: String) {}
        override suspend fun clear() {}
    }

    fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    fun api(
        token: String? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): BitMartApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }) }
            expectSuccess = false
        }
        return BitMartApi(client, "http://test", { token }, noopTokenStore)
    }

    /** 始终返回固定状态与体的便捷 API。 */
    fun fixedApi(status: HttpStatusCode, body: String, token: String? = null): BitMartApi =
        api(token) { respond(body, status, jsonHeaders()) }
}
