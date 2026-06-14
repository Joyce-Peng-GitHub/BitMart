package cn.edu.bit.bitmart.core.domain.repository

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
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
    val includeExpired: Boolean = false,
    val cursor: String? = null,
    val limit: Int? = null,
)

/** 发布草稿（卖品/求购共用）。 */
data class PublishDraft(
    val type: ListingType,
    val category: ListingCategory = ListingCategory.GENERAL,
    val title: String,
    val description: String = "",
    val unitPrice: String? = null,
    val quantityTotal: Int = 1,
    val pickupLocation: String? = null,
    val contacts: List<Contact>,
    val tags: List<String> = emptyList(),
    val expiresInDays: Int? = null,
    /** 绝对过期时间（ISO-8601）。非空时优先于 [expiresInDays]，用于"按过期日"发布。 */
    val expiresAtIso: String? = null,
    val book: BookInfo? = null,
    val imageKeys: List<String> = emptyList(),
)

/** 修改草稿（更新现有 listing）。 */
data class UpdateDraft(
    val title: String? = null,
    val description: String? = null,
    val unitPrice: String? = null,
    val clearUnitPrice: Boolean = false,
    val pickupLocation: String? = null,
    val quantitySold: Int? = null,
    val expiresInDays: Int? = null,
)

/** 列表仓储接口。 */
interface ListingRepository {
    /** 列表查询（公开，无需登录）。 */
    suspend fun list(query: ListingQuery): DomainResult<ListingPage>

    /**
     * 当前用户自己发布的列表（需登录，架构 §6.2）。含已售罄/已过期，排除软删除；
     * keyset 分页，摘要字段同 [list]。
     */
    suspend fun myListings(query: ListingQuery): DomainResult<ListingPage>

    /** 详情（需登录；未登录后端返回 401）。 */
    suspend fun detail(id: Long): DomainResult<ListingDetail>

    /** 发布单条（需登录）。返回新建 id。 */
    suspend fun publish(draft: PublishDraft): DomainResult<Long>

    /** 批量发布（需登录）。全部成功或全部回滚，返回所有新建 id。 */
    suspend fun publishBatch(drafts: List<PublishDraft>): DomainResult<List<Long>>

    /** 上传图片（需登录）。返回 blobKey 供 draft 使用。 */
    suspend fun uploadImage(bytes: ByteArray, filename: String): DomainResult<String>

    /** ISBN 查询书籍元数据（服务端代理 ShowAPI）。404 → null。 */
    suspend fun lookupBook(isbn: String): DomainResult<BookInfo?>

    /** 修改 listing（需登录，仅本人或管理员）。 */
    suspend fun update(id: Long, update: UpdateDraft): DomainResult<Unit>

    /** 删除 listing（需登录，仅本人或管理员）。 */
    suspend fun delete(id: Long): DomainResult<Unit>

    /** 热门标签。 */
    suspend fun popularTags(limit: Int): DomainResult<List<TagInfo>>
}

data class TagInfo(val id: Long, val name: String)
