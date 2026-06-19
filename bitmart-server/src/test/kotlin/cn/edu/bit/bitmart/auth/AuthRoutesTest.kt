package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.AppComponents
import cn.edu.bit.bitmart.configureApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.call.body
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/**
 * /auth 端到端集成测试：跑在内嵌 PostgreSQL + Mock BIT101 之上，覆盖
 * 验证→注册→登录→鉴权操作的完整链路与主要失败分支。
 */
class AuthRoutesTest : FunSpec({

    // 每个用例用唯一学号，避免内嵌库在套件内复用导致的冲突。
    fun sid() = "112020" + (100000..999999).random()

    fun testApp(
        components: AppComponents = AuthTestSupport.components(),
        block: suspend (io.ktor.client.HttpClient) -> Unit,
    ) = testApplication {
        application { configureApp(components) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        block(client)
    }

    test("注册流程：verify 签发票 → register 返回 token 与用户") {
        testApp { client ->
            val studentId = sid()
            val verify = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(studentId, "anyUnifiedPw"))
            }
            verify.status shouldBe HttpStatusCode.OK
            val ticket = verify.body<VerifyResponse>().verifyTicket

            val register = client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(ticket, studentId, "Secret123", "小明"))
            }
            register.status shouldBe HttpStatusCode.OK
            val auth = register.body<AuthResponse>()
            auth.token.isNotBlank() shouldBe true
            auth.user.studentId shouldBe studentId
            auth.user.displayName shouldBe "小明"
        }
    }

    test("BIT101 校验失败 → 401，且不签发票") {
        testApp(AuthTestSupport.components(bit101Succeeds = false)) { client ->
            val resp = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(sid(), "wrong"))
            }
            resp.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("弱密码注册 → 400") {
        testApp { client ->
            val studentId = sid()
            val ticket = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(studentId, "pw"))
            }.body<VerifyResponse>().verifyTicket

            val resp = client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(ticket, studentId, "weak", null))   // 太短且单一字符类
            }
            resp.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("伪造/过期票注册 → 401") {
        testApp { client ->
            val resp = client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("bogus-ticket", sid(), "Secret123", null))
            }
            resp.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("登录：正确密码成功，错误密码 401") {
        testApp { client ->
            val studentId = sid()
            val ticket = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(studentId, "pw"))
            }.body<VerifyResponse>().verifyTicket
            client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(ticket, studentId, "Secret123", null))
            }

            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(studentId, "Secret123"))
            }.status shouldBe HttpStatusCode.OK

            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(studentId, "WrongPass1"))
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("未登录访问受保护端点 → 401") {
        testApp { client ->
            client.delete("/api/v1/auth/session").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("登出后令牌失效（再次访问受保护端点 401）") {
        testApp { client ->
            val studentId = sid()
            val ticket = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(studentId, "pw"))
            }.body<VerifyResponse>().verifyTicket
            val regResp = client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(ticket, studentId, "Secret123", null))
            }
            regResp.status shouldBe HttpStatusCode.OK
            val token = regResp.body<AuthResponse>().token

            // 登出
            client.delete("/api/v1/auth/session") { bearerAuth(token) }.status shouldBe HttpStatusCode.OK
            // 令牌已吊销
            client.delete("/api/v1/auth/session") { bearerAuth(token) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("注销账号：吊销会话且无法再登录") {
        testApp { client ->
            val studentId = sid()
            val ticket = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(studentId, "pw"))
            }.body<VerifyResponse>().verifyTicket
            val token = client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(ticket, studentId, "Secret123", null))
            }.body<AuthResponse>().token

            client.delete("/api/v1/auth/account") { bearerAuth(token) }.status shouldBe HttpStatusCode.OK
            // 软删后无法登录。
            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(studentId, "Secret123"))
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("注销后用同一学号重新注册：返回新用户而非 500") {
        testApp { client ->
            val studentId = sid()

            // 1. 验证 → 注册（旧号）
            val ticket1 = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(studentId, "pw"))
            }.body<VerifyResponse>().verifyTicket
            val token1 = client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(ticket1, studentId, "Secret123", "旧号"))
            }.body<AuthResponse>().token

            // 2. 注销
            client.delete("/api/v1/auth/account") { bearerAuth(token1) }.status shouldBe HttpStatusCode.OK

            // 3. 用同一学号重新注册（新号）—— 修复前会因唯一约束冲突抛 500
            val ticket2 = client.post("/api/v1/auth/bit101/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyRequest(studentId, "pw"))
            }.body<VerifyResponse>().verifyTicket
            val register2 = client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(ticket2, studentId, "Secret456", "新号"))
            }
            register2.status shouldBe HttpStatusCode.OK
            val auth2 = register2.body<AuthResponse>()
            auth2.user.studentId shouldBe studentId
            auth2.user.displayName shouldBe "新号"

            // 4. 用新密码登录成功
            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(studentId, "Secret456"))
            }.status shouldBe HttpStatusCode.OK
        }
    }
})
