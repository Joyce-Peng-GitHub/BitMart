package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.repository.ListingRepositoryImpl
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import io.ktor.client.request.HttpRequestData
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingRepositoryBatchTest {

    @Test
    fun `publishBatch maps drafts to items with book and imageKeys`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond("""{"ids":[10,11]}""", HttpStatusCode.OK, TestApiSupport.jsonHeaders())
        }
        val repo = ListingRepositoryImpl(api)

        val drafts = listOf(
            PublishDraft(
                type = ListingType.SELL,
                category = ListingCategory.BOOK,
                title = "深入理解计算机系统",
                description = "第3版，九成新",
                unitPrice = "50",
                quantityTotal = 1,
                pickupLocation = "图书馆",
                contacts = listOf(Contact("WECHAT", "wxid_abc")),
                tags = listOf("教材", "计算机"),
                book = BookInfo("9787111544937", "深入理解计算机系统", "Bryant", "机械工业", "第3版"),
                imageKeys = listOf("2026/06/02/uuid1.jpg"),
            ),
            PublishDraft(
                type = ListingType.BUY,
                category = ListingCategory.GENERAL,
                title = "求购二手台灯",
                description = "",
                unitPrice = null,
                quantityTotal = 1,
                pickupLocation = null,
                contacts = listOf(Contact("", "123456")),
                tags = emptyList(),
                imageKeys = emptyList(),
            ),
        )

        val result = repo.publishBatch(drafts)

        assertTrue(result is DomainResult.Success)
        assertEquals(listOf(10L, 11L), (result as DomainResult.Success).data)

        // 校验请求路径与 body 结构。
        val req = captured!!
        assertTrue(req.url.encodedPath.endsWith("/listings/batch"))
        assertEquals("Bearer tok", req.headers["Authorization"])
        val bodyText = (req.body as io.ktor.http.content.TextContent).text
        // 验证包含两条 item 且第一条有 book 和 imageKeys。
        assertTrue(bodyText.contains("\"items\""))
        assertTrue(bodyText.contains("9787111544937"))
        assertTrue(bodyText.contains("2026/06/02/uuid1.jpg"))
    }

    @Test
    fun `publishBatch sends absolute expiresAt when set`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond("""{"ids":[1]}""", HttpStatusCode.OK, TestApiSupport.jsonHeaders())
        }
        val repo = ListingRepositoryImpl(api)
        val iso = "2026-07-01T00:00+08:00"

        repo.publishBatch(listOf(
            PublishDraft(
                type = ListingType.SELL,
                title = "按日期过期",
                contacts = listOf(Contact("", "x")),
                expiresAtIso = iso,
            ),
        ))

        val bodyText = (captured!!.body as io.ktor.http.content.TextContent).text
        assertTrue(bodyText.contains("\"expiresAt\""))
        assertTrue(bodyText.contains(iso))
    }

    @Test
    fun `publishBatch 400 failure surfaces combined message`() = runTest {
        val errorBody = """{"error":{"code":"VALIDATION_FAILED","message":"第1项标题不能为空；第2项联系方式无效"}}"""
        val api = TestApiSupport.fixedApi(HttpStatusCode.BadRequest, errorBody, token = "tok")
        val repo = ListingRepositoryImpl(api)

        val result = repo.publishBatch(listOf(
            PublishDraft(
                type = ListingType.SELL,
                category = ListingCategory.GENERAL,
                title = "",
                contacts = listOf(Contact("", "x")),
            ),
        ))

        assertTrue(result is DomainResult.Failure)
        assertEquals(400, (result as DomainResult.Failure).httpStatus)
        assertTrue(result.message.contains("第1项") || result.message.contains("标题"))
    }

    @Test
    fun `uploadImage returns blobKey`() = runTest {
        var captured: HttpRequestData? = null
        val api = TestApiSupport.api(token = "tok") { req ->
            captured = req
            respond(
                """{"blobKey":"2026/06/02/uuid-x.jpg","url":"/static/2026/06/02/uuid-x.jpg","contentType":"image/jpeg"}""",
                HttpStatusCode.OK,
                TestApiSupport.jsonHeaders(),
            )
        }
        val repo = ListingRepositoryImpl(api)

        val result = repo.uploadImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), "test.jpg")

        assertTrue(result is DomainResult.Success)
        assertEquals("2026/06/02/uuid-x.jpg", (result as DomainResult.Success).data)
        // 校验请求。
        assertTrue(captured!!.url.encodedPath.endsWith("/uploads/images"))
        assertEquals("Bearer tok", captured!!.headers["Authorization"])
    }

    @Test
    fun `lookupBook 200 maps fields to BookInfo`() = runTest {
        val api = TestApiSupport.api(token = "tok") { _ ->
            respond(
                """{"isbn":"9787111544937","title":"深入理解计算机系统","author":"Bryant","publisher":"机械工业","edition":"第3版"}""",
                HttpStatusCode.OK,
                TestApiSupport.jsonHeaders(),
            )
        }
        val repo = ListingRepositoryImpl(api)

        val result = repo.lookupBook("9787111544937")

        assertTrue(result is DomainResult.Success)
        val book = (result as DomainResult.Success).data!!
        assertEquals("深入理解计算机系统", book.title)
        assertEquals("Bryant", book.authors)
        assertEquals("机械工业", book.publisher)
        assertEquals("第3版", book.edition)
    }

    @Test
    fun `lookupBook 404 maps to Success(null)`() = runTest {
        val api = TestApiSupport.api(token = "tok") { _ ->
            respond("""{"error":{"code":"NOT_FOUND","message":"未找到"}}""", HttpStatusCode.NotFound, TestApiSupport.jsonHeaders())
        }
        val repo = ListingRepositoryImpl(api)

        val result = repo.lookupBook("1234567890123")

        assertTrue(result is DomainResult.Success)
        assertNull((result as DomainResult.Success).data)
    }
}
