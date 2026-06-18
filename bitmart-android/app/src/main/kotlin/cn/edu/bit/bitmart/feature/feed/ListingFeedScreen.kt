package cn.edu.bit.bitmart.feature.feed

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.ui.AdjustQuantityDialog
import cn.edu.bit.bitmart.core.ui.FilterState
import cn.edu.bit.bitmart.core.ui.ListingCard
import cn.edu.bit.bitmart.core.ui.ListingFilterDialog
import cn.edu.bit.bitmart.core.ui.OwnedListingRow
import cn.edu.bit.bitmart.core.ui.SearchDialog
import cn.edu.bit.bitmart.feature.listing.ListingListViewModel
import cn.edu.bit.bitmart.feature.listing.ListingScope

/** 触底预取距离：最后可见项进入列表末尾该数量内即加载下一页。 */
private const val FEED_LOAD_MORE_PREFETCH = 2

/**
 * 商品/求购列表屏。买/卖共用，文案随类型切换。右下角三枚悬浮按钮：搜索、筛选、发布（自上而下）。
 * 列表项中属于当前登录用户（item.ownerId == currentUserId）的项支持左滑显露
 * 调整数量 / 编辑 / 删除（与"我的商品/收购"一致）；他人项为普通卡片。
 *
 * @param onEditClick 左滑"编辑"本人项时进入编辑页。
 * @param refreshSignal 本人项编辑返回后置 true，触发列表刷新。
 * @param onRefreshConsumed 刷新触发后回调，由上层清除标记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingFeedScreen(
    onItemClick: (Long) -> Unit,
    onPublishClick: () -> Unit,
    onEditClick: (Long) -> Unit = {},
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: ListingListViewModel = hiltViewModel(
        key = "listing_${ListingScope.PUBLIC.name}",
        creationCallback = { factory: ListingListViewModel.Factory -> factory.create(ListingScope.PUBLIC) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var adjustTarget by remember { mutableStateOf<ListingSummary?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (state.items.isEmpty()) viewModel.refresh() }

    // 本人项编辑返回后刷新列表（标题/价格等可能已变），随后清除标记。
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.refresh(showSpinner = false)
            onRefreshConsumed()
        }
    }

    // 异常（如网络异常）用 Toast 提示，不再固定在列表上方。
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    // 触底加载更多：最后一项接近底部时拉取下一页。loadMore 自身有游标/结束守卫，安全幂等。
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - FEED_LOAD_MORE_PREFETCH) viewModel.loadMore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 列表区：左右留白，上下紧贴边缘。
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh(showSpinner = false) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.loading) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                    }
                } else if (state.items.isEmpty()) {
                    // 空列表占位：包成可滚动列表，使空态也能下拉刷新（与"我的商品/收购"一致）。
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (state.type == ListingType.BUY) "还没有收购" else "还没有商品")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            if (state.currentUserId != null && item.ownerId == state.currentUserId) {
                                OwnedListingRow(
                                    item = item,
                                    type = state.type,
                                    adjusting = state.adjustingId == item.id,
                                    onClick = { onItemClick(item.id) },
                                    onAdjustClick = { adjustTarget = item },
                                    onEditClick = { onEditClick(item.id) },
                                    onDeleteClick = { viewModel.delete(item.id) },
                                )
                            } else {
                                ListingCard(item = item, type = state.type, onClick = { onItemClick(item.id) })
                            }
                        }
                        if (state.loadingMore) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 悬浮按钮：贴在右下角，悬浮在列表上方不挤占内容空间。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            SmallFloatingActionButton(onClick = { showSearch = true }) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
            SmallFloatingActionButton(onClick = { showFilter = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "筛选")
            }
            FloatingActionButton(onClick = onPublishClick) {
                Icon(Icons.Default.Add, contentDescription = "发布")
            }
        }
    }

    if (showSearch) {
        SearchDialog(
            initialQuery = state.query,
            onDismiss = { showSearch = false },
            onClear = { viewModel.applySearch(""); showSearch = false },
            onConfirm = { viewModel.applySearch(it); showSearch = false },
        )
    }

    if (showFilter) {
        ListingFilterDialog(
            initial = FilterState(
                minPrice = state.minPrice,
                maxPrice = state.maxPrice,
                includeNoPrice = state.includeNoPrice,
                includeSold = state.includeSold,
                selectedTagIds = state.selectedTagIds,
            ),
            loadTags = { viewModel.loadPopularTags() },
            onDismiss = { showFilter = false },
            onClear = { viewModel.clearFilter() },
            onConfirm = { viewModel.applyFilter(it) },
            // 过期项不公开展示：买卖页筛选不提供"显示过期项"开关。
            showExpiredToggle = false,
        )
    }

    adjustTarget?.let { target ->
        AdjustQuantityDialog(
            quantityTotal = target.quantityTotal,
            currentQuantitySold = target.quantitySold,
            buy = state.type == ListingType.BUY,
            onDismiss = { adjustTarget = null },
            onConfirm = { qty ->
                viewModel.adjustSold(target.id, qty)
                adjustTarget = null
            },
        )
    }
}
