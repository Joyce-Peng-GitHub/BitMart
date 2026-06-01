package cn.edu.bit.bitmart.core.domain.repository

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingType

/** 列表查询过滤条件（客户端视图）。 */
data class ListingQuery(
    val type: ListingType,
    val category: String? = null,
    val text: String? = null,
    val tagIds: List<Long> = emptyList(),
    val minPrice: String? = null,
    val maxPrice: String? = null,
    val includeNoPrice: Boolean = true,
    val includeSold: Boolean = false,
    val cursor: String? = null,
    val limit: Int? = null,
)

/** 发布草稿（卖品/求购共用）。 */
data class PublishDraft(
    val type: ListingType,
    val category: String = "GENERAL",
    val title: String,
    val description: String = "",
    val unitPrice: String? = null,
    val quantityTotal: Int = 1,
    val pickupLocation: String? = null,
    val contacts: List<Contact>,
    val tags: List<String> = emptyList(),
    val expiresInDays: Int? = null,
)

/** 列表仓储接口。 */
interface ListingRepository {
    /** 列表查询（公开，无需登录）。 */
    suspend fun list(query: ListingQuery): DomainResult<ListingPage>

    /** 详情（需登录；未登录后端返回 401）。 */
    suspend fun detail(id: Long): DomainResult<ListingDetail>

    /** 发布单条（需登录）。返回新建 id。 */
    suspend fun publish(draft: PublishDraft): DomainResult<Long>

    /** 热门标签。 */
    suspend fun popularTags(limit: Int): DomainResult<List<String>>
}
