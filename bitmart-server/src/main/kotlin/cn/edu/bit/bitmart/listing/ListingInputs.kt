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
    val quantityTotal: Int,
    val pickupLocation: String?,
    val contacts: List<Contact>,
    val expiresAt: OffsetDateTime,
    val tags: List<String>,
    val book: BookInput?,
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
    val clearUnitPrice: Boolean = false,   // 显式置空价格（区分"未改"与"改为面议"）
    val pickupLocation: String? = null,
    val quantitySold: Int? = null,
    val expiresAt: OffsetDateTime? = null,
)
