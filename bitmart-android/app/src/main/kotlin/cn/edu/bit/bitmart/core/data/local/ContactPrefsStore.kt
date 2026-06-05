package cn.edu.bit.bitmart.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

typealias StoredContact = String

interface ContactPrefsStore {
    val contactsFlow: Flow<List<StoredContact>>
    suspend fun add(contact: StoredContact)
    suspend fun removeAt(index: Int)
}

class DataStoreContactPrefsStore(
    private val dataStore: DataStore<Preferences>,
) : ContactPrefsStore {

    private val key = stringPreferencesKey("recent_contacts")
    private val json = Json { ignoreUnknownKeys = true }

    override val contactsFlow: Flow<List<StoredContact>> =
        dataStore.data.map { prefs -> decode(prefs[key]) }

    override suspend fun add(contact: StoredContact) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key])
            if (contact !in current) {
                prefs[key] = json.encodeToString(current + contact)
            }
        }
    }

    override suspend fun removeAt(index: Int) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key])
            if (index in current.indices) {
                prefs[key] = json.encodeToString(current.filterIndexed { i, _ -> i != index })
            }
        }
    }

    private fun decode(raw: String?): List<StoredContact> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}

suspend fun ContactPrefsStore.current(): List<StoredContact> = contactsFlow.first()
