package cn.edu.bit.bitmart.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `SYSTEM follows the system flag`() {
        assertTrue(ThemeMode.SYSTEM.resolveDark(systemInDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveDark(systemInDark = false))
    }

    @Test
    fun `LIGHT is always light regardless of system`() {
        assertFalse(ThemeMode.LIGHT.resolveDark(systemInDark = true))
        assertFalse(ThemeMode.LIGHT.resolveDark(systemInDark = false))
    }

    @Test
    fun `DARK is always dark regardless of system`() {
        assertTrue(ThemeMode.DARK.resolveDark(systemInDark = true))
        assertTrue(ThemeMode.DARK.resolveDark(systemInDark = false))
    }

    @Test
    fun `entries are SYSTEM LIGHT DARK in order`() {
        assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK), ThemeMode.entries)
    }
}
