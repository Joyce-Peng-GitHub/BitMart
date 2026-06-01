package cn.edu.bit.bitmart.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val loading: Boolean = true,
    val detail: ListingDetail? = null,
    val error: String? = null,
    val needLogin: Boolean = false,
)

/** 详情 ViewModel。未登录时后端返回 401 → 提示需登录（需求"不对未登录客户端显示详情"）。 */
@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, needLogin = false) }
            when (val r = listingRepository.detail(id)) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, detail = r.data) }
                is DomainResult.Failure ->
                    if (r.httpStatus == 401) _state.update { it.copy(loading = false, needLogin = true) }
                    else _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }
}
