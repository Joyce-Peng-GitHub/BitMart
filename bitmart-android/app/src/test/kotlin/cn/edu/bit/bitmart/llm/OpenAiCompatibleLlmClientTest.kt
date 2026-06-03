package cn.edu.bit.bitmart.llm

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleLlmClientTest {

    private val config = LlmConfig(
        baseUrl = "https://llm.example.com/",
        apiKey = "sk-test-123",
        model = "gpt-4o-mini",
        timeoutSeconds = 30,
    )

    /** 构造一个返回固定 chat/completions 响应的客户端，并捕获最后一次请求供断言。 */
    private fun clientReturning(
        status: HttpStatusCode,
        body: String,
        captured: MutableList<HttpRequestData> = mutableListOf(),
    ): Pair<OpenAiCompatibleLlmClient, MutableList<HttpRequestData>> {
        val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { req ->
            captured.add(req)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(MockEngine(handler)) { install(HttpTimeout); expectSuccess = false }
        return OpenAiCompatibleLlmClient(http) to captured
    }

    /** 构造一个底层抛出 IO 异常的客户端，用于覆盖 NetworkError 分支。 */
    private fun clientThrowing(): OpenAiCompatibleLlmClient {
        val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            throw java.io.IOException("connection refused")
        }
        val http = HttpClient(MockEngine(handler)) { install(HttpTimeout); expectSuccess = false }
        return OpenAiCompatibleLlmClient(http)
    }

    /** 把模型内容包成 OpenAI chat/completions 响应信封。 */
    private fun envelope(content: String): String {
        val escaped = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(content))
        return """{"choices":[{"message":{"role":"assistant","content":$escaped}}]}"""
    }

    @Test
    fun `book success parses all fields`() = runBlocking {
        val (client, _) = clientReturning(
            HttpStatusCode.OK,
            envelope("""{"title":"深入理解计算机系统","author":"Bryant","publisher":"机械工业","edition":"第3版","isbn":"9787111544937"}"""),
        )
        val r = client.recognize(config, byteArrayOf(1, 2, 3), ListingCategory.BOOK)
        assertTrue("expected Success but was $r", r is DomainResult.Success)
        val book = (r as DomainResult.Success).data as LlmRecognition.Book
        assertEquals("深入理解计算机系统", book.title)
        assertEquals("Bryant", book.author)
        assertEquals("机械工业", book.publisher)
        assertEquals("第3版", book.edition)
        assertEquals("9787111544937", book.isbn)
    }

    @Test
    fun `book blank isbn normalized to null`() = runBlocking {
        val (client, _) = clientReturning(
            HttpStatusCode.OK,
            envelope("""{"title":"x","author":"","publisher":"","edition":"","isbn":""}"""),
        )
        val r = client.recognize(config, byteArrayOf(1), ListingCategory.BOOK)
        val book = (r as DomainResult.Success).data as LlmRecognition.Book
        assertEquals(null, book.isbn)
    }

    @Test
    fun `general success parses fields`() = runBlocking {
        val (client, _) = clientReturning(
            HttpStatusCode.OK,
            envelope("""{"title":"二手台灯","description":"九成新","suggestedPrice":"35","tags":["家居","照明"]}"""),
        )
        val r = client.recognize(config, byteArrayOf(1), ListingCategory.GENERAL)
        val g = (r as DomainResult.Success).data as LlmRecognition.General
        assertEquals("二手台灯", g.title)
        assertEquals("九成新", g.description)
        assertEquals("35", g.suggestedPrice)
        assertEquals(listOf("家居", "照明"), g.tags)
    }

    @Test
    fun `markdown fenced content is stripped and parsed`() = runBlocking {
        val fenced = "```json\n{\"title\":\"围栏书\",\"author\":\"\",\"publisher\":\"\",\"edition\":\"\",\"isbn\":\"\"}\n```"
        val (client, _) = clientReturning(HttpStatusCode.OK, envelope(fenced))
        val r = client.recognize(config, byteArrayOf(1), ListingCategory.BOOK)
        val book = (r as DomainResult.Success).data as LlmRecognition.Book
        assertEquals("围栏书", book.title)
    }

    @Test
    fun `http error maps to failure`() = runBlocking {
        val (client, _) = clientReturning(HttpStatusCode.Unauthorized, """{"error":"bad key"}""")
        val r = client.recognize(config, byteArrayOf(1), ListingCategory.BOOK)
        assertTrue(r is DomainResult.Failure)
        assertEquals(401, (r as DomainResult.Failure).httpStatus)
    }

    @Test
    fun `malformed content maps to failure`() = runBlocking {
        val (client, _) = clientReturning(HttpStatusCode.OK, envelope("not even json {{{"))
        val r = client.recognize(config, byteArrayOf(1), ListingCategory.BOOK)
        assertTrue(r is DomainResult.Failure)
    }

    @Test
    fun `missing choices structure maps to failure`() = runBlocking {
        val (client, _) = clientReturning(HttpStatusCode.OK, """{"unexpected":true}""")
        val r = client.recognize(config, byteArrayOf(1), ListingCategory.BOOK)
        assertTrue(r is DomainResult.Failure)
    }

    @Test
    fun `request carries bearer auth model and data url image`() = runBlocking {
        val (client, captured) = clientReturning(
            HttpStatusCode.OK,
            envelope("""{"title":"x","author":"","publisher":"","edition":"","isbn":""}"""),
        )
        client.recognize(config, byteArrayOf(0x10, 0x20, 0x30), ListingCategory.BOOK)

        val req = captured.single()
        // URL 命中 /v1/chat/completions（baseUrl 末尾斜杠被规整）。
        assertEquals("https://llm.example.com/v1/chat/completions", req.url.toString())
        // Bearer 鉴权。
        assertEquals("Bearer sk-test-123", req.headers[HttpHeaders.Authorization])

        val bodyText = (req.body as io.ktor.http.content.TextContent).text
        val bodyJson = Json.parseToJsonElement(bodyText).jsonObject
        assertEquals("gpt-4o-mini", bodyJson["model"]!!.jsonPrimitive.content)
        // response_format 为 json_schema。
        assertEquals("json_schema", bodyJson["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        // user 消息含 image_url data URL。
        val userContent = bodyJson["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        val imagePart = userContent[0].jsonObject
        assertEquals("image_url", imagePart["type"]!!.jsonPrimitive.content)
        val url = imagePart["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertTrue(url.startsWith("data:image/jpeg;base64,"))
        // system 提示词为书籍提示词。
        assertEquals("system", bodyJson["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `general category uses general schema name`() = runBlocking {
        val (client, captured) = clientReturning(
            HttpStatusCode.OK,
            envelope("""{"title":"x","description":"","suggestedPrice":"","tags":[]}"""),
        )
        client.recognize(config, byteArrayOf(1), ListingCategory.GENERAL)
        val bodyText = (captured.single().body as io.ktor.http.content.TextContent).text
        val schemaName = Json.parseToJsonElement(bodyText).jsonObject["response_format"]!!
            .jsonObject["json_schema"]!!.jsonObject["name"]!!.jsonPrimitive.content
        assertEquals("general_goods", schemaName)
    }

    @Test
    fun `network failure maps to NetworkError`() = runBlocking {
        val client = clientThrowing()
        val r = client.recognize(config, byteArrayOf(1, 2, 3), ListingCategory.BOOK)
        assertTrue("expected NetworkError but was $r", r is DomainResult.NetworkError)
    }
}
