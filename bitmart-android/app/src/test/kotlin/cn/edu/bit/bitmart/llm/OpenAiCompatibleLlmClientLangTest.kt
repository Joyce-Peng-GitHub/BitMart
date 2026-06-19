package cn.edu.bit.bitmart.llm

import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖 Task 20：识图请求按应用语言组装（system 提示词 / user 文案 / 响应 schema 的 description）。
 * 通过 MockEngine 捕获请求体并断言三者随 languageTag 切换；自定义提示词原样使用。
 */
class OpenAiCompatibleLlmClientLangTest {

    private fun clientCapturing(sink: MutableList<String>): OpenAiCompatibleLlmClient {
        val engine = MockEngine { req ->
            sink.add((req.body as io.ktor.http.content.TextContent).text)
            respond(
                content = ByteReadChannel("""{"choices":[{"message":{"content":"{\"items\":[]}"}}]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return OpenAiCompatibleLlmClient(HttpClient(engine) { install(HttpTimeout); expectSuccess = false })
    }

    // bookPrompt / generalPrompt 此时仍是非空默认值；测试改用空白 prompt 触发按语言取默认。
    private val cfg = LlmConfig(baseUrl = "https://x", apiKey = "k", model = "m", bookPrompt = "", generalPrompt = "")

    private fun systemPrompt(body: String): String =
        Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray[0]
            .jsonObject["content"]!!.jsonPrimitive.content

    private fun userText(body: String): String =
        Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray[1]
            .jsonObject["content"]!!.jsonArray[1].jsonObject["text"]!!.jsonPrimitive.content

    private fun itemsDescription(body: String): String =
        Json.parseToJsonElement(body).jsonObject["response_format"]!!
            .jsonObject["json_schema"]!!.jsonObject["schema"]!!
            .jsonObject["properties"]!!.jsonObject["items"]!!
            .jsonObject["description"]!!.jsonPrimitive.content

    @Test fun blank_prompt_en_uses_english_default() = runBlocking {
        val bodies = mutableListOf<String>()
        clientCapturing(bodies).recognize(cfg, ByteArray(3), ListingCategory.BOOK, "en")
        // 英文 system 提示词。
        assertTrue(systemPrompt(bodies[0]).contains("book recognition assistant"))
        // 英文 user 文案。
        assertEquals("Identify all books in this photo (there may be more than one).", userText(bodies[0]))
        // 英文 schema description。
        assertTrue(itemsDescription(bodies[0]).contains("All books recognized in the image"))
    }

    @Test fun blank_prompt_zh_uses_chinese_default() = runBlocking {
        val bodies = mutableListOf<String>()
        clientCapturing(bodies).recognize(cfg, ByteArray(3), ListingCategory.BOOK, "zh")
        assertTrue(systemPrompt(bodies[0]).contains("图书识别助手"))
        assertEquals("请识别这张图片中所有书本的信息（可能不止一本）。", userText(bodies[0]))
        assertTrue(itemsDescription(bodies[0]).contains("图中识别到的所有书本"))
    }

    @Test fun general_en_uses_english_default() = runBlocking {
        val bodies = mutableListOf<String>()
        clientCapturing(bodies).recognize(cfg, ByteArray(3), ListingCategory.GENERAL, "en")
        assertTrue(systemPrompt(bodies[0]).contains("second-hand goods listing assistant"))
        assertEquals(
            "Identify all items in this photo and generate a listing for each (there may be more than one).",
            userText(bodies[0]),
        )
        assertTrue(itemsDescription(bodies[0]).contains("All goods recognized in the image"))
    }

    @Test fun general_zh_uses_chinese_default() = runBlocking {
        val bodies = mutableListOf<String>()
        clientCapturing(bodies).recognize(cfg, ByteArray(3), ListingCategory.GENERAL, "zh")
        assertTrue(systemPrompt(bodies[0]).contains("二手商品信息助手"))
        assertEquals("请识别这张照片中所有商品并分别生成挂牌信息（可能不止一件）。", userText(bodies[0]))
        assertTrue(itemsDescription(bodies[0]).contains("图中识别到的所有商品"))
    }

    @Test fun custom_prompt_used_verbatim() = runBlocking {
        val bodies = mutableListOf<String>()
        val custom = cfg.copy(bookPrompt = "MY CUSTOM PROMPT")
        clientCapturing(bodies).recognize(custom, ByteArray(3), ListingCategory.BOOK, "en")
        // 自定义提示词非空 → 原样作为 system content，不被英文默认覆盖。
        assertEquals("MY CUSTOM PROMPT", systemPrompt(bodies[0]))
    }

    @Test fun default_language_tag_is_chinese() = runBlocking {
        // 不传 languageTag（旧 3 参调用点）应保持中文，行为不变。
        val bodies = mutableListOf<String>()
        clientCapturing(bodies).recognize(cfg, ByteArray(3), ListingCategory.BOOK)
        assertTrue(systemPrompt(bodies[0]).contains("图书识别助手"))
    }
}
