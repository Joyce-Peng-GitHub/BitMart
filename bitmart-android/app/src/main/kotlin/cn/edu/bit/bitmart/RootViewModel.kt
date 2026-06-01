package cn.edu.bit.bitmart

import androidx.lifecycle.ViewModel
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 根 ViewModel：暴露登录态用于决定起始路由。 */
@HiltViewModel
class RootViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val isLoggedIn: Flow<Boolean> = authRepository.isLoggedIn
}
