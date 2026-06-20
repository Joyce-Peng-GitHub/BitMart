package cn.edu.bit.bitmart.core.data.repository

import cn.edu.bit.bitmart.core.data.remote.BatchCreateRequest
import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.data.remote.BookDto
import cn.edu.bit.bitmart.core.data.remote.BookMetaDto
import cn.edu.bit.bitmart.core.data.remote.ContactDto
import cn.edu.bit.bitmart.core.data.remote.CreateListingRequest
import cn.edu.bit.bitmart.core.data.remote.ListingDetailDto
import cn.edu.bit.bitmart.core.data.remote.ListingPageDto
import cn.edu.bit.bitmart.core.data.remote.ListingSummaryDto
import cn.edu.bit.bitmart.core.data.remote.UpdateListingRequest
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.map
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.repository.TagInfo
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import javax.inject.Inject
import kotlin.enums.enumEntries

/** ListingRepository 实现：构造查询参数、调用 API、映射 DTO → 领域模型。 */
class ListingRepositoryImpl @Inject constructor(private val api: BitMartApi) : ListingRepository {

    override suspend fun list(query: ListingQuery): DomainResult<ListingPage> =
        api.listListings(query.toParams()).map { it.toDomain() }

    override suspend fun myListings(query: ListingQuery): DomainResult<ListingPage> =
        api.myListings(query.toParams()).map { it.toDomain() }

    override suspend fun detail(id: Long): DomainResult<ListingDetail> =
        api.listingDetail(id).map { it.toDomain() }

    override suspend fun publish(draft: PublishDraft): DomainResult<Long> =
        api.createListing(draft.toCreateRequest()).map { it.id }

    override suspend fun publishBatch(drafts: List<PublishDraft>): DomainResult<List<Long>> =
        api.createListingBatch(BatchCreateRequest(drafts.map { it.toCreateRequest() })).map { it.ids }

    override suspend fun uploadImage(bytes: ByteArray, filename: String): DomainResult<String> =
        api.uploadImage(bytes, filename).map { it.blobKey }

    override suspend fun lookupBook(isbn: String): DomainResult<BookInfo?> =
        api.lookupBook(isbn).map { it?.toBookInfo() }

    override suspend fun popularTags(limit: Int): DomainResult<List<TagInfo>> =
        api.popularTags(limit).map { it.tags.map { t -> TagInfo(t.id, t.name) } }

    override suspend fun update(id: Long, update: cn.edu.bit.bitmart.core.domain.repository.UpdateDraft): DomainResult<Unit> =
        api.updateListing(
            id,
            UpdateListingRequest(
                title = update.title,
                description = update.description,
                unitPrice = update.unitPrice,
                clearUnitPrice = update.clearUnitPrice,
                pickupLocation = update.pickupLocation,
                quantitySold = update.quantitySold,
                expiresInDays = update.expiresInDays,
                expiresAt = update.expiresAtIso,
                category = update.category?.name,
                quantityTotal = update.quantityTotal,
                contacts = update.contacts?.map { ContactDto(it.channel, it.value) },
                tags = update.tags,
                imageKeys = update.imageKeys,
                book = update.book?.toDto(),
                originalPrice = update.originalPrice,
                clearOriginalPrice = update.clearOriginalPrice,
            ),
        )

    override suspend fun delete(id: Long): DomainResult<Unit> =
        api.deleteListing(id)
}

private fun PublishDraft.toCreateRequest() = CreateListingRequest(
    type = type.name,
    category = category.name,
    title = title,
    description = description,
    unitPrice = unitPrice,
    quantityTotal = quantityTotal,
    pickupLocation = pickupLocation,
    contacts = contacts.map { ContactDto(it.channel, it.value) },
    tags = tags,
    expiresInDays = expiresInDays,
    expiresAt = expiresAtIso,
    book = book?.toDto(),
    imageKeys = imageKeys,
    originalPrice = originalPrice,
)
private fun BookInfo.toDto() = BookDto(isbn, title, authors, publisher, edition)

private fun BookMetaDto.toBookInfo() = BookInfo(isbn, title, author, publisher, edition, price = price)

private fun ListingPageDto.toDomain() = ListingPage(items.map { it.toDomain() }, nextCursor)

/** 构造列表查询参数（list 与 my-listings 共用，键名与后端 parseListingFilter 对齐）。 */
private fun ListingQuery.toParams(): Map<String, String?> = buildMap {
    put("type", type.name)
    category?.let { put("category", it) }
    text?.let { put("q", it) }
    if (tagNames.isNotEmpty()) put("tags", tagNames.joinToString(","))
    minPrice?.let { put("minPrice", it) }
    maxPrice?.let { put("maxPrice", it) }
    put("includeNoPrice", includeNoPrice.toString())
    put("includeSold", includeSold.toString())
    put("includeExpired", includeExpired.toString())
    cursor?.let { put("cursor", it) }
    limit?.let { put("limit", it.toString()) }
}

private fun ListingSummaryDto.toDomain() = ListingSummary(
    id = id,
    ownerId = ownerId,
    type = enumOrFallback(type, ListingType.SELL),
    category = enumOrFallback(category, ListingCategory.GENERAL),
    title = title, unitPrice = unitPrice, quantityTotal = quantityTotal, quantitySold = quantitySold,
    firstImageUrl = firstImageUrl, nickname = nickname, tags = tags, createdAt = createdAt, expiresAt = expiresAt,
)

private fun ListingDetailDto.toDomain() = ListingDetail(
    id = id,
    type = enumOrFallback(type, ListingType.SELL),
    category = enumOrFallback(category, ListingCategory.GENERAL),
    userId = userId, nickname = nickname, title = title, description = description,
    unitPrice = unitPrice, quantityTotal = quantityTotal, quantitySold = quantitySold,
    pickupLocation = pickupLocation, contacts = contacts.map { it.toDomain() }, tags = tags,
    imageUrls = imageUrls, expiresAt = expiresAt, createdAt = createdAt, book = book?.toDomain(),
    originalPrice = originalPrice,
)

private fun ContactDto.toDomain() = Contact(channel = channel, value = value)

private fun BookDto.toDomain() = BookInfo(isbn, title, authors, publisher, edition)

/**
 * 容错解析后端返回的枚举字符串：未知值（服务端新增类别 / 版本漂移）回退到 [fallback]，
 * 而非抛 IllegalArgumentException。该 String→enum 转换发生在 DomainResult.map{} 内、
 * BitMartApi.safe{} 边界之外，一旦抛出会直接崩溃而非降级；回退可让列表页其余条目正常渲染。
 * 与 ApiResponseMapper 的 ignoreUnknownKeys 一致地“宽容向前兼容”。
 *
 * 回退值选取：[ListingCategory.GENERAL] 是天然的兜底类别（书籍专属 UI 不生效即可）；
 * type 始终由查询上下文决定（list 查询下发 type=SELL/BUY 由后端过滤，未知 type 近乎不可能），
 * SELL 为主类型，此处唯一诉求是“不崩溃”。
 */
private inline fun <reified T : Enum<T>> enumOrFallback(raw: String, fallback: T): T =
    enumEntries<T>().firstOrNull { it.name == raw } ?: fallback
