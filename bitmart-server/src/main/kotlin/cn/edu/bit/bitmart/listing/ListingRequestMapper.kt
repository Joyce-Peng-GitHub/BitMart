package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.config.ExpiryConfig
import cn.edu.bit.bitmart.domain.Contact
import cn.edu.bit.bitmart.domain.ListingCategory
import cn.edu.bit.bitmart.domain.ListingType
import java.math.BigDecimal
import java.time.OffsetDateTime

/** 解析失败异常，路由层捕获后返回 400。 */
class RequestMappingException(message: String) : RuntimeException(message)

/**
 * 请求 DTO → 领域输入的映射。集中处理枚举解析、价格解析、过期天数换算，
 * 使过期默认值来自配置（expiresInDays 缺省时用 defaultDays）。
 */
class ListingRequestMapper(private val expiryConfig: ExpiryConfig) {

    fun toCreateInput(req: CreateListingRequest, userId: Long, now: OffsetDateTime): CreateListingInput {
        val type = parseEnum<ListingType>(req.type, "type")
        val category = parseEnum<ListingCategory>(req.category, "category")
        // 优先用客户端指定的绝对过期时间（按"过期日"换算的瞬时）；否则按天数换算。
        val absoluteExpiry = req.expiresAt?.let { parseExpiresAt(it) }
        val days = req.expiresInDays ?: expiryConfig.defaultDays
        return CreateListingInput(
            type = type.ordinal,
            category = category.ordinal,
            userId = userId,
            title = req.title,
            description = req.description,
            unitPrice = parsePrice(req.unitPrice),
            quantityTotal = req.quantityTotal,
            pickupLocation = req.pickupLocation,
            contacts = req.contacts.map { it.toContact() },
            expiresAt = absoluteExpiry ?: now.plusDays(days.toLong()),
            expiryIsAbsolute = absoluteExpiry != null,
            tags = req.tags,
            book = req.book?.let { BookInput(it.isbn, it.title, it.authors, it.publisher, it.edition) },
            imageKeys = req.imageKeys,
        )
    }

    fun toUpdateInput(req: UpdateListingRequest, now: OffsetDateTime): UpdateListingInput =
        UpdateListingInput(
            title = req.title,
            description = req.description,
            unitPrice = parsePrice(req.unitPrice),
            clearUnitPrice = req.clearUnitPrice,
            pickupLocation = req.pickupLocation,
            quantitySold = req.quantitySold,
            expiresAt = req.expiresInDays?.let { now.plusDays(it.toLong()) },
        )

    private fun ContactDto.toContact(): Contact = Contact(channel, value)

    private fun parsePrice(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        return raw.toBigDecimalOrNull() ?: throw RequestMappingException("价格格式非法: $raw")
    }

    private fun parseExpiresAt(raw: String): OffsetDateTime =
        runCatching { OffsetDateTime.parse(raw) }.getOrElse {
            throw RequestMappingException("过期时间格式非法: $raw")
        }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, field: String): T =
        enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw RequestMappingException("$field 取值非法: $raw")
}
