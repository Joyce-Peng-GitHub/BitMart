package cn.edu.bit.bitmart.user

import cn.edu.bit.bitmart.AppComponents
import cn.edu.bit.bitmart.auth.AuthResponse
import cn.edu.bit.bitmart.auth.AuthTestSupport
import cn.edu.bit.bitmart.auth.RegisterRequest
import cn.edu.bit.bitmart.auth.UserDto
import cn.edu.bit.bitmart.auth.VerifyRequest
import cn.edu.bit.bitmart.auth.VerifyResponse
import cn.edu.bit.bitmart.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
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
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** /me 端到端集成测试。 */
class MeRoutesTest : FunSpec({

    fun sid() = "112020" + (100000..999999).random()

    suspend fun HttpClient.register(): Pair<String, Long> {
        val studentId = sid()
        val ticket = post("/auth/bit101/verify") {
            contentType(ContentType.Application.Json); setBody(VerifyRequest(studentId, "pw"))
        }.body<VerifyResponse>().verifyTicket
        val auth = post("/auth/register") {
            contentType(ContentType.Application.Json); setBody(RegisterRequest(ticket, studentId, "Secret123", null))
        }.body<AuthResponse>()
        return auth.token to auth.user.id
    }

    fun app(
        components: AppComponents = AuthTestSupport.components(),
        block: suspend (HttpClient, AppComponents) -> Unit,
    ) = testApplication {
        application { module(components) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        block(client, components)
    }

    test("GET /me 需登录") {
        app { client, _ -> client.get("/me").status shouldBe HttpStatusCode.Unauthorized }
    }

    test("GET /me 返回当前用户，默认昵称为匿名") {
        app { client, _ ->
            val (token, _) = client.register()
            val me = client.get("/me") { bearerAuth(token) }
            me.status shouldBe HttpStatusCode.OK
            me.body<UserDto>().displayName shouldBe "匿名"
        }
    }

    test("PATCH /me 更新昵称") {
        app { client, _ ->
            val (token, _) = client.register()
            val updated = client.patch("/me") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateProfileRequest("阿强"))
            }
            updated.status shouldBe HttpStatusCode.OK
            updated.body<UserDto>().nickname shouldBe "阿强"
            // 再次读取确认持久化。
            client.get("/me") { bearerAuth(token) }.body<UserDto>().displayName shouldBe "阿强"
        }
    }

    test("通知列表合并个人通知与全员公告并按时间排序") {
        app { client, components ->
            val (token, userId) = client.register()
            // 直接写入一条个人通知和一条全员公告。
            transaction(components.database) {
                components.notificationRepository.create(null, 0, "全员公告", "维护通知", null)
                components.notificationRepository.create(userId, 1, "到期提醒", "你的商品即将到期", null)
            }
            val page = client.get("/me/notifications") { bearerAuth(token) }.body<NotificationPageDto>()
            val titles = page.items.map { it.title }
            (titles.contains("全员公告") && titles.contains("到期提醒")) shouldBe true
        }
    }

    test("标记个人通知已读成功；公告不可标记") {
        app { client, components ->
            val (token, userId) = client.register()
            val (personalId, announceId) = transaction(components.database) {
                val a = components.notificationRepository.create(userId, 1, "私信", "x", null)
                val b = components.notificationRepository.create(null, 0, "公告", "y", null)
                a to b
            }
            client.post("/me/notifications/$personalId/read") { bearerAuth(token) }.status shouldBe HttpStatusCode.OK
            // 公告无归属，标记返回 404。
            client.post("/me/notifications/$announceId/read") { bearerAuth(token) }.status shouldBe HttpStatusCode.NotFound
        }
    }
})
