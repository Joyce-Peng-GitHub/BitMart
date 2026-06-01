package cn.edu.bit.bitmart.core.data.repository

import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.data.remote.BookDto
import cn.edu.bit.bitmart.core.data.remote.ContactDto
import cn.edu.bit.bitmart.core.data.remote.CreateListingRequest
import cn.edu.bit.bitmart.core.data.remote.ListingDetailDto
import cn.edu.bit.bitmart.core.data.remote.ListingPageDto
import cn.edu.bit.bitmart.core.data.remote.ListingSummaryDto
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

    override suspend fun list(query: ListingQuery): DomainResult<ListingPage> {
        val params = buildMap {
            put("type", query.type.name)
            query.category?.let { put("category", it) }
            query.text?.let { put("q", it) }
            if (query.tagIds.isNotEmpty()) put("tagIds", query.tagIds.joinToString(","))
            query.minPrice?.let { put("minPrice", it) }
            query.maxPrice?.let { put("maxPrice", it) }
            put("includeNoPrice", query.includeNoPrice.toString())
            put("includeSold", query.includeSold.toString())
            query.cursor?.let { put("cursor", it) }
            query.limit?.let { put("limit", it.toString()) }
        }
        return api.listListings(params).map { it.toDomain() }
    }

    override suspend fun detail(id: Long): DomainResult<ListingDetail> =
        api.listingDetail(id).map { it.toDomain() }

    override suspend fun publish(draft: PublishDraft): DomainResult<Long> =
        api.createListing(
            CreateListingRequest(
                type = draft.type.name,
                category = draft.category,
                title = draft.title,
                description = draft.description,
                unitPrice = draft.unitPrice,
                quantityTotal = draft.quantityTotal,
                pickupLocation = draft.pickupLocation,
                contacts = draft.contacts.map { ContactDto(it.channel.name, it.value) },
                tags = draft.tags,
                expiresInDays = draft.expiresInDays,
            ),
        ).map { it.id }

    override suspend fun popularTags(limit: Int): DomainResult<List<String>> =
        api.popularTags(limit).map { it.tags }
}

private fun ListingPageDto.toDomain() = ListingPage(items.map { it.toDomain() }, nextCursor)

private fun ListingSummaryDto.toDomain() = ListingSummary(
    id = id,
    type = enumValueOf<ListingType>(type),
    category = enumValueOf<ListingCategory>(category),
    title = title, unitPrice = unitPrice, quantityTotal = quantityTotal, quantitySold = quantitySold,
    pickupLocation = pickupLocation, nickname = nickname, tags = tags, createdAt = createdAt,
)

private fun ListingDetailDto.toDomain() = ListingDetail(
    id = id,
    type = enumValueOf<ListingType>(type),
    category = enumValueOf<ListingCategory>(category),
    userId = userId, nickname = nickname, title = title, description = description,
    unitPrice = unitPrice, quantityTotal = quantityTotal, quantitySold = quantitySold,
    pickupLocation = pickupLocation, contacts = contacts.map { it.toDomain() }, tags = tags,
    expiresAt = expiresAt, createdAt = createdAt, book = book?.toDomain(),
)

private fun ContactDto.toDomain() = Contact(
    channel = runCatching { enumValueOf<ContactChannel>(channel) }.getOrDefault(ContactChannel.OTHER),
    value = value,
)

private fun BookDto.toDomain() = BookInfo(isbn, title, authors, publisher, edition)
