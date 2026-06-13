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

/** 联系方式渠道提示（仅 UI 层使用，API 不强制枚举）。 */
enum class ContactChannel { WECHAT, QQ, PHONE, EMAIL, OTHER }

data class Contact(val channel: String = "", val value: String)

/**
 * 列表摘要（列表页）。价格用字符串以匹配后端 NUMERIC 文本表示。
 * 取货地点仅详情页可见（架构 §6.3），摘要不含；[firstImageUrl] 为首图的服务端相对路径
 * （如 `/static/...`），展示时需拼接 `BuildConfig.API_BASE_URL` 成绝对地址。
 */
data class ListingSummary(
    val id: Long,
    val type: ListingType,
    val category: ListingCategory,
    val title: String,
    val unitPrice: String?,
    val quantityTotal: Int,
    val quantitySold: Int,
    val firstImageUrl: String?,
    val nickname: String?,
    val tags: List<String>,
    val createdAt: String,
    /** 过期时间（ISO 字符串）。列表据此显示并按临期/已过期着色。 */
    val expiresAt: String,
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

/**
 * 通知（公告 / 个人提醒合并流）。
 * category 为后端的整型分类；isAnnouncement 区分公告与个人提醒（如过期提醒）。
 */
data class Notification(
    val id: Long,
    val category: Int,
    val title: String,
    val body: String,
    val payload: String?,
    val read: Boolean,
    val createdAt: String,
    val isAnnouncement: Boolean,
)

/** 一页通知结果及下一页游标（游标原样回传，客户端不解析）。 */
data class NotificationPage(val items: List<Notification>, val nextCursor: String?)
