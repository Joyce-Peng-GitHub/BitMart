package cn.edu.bit.bitmart.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** “我的商品/我的收购”管理页 UI 状态。[type] 区分卖品（SELL）与收购（BUY）。 */
data class MyListingsUiState(
    val type: ListingType = ListingType.SELL,
    val items: List<ListingSummary> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    /** 正在提交“调整已售出数量”的条目 id，用于行内禁用/转圈。 */
    val adjustingId: Long? = null,
)

/**
 * “我的商品/我的收购”管理 ViewModel（架构 §6.2，GET /me/listings）。
 * 列出当前用户自己发布的列表（含已售罄/已过期），支持 keyset 加载更多、删除、调整已售出数量。
 */
@HiltViewModel
class MyListingsViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyListingsUiState())
    val state: StateFlow<MyListingsUiState> = _state.asStateFlow()

    /** 设置类型（SELL/BUY）并加载。重复设置同一类型不重复加载首屏（除非为空）。 */
    fun setType(type: ListingType) {
        if (_state.value.type == type && _state.value.items.isNotEmpty()) return
        _state.update { it.copy(type = type) }
        refresh()
    }

    /** 重新加载首屏（清空游标）。 */
    fun refresh() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, items = emptyList(), nextCursor = null, endReached = false) }
            when (val r = listingRepository.myListings(s.toQuery(cursor = null))) {
                is DomainResult.Success -> _state.update {
                    it.copy(loading = false, items = r.data.items, nextCursor = r.data.nextCursor, endReached = r.data.nextCursor == null)
                }
                is DomainResult.Failure ->
                    if (r.httpStatus == 401) _state.update { it.copy(loading = false, error = "请登录后查看") }
                    else _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 加载下一页（keyset）。 */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.endReached || s.nextCursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val r = listingRepository.myListings(s.toQuery(cursor = s.nextCursor))) {
                is DomainResult.Success -> _state.update {
                    it.copy(loadingMore = false, items = it.items + r.data.items, nextCursor = r.data.nextCursor, endReached = r.data.nextCursor == null)
                }
                is DomainResult.Failure -> _state.update { it.copy(loadingMore = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loadingMore = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /**
     * 调整某条目的已售出数量（UI 规范：我的商品可“调整已售出数量”）。
     * 成功后本地更新该条目的 quantitySold，避免整页刷新。
     */
    fun adjustSold(id: Long, quantitySold: Int) {
        viewModelScope.launch {
            _state.update { it.copy(adjustingId = id, error = null) }
            when (val r = listingRepository.update(id, UpdateDraft(quantitySold = quantitySold))) {
                is DomainResult.Success -> _state.update { st ->
                    st.copy(
                        adjustingId = null,
                        items = st.items.map { if (it.id == id) it.copy(quantitySold = quantitySold) else it },
                    )
                }
                is DomainResult.Failure -> _state.update { it.copy(adjustingId = null, error = "调整失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(adjustingId = null, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 删除某条目，成功后从本地列表移除。 */
    fun delete(id: Long) {
        viewModelScope.launch {
            when (val r = listingRepository.delete(id)) {
                is DomainResult.Success -> _state.update { st -> st.copy(items = st.items.filterNot { it.id == id }) }
                is DomainResult.Failure -> _state.update { it.copy(error = "删除失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(error = "网络异常：${r.message}") }
            }
        }
    }

    private fun MyListingsUiState.toQuery(cursor: String?) = ListingQuery(type = type, cursor = cursor)

    /** UI 通过 Snackbar 展示 error 后调用，避免重复弹出。 */
    fun consumeError() = _state.update { it.copy(error = null) }
}
