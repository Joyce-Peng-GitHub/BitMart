package cn.edu.bit.bitmart.feature.feed

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.ui.FilterState
import cn.edu.bit.bitmart.core.ui.ListingFilterDialog
import cn.edu.bit.bitmart.core.ui.absoluteMediaUrl
import coil3.compose.AsyncImage

/** 商品/求购列表屏。买/卖共用，文案随类型切换。右下角三枚悬浮按钮：搜索、筛选、发布（自上而下）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingFeedScreen(
    onItemClick: (Long) -> Unit,
    onPublishClick: () -> Unit,
    viewModel: ListingFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (state.items.isEmpty()) viewModel.refresh() }

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
            if (total > 0 && last >= total - 2) viewModel.loadMore()
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // 下拉刷新：保留当前列表重新拉取首屏，便于网络不佳时主动重试。
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh(showSpinner = false) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.loading) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            ListingCard(item, type = state.type, onClick = { onItemClick(item.id) })
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
}

/**
 * 搜索弹窗：单行搜索框 + 清空/取消/确认（与筛选弹窗一致）。
 * 回车（ImeAction.Search）或点击确认即应用搜索；清空即清除搜索词回到全部。
 */
@Composable
private fun SearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索") },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索商品名 / 描述") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onConfirm(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(query) }) { Text("确认") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("清空") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 列表项卡片：首图缩略图 + 标题/价格/标签。取货地点仅详情页展示（架构 §6.3），列表不含。 */
@Composable
fun ListingCard(item: ListingSummary, type: ListingType, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val imageUrl = absoluteMediaUrl(item.firstImageUrl)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                val priceLabel = if (type == ListingType.BUY) "期望价" else "售价"
                val price = item.unitPrice?.let { "￥$it" } ?: "面议"
                Text("$priceLabel：$price", style = MaterialTheme.typography.bodyMedium)
                if (item.tags.isNotEmpty()) {
                    Text("标签：${item.tags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
