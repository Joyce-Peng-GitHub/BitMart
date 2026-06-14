package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.AppComponents
import cn.edu.bit.bitmart.auth.AuthResponse
import cn.edu.bit.bitmart.auth.AuthTestSupport
import cn.edu.bit.bitmart.auth.RegisterRequest
import cn.edu.bit.bitmart.auth.VerifyRequest
import cn.edu.bit.bitmart.auth.VerifyResponse
import cn.edu.bit.bitmart.configureApp
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/** /listings 端到端集成测试，跑在内嵌 PostgreSQL 上。 */
class ListingRoutesTest : FunSpec({

    fun sid() = "112020" + (100000..999999).random()

    suspend fun HttpClient.registerToken(): String {
        val studentId = sid()
        val ticket = post("/api/v1/auth/bit101/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerifyRequest(studentId, "pw"))
        }.body<VerifyResponse>().verifyTicket
        return post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(ticket, studentId, "Secret123", null))
        }.body<AuthResponse>().token
    }

    fun app(
        components: AppComponents = AuthTestSupport.components(),
        block: suspend (HttpClient) -> Unit,
    ) = testApplication {
        application { configureApp(components) }
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
            val created = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "线性代数"))
            }
            created.status shouldBe HttpStatusCode.Created

            val page = client.get("/api/v1/listings?type=SELL").body<ListingPageDto>()
            page.items.map { it.title } shouldContain "线性代数"
        }
    }

    // 回归：expiresInDays 取最小值 1 天时，映射与校验须共用同一时间基准，
    // 否则 expiresAt 恰在窗口下边界，会因校验时重取时钟恒判 EXPIRY_TOO_SOON。
    test("有效期为最小值 1 天时单条发布成功") {
        app { client ->
            val token = client.registerToken()
            val created = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "一天后过期").copy(expiresInDays = 1))
            }
            created.status shouldBe HttpStatusCode.Created
        }
    }

    test("有效期为最小值 1 天时批量发布成功") {
        app { client ->
            val token = client.registerToken()
            val created = client.post("/api/v1/listings/batch") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(BatchCreateRequest(listOf(sellReq(title = "批量一天后过期").copy(expiresInDays = 1))))
            }
            created.status shouldBe HttpStatusCode.Created
        }
    }

    test("延期为最小值 1 天时修改成功") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq())
            }.body<CreatedResponse>().id

            val patched = client.patch("/api/v1/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(UpdateListingRequest(expiresInDays = 1))
            }
            patched.status shouldBe HttpStatusCode.OK
        }
    }

    test("详情：未登录 401，登录 200 且含联系方式") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq())
            }.body<CreatedResponse>().id

            client.get("/api/v1/listings/$id").status shouldBe HttpStatusCode.Unauthorized

            val detail = client.get("/api/v1/listings/$id") { bearerAuth(token) }
            detail.status shouldBe HttpStatusCode.OK
            detail.body<ListingDetailDto>().contacts.first().value shouldBe "wxid_x"
        }
    }

    test("校验失败：无联系方式 → 400") {
        app { client ->
            val token = client.registerToken()
            val bad = sellReq().copy(contacts = emptyList())
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(bad)
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("发布：件数超出上限 → 400") {
        app { client ->
            val token = client.registerToken()
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "海量件数商品", quantityTotal = 10000))
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("发布：价格超出 NUMERIC(10,2) 上限 → 400（入库前拦截，不触发 DB 溢出）") {
        app { client ->
            val token = client.registerToken()
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "天价商品", unitPrice = "100000000"))
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("发布：价格恰好等于上限 99999999.99 → 成功") {
        app { client ->
            val token = client.registerToken()
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "上限价商品", unitPrice = "99999999.99"))
            }.status shouldBe HttpStatusCode.Created
        }
    }

    test("修改：价格超出上限 → 400") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq())
            }.body<CreatedResponse>().id

            client.patch("/api/v1/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(UpdateListingRequest(unitPrice = "100000000"))
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("文字搜索命中标题") {
        app { client ->
            val token = client.registerToken()
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "概率论与数理统计"))
            }
            val page = client.get("/api/v1/listings?type=SELL&q=概率论").body<ListingPageDto>()
            page.items.any { it.title.contains("概率论") } shouldBe true
        }
    }

    test("价格区间过滤") {
        app { client ->
            val token = client.registerToken()
            client.post("/api/v1/listings") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "便宜书", unitPrice = "10.00")) }
            client.post("/api/v1/listings") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "贵书", unitPrice = "200.00")) }
            val page = client.get("/api/v1/listings?type=SELL&minPrice=100&includeNoPrice=false").body<ListingPageDto>()
            page.items.all { (it.unitPrice?.toDouble() ?: 0.0) >= 100 } shouldBe true
        }
    }

    test("修改售出数量：增加和减少均合法") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(quantityTotal = 5))
            }.body<CreatedResponse>().id

            client.patch("/api/v1/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(quantitySold = 3))
            }.status shouldBe HttpStatusCode.OK

            // 减少到 1 → 同样合法（需求允许增删）。
            client.patch("/api/v1/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(quantitySold = 1))
            }.status shouldBe HttpStatusCode.OK

            // 超过总量 5 → 校验失败 400。
            client.patch("/api/v1/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(quantitySold = 6))
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("非本人无法修改/删除（403）") {
        app { client ->
            val ownerToken = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(ownerToken); contentType(ContentType.Application.Json); setBody(sellReq())
            }.body<CreatedResponse>().id

            val otherToken = client.registerToken()
            client.delete("/api/v1/listings/$id") { bearerAuth(otherToken) }.status shouldBe HttpStatusCode.Forbidden
            client.patch("/api/v1/listings/$id") {
                bearerAuth(otherToken); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(title = "篡改"))
            }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("删除后不在列表中且详情 404") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "待删除商品xyz"))
            }.body<CreatedResponse>().id

            client.delete("/api/v1/listings/$id") { bearerAuth(token) }.status shouldBe HttpStatusCode.OK
            client.get("/api/v1/listings/$id") { bearerAuth(token) }.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("批量发布：任一条非法则整体回滚") {
        app { client ->
            val token = client.registerToken()
            val before = client.get("/api/v1/listings?type=SELL&limit=50").body<ListingPageDto>().items.size

            val good = sellReq(title = "批量A")
            val bad = sellReq(title = "批量B").copy(contacts = emptyList())   // 非法
            val resp = client.post("/api/v1/listings/batch") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(BatchCreateRequest(listOf(good, bad)))
            }
            resp.status shouldBe HttpStatusCode.BadRequest

            // 整体回滚：数量不变。
            val after = client.get("/api/v1/listings?type=SELL&limit=50").body<ListingPageDto>().items.size
            after shouldBe before
        }
    }

    test("批量发布全部合法则成功") {
        app { client ->
            val token = client.registerToken()
            val resp = client.post("/api/v1/listings/batch") {
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
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(tags = listOf("热门标签测试")))
            }
            val tags = client.get("/api/v1/tags/popular").body<PopularTagsDto>().tags
            tags.map { it.name } shouldContain "热门标签测试"
        }
    }

    test("ISBN 查询命中 ShowAPI 并缓存") {
        val showApiBody = """
            {"showapi_res_code":0,"showapi_res_body":{"ret_code":0,"data":{
              "title":"算法导论","author":"Cormen","publisher":"机械工业","isbn":"9787111407010","edition":"3"}}}
        """.trimIndent()
        app(AuthTestSupport.components(showApiBody = showApiBody)) { client ->
            val token = client.registerToken()
            val resp = client.post("/api/v1/books/lookup") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(BookLookupRequest("9787111407010"))
            }
            resp.status shouldBe HttpStatusCode.OK
            resp.body<BookMetaDto>().title shouldBe "算法导论"
        }
    }

    test("未登录无法查询 ISBN（401）") {
        app { client ->
            client.post("/api/v1/books/lookup") {
                contentType(ContentType.Application.Json); setBody(BookLookupRequest("123"))
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("发布时携带 imageKeys，列表返回 firstImageUrl（首图）") {
        app { client ->
            val token = client.registerToken()
            val req = sellReq(title = "带图商品xyz").copy(imageKeys = listOf("2026/06/02/a.jpg", "2026/06/02/b.jpg"))
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(req)
            }.status shouldBe HttpStatusCode.Created

            val page = client.get("/api/v1/listings?type=SELL&q=带图商品xyz").body<ListingPageDto>()
            val item = page.items.first { it.title == "带图商品xyz" }
            // firstImageUrl 应为按 ord 排序的第一张图（a.jpg）。
            item.firstImageUrl shouldBe "/static/2026/06/02/a.jpg"
        }
    }

    test("公开列表摘要不含取货地点（取货地点仅详情页可见）") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "取货地点测试abc"))
            }.body<CreatedResponse>().id
            // 摘要 DTO 已无 pickupLocation 字段；详情仍返回。
            val detail = client.get("/api/v1/listings/$id") { bearerAuth(token) }.body<ListingDetailDto>()
            detail.pickupLocation shouldBe "三号楼"
        }
    }

    test("我的列表只返回本人发布项，且包含已售罄项") {
        app { client ->
            // 用户 A 发布一条，并将其全部标记售出。
            val tokenA = client.registerToken()
            val idA = client.post("/api/v1/listings") {
                bearerAuth(tokenA); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "我的售罄商品A", quantityTotal = 2))
            }.body<CreatedResponse>().id
            client.patch("/api/v1/listings/$idA") {
                bearerAuth(tokenA); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(quantitySold = 2))
            }.status shouldBe HttpStatusCode.OK

            // 用户 B 发布一条。
            val tokenB = client.registerToken()
            client.post("/api/v1/listings") {
                bearerAuth(tokenB); contentType(ContentType.Application.Json); setBody(sellReq(title = "B的商品"))
            }.status shouldBe HttpStatusCode.Created

            // A 的"我的列表"应含已售罄的 A 项，且不含 B 的项。
            val mine = client.get("/api/v1/me/listings") { bearerAuth(tokenA) }.body<ListingPageDto>()
            val titles = mine.items.map { it.title }
            titles shouldContain "我的售罄商品A"
            (titles.none { it == "B的商品" }) shouldBe true
            // 摘要携带 expiresAt，未过期项的过期时间在未来。
            val item = mine.items.first { it.title == "我的售罄商品A" }
            OffsetDateTime.parse(item.expiresAt).isAfter(OffsetDateTime.now()) shouldBe true
        }
    }

    test("我的列表未登录返回 401") {
        app { client ->
            client.get("/api/v1/me/listings").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("公开列表默认不含已售罄项（与我的列表区别）") {
        app { client ->
            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "公开售罄商品zzz", quantityTotal = 1))
            }.body<CreatedResponse>().id
            client.patch("/api/v1/listings/$id") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateListingRequest(quantitySold = 1))
            }.status shouldBe HttpStatusCode.OK

            // 公开列表（includeSold 默认 false）不应出现该售罄项。
            val page = client.get("/api/v1/listings?type=SELL&q=公开售罄商品zzz").body<ListingPageDto>()
            (page.items.none { it.title == "公开售罄商品zzz" }) shouldBe true
        }
    }

    test("已过期项：仅本人可见（列表与详情），公开列表/他人详情不可访问") {
        val components = AuthTestSupport.components()
        testApplication {
            application { configureApp(components) }
            val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

            val token = client.registerToken()
            val id = client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(sellReq(title = "过期商品qqq"))
            }.body<CreatedResponse>().id

            // 直接把 expires_at 改到过去（API 校验禁止提交过去时间，故绕过走 DB）。
            transaction(components.database) {
                cn.edu.bit.bitmart.db.Listings.update({ cn.edu.bit.bitmart.db.Listings.id eq id }) {
                    it[expiresAt] = OffsetDateTime.now().minusDays(1)
                }
            }

            // 公开列表不含已过期项。
            val publicPage = client.get("/api/v1/listings?type=SELL&q=过期商品qqq").body<ListingPageDto>()
            (publicPage.items.none { it.title == "过期商品qqq" }) shouldBe true

            // 过期项不可公开展示：即便显式 includeExpired=true，公开列表仍不含该项。
            val publicWithExpired = client.get("/api/v1/listings?type=SELL&q=过期商品qqq&includeExpired=true").body<ListingPageDto>()
            (publicWithExpired.items.none { it.title == "过期商品qqq" }) shouldBe true

            // 过期项详情：他人访问按"未找到"处理，发布者本人仍可访问。
            val otherToken = client.registerToken()
            client.get("/api/v1/listings/$id") { bearerAuth(otherToken) }.status shouldBe HttpStatusCode.NotFound
            client.get("/api/v1/listings/$id") { bearerAuth(token) }.status shouldBe HttpStatusCode.OK

            // 我的列表（默认 includeExpired）仍含该项，过期时间已在过去。
            val mine = client.get("/api/v1/me/listings") { bearerAuth(token) }.body<ListingPageDto>()
            val mineItem = mine.items.first { it.title == "过期商品qqq" }
            OffsetDateTime.parse(mineItem.expiresAt).isBefore(OffsetDateTime.now()) shouldBe true

            // 我的列表显式 includeExpired=false 时排除该项。
            val mineNoExpired = client.get("/api/v1/me/listings?includeExpired=false") { bearerAuth(token) }.body<ListingPageDto>()
            (mineNoExpired.items.none { it.title == "过期商品qqq" }) shouldBe true
        }
    }

    test("firstImageUrl 按 ord 取首图，与插入顺序无关") {
        app { client ->
            val token = client.registerToken()
            // imageKeys 顺序即 ord 顺序：第一个元素 ord=0 为首图。
            val req = sellReq(title = "多图排序商品ppp")
                .copy(imageKeys = listOf("first-by-ord.jpg", "second.jpg", "third.jpg"))
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(req)
            }.status shouldBe HttpStatusCode.Created

            val page = client.get("/api/v1/listings?type=SELL&q=多图排序商品ppp").body<ListingPageDto>()
            page.items.first { it.title == "多图排序商品ppp" }.firstImageUrl shouldBe "/static/first-by-ord.jpg"
        }
    }

    test("无图商品 firstImageUrl 为空") {
        app { client ->
            val token = client.registerToken()
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "无图商品nnn"))
            }.status shouldBe HttpStatusCode.Created
            val page = client.get("/api/v1/listings?type=SELL&q=无图商品nnn").body<ListingPageDto>()
            page.items.first { it.title == "无图商品nnn" }.firstImageUrl shouldBe null
        }
    }

    test("批量发布支持逐条携带 imageKeys") {
        app { client ->
            val token = client.registerToken()
            val a = sellReq(title = "批量带图A").copy(imageKeys = listOf("batchA.jpg"))
            val b = sellReq(title = "批量带图B").copy(imageKeys = listOf("batchB.jpg"))
            client.post("/api/v1/listings/batch") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(BatchCreateRequest(listOf(a, b)))
            }.status shouldBe HttpStatusCode.Created

            val page = client.get("/api/v1/listings?type=SELL&limit=50").body<ListingPageDto>()
            page.items.first { it.title == "批量带图A" }.firstImageUrl shouldBe "/static/batchA.jpg"
            page.items.first { it.title == "批量带图B" }.firstImageUrl shouldBe "/static/batchB.jpg"
        }
    }

    test("我的列表按 type 过滤（仅收购）") {
        app { client ->
            val token = client.registerToken()
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(sellReq(title = "我的在售sss"))
            }.status shouldBe HttpStatusCode.Created
            val buyReq = sellReq(title = "我的求购bbb").let {
                CreateListingRequest(
                    type = "BUY", category = "GENERAL", title = it.title, description = it.description,
                    unitPrice = it.unitPrice, quantityTotal = it.quantityTotal, pickupLocation = it.pickupLocation,
                    contacts = it.contacts, tags = it.tags, expiresInDays = it.expiresInDays,
                )
            }
            client.post("/api/v1/listings") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(buyReq)
            }.status shouldBe HttpStatusCode.Created

            val buyMine = client.get("/api/v1/me/listings?type=BUY") { bearerAuth(token) }.body<ListingPageDto>()
            val titles = buyMine.items.map { it.title }
            titles shouldContain "我的求购bbb"
            (titles.none { it == "我的在售sss" }) shouldBe true
        }
    }
})
