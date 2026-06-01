package cn.edu.bit.bitmart.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 登录/注册界面状态。 */
data class AuthUiState(
    val studentId: String = "",
    val password: String = "",
    val nickname: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

/**
 * 认证 ViewModel：承载登录与注册两条流程。注册需先 verify 取得 ticket。
 * 通过 StateFlow 暴露 UI 状态，错误以可展示文案呈现。
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onStudentIdChange(v: String) = _state.update { it.copy(studentId = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onNicknameChange(v: String) = _state.update { it.copy(nickname = v, error = null) }

    /** 登录：学号 + BitMart 密码。 */
    fun login() {
        val s = _state.value
        if (s.studentId.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "请填写学号和密码") }
            return
        }
        launchAuth { authRepository.login(s.studentId, s.password) }
    }

    /**
     * 注册：先用统一身份认证密码 verify，成功后用返回的 ticket 注册并设置 BitMart 密码。
     * @param unifiedPassword 统一身份认证密码（仅用于 verify，不保存）。
     */
    fun register(unifiedPassword: String) {
        val s = _state.value
        if (s.studentId.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "请填写学号和要设置的密码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val verify = authRepository.verify(s.studentId, unifiedPassword)) {
                is DomainResult.Success -> {
                    val reg = authRepository.register(
                        verify.data, s.studentId, s.password, s.nickname.ifBlank { null },
                    )
                    applyResult(reg)
                }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, error = verify.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${verify.message}") }
            }
        }
    }

    private fun launchAuth(block: suspend () -> DomainResult<*>) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            applyResult(block())
        }
    }

    private fun applyResult(result: DomainResult<*>) {
        _state.update {
            when (result) {
                is DomainResult.Success -> it.copy(loading = false, loggedIn = true, error = null)
                is DomainResult.Failure -> it.copy(loading = false, error = result.message)
                is DomainResult.NetworkError -> it.copy(loading = false, error = "网络异常：${result.message}")
            }
        }
    }
}
