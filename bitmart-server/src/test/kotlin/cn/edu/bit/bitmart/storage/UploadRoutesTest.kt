package cn.edu.bit.bitmart.storage

import cn.edu.bit.bitmart.auth.AuthResponse
import cn.edu.bit.bitmart.auth.AuthTestSupport
import cn.edu.bit.bitmart.auth.RegisterRequest
import cn.edu.bit.bitmart.auth.VerifyRequest
import cn.edu.bit.bitmart.auth.VerifyResponse
import cn.edu.bit.bitmart.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/** /uploads/images 集成测试。 */
class UploadRoutesTest : FunSpec({

    fun sid() = "112020" + (100000..999999).random()

    suspend fun HttpClient.token(): String {
        val studentId = sid()
        val ticket = post("/auth/bit101/verify") {
            contentType(ContentType.Application.Json); setBody(VerifyRequest(studentId, "pw"))
        }.body<VerifyResponse>().verifyTicket
        return post("/auth/register") {
            contentType(ContentType.Application.Json); setBody(RegisterRequest(ticket, studentId, "Secret123", null))
        }.body<AuthResponse>().token
    }

    val jpeg = ByteArray(32).also {
        it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte(); it[3] = 0xE0.toByte()
    }

    fun app(block: suspend (HttpClient) -> Unit) = testApplication {
        application { module(AuthTestSupport.components()) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        block(client)
    }

    test("登录后上传 JPEG 成功，返回 blobKey") {
        app { client ->
            val token = client.token()
            val resp = client.submitFormWithBinaryData(
                url = "/uploads/images",
                formData = formData {
                    append("file", jpeg, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"x.jpg\"")
                    })
                },
            ) { bearerAuth(token) }
            resp.status shouldBe HttpStatusCode.Created
            resp.body<UploadResponse>().blobKey.isNotBlank() shouldBe true
        }
    }

    test("未登录上传 401") {
        app { client ->
            val resp = client.submitFormWithBinaryData(
                url = "/uploads/images",
                formData = formData {
                    append("file", jpeg, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"x.jpg\"")
                    })
                },
            )
            resp.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("上传伪造图片（实为文本）被拒 415") {
        app { client ->
            val token = client.token()
            val resp = client.submitFormWithBinaryData(
                url = "/uploads/images",
                formData = formData {
                    append("file", "not an image".toByteArray(), Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"fake.jpg\"")
                    })
                },
            ) { bearerAuth(token) }
            resp.status shouldBe HttpStatusCode.UnsupportedMediaType
        }
    }
})
