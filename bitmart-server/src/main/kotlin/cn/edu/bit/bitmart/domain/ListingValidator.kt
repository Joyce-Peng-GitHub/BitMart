package cn.edu.bit.bitmart.domain

import cn.edu.bit.bitmart.config.ExpiryConfig
import cn.edu.bit.bitmart.config.TagConfig
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * 列表（卖品/求购）发布与修改的领域校验。纯 Kotlin，不依赖框架，便于单测覆盖边界。
 * 卖与买对偶，共用同一套规则；价格语义不同（售价 vs 期望价）但校验一致。
 */
class ListingValidator(
    private val expiryConfig: ExpiryConfig,
    private val tagConfig: TagConfig,
) {

    /**
     * 校验一条待发布的列表。
     * @param now 当前时刻（注入以便测试）。
     */
    fun validateCreate(input: ListingInput, now: Instant): ValidationResult {
        val errors = ValidationErrors()
        validateTitle(input.title, errors)
        validateQuantity(input.quantityTotal, errors)
        validatePrice(input.unitPrice, errors)
        validateContacts(input.contacts, errors)
        validateTags(input.tags, errors)
        validateExpiry(input.expiresAt, now, errors)
        return errors.build()
    }

    /**
     * 校验售出数量更新：允许增加或减少，但不得超过总量且不得小于 0（架构 §9）。
     */
    fun validateQuantitySoldUpdate(
        currentSold: Int,
        newSold: Int,
        quantityTotal: Int,
    ): ValidationResult {
        val errors = ValidationErrors()
        errors.check(
            newSold >= 0,
            field = "quantitySold",
            code = "QUANTITY_SOLD_NEGATIVE",
            message = "售出数量不能为负",
        )
        errors.check(
            newSold <= quantityTotal,
            field = "quantitySold",
            code = "QUANTITY_SOLD_EXCEEDS_TOTAL",
            message = "售出数量 $newSold 不能超过总量 $quantityTotal",
        )
        return errors.build()
    }

    /**
     * 校验延期：新过期时间须落在 [now + minDays, now + maxDays] 内（架构 §9）。
     * 创建与延期同源，复用 validateExpiry。
     */
    fun validateExtension(newExpiresAt: Instant, now: Instant): ValidationResult {
        val errors = ValidationErrors()
        validateExpiry(newExpiresAt, now, errors)
        return errors.build()
    }

    private fun validateTitle(title: String, errors: ValidationErrors) {
        errors.check(title.isNotBlank(), "title", "TITLE_BLANK", "标题不能为空")
    }

    private fun validateQuantity(quantityTotal: Int, errors: ValidationErrors) {
        errors.check(quantityTotal >= 1, "quantityTotal", "QUANTITY_TOTAL_INVALID", "件数必须 >= 1")
    }

    private fun validatePrice(unitPrice: BigDecimal?, errors: ValidationErrors) {
        // 价格允许留空（面议/带价联系）；若填写则不可为负。
        if (unitPrice != null) {
            errors.check(
                unitPrice.signum() >= 0,
                field = "unitPrice",
                code = "PRICE_NEGATIVE",
                message = "价格不能为负",
            )
        }
    }

    private fun validateContacts(contacts: List<Contact>, errors: ValidationErrors) {
        if (contacts.isEmpty()) {
            errors.add("contact", "CONTACT_REQUIRED", "至少需要一种联系方式")
            return
        }
        contacts.forEachIndexed { index, contact ->
            errors.check(
                contact.value.isNotBlank(),
                field = "contact[$index].value",
                code = "CONTACT_VALUE_BLANK",
                message = "联系方式内容不能为空",
            )
        }
    }

    private fun validateTags(tags: List<String>, errors: ValidationErrors) {
        errors.check(
            tags.size <= tagConfig.maxPerListing,
            field = "tags",
            code = "TAGS_TOO_MANY",
            message = "标签数量不能超过 ${tagConfig.maxPerListing} 个",
        )
        tags.forEachIndexed { index, tag ->
            errors.check(
                tag.isNotBlank(),
                field = "tags[$index]",
                code = "TAG_BLANK",
                message = "标签不能为空",
            )
            errors.check(
                tag.length <= tagConfig.maxNameLength,
                field = "tags[$index]",
                code = "TAG_TOO_LONG",
                message = "标签长度不能超过 ${tagConfig.maxNameLength} 个字符",
            )
        }
    }

    private fun validateExpiry(expiresAt: Instant, now: Instant, errors: ValidationErrors) {
        val earliest = now.plus(Duration.ofDays(expiryConfig.minDays.toLong()))
        val latest = now.plus(Duration.ofDays(expiryConfig.maxDays.toLong()))
        errors.check(
            !expiresAt.isBefore(earliest),
            field = "expiresAt",
            code = "EXPIRY_TOO_SOON",
            message = "过期时间不得早于 ${expiryConfig.minDays} 天后",
        )
        errors.check(
            !expiresAt.isAfter(latest),
            field = "expiresAt",
            code = "EXPIRY_TOO_LATE",
            message = "过期时间不得晚于 ${expiryConfig.maxDays} 天后",
        )
    }
}

/** 待校验的列表输入（领域层视图，与传输层 DTO 解耦）。 */
data class ListingInput(
    val title: String,
    val quantityTotal: Int,
    val unitPrice: BigDecimal?,
    val contacts: List<Contact>,
    val tags: List<String>,
    val expiresAt: Instant,
)
