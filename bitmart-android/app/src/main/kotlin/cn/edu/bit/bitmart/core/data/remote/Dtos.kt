package cn.edu.bit.bitmart.core.data.remote

import kotlinx.serialization.Serializable

/** 与后端对应的传输模型。可空字段均给默认值（后端 explicitNulls=false 会省略 null）。 */

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody) {
    @Serializable
    data class ApiErrorBody(val code: String, val message: String)
}

@Serializable
data class VerifyRequest(val studentId: String, val password: String)

@Serializable
data class VerifyResponse(val verifyTicket: String)

@Serializable
data class RegisterRequest(
    val verifyTicket: String,
    val studentId: String,
    val password: String,
    val nickname: String? = null,
)

@Serializable
data class LoginRequest(val studentId: String, val password: String)

@Serializable
data class ResetPasswordRequest(val verifyTicket: String, val studentId: String, val newPassword: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class UserDto(
    val id: Long,
    val studentId: String,
    val nickname: String? = null,
    val displayName: String,
    val role: String,
)

@Serializable
data class ContactDto(val channel: String, val value: String)

@Serializable
data class BookDto(
    val isbn: String? = null,
    val title: String? = null,
    val authors: String? = null,
    val publisher: String? = null,
    val edition: String? = null,
)

@Serializable
data class ListingSummaryDto(
    val id: Long,
    val ownerId: Long,
    val type: String,
    val category: String,
    val title: String,
    val unitPrice: String? = null,
    val quantityTotal: Int,
    val quantitySold: Int,
    val nickname: String? = null,
    val firstImageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String,
    val expiresAt: String,
)

@Serializable
data class ListingPageDto(val items: List<ListingSummaryDto>, val nextCursor: String? = null)

@Serializable
data class ListingDetailDto(
    val id: Long,
    val type: String,
    val category: String,
    val userId: Long,
    val nickname: String? = null,
    val title: String,
    val description: String,
    val unitPrice: String? = null,
    val quantityTotal: Int,
    val quantitySold: Int,
    val pickupLocation: String? = null,
    val contacts: List<ContactDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val expiresAt: String,
    val createdAt: String,
    val updatedAt: String,
    val book: BookDto? = null,
    val originalPrice: String? = null,
)
@Serializable
data class TagDto(val id: Long, val name: String)

@Serializable
data class PopularTagsDto(val tags: List<TagDto>)

/** 创建 listing 请求（与后端 CreateListingRequest 对齐）。 */
@Serializable
data class CreateListingRequest(
    val type: String,
    val category: String = "GENERAL",
    val title: String,
    val description: String = "",
    val unitPrice: String? = null,
    val quantityTotal: Int = 1,
    val pickupLocation: String? = null,
    val contacts: List<ContactDto>,
    val tags: List<String> = emptyList(),
    val expiresInDays: Int? = null,
    /** 绝对过期时间（ISO-8601）。非空时后端优先采用，语义为商品在 [今天, 过期日) 内有效。 */
    val expiresAt: String? = null,
    val book: BookDto? = null,
    val imageKeys: List<String> = emptyList(),
    val originalPrice: String? = null,
)
@Serializable
data class CreatedResponse(val id: Long)

/** 修改 listing 请求（与后端 UpdateListingRequest 对齐）。null 字段不改。 */
@Serializable
data class UpdateListingRequest(
    val title: String? = null,
    val description: String? = null,
    val unitPrice: String? = null,
    val clearUnitPrice: Boolean = false,
    val pickupLocation: String? = null,
    val quantitySold: Int? = null,
    val expiresInDays: Int? = null,
    val expiresAt: String? = null,
    val category: String? = null,
    val quantityTotal: Int? = null,
    val contacts: List<ContactDto>? = null,
    val tags: List<String>? = null,
    val imageKeys: List<String>? = null,
    val book: BookDto? = null,
    val originalPrice: String? = null,
)
/** 修改当前用户资料请求（PATCH /me），目前仅支持昵称。 */
@Serializable
data class UpdateMeRequest(val nickname: String? = null)

/** 单条通知（公告 / 个人提醒合并流）。 */
@Serializable
data class NotificationDto(
    val id: Long,
    val category: Int,
    val title: String,
    val body: String,
    val payload: String? = null,
    val read: Boolean,
    val createdAt: String,
    val isAnnouncement: Boolean,
)

@Serializable
data class NotificationPageDto(val items: List<NotificationDto>, val nextCursor: String? = null)

/** 未读通知数（GET /me/notifications/unread-count，仅个人未读，不含公告）。 */
@Serializable
data class UnreadCountDto(val count: Long)

/** 批量发布请求（POST /listings/batch）。 */
@Serializable
data class BatchCreateRequest(val items: List<CreateListingRequest>)

/** 批量发布响应（所有创建成功后返回全部 id）。 */
@Serializable
data class BatchCreatedResponse(val ids: List<Long>)

/** 图片上传响应（POST /uploads/images，multipart）。 */
@Serializable
data class UploadResponse(
    val blobKey: String,
    val url: String,
    val contentType: String,
)

/** 书籍元数据（POST /books/lookup 响应）。 */
@Serializable
data class BookMetaDto(
    val isbn: String,
    val title: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val edition: String? = null,
    val pubdate: String? = null,
    val price: String? = null,
    val page: Int? = null,
    val binding: String? = null,
    val format: String? = null,
    val img: String? = null,
    val summary: String? = null,
)
