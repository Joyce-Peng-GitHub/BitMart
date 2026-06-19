package cn.edu.bit.bitmart.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.edu.bit.bitmart.core.data.local.DataStoreThemePrefsStore
import cn.edu.bit.bitmart.core.data.local.ThemePrefsStore
import cn.edu.bit.bitmart.core.data.local.current
import cn.edu.bit.bitmart.core.domain.model.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePrefsStoreTest {

    @Test
    fun `default is SYSTEM when unset`() = runTest {
        val store: ThemePrefsStore = DataStoreThemePrefsStore(InMemoryPreferencesDataStore())
        assertEquals(ThemeMode.SYSTEM, store.current())
    }

    @Test
    fun `setMode round-trips DARK`() = runTest {
        val store: ThemePrefsStore = DataStoreThemePrefsStore(InMemoryPreferencesDataStore())
        store.setMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.current())
    }

    @Test
    fun `setMode round-trips LIGHT`() = runTest {
        val store: ThemePrefsStore = DataStoreThemePrefsStore(InMemoryPreferencesDataStore())
        store.setMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, store.current())
    }

    @Test
    fun `garbage value decodes to SYSTEM`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "not-a-mode" }
        val store: ThemePrefsStore = DataStoreThemePrefsStore(dataStore)
        assertEquals(ThemeMode.SYSTEM, store.current())
    }
}
