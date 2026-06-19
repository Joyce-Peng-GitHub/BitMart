package cn.edu.bit.bitmart.core.ui

import cn.edu.bit.bitmart.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorCodeResTest {
    @Test fun known_codes_map_to_specific_strings() {
        assertEquals(R.string.error_unauthorized, errorMessageRes("UNAUTHORIZED"))
        assertEquals(R.string.error_validation_failed, errorMessageRes("VALIDATION_FAILED"))
        assertEquals(R.string.error_rate_limited, errorMessageRes("RATE_LIMITED"))
        assertEquals(R.string.error_generic, errorMessageRes("INTERNAL_ERROR"))
    }

    @Test fun unknown_code_maps_to_generic() {
        assertEquals(R.string.error_generic, errorMessageRes("SOMETHING_ELSE"))
        assertEquals(R.string.error_generic, errorMessageRes(""))
    }
}
