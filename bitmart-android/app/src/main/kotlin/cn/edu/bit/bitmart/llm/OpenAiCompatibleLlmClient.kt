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
import kotlinx.serialization.json.JsonArray
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
    ): DomainResult<List<LlmRecognition>> = try {
        android.util.Log.d(tag, "LLM recognize start category=$category bytes=${imageBytes.size}")
        val response = client.post("${config.baseUrl.trimEnd('/')}/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            timeout {
                // 用户配置的阈值同时作用于整请求与底层 socket 读写：
                // LLM 识图存在「模型思考、迟迟不吐首字节」的阶段，若仅设 requestTimeoutMillis，
                // OkHttp 默认 10s 的 socket 读超时会先掐断 HTTP/2 流（见 logcat 排查），
                // 导致配置的阈值形同虚设。故让 socketTimeoutMillis 跟随同一阈值。
                val millis = config.timeoutSeconds.coerceAtLeast(1) * 1000L
                requestTimeoutMillis = millis
                socketTimeoutMillis = millis
                connectTimeoutMillis = millis
            }
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
        DomainResult.NetworkError(e.message ?: "网络异常", e)
    }

    /** 组装 chat/completions 请求体：system 提示词 + 携带 data URL 图片的 user 消息 + json_schema。 */
    @OptIn(ExperimentalEncodingApi::class)
    private fun buildRequest(config: LlmConfig, imageBytes: ByteArray, category: ListingCategory): JsonObject {
        val prompt = if (category == ListingCategory.BOOK) config.bookPrompt else config.generalPrompt
        val dataUrl = "data:image/jpeg;base64,${Base64.Default.encode(imageBytes)}"
        val userText = if (category == ListingCategory.BOOK) "请识别这张图片中所有书本的信息（可能不止一本）。" else "请识别这张照片中所有商品并分别生成挂牌信息（可能不止一件）。"
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

    /** 解析 choices[0].message.content，剥离 ``` 围栏后按品类反序列化为识别项列表。 */
    private fun parseContent(body: String, category: ListingCategory): DomainResult<List<LlmRecognition>> {
        val content = runCatching {
            json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }.getOrNull() ?: return DomainResult.InvalidResponse("无法解析识别服务的响应结构")

        val cleaned = stripMarkdownFence(content)
        return runCatching {
            when (category) {
                ListingCategory.BOOK -> itemsArray(cleaned).map { json.decodeFromJsonElement(BookPayload.serializer(), it).toRecognition() }
                ListingCategory.GENERAL -> itemsArray(cleaned).map { json.decodeFromJsonElement(GeneralPayload.serializer(), it).toRecognition() }
            }
        }.fold(
            onSuccess = { DomainResult.Success(it) },
            onFailure = { DomainResult.InvalidResponse("识别结果不是预期的 JSON 格式", it) },
        )
    }

    /**
     * 归一化为识别项的 JSON 数组：优先取 `{"items":[...]}`；
     * 兼容回退——裸数组 `[...]` 原样、裸对象 `{...}` 包成单元素列表，提升对不守 schema 模型的健壮性。
     */
    private fun itemsArray(cleaned: String): List<JsonObject> {
        val element = json.parseToJsonElement(cleaned)
        val array = when {
            element is JsonObject && element["items"] is JsonArray -> element["items"]!!.jsonArray
            element is JsonArray -> element
            element is JsonObject -> kotlinx.serialization.json.JsonArray(listOf(element))
            else -> kotlinx.serialization.json.JsonArray(emptyList())
        }
        return array.map { it.jsonObject }
    }

    private fun BookPayload.toRecognition() = LlmRecognition.Book(
        title = title, author = author, publisher = publisher, edition = edition,
        isbn = isbn.ifBlank { null },
        originalPrice = originalPrice.ifBlank { null },
    )

    private fun GeneralPayload.toRecognition() = LlmRecognition.General(
        title = title, description = description,
        originalPrice = originalPrice.ifBlank { null },
        tags = tags.map { it.trim() }.filter { it.isNotEmpty() },
    )

    private fun responseFormat(category: ListingCategory): JsonObject =
        json.parseToJsonElement(if (category == ListingCategory.BOOK) BOOK_SCHEMA else GENERAL_SCHEMA).jsonObject

    /** 剥离 Markdown ``` 代码块围栏（沿用参考实现的策略）。 */
    private fun stripMarkdownFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith(MARKDOWN_FENCE)) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline == -1) return trimmed
        val withoutStart = trimmed.substring(firstNewline + 1)
        return if (withoutStart.endsWith(MARKDOWN_FENCE)) withoutStart.dropLast(MARKDOWN_FENCE.length).trim() else withoutStart.trim()
    }

    private companion object {
        /** Markdown 代码块围栏标记，用于识别与剥离模型输出可能包裹的 ``` 围栏。 */
        const val MARKDOWN_FENCE = "```"

        const val BOOK_SCHEMA = """
            {"type":"json_schema","json_schema":{"name":"book_list","strict":true,"schema":{
            "type":"object","properties":{
            "items":{"type":"array","description":"图中识别到的所有书本，每本一个元素；图中没有书则为空数组","items":{
            "type":"object","properties":{
            "title":{"type":"string","description":"书名，无法识别则为空字符串"},
            "author":{"type":"string","description":"作者，无法识别则为空字符串"},
            "publisher":{"type":"string","description":"出版社，无法识别则为空字符串"},
            "edition":{"type":"string","description":"版本/版次，如『第3版』，无法识别则为空字符串"},
            "isbn":{"type":"string","description":"国际标准书号 ISBN（仅数字），无法识别则为空字符串"},
            "originalPrice":{"type":"string","description":"图中可见的标价/吊牌定价（人民币元，纯数字字符串，如『59.00』）；图中看不到价格则为空字符串，切勿臆测"}},
            "required":["title","author","publisher","edition","isbn","originalPrice"],"additionalProperties":false}}},
            "required":["items"],"additionalProperties":false}}}
        """

        const val GENERAL_SCHEMA = """
            {"type":"json_schema","json_schema":{"name":"general_goods_list","strict":true,"schema":{
            "type":"object","properties":{
            "items":{"type":"array","description":"图中识别到的所有商品，每件一个元素；图中没有商品则为空数组","items":{
            "type":"object","properties":{
            "title":{"type":"string","description":"简洁的商品标题"},
            "description":{"type":"string","description":"一段简短的商品描述（成色、特征等）"},
            "originalPrice":{"type":"string","description":"图中可见的商品标价/吊牌原价（人民币元，纯数字字符串，如『199.00』）；图中看不到价格则为空字符串，切勿臆测，也不要给出售价"},
            "tags":{"type":"array","items":{"type":"string"},"description":"有助于检索的标签，可为空数组"}},
            "required":["title","description","originalPrice","tags"],"additionalProperties":false}}},
            "required":["items"],"additionalProperties":false}}}
        """
    }
}
