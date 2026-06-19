package cn.edu.bit.bitmart.feature.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.isUnauthorized
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.domain.repository.TagInfo
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import cn.edu.bit.bitmart.core.ui.FilterState
import cn.edu.bit.bitmart.core.ui.UiText
import cn.edu.bit.bitmart.core.ui.toUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 列表数据来源：公开买卖列表（[PUBLIC]，GET /listings）或本人发布管理列表（[MINE]，GET /me/listings）。
 * 二者的查询逻辑、分页、增改删几乎一致，差异仅在于：拉取端点、是否解析本人 id 以区分卡片样式、
 * 以及默认是否含售罄/过期项。统一由 [scope] 一处区分。
 */
enum class ListingScope { PUBLIC, MINE }

/**
 * 列表页 UI 状态（公开列表与"我的列表"共用）。[type] 区分卖品（SELL）与收购/求购（BUY）。
 */
data class ListingListUiState(
    val type: ListingType = ListingType.SELL,
    val items: List<ListingSummary> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 下拉刷新进行中（保留当前列表，仅驱动下拉指示器）。 */
    val refreshing: Boolean = false,
    val error: UiText? = null,
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    /** 当前登录用户 id（未登录为 null）。仅 PUBLIC 解析：列表项 ownerId 与之相等者为本人项，启用左滑操作。 */
    val currentUserId: Long? = null,
    /** 正在提交"调整已售出数量"的条目 id，用于行内禁用/转圈。 */
    val adjustingId: Long? = null,
    // 筛选条件（由筛选弹窗设置）。价格用字符串以匹配后端 NUMERIC 文本表示，空串视为未设置。
    val minPrice: String = "",
    val maxPrice: String = "",
    val includeNoPrice: Boolean = true,
    val includeSold: Boolean = false,
    /** 仅 MINE 有意义：是否含过期项（公开列表恒不含，忽略此字段）。 */
    val includeExpired: Boolean = false,
    val selectedTags: List<String> = emptyList(),
)

/**
 * 列表 ViewModel：公开买卖列表与"我的商品/收购"管理页共用一套逻辑（架构 §5.2 / §6.2）。
 * 由 [scope] 决定数据源与行为差异（见 [ListingScope]）。支持搜索、筛选、keyset 加载更多、
 * 本人项的删除与调整已售出数量。
 *
 * 通过 Hilt assisted 注入接收 [scope]：屏幕侧以 `hiltViewModel(creationCallback = { it.create(scope) })`
 * 构造，使同一类在不同入口承载不同来源，且各入口（含"我的商品"/"我的收购"两个分离入口）各自持有独立实例。
 */
@HiltViewModel(assistedFactory = ListingListViewModel.Factory::class)
class ListingListViewModel @AssistedInject constructor(
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    @Assisted private val scope: ListingScope,
) : ViewModel() {

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(scope: ListingScope): ListingListViewModel
    }

    private val defaultSold get() = scope == ListingScope.MINE
    private val defaultExpired get() = scope == ListingScope.MINE

    private val _state = MutableStateFlow(
        ListingListUiState(includeSold = defaultSold, includeExpired = defaultExpired),
    )
    val state: StateFlow<ListingListUiState> = _state.asStateFlow()

    /** 最近一次登录态（仅 PUBLIC 使用）。供 refresh 时按需补解析本人 id（首启离线导致 init 取不到的情形）。 */
    private var loggedIn = false

    init {
        // 公开列表需区分"本人项"以启用左滑操作：跟随登录态解析当前用户 id。
        // "我的列表"全部为本人项，无需解析（始终 currentUserId == null，卡片由屏幕按 MINE 统一渲染）。
        if (scope == ListingScope.PUBLIC) {
            viewModelScope.launch {
                authRepository.isLoggedIn.collect { isLoggedIn ->
                    loggedIn = isLoggedIn
                    if (!isLoggedIn) _state.update { it.copy(currentUserId = null) }
                    else ensureCurrentUser()
                }
            }
        }
    }

    /**
     * 已登录但本人 id 未知时取一次 /me 解析；已知或未登录则空操作。仅 PUBLIC 生效。
     * 取不到（如离线）保持 null，下次 refresh 会再次尝试，避免首启离线后永久失去左滑能力。
     */
    private suspend fun ensureCurrentUser() {
        if (scope != ListingScope.PUBLIC || !loggedIn || _state.value.currentUserId != null) return
        (profileRepository.getMe() as? DomainResult.Success)?.let { me ->
            _state.update { it.copy(currentUserId = me.data.id) }
        }
    }

    /**
     * 设置当前类型（SELL/BUY）并加载。
     * MINE 下"我的商品"与"我的收购"是分离入口、各自独立实例，重复设置同一类型且已有数据时跳过重复首屏加载；
     * PUBLIC 下买/卖共用同一实例并通过 tab 切换，故每次都重新加载。
     */
    fun setType(type: ListingType) {
        if (scope == ListingScope.MINE && _state.value.type == type && _state.value.items.isNotEmpty()) return
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
     * 调整本人某条目的已售出数量。成功后本地更新 quantitySold，避免整页刷新；失败置 error 供 Toast 展示。
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
                is DomainResult.Error -> _state.update { it.copy(adjustingId = null, error = r.toUiText()) }
            }
        }
    }

    /** 删除本人某条目，成功后从本地列表移除。失败置 error 供 Toast 展示。 */
    fun delete(id: Long) {
        viewModelScope.launch {
            when (val r = listingRepository.delete(id)) {
                is DomainResult.Success -> _state.update { st -> st.copy(items = st.items.filterNot { it.id == id }) }
                is DomainResult.Error -> _state.update { it.copy(error = r.toUiText()) }
            }
        }
    }

    /**
     * 应用筛选条件并重新加载。公开列表不展示过期项：忽略 [FilterState.includeExpired]，
     * 恒按默认（不含过期）查询；"我的列表"则采纳该开关。
     */
    fun applyFilter(filter: FilterState) {
        _state.update {
            it.copy(
                minPrice = filter.minPrice.trim(),
                maxPrice = filter.maxPrice.trim(),
                includeNoPrice = filter.includeNoPrice,
                includeSold = filter.includeSold,
                includeExpired = if (scope == ListingScope.MINE) filter.includeExpired else false,
                selectedTags = filter.selectedTags,
            )
        }
        refresh()
    }

    /** 清空筛选条件并重新查询。回到各自来源的默认（MINE 含售罄与过期项；PUBLIC 两者皆不含）。不影响搜索关键词。 */
    fun clearFilter() {
        _state.update {
            it.copy(
                minPrice = "",
                maxPrice = "",
                includeNoPrice = true,
                includeSold = defaultSold,
                includeExpired = defaultExpired,
                selectedTags = emptyList(),
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
        // PUBLIC：并发补解析本人 id（不阻塞列表加载），覆盖首启离线、稍后联网后下拉刷新的情形。MINE 跳过，不空跑协程。
        if (scope == ListingScope.PUBLIC) viewModelScope.launch { ensureCurrentUser() }
        viewModelScope.launch {
            _state.update {
                if (showSpinner) it.copy(loading = true, error = null, items = emptyList(), nextCursor = null, endReached = false)
                else it.copy(refreshing = true, error = null)
            }
            when (val r = fetchPage(s.toQuery(cursor = null))) {
                is DomainResult.Success -> _state.update {
                    it.copy(loading = false, refreshing = false, items = r.data.items, nextCursor = r.data.nextCursor, endReached = r.data.nextCursor == null)
                }
                is DomainResult.Error -> {
                    // "我的列表"需登录；401 给出更明确的引导文案。公开列表无此分支。
                    val msg = if (scope == ListingScope.MINE && r.isUnauthorized()) {
                        UiText.Res(R.string.error_login_required)
                    } else {
                        r.toUiText()
                    }
                    _state.update { it.copy(loading = false, refreshing = false, error = msg) }
                }
            }
        }
    }

    /** 加载下一页（keyset）。 */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.refreshing || s.endReached || s.nextCursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val r = fetchPage(s.toQuery(cursor = s.nextCursor))) {
                is DomainResult.Success -> _state.update {
                    it.copy(loadingMore = false, items = it.items + r.data.items, nextCursor = r.data.nextCursor, endReached = r.data.nextCursor == null)
                }
                is DomainResult.Error -> _state.update { it.copy(loadingMore = false, error = r.toUiText()) }
            }
        }
    }

    /** 按来源选择拉取端点：公开列表 vs 本人列表。 */
    private suspend fun fetchPage(query: ListingQuery): DomainResult<ListingPage> =
        if (scope == ListingScope.MINE) listingRepository.myListings(query)
        else listingRepository.list(query)

    private fun ListingListUiState.toQuery(cursor: String?) = ListingQuery(
        type = type,
        text = query.ifBlank { null },
        minPrice = minPrice.ifBlank { null },
        maxPrice = maxPrice.ifBlank { null },
        includeNoPrice = includeNoPrice,
        includeSold = includeSold,
        // 公开列表恒不含过期项（后端亦强制）；"我的列表"采纳状态。
        includeExpired = scope == ListingScope.MINE && includeExpired,
        tagNames = selectedTags,
        cursor = cursor,
    )

    /** 拉取热门标签供筛选弹窗展示（失败则返回空列表，弹窗自行降级）。 */
    suspend fun loadPopularTags(limit: Int = PublishConfig.POPULAR_TAGS_LIMIT): List<TagInfo> =
        when (val r = listingRepository.popularTags(limit)) {
            is DomainResult.Success -> r.data
            else -> emptyList()
        }
}
