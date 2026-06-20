package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.domain.Contact
import java.math.BigDecimal
import java.time.OffsetDateTime

/** 创建一条 listing 的输入（领域层，已通过校验）。 */
data class CreateListingInput(
    val type: Int,
    val category: Int,
    val userId: Long,
    val title: String,
    val description: String,
    val unitPrice: BigDecimal?,
    val originalPrice: BigDecimal? = null,
    val quantityTotal: Int,
    val pickupLocation: String?,
    val contacts: List<Contact>,
    val expiresAt: OffsetDateTime,
    /** 过期时间是否来自客户端指定的绝对日期（true）而非天数换算（false）。影响校验下界。 */
    val expiryIsAbsolute: Boolean = false,
    val tags: List<String>,
    val book: BookInput?,
    val imageKeys: List<String> = emptyList(),   // 由 /uploads/images 返回的 blobKey，按顺序入库
    val source: Int = 0,
)

/** 书籍专属输入（仅 category=BOOK）。 */
data class BookInput(
    val isbn: String?,
    val title: String?,
    val authors: String?,
    val publisher: String?,
    val edition: String?,
)

/** 修改一条 listing 的输入（仅非空字段被更新）。 */
data class UpdateListingInput(
    val title: String? = null,
    val description: String? = null,
    val unitPrice: BigDecimal? = null,
    val originalPrice: BigDecimal? = null,
    val clearUnitPrice: Boolean = false,   // 显式置空价格（区分"未改"与"改为面议"）
    val clearOriginalPrice: Boolean = false, // 显式清空原价（区分"未改"与"清空"）
    val pickupLocation: String? = null,
    val quantitySold: Int? = null,
    val expiresAt: OffsetDateTime? = null,
    val expiryIsAbsolute: Boolean = false, // expiresAt 是否来自绝对日期（影响校验下界）
    // —— 全字段编辑新增（均为 null 表示不改） ——
    val category: Int? = null,             // ListingCategory 序号
    val quantityTotal: Int? = null,
    val contacts: List<Contact>? = null,
    val tags: List<String>? = null,
    val imageKeys: List<String>? = null,   // 非 null 则整体替换图片
    val book: BookInput? = null,           // category=BOOK 时的书籍信息
)
