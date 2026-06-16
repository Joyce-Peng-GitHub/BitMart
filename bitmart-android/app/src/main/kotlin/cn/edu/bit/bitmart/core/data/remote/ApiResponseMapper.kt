package cn.edu.bit.bitmart.core.data.remote

import cn.edu.bit.bitmart.core.domain.DomainResult
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * 将 Ktor 响应统一映射为 DomainResult：2xx → Success(解析体)，其余 → Failure（尽量解析错误信封）。
 * 网络异常由调用方 try/catch 转为 NetworkError。
 */
object ApiResponseMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private const val TAG = "BitMartApi"

    suspend inline fun <reified T> handle(response: HttpResponse): DomainResult<T> {
        return if (response.status.isSuccess()) {
            // 2xx 但响应体反序列化失败 → InvalidResponse（而非沿用旧路径被上层 try/catch 误判为 NetworkError）。
            runCatching { DomainResult.Success(response.body<T>()) }
                .getOrElse { DomainResult.InvalidResponse("无法解析服务器响应", it) }
        } else {
            parseError(response.status.value, response.bodyAsText())
        }
    }

    /** 用于无响应体的成功（如 200 {status:ok}）。 */
    suspend fun handleUnit(response: HttpResponse): DomainResult<Unit> {
        return if (response.status.isSuccess()) {
            DomainResult.Success(Unit)
        } else {
            parseError(response.status.value, response.bodyAsText())
        }
    }

    fun parseError(status: Int, body: String): DomainResult.Failure {
        val envelope = runCatching { json.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
        val code = envelope?.error?.code ?: "HTTP_$status"
        val message = envelope?.error?.message ?: "Request failed ($status)"
        android.util.Log.w(TAG, "API error status=$status code=$code message=$message")
        return DomainResult.Failure(code = code, message = message, httpStatus = status)
    }
}
