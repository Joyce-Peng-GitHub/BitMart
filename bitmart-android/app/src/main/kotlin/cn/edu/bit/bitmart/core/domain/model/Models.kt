package cn.edu.bit.bitmart.core.domain.model

/** 领域用户模型（对应后端 UserDto）。 */
data class User(
    val id: Long,
    val studentId: String,
    val nickname: String?,
    val displayName: String,
    val role: String,
)

/** 列表类型：卖品 / 求购（与后端枚举名一致）。 */
enum class ListingType { SELL, BUY }

/** 列表品类。 */
enum class ListingCategory { GENERAL, BOOK }

/** 联系方式渠道。 */
enum class ContactChannel { WECHAT, QQ, PHONE, EMAIL, OTHER }

data class Contact(val channel: ContactChannel, val value: String)

/** 列表摘要（列表页）。价格用字符串以匹配后端 NUMERIC 文本表示。 */
data class ListingSummary(
    val id: Long,
    val type: ListingType,
    val category: ListingCategory,
    val title: String,
    val unitPrice: String?,
    val quantityTotal: Int,
    val quantitySold: Int,
    val pickupLocation: String?,
    val nickname: String?,
    val tags: List<String>,
    val createdAt: String,
)

/** 列表详情（详情页，含联系方式）。 */
data class ListingDetail(
    val id: Long,
    val type: ListingType,
    val category: ListingCategory,
    val userId: Long,
    val nickname: String?,
    val title: String,
    val description: String,
    val unitPrice: String?,
    val quantityTotal: Int,
    val quantitySold: Int,
    val pickupLocation: String?,
    val contacts: List<Contact>,
    val tags: List<String>,
    val imageUrls: List<String>,
    val expiresAt: String,
    val createdAt: String,
    val book: BookInfo?,
)

data class BookInfo(
    val isbn: String?,
    val title: String?,
    val authors: String?,
    val publisher: String?,
    val edition: String?,
)

/** 一页列表结果及下一页游标。 */
data class ListingPage(val items: List<ListingSummary>, val nextCursor: String?)
