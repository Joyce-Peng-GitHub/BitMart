package cn.edu.bit.bitmart.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
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
     * 已选标签名（来自热门标签）。注意：后端按 tagIds(Long) 过滤，而热门标签接口仅返回名称，
     * 且无“名称→ID”查询接口，故标签筛选暂不下发到查询（仅在弹窗中回显选择）。详见 [toQuery] 的 TODO。
     */
    val selectedTags: List<String> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val endReached: Boolean = false,
)

/**
 * 列表页 ViewModel：买/卖共用一套逻辑（架构 §5.2）。支持搜索、是否含售罄、keyset 加载更多。
 */
@HiltViewModel
class ListingFeedViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    /** 设置当前类型（SELL/BUY）并重新加载。 */
    fun setType(type: ListingType) {
        _state.update { it.copy(type = type) }
        refresh()
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun toggleIncludeSold() {
        _state.update { it.copy(includeSold = !it.includeSold) }
        refresh()
    }

    /**
     * 应用筛选弹窗的条件并重新查询。价格空串视为未设置；标签仅回显，不下发（见 [FeedUiState.selectedTags]）。
     */
    fun applyFilter(
        minPrice: String,
        maxPrice: String,
        includeNoPrice: Boolean,
        includeSold: Boolean,
        selectedTags: List<String>,
    ) {
        _state.update {
            it.copy(
                minPrice = minPrice.trim(),
                maxPrice = maxPrice.trim(),
                includeNoPrice = includeNoPrice,
                includeSold = includeSold,
                selectedTags = selectedTags,
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
                selectedTags = emptyList(),
            )
        }
        refresh()
    }

    /** 首屏/条件变化时重新加载（清空游标）。 */
    fun refresh() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, items = emptyList(), nextCursor = null, endReached = false) }
            when (val r = listingRepository.list(s.toQuery(cursor = null))) {
                is DomainResult.Success -> _state.update {
                    it.copy(loading = false, items = r.data.items, nextCursor = r.data.nextCursor, endReached = r.data.nextCursor == null)
                }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
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
        // TODO(标签筛选)：后端按 tagIds(Long) 过滤，但热门标签接口仅返回名称且无“名称→ID”查询接口，
        // 故此处不下发 tagIds（保持空），标签选择仅在弹窗中回显。待后端提供名称→ID 解析后再补全。
        tagIds = emptyList(),
        cursor = cursor,
    )

    /** 拉取热门标签供筛选弹窗展示（失败则返回空列表，弹窗自行降级）。 */
    suspend fun loadPopularTags(limit: Int = 20): List<String> =
        when (val r = listingRepository.popularTags(limit)) {
            is DomainResult.Success -> r.data
            else -> emptyList()
        }
}
