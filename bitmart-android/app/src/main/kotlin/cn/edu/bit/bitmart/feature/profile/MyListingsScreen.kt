package cn.edu.bit.bitmart.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.ui.AdjustQuantityDialog
import cn.edu.bit.bitmart.core.ui.FilterState
import cn.edu.bit.bitmart.core.ui.ListingFilterDialog
import cn.edu.bit.bitmart.core.ui.ListingTimeInfo
import cn.edu.bit.bitmart.core.ui.absoluteMediaUrl
import coil3.compose.AsyncImage

/**
 * “我的商品 / 我的收购”管理页（架构 §6.2，GET /me/listings）。
 * 列出当前用户自己发布的项（含已售罄/已过期），可点进详情（详情页提供本人删/改），
 * 并在行内直接调整数量、编辑或删除。
 * @param buy true 表示“我的收购”（BUY），false 表示“我的商品”（SELL）。
 * @param onItemClick 点击条目进入详情（使用外层导航控制器，详情为全屏同级页）。
 * @param onEditClick 点击编辑进入编辑页。
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
    onBack: () -> Unit,
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: MyListingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val title = if (buy) "我的收购" else "我的商品"
    var adjustTarget by remember { mutableStateOf<ListingSummary?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(buy) { viewModel.setType(if (buy) ListingType.BUY else ListingType.SELL) }

    // 从编辑/删除返回后刷新列表（标题/价格等可能已变），随后清除标记。
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    // 列表非空时的操作错误（调整已售/删除/409 冲突）通过 Snackbar 展示，
    // 避免被仅在空列表时渲染的内联错误吞掉；展示后清空。空列表的加载错误见下方内联分支。
    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null && state.items.isNotEmpty()) {
            snackbarHostState.showSnackbar(err)
            viewModel.consumeError()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) -> if (total > 0 && last >= total - 2) viewModel.loadMore() }
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
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // 下拉刷新：保留列表重新拉取首屏，便于网络不佳时主动重试（空/错误态也可下拉）。
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh(showSpinner = false) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.items.isEmpty() ->
                    // 包成可滚动列表，使错误态也能下拉重试。
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
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
                        MyListingRow(
                            item = item,
                            buy = buy,
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
                selectedTagIds = state.selectedTagIds,
            ),
            loadTags = { viewModel.loadPopularTags() },
            onDismiss = { showFilter = false },
            onClear = { viewModel.clearFilter() },
            onConfirm = { viewModel.applyFilter(it) },
        )
    }
}
@Composable
private fun MyListingRow(
    item: ListingSummary,
    buy: Boolean,
    adjusting: Boolean,
    onClick: () -> Unit,
    onAdjustClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val soldOut = item.quantitySold >= item.quantityTotal
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val imageUrl = absoluteMediaUrl(item.firstImageUrl)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                val priceLabel = if (buy) "期望价" else "售价"
                val price = item.unitPrice?.let { "￥$it" } ?: "面议"
                Text("$priceLabel：$price", style = MaterialTheme.typography.bodyMedium)
                val soldVerb = if (buy) "已收" else "已售"
                val fullLabel = if (buy) "（已收满）" else "（售罄）"
                val soldText = "$soldVerb ${item.quantitySold}/${item.quantityTotal}" + if (soldOut) fullLabel else ""
                Text(
                    soldText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (soldOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ListingTimeInfo(createdAtIso = item.createdAt, expiresAtIso = item.expiresAt)
            }
            if (adjusting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onAdjustClick) {
                    Icon(Icons.Default.Numbers, contentDescription = "调整数量")
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

