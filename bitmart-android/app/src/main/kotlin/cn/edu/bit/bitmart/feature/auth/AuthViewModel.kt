package cn.edu.bit.bitmart.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.isUnauthorized
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.ui.UiText
import cn.edu.bit.bitmart.core.ui.toUiText
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
    val error: UiText? = null,
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
            _state.update { it.copy(error = UiText.Res(R.string.auth_fill_credentials)) }
            return
        }
        launchAuth(AuthFlow.LOGIN) { authRepository.login(s.studentId, s.password) }
    }

    /**
     * 注册：先用统一身份认证密码 verify，成功后用返回的 ticket 注册并设置 BitMart 密码。
     * @param unifiedPassword 统一身份认证密码（仅用于 verify，不保存）。
     */
    fun register(unifiedPassword: String) {
        val s = _state.value
        if (s.studentId.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = UiText.Res(R.string.auth_fill_register_credentials)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val verify = authRepository.verify(s.studentId, unifiedPassword)) {
                is DomainResult.Success -> {
                    // ticket 已取得：后续 register 阶段的 401（票据失效/过期）同属"验证失败"语义。
                    val reg = authRepository.register(
                        verify.data, s.studentId, s.password, s.nickname.ifBlank { null },
                    )
                    applyResult(reg, AuthFlow.VERIFY)
                }
                // verify 阶段的 401 表示统一身份认证失败（密码错误等），归为"验证失败"。
                is DomainResult.Error -> _state.update { it.copy(loading = false, error = verify.toAuthError(AuthFlow.VERIFY)) }
            }
        }
    }

    private fun launchAuth(flow: AuthFlow, block: suspend () -> DomainResult<*>) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            applyResult(block(), flow)
        }
    }

    private fun applyResult(result: DomainResult<*>, flow: AuthFlow) {
        _state.update {
            when (result) {
                is DomainResult.Success -> it.copy(loading = false, loggedIn = true, error = null)
                is DomainResult.Error -> it.copy(loading = false, error = result.toAuthError(flow))
            }
        }
    }

    /**
     * 认证流程内的错误文案：401 按流程给出针对性文案（登录/注册口令步 → 凭据错误；身份验证步 → 验证失败），
     * 其余失败（已注册 CONFLICT、未注册 NOT_FOUND、口令策略 VALIDATION_FAILED 等）沿用通用 [toUiText] 码映射。
     * 镜像 [cn.edu.bit.bitmart.feature.listing.ListingListViewModel] 中以 [isUnauthorized] 特判 401 的既有约定。
     */
    private fun DomainResult.Error.toAuthError(flow: AuthFlow): UiText =
        if (isUnauthorized()) {
            UiText.Res(
                when (flow) {
                    AuthFlow.LOGIN -> R.string.auth_error_invalid_credentials
                    AuthFlow.VERIFY -> R.string.auth_error_verify_failed
                },
            )
        } else {
            toUiText()
        }
}

/** 当前认证流程，用于将 401 失败翻译成对应的针对性文案。 */
private enum class AuthFlow { LOGIN, VERIFY }
