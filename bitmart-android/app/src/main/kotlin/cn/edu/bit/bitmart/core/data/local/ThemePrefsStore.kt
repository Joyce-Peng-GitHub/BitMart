package cn.edu.bit.bitmart.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.edu.bit.bitmart.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ThemePrefsStore {
    val themeModeFlow: Flow<ThemeMode>
    suspend fun setMode(mode: ThemeMode)
}

class DataStoreThemePrefsStore(
    private val dataStore: DataStore<Preferences>,
) : ThemePrefsStore {

    private val key = stringPreferencesKey("theme_mode")

    override val themeModeFlow: Flow<ThemeMode> =
        dataStore.data.map { prefs -> decode(prefs[key]) }

    override suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[key] = mode.name }
    }

    private fun decode(raw: String?): ThemeMode =
        if (raw.isNullOrBlank()) ThemeMode.SYSTEM
        else runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
}

suspend fun ThemePrefsStore.current(): ThemeMode = themeModeFlow.first()
