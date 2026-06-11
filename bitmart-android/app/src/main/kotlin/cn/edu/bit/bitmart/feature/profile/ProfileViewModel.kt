package cn.edu.bit.bitmart.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** “我的”页状态：登录态、用户信息（昵称/学号/ID）与未读通知数。 */
data class ProfileUiState(
    val loggedIn: Boolean = false,
    val user: User? = null,
    val unreadCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * “我的”页 ViewModel（占位）：根据登录态拉取 /me 展示昵称/学号/ID。
 * 完整的设置/通知/联系方式等栏目为后续任务。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _state.update {
                    it.copy(
                        loggedIn = loggedIn,
                        user = if (loggedIn) it.user else null,
                        unreadCount = if (loggedIn) it.unreadCount else 0,
                    )
                }
                if (loggedIn) {
                    loadMe()
                    refreshUnreadCount()
                } else {
                    _state.update { it.copy(error = null) }
                }
            }
        }
    }

    /** 拉取当前用户信息（已登录时）。 */
    fun loadMe() {
        if (!_state.value.loggedIn) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = profileRepository.getMe()) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, user = r.data) }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /**
     * 刷新未读通知数（邮件图标角标）。屏幕每次进入组合时也会调用，
     * 保证从通知页返回后角标及时减少。角标非关键信息，失败静默保持原值。
     */
    fun refreshUnreadCount() {
        if (!_state.value.loggedIn) return
        viewModelScope.launch {
            when (val r = profileRepository.unreadNotificationCount()) {
                is DomainResult.Success -> _state.update { it.copy(unreadCount = r.data) }
                else -> {}
            }
        }
    }
}
