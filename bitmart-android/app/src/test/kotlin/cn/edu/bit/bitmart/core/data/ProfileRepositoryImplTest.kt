package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.repository.ProfileRepositoryImpl
import cn.edu.bit.bitmart.core.domain.DomainResult
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryImplTest {

    private val userBody =
        """{"id":7,"studentId":"1120201234","nickname":"小明","displayName":"小明","role":"NORMAL"}"""

    @Test
    fun `updateNickname patches me and maps user`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond(userBody, HttpStatusCode.OK, TestApiSupport.jsonHeaders())
        }
        val repo = ProfileRepositoryImpl(api)

        val result = repo.updateNickname("小明")

        assertTrue(result is DomainResult.Success)
        assertEquals("小明", (result as DomainResult.Success).data.nickname)
        val req = requireNotNull(captured)
        assertEquals(HttpMethod.Patch, req.method)
        assertTrue(req.url.encodedPath.endsWith("/me"))
    }

    @Test
    fun `notifications maps page and echoes cursor as param`() = runTest {
        val pageBody = """
            {"items":[
              {"id":1,"category":0,"title":"系统公告","body":"欢迎使用","read":false,
               "createdAt":"2026-06-02T00:00:00Z","isAnnouncement":true},
              {"id":2,"category":1,"title":"过期提醒","body":"你的商品即将过期","read":true,
               "createdAt":"2026-06-01T00:00:00Z","isAnnouncement":false}
            ],"nextCursor":"2026-06-01T00:00:00Z|2"}
        """.trimIndent()
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond(pageBody, HttpStatusCode.OK, TestApiSupport.jsonHeaders())
        }
        val repo = ProfileRepositoryImpl(api)

        val result = repo.notifications(cursor = "abc|3", limit = 20)

        assertTrue(result is DomainResult.Success)
        val page = (result as DomainResult.Success).data
        assertEquals(2, page.items.size)
        assertTrue(page.items[0].isAnnouncement)
        assertFalse(page.items[1].isAnnouncement)
        assertEquals("2026-06-01T00:00:00Z|2", page.nextCursor)
        // 校验游标与 limit 作为查询参数透传。
        val req = requireNotNull(captured)
        assertEquals("abc|3", req.url.parameters["cursor"])
        assertEquals("20", req.url.parameters["limit"])
    }

    @Test
    fun `notifications omits cursor param when null`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond("""{"items":[],"nextCursor":null}""", HttpStatusCode.OK, TestApiSupport.jsonHeaders())
        }
        val repo = ProfileRepositoryImpl(api)

        repo.notifications(cursor = null, limit = 20)

        assertNull(requireNotNull(captured).url.parameters["cursor"])
    }

    @Test
    fun `markNotificationRead posts to read endpoint`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond("", HttpStatusCode.OK)
        }
        val repo = ProfileRepositoryImpl(api)

        val result = repo.markNotificationRead(42)

        assertTrue(result is DomainResult.Success)
        val req = requireNotNull(captured)
        assertEquals(HttpMethod.Post, req.method)
        assertTrue(req.url.encodedPath.endsWith("/me/notifications/42/read"))
    }

    @Test
    fun `unreadNotificationCount gets count endpoint and maps to int`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond("""{"count":7}""", HttpStatusCode.OK, TestApiSupport.jsonHeaders())
        }
        val repo = ProfileRepositoryImpl(api)

        val result = repo.unreadNotificationCount()

        assertTrue(result is DomainResult.Success)
        assertEquals(7, (result as DomainResult.Success).data)
        val req = requireNotNull(captured)
        assertEquals(HttpMethod.Get, req.method)
        assertTrue(req.url.encodedPath.endsWith("/me/notifications/unread-count"))
    }
}
