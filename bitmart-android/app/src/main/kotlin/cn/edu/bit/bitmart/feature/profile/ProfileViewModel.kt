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
    /** 下拉刷新进行中（驱动下拉指示器）。 */
    val refreshing: Boolean = false,
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
            applyMeResult(profileRepository.getMe())
            _state.update { it.copy(loading = false) }
        }
    }

    /** 将 /me 结果落到状态：成功更新 user，失败/网络错误置 error（前缀统一）。 */
    private fun applyMeResult(r: DomainResult<User>) = _state.update {
        when (r) {
            is DomainResult.Success -> it.copy(user = r.data)
            is DomainResult.Failure -> it.copy(error = r.message)
            is DomainResult.NetworkError -> it.copy(error = "网络异常：${r.message}")
        }
    }

    /**
     * 下拉刷新：重新校验登录态并拉取最新用户信息与未读数（仅已登录时有内容可刷）。
     * 网络不佳导致 /me 之前失败时，用户可据此主动重试。登录态本身由 [authRepository] 响应式维护。
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            if (_state.value.loggedIn) {
                applyMeResult(profileRepository.getMe())
                when (val c = profileRepository.unreadNotificationCount()) {
                    is DomainResult.Success -> _state.update { it.copy(unreadCount = c.data) }
                    else -> {} // 角标失败静默保持原值。
                }
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    /** 消费一次性错误提示（UI 以 Toast 展示后调用，置空以便相同错误可再次触发）。 */
    fun consumeError() = _state.update { it.copy(error = null) }

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
