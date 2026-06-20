package cn.edu.bit.bitmart.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 应用级登录态：把 [AuthRepository.isLoggedIn] 暴露为可在导航层同步读取的 [StateFlow]，
 * 供“发布”入口的未登录拦截判断使用。随 Activity 作用域存活、始终被外壳观察，故用 Eagerly。
 */
@HiltViewModel
class AppAuthViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean> =
        authRepository.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
