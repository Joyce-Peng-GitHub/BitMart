package cn.edu.bit.bitmart.external

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * ShowAPI（万维易源）ISBN 查询客户端。
 *
 * GET {baseUrl}/1626-1?appKey=...&isbn=...，两级错误码（见 demo IsbnQueryDemo）：
 *  - 顶层 showapi_res_code == 0 表示网关调用成功；
 *  - showapi_res_body.ret_code == 0 表示业务查询成功，data 含书籍字段。
 *
 * appKey 为凭据，注入而不写死；HttpClient 注入以便测试用 MockEngine 替换。
 */
class ShowApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val appKey: String,
    private val requestTimeoutMs: Long,
) {
    private val log = LoggerFactory.getLogger(ShowApiClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(isbn: String): IsbnLookupResult {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/1626-1") {
                timeout { requestTimeoutMillis = requestTimeoutMs }
                parameter("appKey", appKey)
                parameter("isbn", isbn)
            }
            if (response.status != HttpStatusCode.OK) {
                return IsbnLookupResult.ServiceError("ShowAPI 返回 ${response.status}")
            }
            parseBody(isbn, response.bodyAsText())
        } catch (e: Exception) {
            log.warn("ShowAPI 查询异常 isbn={}: {}", isbn, e.message)
            IsbnLookupResult.ServiceError("ShowAPI 查询异常: ${e.message}")
        }
    }

    private fun parseBody(isbn: String, raw: String): IsbnLookupResult {
        val root = json.parseToJsonElement(raw).jsonObject

        val resCode = root["showapi_res_code"]?.jsonPrimitive?.intOrNull
        if (resCode != 0) {
            val err = root["showapi_res_error"]?.jsonPrimitive?.contentOrNull ?: "未知网关错误"
            return IsbnLookupResult.ServiceError("ShowAPI 网关错误(code=$resCode): $err")
        }

        val body = root["showapi_res_body"]?.jsonObject
            ?: return IsbnLookupResult.ServiceError("ShowAPI 响应缺少 showapi_res_body")

        val retCode = body["ret_code"]?.jsonPrimitive?.intOrNull
        if (retCode != 0) {
            // ret_code 非 0：通常为未找到该 ISBN。
            return IsbnLookupResult.NotFound
        }

        val data = body["data"]?.jsonObject
            ?: return IsbnLookupResult.NotFound

        return IsbnLookupResult.Found(meta = mapBookMeta(isbn, data), rawJson = raw)
    }

    private fun mapBookMeta(isbn: String, data: JsonObject): BookMeta {
        fun field(key: String): String? =
            data[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return BookMeta(
            // 优先使用上游返回的 isbn，缺失时回退到查询用的 isbn。
            isbn = field("isbn") ?: isbn,
            title = field("title"),
            author = field("author"),
            publisher = field("publisher"),
            pubdate = field("pubdate"),
            edition = field("edition"),
            price = field("price"),
            page = field("page"),
            binding = field("binding"),
            format = field("format"),
            img = field("img"),
            summary = field("gist"),
        )
    }
}
