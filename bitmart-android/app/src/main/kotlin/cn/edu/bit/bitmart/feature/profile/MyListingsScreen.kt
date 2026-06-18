package cn.edu.bit.bitmart.feature.profile

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import cn.edu.bit.bitmart.core.ui.ListingFilterDialog
import cn.edu.bit.bitmart.core.ui.OwnedListingRow
import cn.edu.bit.bitmart.core.ui.SearchDialog
import cn.edu.bit.bitmart.feature.listing.ListingListViewModel
import cn.edu.bit.bitmart.feature.listing.ListingScope

/** 触底预取距离：最后可见项进入列表末尾该数量内即加载下一页。 */
private const val MY_LISTINGS_LOAD_MORE_PREFETCH = 2

/**
 * “我的商品 / 我的收购”管理页（架构 §6.2，GET /me/listings）。
 * 列表卡片与“买卖”页完全一致（[OwnedListingRow] 复用统一卡片）；区别仅在于顶部保留返回键与
 * 标题、无买卖 tab 切换条。调整数量 / 编辑 / 删除改为左滑卡片显露（不再常驻图标按钮）。
 * @param buy true 表示“我的收购”（BUY），false 表示“我的商品”（SELL）。
 * @param onItemClick 点击条目进入详情（使用外层导航控制器，详情为全屏同级页）。
 * @param onEditClick 点击编辑进入编辑页。
 * @param onPublishClick 点击发布（跳转到当前类型的发布页）。
 * @param onBack 返回上一页。
 * @param refreshSignal 编辑/删除返回后置 true，触发列表刷新。
 * @param onRefreshConsumed 刷新触发后回调，由上层清除标记避免重复刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    buy: Boolean,
    onItemClick: (Long) -> Unit,
    onEditClick: (Long) -> Unit,
    onPublishClick: () -> Unit,
    onBack: () -> Unit,
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: ListingListViewModel = hiltViewModel(
        key = "listing_${ListingScope.MINE.name}",
        creationCallback = { factory: ListingListViewModel.Factory -> factory.create(ListingScope.MINE) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val title = if (buy) "我的收购" else "我的商品"
    var adjustTarget by remember { mutableStateOf<ListingSummary?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(buy) { viewModel.setType(if (buy) ListingType.BUY else ListingType.SELL) }

    // 从编辑/删除返回后刷新列表（标题/价格等可能已变），随后清除标记。保留列表（不闪全屏转圈）。
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.refresh(showSpinner = false)
            onRefreshConsumed()
        }
    }

    // 异常（加载失败/调整已售/删除/409 冲突等）统一用 Toast 提示，与公共列表一致；展示后清空。
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) -> if (total > 0 && last >= total - MY_LISTINGS_LOAD_MORE_PREFETCH) viewModel.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        // 悬浮按钮放入 Scaffold 槽位，由其自动应用系统栏 inset，避免与手势/三键导航栏重叠。
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
        },
    ) { padding ->
        // 下拉刷新：保留列表重新拉取首屏，便于网络不佳时主动重试（空/错误态也可下拉）。
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh(showSpinner = false) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.items.isEmpty() ->
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("还没有$title")
                            }
                        }
                    }
                else -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        OwnedListingRow(
                            item = item,
                            type = if (buy) ListingType.BUY else ListingType.SELL,
                            adjusting = state.adjustingId == item.id,
                            onClick = { onItemClick(item.id) },
                            onAdjustClick = { adjustTarget = item },
                            onEditClick = { onEditClick(item.id) },
                            onDeleteClick = { viewModel.delete(item.id) },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    adjustTarget?.let { target ->
        AdjustQuantityDialog(
            quantityTotal = target.quantityTotal,
            currentQuantitySold = target.quantitySold,
            buy = buy,
            onDismiss = { adjustTarget = null },
            onConfirm = { qty ->
                viewModel.adjustSold(target.id, qty)
                adjustTarget = null
            },
        )
    }

    if (showFilter) {
        ListingFilterDialog(
            initial = FilterState(
                minPrice = state.minPrice,
                maxPrice = state.maxPrice,
                includeNoPrice = state.includeNoPrice,
                includeSold = state.includeSold,
                includeExpired = state.includeExpired,
                selectedTags = state.selectedTags,
            ),
            loadTags = { viewModel.loadPopularTags() },
            onDismiss = { showFilter = false },
            onClear = { viewModel.clearFilter() },
            onConfirm = { viewModel.applyFilter(it) },
            // 我的列表默认含过期项，允许通过筛选收窄。
            showExpiredToggle = true,
        )
    }

    if (showSearch) {
        SearchDialog(
            initialQuery = state.query,
            onDismiss = { showSearch = false },
            onClear = { viewModel.applySearch(""); showSearch = false },
            onConfirm = { viewModel.applySearch(it); showSearch = false },
        )
    }
}
