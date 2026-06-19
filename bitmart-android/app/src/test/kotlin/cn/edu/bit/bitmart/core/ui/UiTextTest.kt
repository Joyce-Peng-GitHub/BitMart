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
}
