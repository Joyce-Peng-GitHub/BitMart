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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `save persists trimmed config and shows message`() = runTest {
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
        assertEquals(UiText.Res(R.string.llm_msg_saved), vm.state.value.message)
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
    fun `clear wipes store and resets form to defaults`() = runTest {
        val store = FakeLlmConfigStore(
            LlmConfig(baseUrl = "https://api", apiKey = "sk", model = "m"),
        )
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.clear()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LlmConfig(), store.current())
        val s = vm.state.value
        assertEquals("", s.baseUrl)
        assertEquals("", s.apiKey)
        // 提示词留空（识别时按当前语言回退到内置默认提示词）。
        assertEquals("", s.bookPrompt)
        assertEquals("", s.generalPrompt)
        assertEquals(UiText.Res(R.string.llm_msg_cleared), s.message)
    }

    @Test
    fun `consumeMessage clears one-shot message`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onBaseUrl("https://api")
        vm.onModel("m")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.state.value.message)

        vm.consumeMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `onTimeout filters non-digits`() = runTest {
        val store = FakeLlmConfigStore()
        val vm = LlmSettingsViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onTimeout("12a3")
        assertEquals("123", vm.state.value.timeoutSeconds)
    }
}
