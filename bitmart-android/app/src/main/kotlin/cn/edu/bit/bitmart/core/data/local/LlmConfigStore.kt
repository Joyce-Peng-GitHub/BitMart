package cn.edu.bit.bitmart.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.edu.bit.bitmart.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** LLM 配置本地存储抽象，便于在 JVM 单测中以内存实现替换。 */
interface LlmConfigStore {
    /** 当前已保存的 LLM 配置（响应式）；未保存时为默认 [LlmConfig]。 */
    val configFlow: Flow<LlmConfig>

    /** 保存整份配置（覆盖）。 */
    suspend fun save(config: LlmConfig)

    /** 清空已保存的配置（恢复为默认）。 */
    suspend fun clear()
}

/**
 * 基于 DataStore 的 LLM 配置存储：整份配置以 JSON 字符串保存。
 *
 * TODO(security): API Key 应迁移到 EncryptedDataStore（Tink/Jetpack Security），见架构 §5.4。
 * 当前与 ContactPrefsStore 一致明文存于 DataStore（“轻量优先、后续加固”约定）；
 * Key 仅留存本地、不随任何业务请求上传 BitMart 服务器，加密落盘为可分离的硬化步骤。
 */
class DataStoreLlmConfigStore(
    private val dataStore: DataStore<Preferences>,
) : LlmConfigStore {

    private val key = stringPreferencesKey("llm_config")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val configFlow: Flow<LlmConfig> =
        dataStore.data.map { prefs -> decode(prefs[key]) }

    override suspend fun save(config: LlmConfig) {
        dataStore.edit { prefs -> prefs[key] = json.encodeToString(config) }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(key) }
    }

    private fun decode(raw: String?): LlmConfig =
        if (raw.isNullOrBlank()) LlmConfig()
        else runCatching { json.decodeFromString<LlmConfig>(raw) }.getOrDefault(LlmConfig())
}

/** 便于单测读取当前快照。 */
suspend fun LlmConfigStore.current(): LlmConfig = configFlow.first()
