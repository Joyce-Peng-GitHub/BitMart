package cn.edu.bit.bitmart.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 会话令牌本地存储抽象，便于在 JVM 单测中以内存实现替换。 */
interface TokenStore {
    val tokenFlow: Flow<String?>
    suspend fun current(): String?
    suspend fun save(token: String)
    suspend fun clear()
}

/** 基于 DataStore 的令牌存储实现。令牌为后端签发的不透明 token。 */
class DataStoreTokenStore(private val dataStore: DataStore<Preferences>) : TokenStore {

    private val tokenKey = stringPreferencesKey("auth_token")

    override val tokenFlow: Flow<String?> = dataStore.data.map { it[tokenKey] }

    override suspend fun current(): String? = tokenFlow.first()

    override suspend fun save(token: String) {
        dataStore.edit { it[tokenKey] = token }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(tokenKey) }
    }
}
