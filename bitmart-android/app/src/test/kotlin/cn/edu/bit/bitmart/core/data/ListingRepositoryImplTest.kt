package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.repository.ListingRepositoryImpl
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingRepositoryImplTest {

    private val pageBody = """
        {"items":[{"id":5,"type":"SELL","category":"GENERAL","title":"线性代数","unitPrice":"30.00",
          "quantityTotal":2,"quantitySold":0,"tags":["教材"],"createdAt":"2026-06-02T00:00:00Z"}],
         "nextCursor":null}
    """.trimIndent()

    @Test
    fun `list maps dto to domain and sends filters as params`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api { req ->
            captured = req
            respond(pageBody, HttpStatusCode.OK, TestApiSupport.jsonHeaders())
        }
        val repo = ListingRepositoryImpl(api)

        val result = repo.list(
            ListingQuery(type = ListingType.SELL, text = "代数", minPrice = "10", includeSold = true),
        )

        assertTrue(result is DomainResult.Success)
        val page = (result as DomainResult.Success).data
        assertEquals(1, page.items.size)
        assertEquals("线性代数", page.items.first().title)
        // 校验查询参数。
        val params = captured!!.url.parameters
        assertEquals("SELL", params["type"])
        assertEquals("代数", params["q"])
        assertEquals("10", params["minPrice"])
        assertEquals("true", params["includeSold"])
    }

    @Test
    fun `detail 401 maps to failure`() = runTest {
        val body = """{"error":{"code":"UNAUTHORIZED","message":"需要登录"}}"""
        val api = TestApiSupport.fixedApi(HttpStatusCode.Unauthorized, body)
        val repo = ListingRepositoryImpl(api)

        val result = repo.detail(1)

        assertTrue(result is DomainResult.Failure)
        assertEquals(401, (result as DomainResult.Failure).httpStatus)
    }
}
