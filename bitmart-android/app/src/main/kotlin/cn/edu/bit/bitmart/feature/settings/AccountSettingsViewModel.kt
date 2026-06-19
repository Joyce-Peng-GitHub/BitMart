package cn.edu.bit.bitmart.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.ui.UiText
import cn.edu.bit.bitmart.core.ui.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 账号设置页状态。 */
data class AccountSettingsUiState(
    val loggedIn: Boolean = true,
    val user: User? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    /** 一次性提示文案（成功后展示，由 UI 消费后清空）。 */
    val message: UiText? = null,
    /** 注销账号成功后置 true，UI 据此返回。 */
    val loggedOut: Boolean = false,
    /** 改密成功后置 true，UI 据此导航到登录页（改密会吊销会话）。 */
    val passwordChanged: Boolean = false,
)

/**
 * 账号设置 ViewModel：修改昵称（PATCH /me）、修改密码（统一身份认证 verify → resetPassword）、
 * 退出登录、注销账号。需登录；未登录时由 UI 跳转登录页。
 */
@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountSettingsUiState())
    val state: StateFlow<AccountSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _state.update { it.copy(loggedIn = loggedIn) }
                if (loggedIn) loadMe()
            }
        }
    }

    private fun loadMe() {
        viewModelScope.launch {
            when (val r = profileRepository.getMe()) {
                is DomainResult.Success -> _state.update { it.copy(user = r.data) }
                is DomainResult.Error -> _state.update { it.copy(error = r.toUiText()) }
            }
        }
    }

    /** 修改昵称。传空白表示清空昵称（显示为匿名）。 */
    fun updateNickname(nickname: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            when (val r = profileRepository.updateNickname(nickname.ifBlank { null })) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, user = r.data, message = UiText.Res(R.string.account_msg_nickname_updated)) }
                is DomainResult.Error -> _state.update { it.copy(loading = false, error = r.toUiText()) }
            }
        }
    }

    /**
     * 修改密码：先用统一身份认证密码 verify 取得 ticket，再用 ticket 重置 BitMart 密码。
     * @param studentId 学号（默认取当前用户）。
     * @param unifiedPassword 统一身份认证密码（仅用于验证身份）。
     * @param newPassword 新的 BitMart 密码。
     * @param confirmPassword 二次输入的新密码，仅用于客户端一致性校验（不保存）。
     */
    fun changePassword(studentId: String, unifiedPassword: String, newPassword: String, confirmPassword: String) {
        if (studentId.isBlank() || unifiedPassword.isBlank() || newPassword.isBlank()) {
            _state.update { it.copy(error = UiText.Res(R.string.account_error_fill_password_fields)) }
            return
        }
        if (newPassword != confirmPassword) {
            _state.update { it.copy(error = UiText.Res(R.string.auth_error_password_mismatch)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            when (val verify = authRepository.verify(studentId, unifiedPassword)) {
                is DomainResult.Success -> {
                    when (val reset = authRepository.resetPassword(verify.data, studentId, newPassword)) {
                        is DomainResult.Success -> _state.update { it.copy(loading = false, passwordChanged = true, message = UiText.Res(R.string.account_msg_password_changed)) }
                        is DomainResult.Error -> _state.update { it.copy(loading = false, error = reset.toUiText()) }
                    }
                }
                is DomainResult.Error -> _state.update { it.copy(loading = false, error = verify.toUiText()) }
            }
        }
    }

    /** 退出登录：清除本地令牌（本地登出优先）。 */
    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = authRepository.logout()) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, loggedOut = true) }
                is DomainResult.Error -> _state.update { it.copy(loading = false, error = r.toUiText()) }
            }
        }
    }

    /** 注销账号：成功后令牌已清除。 */
    fun deleteAccount() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = authRepository.deleteAccount()) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, loggedOut = true) }
                is DomainResult.Error -> _state.update { it.copy(loading = false, error = r.toUiText()) }
            }
        }
    }

    /** UI 消费一次性提示后调用，避免重复展示。 */
    fun consumeMessage() = _state.update { it.copy(message = null) }
}
