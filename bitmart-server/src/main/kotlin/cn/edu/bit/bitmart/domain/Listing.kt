package cn.edu.bit.bitmart.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

/** 列表摘要（用于列表页，不含 contact、完整描述与取货地点，架构 §6.3）。 */
data class ListingSummary(
    val id: Long,
    /** 发布者用户 id（客户端据此判断"是否本人"，在公开列表中对本人项启用左滑操作）。 */
    val ownerId: Long,
    val type: ListingType,
    val category: ListingCategory,
    val title: String,
    val unitPrice: BigDecimal?,
    val quantityTotal: Int,
    val quantitySold: Int,
    val nickname: String?,
    val firstImageUrl: String?,
    val tags: List<String>,
    val createdAt: OffsetDateTime,
    /** 过期时间。客户端据此在列表/详情显示并按临期/已过期着色。 */
    val expiresAt: OffsetDateTime,
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

/** 列表查询过滤条件（架构 §6.3 公开列表；§6.2 我的列表）。 */
data class ListingFilter(
    val type: ListingType? = null,        // null = 不限买卖（我的列表可用）
    val category: ListingCategory? = null,
    val query: String? = null,
    val tagIds: List<Long> = emptyList(),
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val includeNoPrice: Boolean = true,
    val includeSold: Boolean = false,
    val cursor: ListingCursor? = null,
    val limit: Int = 20,
    val ownerId: Long? = null,            // 设置则仅返回该用户发布的项（我的列表）
    val includeExpired: Boolean = false,  // true 则不按 expires_at 过滤（我的列表）
)
