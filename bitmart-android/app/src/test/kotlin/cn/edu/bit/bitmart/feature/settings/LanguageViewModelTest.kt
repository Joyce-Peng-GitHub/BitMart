package cn.edu.bit.bitmart.feature.settings

import cn.edu.bit.bitmart.core.data.FakeLanguagePrefsStore
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LanguageViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test
    fun `setLanguage updates exposed language`() = runTest {
        val vm = LanguageViewModel(FakeLanguagePrefsStore(AppLanguage.SYSTEM))
        // language 使用 WhileSubscribed，需有订阅者才会从上游收集。
        vm.language.launchIn(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setLanguage(AppLanguage.EN)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppLanguage.EN, vm.language.value)
    }

    @Test
    fun `initial language reflects stored value`() = runTest {
        val vm = LanguageViewModel(FakeLanguagePrefsStore(AppLanguage.ZH))
        vm.language.launchIn(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppLanguage.ZH, vm.language.value)
    }
}
