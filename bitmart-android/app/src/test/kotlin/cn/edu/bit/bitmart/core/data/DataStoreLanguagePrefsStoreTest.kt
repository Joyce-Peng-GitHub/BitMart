package cn.edu.bit.bitmart.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.edu.bit.bitmart.core.data.local.DataStoreLanguagePrefsStore
import cn.edu.bit.bitmart.core.data.local.LanguagePrefsStore
import cn.edu.bit.bitmart.core.data.local.current
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreLanguagePrefsStoreTest {

    @Test
    fun `default is SYSTEM when unset`() = runTest {
        val store: LanguagePrefsStore = DataStoreLanguagePrefsStore(InMemoryPreferencesDataStore())
        assertEquals(AppLanguage.SYSTEM, store.current())
    }

    @Test
    fun `setLanguage round-trips EN`() = runTest {
        val store: LanguagePrefsStore = DataStoreLanguagePrefsStore(InMemoryPreferencesDataStore())
        store.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, store.current())
    }

    @Test
    fun `setLanguage round-trips ZH`() = runTest {
        val store: LanguagePrefsStore = DataStoreLanguagePrefsStore(InMemoryPreferencesDataStore())
        store.setLanguage(AppLanguage.ZH)
        assertEquals(AppLanguage.ZH, store.current())
    }

    @Test
    fun `garbage value decodes to SYSTEM`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { it[stringPreferencesKey("app_language")] = "not-a-language" }
        val store: LanguagePrefsStore = DataStoreLanguagePrefsStore(dataStore)
        assertEquals(AppLanguage.SYSTEM, store.current())
    }
}
