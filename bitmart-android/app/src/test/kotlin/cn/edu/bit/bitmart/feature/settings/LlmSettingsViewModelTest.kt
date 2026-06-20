package cn.edu.bit.bitmart.feature.settings

import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.data.FakeLlmConfigStore
import cn.edu.bit.bitmart.core.data.local.current
import cn.edu.bit.bitmart.core.ui.UiText
import cn.edu.bit.bitmart.llm.LlmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test
    fun `loads saved config into editable state`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", apiKey = "sk", model = "m", timeoutSeconds = 42),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals("https://api", s.baseUrl)
        assertEquals("sk", s.apiKey)
        assertEquals("m", s.model)
        assertEquals("42", s.timeoutSeconds)
        assertEquals(true, s.loaded)
    }

    @Test
    fun `save persists trimmed config`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("  https://api.example.com  ")
        vm.onApiKey("  sk-key  ")
        vm.onModel("  gpt-4o  ")
        vm.onTimeout("90")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = store.current()
        assertEquals("https://api.example.com", saved.baseUrl)
        assertEquals("sk-key", saved.apiKey)
        assertEquals("gpt-4o", saved.model)
        assertEquals(90, saved.timeoutSeconds)
    }

    @Test
    fun `save keeps blank prompts blank (no default backfill)`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("https://api"); vm.onModel("m")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        // 提示词留空即留空：识别时由 LlmClient 按当前语言回退到内置默认提示词。
        val saved = store.current()
        assertEquals("", saved.bookPrompt)
        assertEquals("", saved.generalPrompt)
    }

    @Test
    fun `save persists custom prompts verbatim`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("https://api"); vm.onModel("m")
        vm.onBookPrompt("my book prompt"); vm.onGeneralPrompt("my general prompt")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = store.current()
        assertEquals("my book prompt", saved.bookPrompt)
        assertEquals("my general prompt", saved.generalPrompt)
    }

    @Test
    fun `save rejects blank base url`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onModel("m")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiText.Res(R.string.llm_error_base_url_required), vm.state.value.error)
        assertEquals(LlmConfig(), store.current())
    }

    @Test
    fun `save rejects non-positive timeout`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("https://api")
        vm.onModel("m")
        vm.onTimeout("0")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiText.Res(R.string.llm_error_timeout_invalid), vm.state.value.error)
    }

    @Test
    fun `reset restores form to defaults without persisting`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", apiKey = "sk", model = "m", timeoutSeconds = 42),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.reset()
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals("", s.baseUrl)
        assertEquals("", s.apiKey)
        assertEquals("", s.model)
        assertEquals(LlmConfig.DEFAULT_TIMEOUT_SECONDS.toString(), s.timeoutSeconds)
        assertEquals("", s.bookPrompt)
        assertEquals("", s.generalPrompt)
        // 重置仅改内存草稿，不写盘：store 仍保留最初落盘的配置。
        assertEquals(
            LlmConfig(baseUrl = "https://api", apiKey = "sk", model = "m", timeoutSeconds = 42),
            store.current(),
        )
    }

    @Test
    fun `onTimeout filters non-digits`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onTimeout("12a3")
        assertEquals("123", vm.state.value.timeoutSeconds)
    }

    @Test
    fun `hasUnsavedChanges is false right after load`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", apiKey = "sk", model = "m", timeoutSeconds = 42),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges becomes true after editing a field`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", model = "m"),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("https://changed")
        assertTrue(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges is false after a successful save`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("https://api"); vm.onModel("m"); vm.onTimeout("60")
        assertTrue(vm.hasUnsavedChanges())

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges ignores insignificant whitespace matching saved`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", model = "m"),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("  https://api  ")
        assertFalse(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges is true after reset when saved config was non-default`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", apiKey = "sk", model = "m", timeoutSeconds = 42),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.reset()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges is false after reset when saved config was already default`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.reset()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges becomes true after editing a prompt`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", model = "m"),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        // 提示词逐字比较（保存时不去空白），任何改动都算未保存修改。
        vm.onBookPrompt("custom book prompt")
        assertTrue(vm.hasUnsavedChanges())
    }
}
