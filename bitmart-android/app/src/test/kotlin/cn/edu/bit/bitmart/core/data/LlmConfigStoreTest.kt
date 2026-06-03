package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.DataStoreLlmConfigStore
import cn.edu.bit.bitmart.core.data.local.LlmConfigStore
import cn.edu.bit.bitmart.core.data.local.current
import cn.edu.bit.bitmart.llm.LlmConfig
import cn.edu.bit.bitmart.llm.LlmProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmConfigStoreTest {

    @Test
    fun `fake round-trips full config`() = runTest {
        val store: LlmConfigStore = FakeLlmConfigStore()
        val config = LlmConfig(
            protocol = LlmProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com",
            apiKey = "sk-abc",
            model = "gpt-4o",
            timeoutSeconds = 45,
            bookPrompt = "book",
            generalPrompt = "general",
        )
        store.save(config)
        assertEquals(config, store.current())
    }

    @Test
    fun `fake clear resets to default`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "https://x", apiKey = "k", model = "m"))
        store.clear()
        assertEquals(LlmConfig(), store.current())
    }

    @Test
    fun `datastore decodes blank to default`() = runTest {
        // 直接验证解码兜底：用内存 DataStore 在 DataStoreLlmConfigStore 下读未写入的默认值。
        val store = DataStoreLlmConfigStore(InMemoryPreferencesDataStore())
        assertEquals(LlmConfig(), store.current())
    }

    @Test
    fun `datastore round-trips and clears`() = runTest {
        val store = DataStoreLlmConfigStore(InMemoryPreferencesDataStore())
        val config = LlmConfig(baseUrl = "https://api", apiKey = "sk", model = "m", timeoutSeconds = 10)
        store.save(config)
        assertEquals(config, store.current())
        store.clear()
        assertEquals(LlmConfig(), store.current())
    }
}
