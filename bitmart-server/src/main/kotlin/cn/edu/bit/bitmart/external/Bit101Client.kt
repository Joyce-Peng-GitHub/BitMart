package cn.edu.bit.bitmart.external

import cn.edu.bit.bitmart.auth.Bit101PasswordCipher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.slf4j.LoggerFactory

/**
 * BIT101 统一身份认证客户端。
 *
 * 两步流程（见架构 §7.1、demo BitWebvpnVerifyDemo）：
 *  1. POST /user/webvpn_verify_init {sid} → {salt, execution, cookie}
 *  2. 用 salt 作为 AES 密钥加密明文密码，POST /user/webvpn_verify {...} → {token, code, msg}
 *
 * 明文密码仅用于本次直连，不落盘、不入日志。HttpClient 注入以便测试用 MockEngine 替换。
 */
class Bit101Client(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val requestTimeoutMs: Long,
) {
    private val log = LoggerFactory.getLogger(Bit101Client::class.java)

    /**
     * 校验学号与统一身份认证密码。
     * @return 成功 / 凭据无效 / 服务异常；调用方据此决定是否签发 verifyTicket。
     */
    suspend fun verify(studentId: String, plainPassword: String): Bit101VerifyResult {
        return try {
            val init = initSession(studentId)
                ?: return Bit101VerifyResult.ServiceError("BIT101 init failed")

            val encrypted = Bit101PasswordCipher.encrypt(plainPassword, init.salt)
            val verifyResponse: HttpResponse = httpClient.post("$baseUrl/user/webvpn_verify") {
                timeout { requestTimeoutMillis = requestTimeoutMs }
                contentType(ContentType.Application.Json)
                setBody(
                    WebvpnVerifyRequest(
                        sid = studentId,
                        salt = init.salt,
                        password = encrypted,
                        execution = init.execution,
                        cookie = init.cookie,
                        captcha = init.captcha,
                    ),
                )
            }

            if (verifyResponse.status != HttpStatusCode.OK) {
                log.warn("BIT101 verify returned non-200: {}", verifyResponse.status)
                return Bit101VerifyResult.ServiceError("BIT101 verify service error: ${verifyResponse.status}")
            }

            val body: WebvpnVerifyResponse = verifyResponse.body()
            if (body.token.isNotBlank()) {
                Bit101VerifyResult.Success
            } else {
                // HTTP 200 但无 token：视为凭据无效（学号或密码错误）。
                Bit101VerifyResult.InvalidCredentials(body.msg.ifBlank { "Incorrect student ID or password" })
            }
        } catch (e: Exception) {
            log.warn("BIT101 verify exception: {}", e.message)
            Bit101VerifyResult.ServiceError("BIT101 verify exception: ${e.message}")
        }
    }

    private suspend fun initSession(studentId: String): WebvpnVerifyInitResponse? {
        val response: HttpResponse = httpClient.post("$baseUrl/user/webvpn_verify_init") {
            timeout { requestTimeoutMillis = requestTimeoutMs }
            contentType(ContentType.Application.Json)
            setBody(WebvpnVerifyInitRequest(sid = studentId))
        }
        return if (response.status == HttpStatusCode.OK) response.body() else null
    }
}
