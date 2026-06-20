package cn.edu.bit.bitmart.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ListingTypeTest {
    @Test fun `of maps true to BUY`() {
        assertEquals(ListingType.BUY, ListingType.of(true))
    }

    @Test fun `of maps false to SELL`() {
        assertEquals(ListingType.SELL, ListingType.of(false))
    }
}
