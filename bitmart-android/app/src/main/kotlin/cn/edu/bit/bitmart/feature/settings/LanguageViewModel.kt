package cn.edu.bit.bitmart.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.data.local.LanguagePrefsStore
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 语言设置 ViewModel：暴露当前语言偏好并写入本地。偏好仅存本地，应用全局即时生效。
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val store: LanguagePrefsStore,
) : ViewModel() {

    val language: StateFlow<AppLanguage> =
        store.languageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.SYSTEM)

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { store.setLanguage(lang) }
    }
}
