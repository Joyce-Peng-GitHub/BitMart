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
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ContactChannel
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import javax.inject.Inject

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

    override suspend fun popularTags(limit: Int): DomainResult<List<String>> =
        api.popularTags(limit).map { it.tags }

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
    contacts = contacts.map { ContactDto(it.channel.name, it.value) },
    tags = tags,
    expiresInDays = expiresInDays,
    book = book?.toDto(),
    imageKeys = imageKeys,
)

private fun BookInfo.toDto() = BookDto(isbn, title, authors, publisher, edition)

private fun BookMetaDto.toBookInfo() = BookInfo(isbn, title, author, publisher, edition)

private fun ListingPageDto.toDomain() = ListingPage(items.map { it.toDomain() }, nextCursor)

/** 构造列表查询参数（list 与 my-listings 共用，键名与后端 parseListingFilter 对齐）。 */
private fun ListingQuery.toParams(): Map<String, String?> = buildMap {
    put("type", type.name)
    category?.let { put("category", it) }
    text?.let { put("q", it) }
    if (tagIds.isNotEmpty()) put("tagIds", tagIds.joinToString(","))
    minPrice?.let { put("minPrice", it) }
    maxPrice?.let { put("maxPrice", it) }
    put("includeNoPrice", includeNoPrice.toString())
    put("includeSold", includeSold.toString())
    cursor?.let { put("cursor", it) }
    limit?.let { put("limit", it.toString()) }
}

private fun ListingSummaryDto.toDomain() = ListingSummary(
    id = id,
    type = enumValueOf<ListingType>(type),
    category = enumValueOf<ListingCategory>(category),
    title = title, unitPrice = unitPrice, quantityTotal = quantityTotal, quantitySold = quantitySold,
    firstImageUrl = firstImageUrl, nickname = nickname, tags = tags, createdAt = createdAt,
)

private fun ListingDetailDto.toDomain() = ListingDetail(
    id = id,
    type = enumValueOf<ListingType>(type),
    category = enumValueOf<ListingCategory>(category),
    userId = userId, nickname = nickname, title = title, description = description,
    unitPrice = unitPrice, quantityTotal = quantityTotal, quantitySold = quantitySold,
    pickupLocation = pickupLocation, contacts = contacts.map { it.toDomain() }, tags = tags,
    imageUrls = imageUrls, expiresAt = expiresAt, createdAt = createdAt, book = book?.toDomain(),
)

private fun ContactDto.toDomain() = Contact(
    channel = runCatching { enumValueOf<ContactChannel>(channel) }.getOrDefault(ContactChannel.OTHER),
    value = value,
)

private fun BookDto.toDomain() = BookInfo(isbn, title, authors, publisher, edition)
