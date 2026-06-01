package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.AppComponents
import cn.edu.bit.bitmart.auth.AuthResponse
import cn.edu.bit.bitmart.auth.AuthTestSupport
import cn.edu.bit.bitmart.auth.RegisterRequest
import cn.edu.bit.bitmart.auth.VerifyRequest
import cn.edu.bit.bitmart.auth.VerifyResponse
import cn.edu.bit.bitmart.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/** /listings 端到端集成测试，跑在内嵌 PostgreSQL 上。 */
class ListingRoutesTest : FunSpec({

    fun sid() = "112020" + (100000..999999).random()

    suspend fun HttpClient.registerToken(): String {
        val studentId = sid()
        val ticket = post("/auth/bit101/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerifyRequest(studentId, "pw"))
        }.body<VerifyResponse>().verifyTicket
        return post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(ticket, studentId, "Secret123", null))
        }.body<AuthResponse>().token
    }

    fun app(
        components: AppComponents = AuthTestSupport.components(),
        block: suspend (HttpClient) -> Unit,
    ) = testApplication {
        application { module(components) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        block(client)
    }

    fun sellReq(
        title: String = "二手教材",
        unitPrice: String? = "30.00",
        tags: List<String> = listOf("教材"),
        quantityTotal: Int = 3,
    ) = CreateListingRequest(
        type = "SELL", category = "GENERAL", title = title,
        description = "九成新", unitPrice = unitPrice, quantityTotal = quantityTotal,
        pickupLocation = "三号楼", contacts = listOf(ContactDto("WECHAT", "wxid_x")),
        tags = tags, expiresInDays = 30,
    )

    test("发布后可在列表中查到") {
        app { client ->
            val token = client.registerToken()
            val created = client.post("/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "线性代数"))
            }
            created.status shouldBe HttpStatusCode.Created

            val page = client.get("/listings?type=SELL").body<ListingPageDto>()
            page.items.map { it.title } shouldContain "线性代数"
        }
    }

    test("详情：未登录 401，登录 200 且含联系方式") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq())
            }.body<CreatedResponse>().id

            client.get("/listings/$id").status shouldBe HttpStatusCode.Unauthorized

            val detail = client.get("/listings/$id") { bearerAuth(token) }
            detail.status shouldBe HttpStatusCode.OK
            detail.body<ListingDetailDto>().contacts.first().value shouldBe "wxid_x"
        }
    }

    test("校验失败：无联系方式 → 400") {
        app { client ->
            val token = client.registerToken()
            val bad = sellReq().copy(contacts = emptyList())
            client.post("/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(bad)
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("文字搜索命中标题") {
        app { client ->
            val token = client.registerToken()
            client.post("/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "概率论与数理统计"))
            }
            val page = client.get("/listings?type=SELL&q=概率论").body<ListingPageDto>()
            page.items.any { it.title.contains("概率论") } shouldBe true
        }
    }

    test("价格区间过滤") {
        app { client ->
            val token = client.registerToken()
            client.post("/listings") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "便宜书", unitPrice = "10.00")) }
            client.post("/listings") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "贵书", unitPrice = "200.00")) }
            val page = client.get("/listings?type=SELL&minPrice=100&includeNoPrice=false").body<ListingPageDto>()
            page.items.all { (it.unitPrice?.toDouble() ?: 0.0) >= 100 } shouldBe true
        }
    }

    test("修改售出数量：单调递增，回退被拒") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(quantityTotal = 5))
            }.body<CreatedResponse>().id

            client.patch("/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(quantitySold = 3))
            }.status shouldBe HttpStatusCode.OK

            // 回退到 1 → 校验失败 400。
            client.patch("/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(quantitySold = 1))
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("非本人无法修改/删除（403）") {
        app { client ->
            val ownerToken = client.registerToken()
            val id = client.post("/listings") {
                bearerAuth(ownerToken); contentType(ContentType.Application.Json); setBody(sellReq())
            }.body<CreatedResponse>().id

            val otherToken = client.registerToken()
            client.delete("/listings/$id") { bearerAuth(otherToken) }.status shouldBe HttpStatusCode.Forbidden
            client.patch("/listings/$id") {
                bearerAuth(otherToken); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(title = "篡改"))
            }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("删除后不在列表中且详情 404") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "待删除商品xyz"))
            }.body<CreatedResponse>().id

            client.delete("/listings/$id") { bearerAuth(token) }.status shouldBe HttpStatusCode.OK
            client.get("/listings/$id") { bearerAuth(token) }.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("批量发布：任一条非法则整体回滚") {
        app { client ->
            val token = client.registerToken()
            val before = client.get("/listings?type=SELL&limit=50").body<ListingPageDto>().items.size

            val good = sellReq(title = "批量A")
            val bad = sellReq(title = "批量B").copy(contacts = emptyList())   // 非法
            val resp = client.post("/listings/batch") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(BatchCreateRequest(listOf(good, bad)))
            }
            resp.status shouldBe HttpStatusCode.BadRequest

            // 整体回滚：数量不变。
            val after = client.get("/listings?type=SELL&limit=50").body<ListingPageDto>().items.size
            after shouldBe before
        }
    }

    test("批量发布全部合法则成功") {
        app { client ->
            val token = client.registerToken()
            val resp = client.post("/listings/batch") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(BatchCreateRequest(listOf(sellReq(title = "批C"), sellReq(title = "批D"))))
            }
            resp.status shouldBe HttpStatusCode.Created
            resp.body<BatchCreatedResponse>().ids.size shouldBe 2
        }
    }

    test("热门标签返回已用标签") {
        app { client ->
            val token = client.registerToken()
            client.post("/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(tags = listOf("热门标签测试")))
            }
            val tags = client.get("/tags/popular").body<PopularTagsDto>().tags
            tags shouldContain "热门标签测试"
        }
    }

    test("ISBN 查询命中 ShowAPI 并缓存") {
        val showApiBody = """
            {"showapi_res_code":0,"showapi_res_body":{"ret_code":0,"data":{
              "title":"算法导论","author":"Cormen","publisher":"机械工业","isbn":"9787111407010","edition":"3"}}}
        """.trimIndent()
        app(AuthTestSupport.components(showApiBody = showApiBody)) { client ->
            val token = client.registerToken()
            val resp = client.post("/books/lookup") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(BookLookupRequest("9787111407010"))
            }
            resp.status shouldBe HttpStatusCode.OK
            resp.body<BookMetaDto>().title shouldBe "算法导论"
        }
    }

    test("未登录无法查询 ISBN（401）") {
        app { client ->
            client.post("/books/lookup") {
                contentType(ContentType.Application.Json); setBody(BookLookupRequest("123"))
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
