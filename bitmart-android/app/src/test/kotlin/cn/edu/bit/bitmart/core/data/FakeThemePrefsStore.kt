package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.ThemePrefsStore
import cn.edu.bit.bitmart.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

/** 内存主题偏好存储，供 JVM 单测使用。 */
class FakeThemePrefsStore(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePrefsStore {
    private val flow = MutableStateFlow(initial)
    override val themeModeFlow = flow

    override suspend fun setMode(mode: ThemeMode) {
        flow.value = mode
    }
}
