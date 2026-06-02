package cn.edu.bit.bitmart.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

/** 列表摘要（用于列表页，不含 contact 与完整描述，架构 §6.3）。 */
data class ListingSummary(
    val id: Long,
    val type: ListingType,
    val category: ListingCategory,
    val title: String,
    val unitPrice: BigDecimal?,
    val quantityTotal: Int,
    val quantitySold: Int,
    val pickupLocation: String?,
    val nickname: String?,
    val tags: List<String>,
    val createdAt: OffsetDateTime,
)

/** 列表详情（含 contact、完整描述、书籍信息；仅登录可见）。 */
data class ListingDetail(
    val id: Long,
    val type: ListingType,
    val category: ListingCategory,
    val userId: Long,
    val nickname: String?,
    val title: String,
    val description: String,
    val unitPrice: BigDecimal?,
    val quantityTotal: Int,
    val quantitySold: Int,
    val pickupLocation: String?,
    val contacts: List<Contact>,
    val tags: List<String>,
    val imageUrls: List<String>,
    val expiresAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val book: BookInfo?,
)

/** 书籍专属信息（仅 category=BOOK）。 */
data class BookInfo(
    val isbn: String?,
    val title: String?,
    val authors: String?,
    val publisher: String?,
    val edition: String?,
)

/** 列表排序与分页游标（keyset）。 */
data class ListingCursor(val createdAt: OffsetDateTime, val id: Long)

/** 列表查询过滤条件（架构 §6.3）。 */
data class ListingFilter(
    val type: ListingType,
    val category: ListingCategory? = null,
    val query: String? = null,
    val tagIds: List<Long> = emptyList(),
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val includeNoPrice: Boolean = true,
    val includeSold: Boolean = false,
    val cursor: ListingCursor? = null,
    val limit: Int = 20,
)
