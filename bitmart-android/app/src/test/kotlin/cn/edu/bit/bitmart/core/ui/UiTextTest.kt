package cn.edu.bit.bitmart.core.ui

import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.ValidationDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {

    @Test
    fun `failure without details maps by code`() {
        val r = DomainResult.Failure("VALIDATION_FAILED", "x", 400)
        assertEquals(UiText.Res(R.string.error_validation_failed), r.toUiText())
    }

    @Test
    fun `single password-too-short detail maps to localized template with minLength arg`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("password", "PASSWORD_TOO_SHORT", mapOf("minLength" to "8"))),
        )
        assertEquals(UiText.Res(R.string.error_password_too_short, listOf("8")), r.toUiText())
    }

    @Test
    fun `multiple details map to Multi of localized templates`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(
                ValidationDetail("password", "PASSWORD_TOO_SHORT", mapOf("minLength" to "8")),
                ValidationDetail("password", "PASSWORD_TOO_SIMPLE", mapOf("minCharClasses" to "2")),
            ),
        )
        val expected = UiText.Multi(
            listOf(
                UiText.Res(R.string.error_password_too_short, listOf("8")),
                UiText.Res(R.string.error_password_too_simple, listOf("2")),
            ),
        )
        assertEquals(expected, r.toUiText())
    }

    @Test
    fun `unknown detail code falls back to generic validation message`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("password", "SOMETHING_NEW", emptyMap())),
        )
        assertEquals(UiText.Res(R.string.error_validation_failed), r.toUiText())
    }

    // —— 发布/编辑校验明细映射 ——

    @Test
    fun `original price too-large detail maps to original-price range with max arg`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("originalPrice", "PRICE_TOO_LARGE", mapOf("max" to "99999999.99"))),
        )
        assertEquals(
            UiText.Res(R.string.publish_error_original_price_range, listOf("99999999.99")),
            r.toUiText(),
        )
    }

    @Test
    fun `unit price negative detail maps to unit-price range, not original`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("unitPrice", "PRICE_NEGATIVE", mapOf("max" to "99999999.99"))),
        )
        assertEquals(
            UiText.Res(R.string.publish_error_unit_price_range, listOf("99999999.99")),
            r.toUiText(),
        )
    }

    @Test
    fun `quantity too-large detail maps to quantity range with max arg`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("quantityTotal", "QUANTITY_TOTAL_TOO_LARGE", mapOf("max" to "9999"))),
        )
        assertEquals(
            UiText.Res(R.string.publish_error_quantity_range, listOf("9999")),
            r.toUiText(),
        )
    }

    @Test
    fun `expiry-too-late detail maps to expiry range with both day args`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("expiresAt", "EXPIRY_TOO_LATE", mapOf("minDays" to "1", "maxDays" to "365"))),
        )
        assertEquals(
            UiText.Res(R.string.publish_error_expiry_range, listOf("1", "365")),
            r.toUiText(),
        )
    }

    @Test
    fun `contact-value-blank with indexed subfield reduces to base contact field`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("contact[0].value", "CONTACT_VALUE_BLANK", emptyMap())),
        )
        assertEquals(UiText.Res(R.string.publish_error_contact_blank), r.toUiText())
    }

    @Test
    fun `tag-blank with indexed subfield reduces to base tags field`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("tags[3]", "TAG_BLANK", emptyMap())),
        )
        assertEquals(UiText.Res(R.string.publish_error_tag_blank), r.toUiText())
    }

    @Test
    fun `batch-prefixed field wraps the inner message with its 1-based item number`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(ValidationDetail("items[2].unitPrice", "PRICE_TOO_LARGE", mapOf("max" to "99999999.99"))),
        )
        // items[2] → 第 3 条；内层为单价区间文案。
        assertEquals(
            UiText.Res(
                R.string.publish_error_batch_item,
                listOf(3, UiText.Res(R.string.publish_error_unit_price_range, listOf("99999999.99"))),
            ),
            r.toUiText(),
        )
    }

    @Test
    fun `toFirstProblemUiText returns only the first of multiple details`() {
        val r = DomainResult.Failure(
            "VALIDATION_FAILED", "x", 400,
            details = listOf(
                ValidationDetail("items[0].title", "TITLE_BLANK", emptyMap()),
                ValidationDetail("items[1].unitPrice", "PRICE_TOO_LARGE", mapOf("max" to "99999999.99")),
            ),
        )
        // 仅展示首个问题：第 1 条标题为空。
        assertEquals(
            UiText.Res(
                R.string.publish_error_batch_item,
                listOf(1, UiText.Res(R.string.publish_error_title_required)),
            ),
            r.toFirstProblemUiText(),
        )
    }

    @Test
    fun `toFirstProblemUiText without details falls back to error code mapping`() {
        val r = DomainResult.Failure("VALIDATION_FAILED", "x", 400)
        assertEquals(UiText.Res(R.string.error_validation_failed), r.toFirstProblemUiText())
    }

    @Test
    fun `toFirstProblemUiText on network error mirrors toUiText`() {
        val r = DomainResult.NetworkError("offline")
        assertEquals(UiText.Res(R.string.error_network), r.toFirstProblemUiText())
    }
}
