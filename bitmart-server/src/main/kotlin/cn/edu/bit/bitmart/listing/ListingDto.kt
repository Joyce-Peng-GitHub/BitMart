package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.domain.BookInfo
import cn.edu.bit.bitmart.domain.Contact
import cn.edu.bit.bitmart.domain.ListingDetail
import cn.edu.bit.bitmart.domain.ListingSummary
import kotlinx.serialization.Serializable

/** /listings 端点的请求/响应 DTO。价格用字符串承载以避免浮点误差（NUMERIC）。 */

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

/** 创建请求。type/category 用字符串枚举名；expiresInDays 由服务端换算过期时间。 */
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
    /**
     * 绝对过期时间（ISO-8601）。客户端按"过期日"换算为该日 00:00（设备时区）的瞬时；
     * 提供时优先于 [expiresInDays]，语义为商品在 [今天, 过期日) 内有效。
     */
    val expiresAt: String? = null,
    val book: BookDto? = null,
    val imageKeys: List<String> = emptyList(),   // /uploads/images 返回的 blobKey，按顺序展示
)

@Serializable
data class BatchCreateRequest(val items: List<CreateListingRequest>)

@Serializable
data class CreatedResponse(val id: Long)

@Serializable
data class BatchCreatedResponse(val ids: List<Long>)

/** 修改请求。clearUnitPrice 用于显式改为面议。全字段编辑：非 null 字段才更新。 */
@Serializable
data class UpdateListingRequest(
    val title: String? = null,
    val description: String? = null,
    val unitPrice: String? = null,
    val clearUnitPrice: Boolean = false,
    val pickupLocation: String? = null,
    val quantitySold: Int? = null,
    val expiresInDays: Int? = null,
    val expiresAt: String? = null,            // 绝对过期时间（ISO），优先于 expiresInDays
    val category: String? = null,             // 枚举名 GENERAL/BOOK
    val quantityTotal: Int? = null,
    val contacts: List<ContactDto>? = null,
    val tags: List<String>? = null,
    val imageKeys: List<String>? = null,      // 非 null 整体替换图片
    val book: BookDto? = null,
)

@Serializable
data class ListingSummaryDto(
    val id: Long,
    val type: String,
    val category: String,
    val title: String,
    val unitPrice: String? = null,
    val quantityTotal: Int,
    val quantitySold: Int,
    val nickname: String? = null,
    val firstImageUrl: String? = null,
    val tags: List<String>,
    val createdAt: String,
    val expiresAt: String,
) {
    companion object {
        fun from(s: ListingSummary) = ListingSummaryDto(
            id = s.id,
            type = s.type.name,
            category = s.category.name,
            title = s.title,
            unitPrice = s.unitPrice?.toPlainString(),
            quantityTotal = s.quantityTotal,
            quantitySold = s.quantitySold,
            nickname = s.nickname,
            firstImageUrl = s.firstImageUrl,
            tags = s.tags,
            createdAt = s.createdAt.toString(),
            expiresAt = s.expiresAt.toString(),
        )
    }
}

/** 列表分页响应：摘要列表 + 下一页游标（编码为 "createdAtISO|id"）。 */
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
    val contacts: List<ContactDto>,
    val tags: List<String>,
    val imageUrls: List<String> = emptyList(),
    val expiresAt: String,
    val createdAt: String,
    val updatedAt: String,
    val book: BookDto? = null,
) {
    companion object {
        fun from(d: ListingDetail) = ListingDetailDto(
            id = d.id,
            type = d.type.name,
            category = d.category.name,
            userId = d.userId,
            nickname = d.nickname,
            title = d.title,
            description = d.description,
            unitPrice = d.unitPrice?.toPlainString(),
            quantityTotal = d.quantityTotal,
            quantitySold = d.quantitySold,
            pickupLocation = d.pickupLocation,
            contacts = d.contacts.map { ContactDto(it.channel, it.value) },
            tags = d.tags,
            imageUrls = d.imageUrls,
            expiresAt = d.expiresAt.toString(),
            createdAt = d.createdAt.toString(),
            updatedAt = d.updatedAt.toString(),
            book = d.book?.let { BookDto(it.isbn, it.title, it.authors, it.publisher, it.edition) },
        )
    }
}

@Serializable
data class TagDto(val id: Long, val name: String)

@Serializable
data class PopularTagsDto(val tags: List<TagDto>)

@Serializable
data class BookLookupRequest(val isbn: String)

@Serializable
data class BookMetaDto(
    val isbn: String,
    val title: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val edition: String? = null,
    val pubdate: String? = null,
    val price: String? = null,
    val page: String? = null,
    val binding: String? = null,
    val format: String? = null,
    val img: String? = null,
    val summary: String? = null,
)

/** 把领域 Contact 与 DTO 互转。 */
fun ContactDto.toDomain() = Contact(channel, value)
fun BookInfo.toDto() = BookDto(isbn, title, authors, publisher, edition)
