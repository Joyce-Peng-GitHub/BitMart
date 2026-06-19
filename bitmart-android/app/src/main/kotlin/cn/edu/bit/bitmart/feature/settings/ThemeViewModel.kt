package cn.edu.bit.bitmart.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.data.local.ThemePrefsStore
import cn.edu.bit.bitmart.core.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主题设置 ViewModel：暴露当前主题模式并写入偏好。模式仅存本地，应用全局即时生效。
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val store: ThemePrefsStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        store.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { store.setMode(mode) }
    }
}
