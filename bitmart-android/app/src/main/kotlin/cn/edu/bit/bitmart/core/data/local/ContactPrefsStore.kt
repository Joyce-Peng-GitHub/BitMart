package cn.edu.bit.bitmart.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 常用联系方式条目（本地存储，不上传后端）。 */
@Serializable
data class StoredContact(val channel: String, val value: String)

/** 常用联系方式本地存储抽象，便于在 JVM 单测中以内存实现替换。 */
interface ContactPrefsStore {
    /** 当前已保存的联系方式列表（响应式）。 */
    val contactsFlow: Flow<List<StoredContact>>

    /** 追加一条联系方式（去重：相同渠道+内容不重复添加）。 */
    suspend fun add(contact: StoredContact)

    /** 删除指定位置的联系方式。 */
    suspend fun removeAt(index: Int)
}

/** 基于 DataStore 的常用联系方式存储：以 JSON 字符串保存整个列表。 */
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
            if (current.none { it.channel == contact.channel && it.value == contact.value }) {
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
        else runCatching { json.decodeFromString<List<StoredContact>>(raw) }.getOrDefault(emptyList())
}

/** 便于单测读取当前快照。 */
suspend fun ContactPrefsStore.current(): List<StoredContact> = contactsFlow.first()
