package cn.edu.bit.bitmart.core.data.remote

import cn.edu.bit.bitmart.core.data.local.TokenStore
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.isUnauthorized
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * 后端 API 客户端。封装 Ktor 调用、鉴权头注入与响应映射。
 * tokenProvider 在每次需要鉴权时取当前令牌，便于登录态变化即时生效。
 * 任何需鉴权的请求收到 401 时自动清除本地令牌，避免客户端误以为已登录。
 */
class BitMartApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val tokenStore: TokenStore,
) {
    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1$path"

    // —— 认证 ——
    suspend fun verify(req: VerifyRequest): DomainResult<VerifyResponse> = safe {
        ApiResponseMapper.handle(client.post(url("/auth/bit101/verify")) {
            contentType(ContentType.Application.Json); setBody(req)
        })
    }

    suspend fun register(req: RegisterRequest): DomainResult<AuthResponse> = safe {
        ApiResponseMapper.handle(client.post(url("/auth/register")) {
            contentType(ContentType.Application.Json); setBody(req)
        })
    }

    suspend fun login(req: LoginRequest): DomainResult<AuthResponse> = safe {
        ApiResponseMapper.handle(client.post(url("/auth/login")) {
            contentType(ContentType.Application.Json); setBody(req)
        })
    }

    suspend fun resetPassword(req: ResetPasswordRequest): DomainResult<Unit> = safe {
        ApiResponseMapper.handleUnit(client.post(url("/auth/reset-password")) {
            contentType(ContentType.Application.Json); setBody(req)
        })
    }

    suspend fun logout(): DomainResult<Unit> = authBlock {
        ApiResponseMapper.handleUnit(client.delete(url("/auth/session")) { auth() })
    }

    suspend fun deleteAccount(): DomainResult<Unit> = authBlock {
        ApiResponseMapper.handleUnit(client.delete(url("/auth/account")) { auth() })
    }

    // —— 列表 ——
    suspend fun listListings(params: Map<String, String?>): DomainResult<ListingPageDto> = safe {
        ApiResponseMapper.handle(client.get(url("/listings")) {
            params.forEach { (k, v) -> if (v != null) parameter(k, v) }
        })
    }

    /** 当前用户自己发布的列表（需登录）。参数同 listListings（type 可省略）。 */
    suspend fun myListings(params: Map<String, String?>): DomainResult<ListingPageDto> = authBlock {
        ApiResponseMapper.handle(client.get(url("/me/listings")) {
            auth(); params.forEach { (k, v) -> if (v != null) parameter(k, v) }
        })
    }

    suspend fun listingDetail(id: Long): DomainResult<ListingDetailDto> = authBlock {
        ApiResponseMapper.handle(client.get(url("/listings/$id")) { auth() })
    }

    suspend fun createListing(req: CreateListingRequest): DomainResult<CreatedResponse> = authBlock {
        ApiResponseMapper.handle(client.post(url("/listings")) {
            auth(); contentType(ContentType.Application.Json); setBody(req)
        })
    }

    suspend fun createListingBatch(req: BatchCreateRequest): DomainResult<BatchCreatedResponse> = authBlock {
        ApiResponseMapper.handle(client.post(url("/listings/batch")) {
            auth(); contentType(ContentType.Application.Json); setBody(req)
        })
    }

    suspend fun uploadImage(bytes: ByteArray, filename: String): DomainResult<UploadResponse> = authBlock {
        ApiResponseMapper.handle(client.post(url("/uploads/images")) {
            auth()
            setBody(io.ktor.client.request.forms.MultiPartFormDataContent(
                io.ktor.client.request.forms.formData {
                    append("file", bytes, io.ktor.http.Headers.build {
                        append(io.ktor.http.HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    })
                }
            ))
        })
    }

    suspend fun lookupBook(isbn: String): DomainResult<BookMetaDto?> = authBlock {
        val response = client.post(url("/books/lookup")) {
            auth(); contentType(ContentType.Application.Json)
            setBody(mapOf("isbn" to isbn))
        }
        if (response.status == io.ktor.http.HttpStatusCode.NotFound) {
            DomainResult.Success(null)
        } else {
            ApiResponseMapper.handle(response)
        }
    }

    suspend fun popularTags(limit: Int): DomainResult<PopularTagsDto> = safe {
        ApiResponseMapper.handle(client.get(url("/tags/popular")) { parameter("limit", limit) })
    }

    suspend fun updateListing(id: Long, req: UpdateListingRequest): DomainResult<Unit> = authBlock {
        ApiResponseMapper.handleUnit(client.patch(url("/listings/$id")) {
            auth(); contentType(ContentType.Application.Json); setBody(req)
        })
    }

    suspend fun deleteListing(id: Long): DomainResult<Unit> = authBlock {
        ApiResponseMapper.handleUnit(client.delete(url("/listings/$id")) { auth() })
    }

    // —— 用户资料 ——
    suspend fun getMe(): DomainResult<UserDto> = authBlock {
        ApiResponseMapper.handle(client.get(url("/me")) { auth() })
    }

    suspend fun updateMe(req: UpdateMeRequest): DomainResult<UserDto> = authBlock {
        ApiResponseMapper.handle(client.patch(url("/me")) {
            auth(); contentType(ContentType.Application.Json); setBody(req)
        })
    }

    // —— 通知 ——
    suspend fun notifications(cursor: String?, limit: Int): DomainResult<NotificationPageDto> = authBlock {
        ApiResponseMapper.handle(client.get(url("/me/notifications")) {
            auth(); if (cursor != null) parameter("cursor", cursor); parameter("limit", limit)
        })
    }

    suspend fun markNotificationRead(id: Long): DomainResult<Unit> = authBlock {
        ApiResponseMapper.handleUnit(client.post(url("/me/notifications/$id/read")) { auth() })
    }

    suspend fun unreadNotificationCount(): DomainResult<UnreadCountDto> = authBlock {
        ApiResponseMapper.handle(client.get(url("/me/notifications/unread-count")) { auth() })
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.auth() {
        tokenProvider()?.let { bearerAuth(it) }
    }

    /** 统一捕获网络异常为 NetworkError。 */
    private suspend inline fun <T> safe(block: () -> DomainResult<T>): DomainResult<T> =
        try {
            block()
        } catch (e: Exception) {
            DomainResult.NetworkError(e.message ?: "Network error")
        }

    /**
     * 带鉴权的请求包装：与 [safe] 相同，额外在收到 401 时自动清除本地令牌。
     * 令牌清除后 [cn.edu.bit.bitmart.core.domain.repository.AuthRepository.isLoggedIn] 变为 false，
     * 整个 UI 自然退回到未登录状态，无需各 ViewModel 单独处理。
     */
    private suspend inline fun <T> authBlock(block: () -> DomainResult<T>): DomainResult<T> {
        val result = safe(block)
        if (result.isUnauthorized()) {
            tokenStore.clear()
        }
        return result
    }
}
