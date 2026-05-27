package cn.edu.bit.llmimageanalysisdemo.network

import cn.edu.bit.llmimageanalysisdemo.model.AnalysisResult
import cn.edu.bit.llmimageanalysisdemo.model.BookInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun analyzeImage(base64Image: String, model: String): Result<AnalysisResult> {
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"

        val responseFormat = """
        {
            "type": "json_schema",
            "json_schema": {
                "name": "book_analysis",
                "strict": true,
                "schema": {
                    "type": "object",
                    "properties": {
                        "books": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "title": { "type": "string", "description": "书名，无法识别则为空字符串" },
                                    "author": { "type": "string", "description": "作者，无法识别则为空字符串" },
                                    "publisher": { "type": "string", "description": "出版社，无法识别则为空字符串" },
                                    "edition": { "type": "string", "description": "版本，无法识别则为空字符串" }
                                },
                                "required": ["title", "author", "publisher", "edition"],
                                "additionalProperties": false
                            }
                        }
                    },
                    "required": ["books"],
                    "additionalProperties": false
                }
            }
        }
        """.trimIndent()

        val requestBody = """
        {
            "model": "$model",
            "messages": [
                {
                    "role": "system",
                    "content": "你是一个图书识别助手。用户会发送书本封面或书脊的照片，请识别图中所有书本的信息。对于每本书，尽可能识别书名(title)、作者(author)、出版社(publisher)、版本(edition)。如果某项信息无法从图片中识别，请将其设为空字符串。\n\n你必须且只能输出一个纯 JSON 对象，格式为 {\"books\":[...]}，不要输出任何其他内容，不要使用 Markdown 代码块包裹。"
                },
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": "data:image/jpeg;base64,$base64Image"
                            }
                        },
                        {
                            "type": "text",
                            "text": "请识别这张图片中所有书本的信息。"
                        }
                    ]
                }
            ],
            "response_format": $responseFormat
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return Result.failure(Exception("API error ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(Exception("Empty response body"))

                val jsonResponse = json.parseToJsonElement(responseBody) as JsonObject
                val rawContent = jsonResponse["choices"]!!
                    .jsonArray[0]
                    .jsonObject["message"]!!
                    .jsonObject["content"]!!
                    .jsonPrimitive.content

                val cleanedJson = stripMarkdownFence(rawContent)
                val result = parseResult(cleanedJson)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun stripMarkdownFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline == -1) return trimmed
        val withoutStart = trimmed.substring(firstNewline + 1)
        return if (withoutStart.endsWith("```")) {
            withoutStart.dropLast(3).trim()
        } else {
            withoutStart.trim()
        }
    }

    private fun parseResult(jsonString: String): AnalysisResult {
        val element = json.parseToJsonElement(jsonString)
        return if (element is kotlinx.serialization.json.JsonArray) {
            AnalysisResult(books = json.decodeFromString<List<BookInfo>>(jsonString))
        } else {
            json.decodeFromString<AnalysisResult>(jsonString)
        }
    }
}
