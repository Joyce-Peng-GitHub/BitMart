package cn.edu.bit.bitmart.llm

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI-Compatible 识图客户端实现（架构 §5.4）。
 *
 * 直连用户配置的 `{baseUrl}/v1/chat/completions`，Bearer 鉴权，发送
 * 一条 system 提示词 + 一条携带 base64 data URL 图片的 user 消息，并以
 * `response_format=json_schema(strict)` 强约束输出结构。解析 `choices[0].message.content`，
 * 剥离可能的 ``` 代码块围栏后按品类反序列化。
 *
 * 注入的 [HttpClient] 需安装 HttpTimeout 插件（见 AppModule / 测试支持），
 * 以便按 [LlmConfig.timeoutSeconds] 设定单次请求超时。
 */
class OpenAiCompatibleLlmClient(
    private val client: HttpClient,
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val tag = "BitMartLlm"

    override suspend fun recognize(
        config: LlmConfig,
        imageBytes: ByteArray,
        category: ListingCategory,
    ): DomainResult<LlmRecognition> = try {
        android.util.Log.d(tag, "LLM recognize start category=$category bytes=${imageBytes.size}")
        val response = client.post("${config.baseUrl.trimEnd('/')}/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = config.timeoutSeconds.coerceAtLeast(1) * 1000L }
            setBody(json.encodeToString(JsonObject.serializer(), buildRequest(config, imageBytes, category)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            android.util.Log.w(tag, "LLM HTTP error status=${response.status.value}")
            DomainResult.Failure("LLM_HTTP_${response.status.value}", "识别服务返回错误（${response.status.value}）", response.status.value)
        } else {
            val result = parseContent(text, category)
            if (result is DomainResult.Success) android.util.Log.d(tag, "LLM recognize success category=$category")
            else android.util.Log.w(tag, "LLM parse failed category=$category")
            result
        }
    } catch (e: Exception) {
        android.util.Log.e(tag, "LLM recognize error: ${e.message}", e)
        DomainResult.NetworkError(e.message ?: "网络异常")
    }

    /** 组装 chat/completions 请求体：system 提示词 + 携带 data URL 图片的 user 消息 + json_schema。 */
    @OptIn(ExperimentalEncodingApi::class)
    private fun buildRequest(config: LlmConfig, imageBytes: ByteArray, category: ListingCategory): JsonObject {
        val prompt = if (category == ListingCategory.BOOK) config.bookPrompt else config.generalPrompt
        val dataUrl = "data:image/jpeg;base64,${Base64.Default.encode(imageBytes)}"
        val userText = if (category == ListingCategory.BOOK) "请识别这张图片中书本的信息。" else "请根据这张商品照片生成挂牌信息。"
        return buildJsonObject {
            put("model", config.model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", prompt)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", dataUrl) }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", userText)
                        }
                    }
                }
            }
            put("response_format", responseFormat(category))
        }
    }

    /** 解析 choices[0].message.content，剥离 ``` 围栏后按品类反序列化。 */
    private fun parseContent(body: String, category: ListingCategory): DomainResult<LlmRecognition> {
        val content = runCatching {
            json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }.getOrNull() ?: return DomainResult.Failure("LLM_PARSE_ERROR", "无法解析识别服务的响应结构", 200)

        val cleaned = stripMarkdownFence(content)
        return runCatching {
            when (category) {
                ListingCategory.BOOK -> json.decodeFromString<BookPayload>(cleaned).toRecognition()
                ListingCategory.GENERAL -> json.decodeFromString<GeneralPayload>(cleaned).toRecognition()
            }
        }.fold(
            onSuccess = { DomainResult.Success(it) },
            onFailure = { DomainResult.Failure("LLM_PARSE_ERROR", "识别结果不是预期的 JSON 格式", 200) },
        )
    }

    private fun BookPayload.toRecognition() = LlmRecognition.Book(
        title = title, author = author, publisher = publisher, edition = edition,
        isbn = isbn.ifBlank { null },
    )

    private fun GeneralPayload.toRecognition() = LlmRecognition.General(
        title = title, description = description,
        suggestedPrice = suggestedPrice.ifBlank { null },
        tags = tags.map { it.trim() }.filter { it.isNotEmpty() },
    )

    private fun responseFormat(category: ListingCategory): JsonObject =
        json.parseToJsonElement(if (category == ListingCategory.BOOK) BOOK_SCHEMA else GENERAL_SCHEMA).jsonObject

    /** 剥离 Markdown ``` 代码块围栏（沿用参考实现的策略）。 */
    private fun stripMarkdownFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline == -1) return trimmed
        val withoutStart = trimmed.substring(firstNewline + 1)
        return if (withoutStart.endsWith("```")) withoutStart.dropLast(3).trim() else withoutStart.trim()
    }

    private companion object {
        const val BOOK_SCHEMA = """
            {"type":"json_schema","json_schema":{"name":"book_info","strict":true,"schema":{
            "type":"object","properties":{
            "title":{"type":"string","description":"书名，无法识别则为空字符串"},
            "author":{"type":"string","description":"作者，无法识别则为空字符串"},
            "publisher":{"type":"string","description":"出版社，无法识别则为空字符串"},
            "edition":{"type":"string","description":"版本，无法识别则为空字符串"},
            "isbn":{"type":"string","description":"ISBN，无法识别则为空字符串"}},
            "required":["title","author","publisher","edition","isbn"],"additionalProperties":false}}}
        """

        const val GENERAL_SCHEMA = """
            {"type":"json_schema","json_schema":{"name":"general_goods","strict":true,"schema":{
            "type":"object","properties":{
            "title":{"type":"string","description":"商品标题"},
            "description":{"type":"string","description":"商品描述"},
            "suggestedPrice":{"type":"string","description":"建议价格（人民币元，纯数字字符串），无法判断则为空字符串"},
            "tags":{"type":"array","items":{"type":"string"},"description":"检索标签，可为空数组"}},
            "required":["title","description","suggestedPrice","tags"],"additionalProperties":false}}}
        """
    }
}
