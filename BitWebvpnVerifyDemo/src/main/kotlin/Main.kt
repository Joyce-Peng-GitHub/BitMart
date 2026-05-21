package cn.bit.edu

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Serializable
data class WebvpnVerifyInitRequest(val sid: String)

@Serializable
data class WebvpnVerifyInitResponse(
    val salt: String,
    val execution: String,
    val cookie: String,
    val captcha: String = "",
)

@Serializable
data class WebvpnVerifyRequest(
    val sid: String,
    val salt: String,
    val password: String,
    val execution: String,
    val cookie: String,
    val captcha: String = "",
)

@Serializable
data class WebvpnVerifyResponse(
    val token: String = "",
    val code: String = "",
    val msg: String = "",
)

/**
 * Encrypts the password with AES/ECB/PKCS5Padding using the Base64-decoded salt as key.
 * Must remain byte-compatible with BIT101 frontend EncryptPassword.ts (CryptoJS AES.encrypt
 * with mode.ECB, pad.Pkcs7, key parsed from Base64). Note: PKCS5Padding in JCE is identical
 * to PKCS7Padding for 16-byte AES blocks.
 */
fun encryptPassword(password: String, salt: String): String {
    val keyBytes = Base64.getDecoder().decode(salt)
    val secretKey = SecretKeySpec(keyBytes, "AES")
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(encrypted)
}

private const val BIT101_API_BASE = "https://bit101.flwfdd.xyz"

fun main() = runBlocking {
    print("Enter your Student ID: ")
    val sid = readlnOrNull()?.trim().orEmpty()
    if (sid.isBlank()) {
        println("SID cannot be empty")
        return@runBlocking
    }

    print("Enter your password: ")
    val password = readlnOrNull()?.trim().orEmpty()
    if (password.isBlank()) {
        println("Password cannot be empty")
        return@runBlocking
    }

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    try {
        println("\nInitializing...")
        val initResponse: HttpResponse = client.post("$BIT101_API_BASE/user/webvpn_verify_init") {
            contentType(ContentType.Application.Json)
            setBody(WebvpnVerifyInitRequest(sid = sid))
        }

        if (initResponse.status != HttpStatusCode.OK) {
            println("Failed to initialize: HTTP ${initResponse.status.value}")
            println("   ${initResponse.bodyAsText()}")
            return@runBlocking
        }

        val initData: WebvpnVerifyInitResponse = initResponse.body()
        println("Initialization Success!")

        val encryptedPassword = encryptPassword(password, initData.salt)

        println("Verifying...")
        val verifyResponse: HttpResponse = client.post("$BIT101_API_BASE/user/webvpn_verify") {
            contentType(ContentType.Application.Json)
            setBody(
                WebvpnVerifyRequest(
                    sid = sid,
                    salt = initData.salt,
                    password = encryptedPassword,
                    execution = initData.execution,
                    cookie = initData.cookie,
                )
            )
        }

        if (verifyResponse.status == HttpStatusCode.OK) {
            val result: WebvpnVerifyResponse = verifyResponse.body()
            println()
            println("Verification Success!")
            println("Message: ${result.msg}")
            println("Token: ${result.token}")
            println("Code: ${result.code}")
        } else {
            val errorBody = verifyResponse.bodyAsText()
            println()
            println("Error!")
            println("HTTP Status: ${verifyResponse.status}")
            println("Message: $errorBody")
        }
    } catch (e: Exception) {
        println()
        println("Error!")
        println("Message: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}
