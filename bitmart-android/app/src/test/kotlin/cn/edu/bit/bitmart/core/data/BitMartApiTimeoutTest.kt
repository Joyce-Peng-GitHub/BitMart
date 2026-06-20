package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.TokenStore
import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.domain.DomainResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class BitMartApiTimeoutTest {
    private val noopTokenStore = object : TokenStore {
        override val tokenFlow: Flow<String?> = flowOf(null)
        override suspend fun current(): String? = null
        override suspend fun save(token: String) {}
        override suspend fun clear() {}
    }

    @Test
    fun `request exceeding timeout maps to NetworkError instead of hanging`() = runBlocking {
        val client = HttpClient(MockEngine { _ ->
            delay(2000) // 模拟上游卡住，远超下面 50ms 的请求超时
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 50 }
            expectSuccess = false
        }
        val api = BitMartApi(client, "http://test", { null }, noopTokenStore)
        val result = api.listListings(emptyMap())
        assertTrue("expected NetworkError but was $result", result is DomainResult.NetworkError)
    }
}
