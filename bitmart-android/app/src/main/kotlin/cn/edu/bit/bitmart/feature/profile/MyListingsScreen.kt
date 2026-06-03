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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.ui.absoluteMediaUrl
import coil3.compose.AsyncImage

/**
 * “我的商品 / 我的收购”管理页（架构 §6.2，GET /me/listings）。
 * 列出当前用户自己发布的项（含已售罄/已过期），可点进详情（详情页提供本人删/改），
 * 并在行内直接删除或调整已售出数量。
 * @param buy true 表示“我的收购”（BUY），false 表示“我的商品”（SELL）。
 * @param onItemClick 点击条目进入详情（使用外层导航控制器，详情为全屏同级页）。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    buy: Boolean,
    onItemClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MyListingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val title = if (buy) "我的收购" else "我的商品"
    var adjustTarget by remember { mutableStateOf<ListingSummary?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(buy) { viewModel.setType(if (buy) ListingType.BUY else ListingType.SELL) }

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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.items.isEmpty() ->
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                state.items.isEmpty() ->
                    Text("还没有$title", modifier = Modifier.align(Alignment.Center))
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
        AdjustSoldDialog(
            item = target,
            onDismiss = { adjustTarget = null },
            onConfirm = { qty ->
                viewModel.adjustSold(target.id, qty)
                adjustTarget = null
            },
        )
    }
}

/** 我的列表行：首图缩略图 + 标题/价格/已售状态，行尾“调整已售”与“删除”按钮，点击进入详情。 */
@Composable
private fun MyListingRow(
    item: ListingSummary,
    buy: Boolean,
    adjusting: Boolean,
    onClick: () -> Unit,
    onAdjustClick: () -> Unit,
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
                val soldText = "已售 ${item.quantitySold}/${item.quantityTotal}" + if (soldOut) "（售罄）" else ""
                Text(
                    soldText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (soldOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (adjusting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onAdjustClick) {
                    Icon(Icons.Default.Edit, contentDescription = "调整已售出数量")
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 调整已售出数量对话框：输入 0..quantityTotal 之间的整数。 */
@Composable
private fun AdjustSoldDialog(
    item: ListingSummary,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(item.quantitySold.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 0..item.quantityTotal
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整已售出数量") },
        text = {
            Column {
                Text("总数：${item.quantityTotal}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("已售出数量") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (!valid) {
                    Text("请输入 0 到 ${item.quantityTotal} 之间的整数", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
