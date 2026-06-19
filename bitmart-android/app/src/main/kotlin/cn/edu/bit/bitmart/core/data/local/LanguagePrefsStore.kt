package cn.edu.bit.bitmart.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface LanguagePrefsStore {
    val languageFlow: Flow<AppLanguage>
    suspend fun setLanguage(lang: AppLanguage)
}

class DataStoreLanguagePrefsStore(
    private val dataStore: DataStore<Preferences>,
) : LanguagePrefsStore {

    private val key = stringPreferencesKey("app_language")

    override val languageFlow: Flow<AppLanguage> =
        dataStore.data.map { prefs -> decode(prefs[key]) }

    override suspend fun setLanguage(lang: AppLanguage) {
        dataStore.edit { prefs -> prefs[key] = lang.name }
    }

    private fun decode(raw: String?): AppLanguage =
        if (raw.isNullOrBlank()) AppLanguage.SYSTEM
        else runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
}

suspend fun LanguagePrefsStore.current(): AppLanguage = languageFlow.first()
