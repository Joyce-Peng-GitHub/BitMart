package cn.edu.bit.bitmart.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.TagInfo
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import cn.edu.bit.bitmart.core.ui.FilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 列表页 UI 状态。买/卖共用，由 [type] 区分文案与查询。 */
data class FeedUiState(
    val type: ListingType = ListingType.SELL,
    val items: List<ListingSummary> = emptyList(),
    val query: String = "",
    val includeSold: Boolean = false,
    // 筛选条件（由筛选弹窗设置）。价格用字符串以匹配后端 NUMERIC 文本表示，空串视为未设置。
    val minPrice: String = "",
    val maxPrice: String = "",
    val includeNoPrice: Boolean = true,
    /**
     * 已选标签 ID（来自热门标签接口）。直接下发到 [toQuery] 的 tagIds。
     */
    val selectedTagIds: List<Long> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 下拉刷新进行中（保留当前列表，仅驱动下拉指示器）。 */
    val refreshing: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    /** 当前登录用户 id（未登录为 null）。列表项 ownerId 与之相等者为本人项，启用左滑操作。 */
    val currentUserId: Long? = null,
    /** 正在提交"调整已售出数量"的本人条目 id，用于左滑动作区行内转圈。 */
    val adjustingId: Long? = null,
)

/**
 * 列表页 ViewModel：买/卖共用一套逻辑（架构 §5.2）。支持搜索、是否含售罄、keyset 加载更多。
 */
@HiltViewModel
class ListingFeedViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    /** 最近一次登录态。供 refresh 时按需补解析本人 id（首启离线导致 init 取不到的情形）。 */
    private var loggedIn = false

    init {
        // 跟随登录态解析当前用户 id：登录后取一次 /me 用于"本人项"判定；登出即清空。
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { isLoggedIn ->
                loggedIn = isLoggedIn
                if (!isLoggedIn) _state.update { it.copy(currentUserId = null) }
                else ensureCurrentUser()
            }
        }
    }

    /**
     * 已登录但本人 id 未知时取一次 /me 解析；已知或未登录则空操作。
     * 取不到（如离线）保持 null，下次 refresh 会再次尝试，避免首启离线后永久失去左滑能力。
     */
    private suspend fun ensureCurrentUser() {
        if (!loggedIn || _state.value.currentUserId != null) return
        (profileRepository.getMe() as? DomainResult.Success)?.let { me ->
            _state.update { it.copy(currentUserId = me.data.id) }
        }
    }

    /** 设置当前类型（SELL/BUY）并重新加载。 */
    fun setType(type: ListingType) {
        _state.update { it.copy(type = type) }
        refresh()
    }

    /** 应用搜索词并重新查询（空串即取消搜索，回到全部）。 */
    fun applySearch(query: String) {
        _state.update { it.copy(query = query.trim()) }
        refresh()
    }

    /** 消费一次性错误提示（UI 以 Toast 展示后调用，置空以便相同错误可再次触发）。 */
    fun consumeError() = _state.update { it.copy(error = null) }

    /**
     * 调整本人某条目的已售出数量（公开列表中对本人项左滑可用）。
     * 成功后本地更新 quantitySold，避免整页刷新。失败置 error 供 Toast 展示。
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

    /** 删除本人某条目，成功后从本地列表移除。失败置 error 供 Toast 展示。 */
    fun delete(id: Long) {
        viewModelScope.launch {
            when (val r = listingRepository.delete(id)) {
                is DomainResult.Success -> _state.update { st -> st.copy(items = st.items.filterNot { it.id == id }) }
                is DomainResult.Failure -> _state.update { it.copy(error = "删除失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(error = "网络异常：${r.message}") }
            }
        }
    }

    fun toggleIncludeSold() {
        _state.update { it.copy(includeSold = !it.includeSold) }
        refresh()
    }

    // 公开列表不展示过期项：忽略 filter.includeExpired，恒按默认（不含过期）查询。
    fun applyFilter(filter: FilterState) {
        _state.update {
            it.copy(
                minPrice = filter.minPrice.trim(),
                maxPrice = filter.maxPrice.trim(),
                includeNoPrice = filter.includeNoPrice,
                includeSold = filter.includeSold,
                selectedTagIds = filter.selectedTagIds,
            )
        }
        refresh()
    }

    /** 清空筛选条件（回到默认）并重新查询。不影响搜索关键词。 */
    fun clearFilter() {
        _state.update {
            it.copy(
                minPrice = "",
                maxPrice = "",
                includeNoPrice = true,
                includeSold = false,
                selectedTagIds = emptyList(),
            )
        }
        refresh()
    }

    /**
     * 首屏/条件变化时重新加载（清空游标）。
     * @param showSpinner true（默认）走整页加载：置 loading 并清空列表（用于切换类型/筛选/首屏）。
     *   false 为下拉刷新：保留当前列表，仅置 refreshing 驱动下拉指示器。
     */
    fun refresh(showSpinner: Boolean = true) {
        val s = _state.value
        // 并发补解析本人 id（不阻塞列表加载）：覆盖首启离线、稍后联网后下拉刷新的情形。
        viewModelScope.launch { ensureCurrentUser() }
        viewModelScope.launch {
            _state.update {
                if (showSpinner) it.copy(loading = true, error = null, items = emptyList(), nextCursor = null, endReached = false)
                else it.copy(refreshing = true, error = null)
            }
            when (val r = listingRepository.list(s.toQuery(cursor = null))) {
                is DomainResult.Success -> _state.update {
                    it.copy(loading = false, refreshing = false, items = r.data.items, nextCursor = r.data.nextCursor, endReached = r.data.nextCursor == null)
                }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, refreshing = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, refreshing = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 加载下一页（keyset）。 */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.refreshing || s.endReached || s.nextCursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val r = listingRepository.list(s.toQuery(cursor = s.nextCursor))) {
                is DomainResult.Success -> _state.update {
                    it.copy(loadingMore = false, items = it.items + r.data.items, nextCursor = r.data.nextCursor, endReached = r.data.nextCursor == null)
                }
                is DomainResult.Failure -> _state.update { it.copy(loadingMore = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loadingMore = false, error = "网络异常：${r.message}") }
            }
        }
    }

    private fun FeedUiState.toQuery(cursor: String?) = ListingQuery(
        type = type,
        text = query.ifBlank { null },
        minPrice = minPrice.ifBlank { null },
        maxPrice = maxPrice.ifBlank { null },
        includeNoPrice = includeNoPrice,
        includeSold = includeSold,
        tagIds = selectedTagIds,
        cursor = cursor,
    )

    /** 拉取热门标签供筛选弹窗展示（失败则返回空列表，弹窗自行降级）。 */
    suspend fun loadPopularTags(limit: Int = 20): List<TagInfo> =
        when (val r = listingRepository.popularTags(limit)) {
            is DomainResult.Success -> r.data
            else -> emptyList()
        }
}
