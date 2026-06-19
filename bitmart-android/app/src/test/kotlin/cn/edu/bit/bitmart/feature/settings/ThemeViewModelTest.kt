package cn.edu.bit.bitmart.feature.settings

import cn.edu.bit.bitmart.core.data.FakeThemePrefsStore
import cn.edu.bit.bitmart.core.domain.model.ThemeMode
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

class ThemeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test
    fun `setMode updates exposed themeMode`() = runTest {
        val vm = ThemeViewModel(FakeThemePrefsStore())
        // themeMode 使用 WhileSubscribed，需有订阅者才会从上游收集。
        vm.themeMode.launchIn(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setMode(ThemeMode.DARK)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun `initial themeMode reflects stored value`() = runTest {
        val vm = ThemeViewModel(FakeThemePrefsStore(ThemeMode.LIGHT))
        vm.themeMode.launchIn(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, vm.themeMode.value)
    }
}
