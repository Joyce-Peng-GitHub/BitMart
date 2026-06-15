package cn.edu.bit.bitmart.domain

import cn.edu.bit.bitmart.config.ExpiryConfig
import cn.edu.bit.bitmart.config.TagConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class ListingValidatorTest : FunSpec({

    val expiry = ExpiryConfig(minDays = 1, maxDays = 365, defaultDays = 30)
    val tags = TagConfig(maxPerListing = 8, maxNameLength = 20)
    val validator = ListingValidator(expiry, tags)
    val now = Instant.parse("2026-06-02T00:00:00Z")

    fun validInput(
        title: String = "二手教材",
        quantityTotal: Int = 1,
        unitPrice: BigDecimal? = BigDecimal("30.00"),
        contacts: List<Contact> = listOf(Contact("WECHAT", "wxid_abc")),
        tagList: List<String> = listOf("教材"),
        expiresAt: Instant = now.plus(Duration.ofDays(30)),
        expiryIsAbsolute: Boolean = false,
    ) = ListingInput(title, quantityTotal, unitPrice, contacts, tagList, expiresAt, expiryIsAbsolute)

    fun codes(r: ValidationResult) = r.errors.map { it.code }

    test("合法输入通过校验") {
        validator.validateCreate(validInput(), now).isValid.shouldBeTrue()
    }

    test("价格为空（面议）合法") {
        validator.validateCreate(validInput(unitPrice = null), now).isValid.shouldBeTrue()
    }

    test("价格为零合法，负价格拒绝") {
        validator.validateCreate(validInput(unitPrice = BigDecimal.ZERO), now).isValid.shouldBeTrue()
        val r = validator.validateCreate(validInput(unitPrice = BigDecimal("-1")), now)
        codes(r) shouldContain "PRICE_NEGATIVE"
    }

    test("价格恰好等于上限 99999999.99 合法") {
        validator.validateCreate(validInput(unitPrice = BigDecimal("99999999.99")), now).isValid.shouldBeTrue()
    }

    test("价格超过 NUMERIC(10,2) 上限被拒绝（入库前拦截，不触发 DB 溢出）") {
        codes(validator.validateCreate(validInput(unitPrice = BigDecimal("100000000")), now)) shouldContain "PRICE_TOO_LARGE"
        // 99999999.999 按 scale=2 四舍五入会进位为 100000000.00（9 位整数）而溢出，须同样在入库前拒绝。
        codes(validator.validateCreate(validInput(unitPrice = BigDecimal("99999999.999")), now)) shouldContain "PRICE_TOO_LARGE"
    }

    test("单独价格校验：合法/空通过，负价与超限分别拒绝") {
        validator.validatePriceField(BigDecimal("100.00")).isValid.shouldBeTrue()
        validator.validatePriceField(null).isValid.shouldBeTrue()
        validator.validatePriceField(BigDecimal("99999999.99")).isValid.shouldBeTrue()
        codes(validator.validatePriceField(BigDecimal("-1"))) shouldContain "PRICE_NEGATIVE"
        codes(validator.validatePriceField(BigDecimal("100000000"))) shouldContain "PRICE_TOO_LARGE"
    }

    test("空标题被拒绝") {
        codes(validator.validateCreate(validInput(title = "   "), now)) shouldContain "TITLE_BLANK"
    }

    test("件数为 0 被拒绝") {
        codes(validator.validateCreate(validInput(quantityTotal = 0), now)) shouldContain "QUANTITY_TOTAL_INVALID"
    }

    test("件数恰好等于上限 9999 合法") {
        validator.validateCreate(validInput(quantityTotal = 9999), now).isValid.shouldBeTrue()
    }

    test("件数超过上限被拒绝") {
        codes(validator.validateCreate(validInput(quantityTotal = 10000), now)) shouldContain "QUANTITY_TOTAL_TOO_LARGE"
    }

    test("无联系方式被拒绝") {
        codes(validator.validateCreate(validInput(contacts = emptyList()), now)) shouldContain "CONTACT_REQUIRED"
    }

    test("联系方式内容为空被拒绝") {
        val r = validator.validateCreate(
            validInput(contacts = listOf(Contact("QQ", "  "))), now,
        )
        codes(r) shouldContain "CONTACT_VALUE_BLANK"
    }

    test("标签数量超限被拒绝") {
        val many = (1..9).map { "tag$it" }
        codes(validator.validateCreate(validInput(tagList = many), now)) shouldContain "TAGS_TOO_MANY"
    }

    test("标签恰好达到上限合法") {
        val exactly = (1..8).map { "tag$it" }
        validator.validateCreate(validInput(tagList = exactly), now).isValid.shouldBeTrue()
    }

    test("过长标签被拒绝") {
        val longTag = "x".repeat(21)
        codes(validator.validateCreate(validInput(tagList = listOf(longTag)), now)) shouldContain "TAG_TOO_LONG"
    }

    test("过期时间过早（小于 minDays）被拒绝") {
        val tooSoon = now.plus(Duration.ofHours(12))   // < 1 天
        codes(validator.validateCreate(validInput(expiresAt = tooSoon), now)) shouldContain "EXPIRY_TOO_SOON"
    }

    test("过期时间恰好等于 minDays 边界合法") {
        val exactly = now.plus(Duration.ofDays(1))
        validator.validateCreate(validInput(expiresAt = exactly), now).isValid.shouldBeTrue()
    }

    test("过期时间过晚（大于 maxDays）被拒绝") {
        val tooLate = now.plus(Duration.ofDays(366))
        codes(validator.validateCreate(validInput(expiresAt = tooLate), now)) shouldContain "EXPIRY_TOO_LATE"
    }

    test("过期时间恰好等于 maxDays 边界合法") {
        val exactly = now.plus(Duration.ofDays(365))
        validator.validateCreate(validInput(expiresAt = exactly), now).isValid.shouldBeTrue()
    }

    // —— 绝对过期日期（expiryIsAbsolute=true）：仅要求晚于此刻，允许"明天零点"这类不足一天的情形 ——
    test("绝对过期：明天零点（距今不足一天）合法") {
        val tomorrowStart = now.plus(Duration.ofHours(8))   // < 1 天，但晚于此刻
        validator.validateCreate(validInput(expiresAt = tomorrowStart, expiryIsAbsolute = true), now).isValid.shouldBeTrue()
    }

    test("绝对过期：不晚于此刻被拒绝") {
        codes(validator.validateCreate(validInput(expiresAt = now, expiryIsAbsolute = true), now)) shouldContain "EXPIRY_TOO_SOON"
        val past = now.minus(Duration.ofSeconds(1))
        codes(validator.validateCreate(validInput(expiresAt = past, expiryIsAbsolute = true), now)) shouldContain "EXPIRY_TOO_SOON"
    }

    test("绝对过期：超过 maxDays 仍被拒绝") {
        val tooLate = now.plus(Duration.ofDays(366))
        codes(validator.validateCreate(validInput(expiresAt = tooLate, expiryIsAbsolute = true), now)) shouldContain "EXPIRY_TOO_LATE"
    }

    test("绝对过期：恰好等于 maxDays 边界合法") {
        val exactly = now.plus(Duration.ofDays(365))
        validator.validateCreate(validInput(expiresAt = exactly, expiryIsAbsolute = true), now).isValid.shouldBeTrue()
    }

    test("多个错误一次性累积返回") {
        val bad = validInput(title = "", quantityTotal = 0, contacts = emptyList())
        val r = validator.validateCreate(bad, now)
        r.isValid.shouldBeFalse()
        r.errors.size shouldBe 3
    }

    // —— 售出数量更新 ——
    test("售出数量增加合法") {
        validator.validateQuantitySoldUpdate(currentSold = 2, newSold = 5, quantityTotal = 10).isValid.shouldBeTrue()
    }

    test("售出数量不变合法") {
        validator.validateQuantitySoldUpdate(currentSold = 3, newSold = 3, quantityTotal = 10).isValid.shouldBeTrue()
    }

    test("售出数量减少合法") {
        validator.validateQuantitySoldUpdate(currentSold = 5, newSold = 3, quantityTotal = 10).isValid.shouldBeTrue()
    }

    test("售出数量减为 0 合法") {
        validator.validateQuantitySoldUpdate(currentSold = 5, newSold = 0, quantityTotal = 10).isValid.shouldBeTrue()
    }

    test("售出数量为负被拒绝") {
        val r = validator.validateQuantitySoldUpdate(currentSold = 5, newSold = -1, quantityTotal = 10)
        codes(r) shouldContain "QUANTITY_SOLD_NEGATIVE"
    }

    test("售出数量超过总量被拒绝") {
        val r = validator.validateQuantitySoldUpdate(currentSold = 5, newSold = 11, quantityTotal = 10)
        codes(r) shouldContain "QUANTITY_SOLD_EXCEEDS_TOTAL"
    }

    test("售出数量等于总量（售罄）合法") {
        validator.validateQuantitySoldUpdate(currentSold = 5, newSold = 10, quantityTotal = 10).isValid.shouldBeTrue()
    }

    // —— 延期 ——
    test("延期校验复用过期窗口规则") {
        validator.validateExtension(now.plus(Duration.ofDays(30)), now).isValid.shouldBeTrue()
        codes(validator.validateExtension(now.plus(Duration.ofDays(400)), now)) shouldContain "EXPIRY_TOO_LATE"
    }

    // —— 全字段编辑校验（validateUpdate）：仅校验非 null 字段，与发布同规则 ——
    test("更新校验：全 null 输入合法（不改任何字段）") {
        validator.validateUpdate(ListingUpdateInput(), currentQuantitySold = 0, now = now).isValid.shouldBeTrue()
    }

    test("更新校验：件数不得少于已售出，等于已售出合法") {
        codes(validator.validateUpdate(ListingUpdateInput(quantityTotal = 2), currentQuantitySold = 3, now = now)) shouldContain "QUANTITY_TOTAL_BELOW_SOLD"
        validator.validateUpdate(ListingUpdateInput(quantityTotal = 3), currentQuantitySold = 3, now = now).isValid.shouldBeTrue()
    }

    test("更新校验：件数上限沿用 MAX_QUANTITY") {
        codes(validator.validateUpdate(ListingUpdateInput(quantityTotal = 10000), currentQuantitySold = 0, now = now)) shouldContain "QUANTITY_TOTAL_TOO_LARGE"
    }

    test("更新校验：绝对过期明天合法、超 maxDays 拒绝") {
        validator.validateUpdate(ListingUpdateInput(expiresAt = now.plus(Duration.ofHours(8)), expiryIsAbsolute = true), currentQuantitySold = 0, now = now).isValid.shouldBeTrue()
        codes(validator.validateUpdate(ListingUpdateInput(expiresAt = now.plus(Duration.ofDays(366)), expiryIsAbsolute = true), currentQuantitySold = 0, now = now)) shouldContain "EXPIRY_TOO_LATE"
    }

    test("更新校验：价格超限 / 标签超限 / 空标题 / 无联系方式 分别拒绝") {
        codes(validator.validateUpdate(ListingUpdateInput(unitPrice = BigDecimal("100000000")), 0, now)) shouldContain "PRICE_TOO_LARGE"
        codes(validator.validateUpdate(ListingUpdateInput(tags = (1..9).map { "t$it" }), 0, now)) shouldContain "TAGS_TOO_MANY"
        codes(validator.validateUpdate(ListingUpdateInput(title = "   "), 0, now)) shouldContain "TITLE_BLANK"
        codes(validator.validateUpdate(ListingUpdateInput(contacts = emptyList()), 0, now)) shouldContain "CONTACT_REQUIRED"
    }
})
