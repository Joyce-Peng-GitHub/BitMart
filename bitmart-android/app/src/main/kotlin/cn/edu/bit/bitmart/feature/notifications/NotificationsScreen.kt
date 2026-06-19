package cn.edu.bit.bitmart.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.Notification
import cn.edu.bit.bitmart.core.ui.formatTimestampMinute
import cn.edu.bit.bitmart.core.ui.titleLabel

/** 触底预取距离：最后可见项进入列表末尾该数量内即加载下一页。 */
private const val NOTIFICATIONS_LOAD_MORE_PREFETCH = 3

/**
 * 通知页：公告与个人提醒（如过期提醒）合并展示。点击未读项标记已读。
 * 列表滑动到底部时自动加载下一页（若有游标）。
 * @param onBack 返回上一页的导航回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 接近底部时触发加载更多。
    val shouldLoadMore by remember {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.items.size - NOTIFICATIONS_LOAD_MORE_PREFETCH && state.canLoadMore
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            when {
                state.loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                state.items.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { Text(stringResource(R.string.notif_empty), style = MaterialTheme.typography.bodyLarge) }

                else -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(state.items, key = { it.id }) { n ->
                        NotificationCard(n, onClick = { viewModel.markRead(n.id) })
                    }
                    if (state.loadingMore) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: Notification, onClick: () -> Unit) {
    // 到期提醒按后端结构化 payload 本地化渲染；无法解析（含老数据、未知模板）时回落服务端 title/body。
    val expiry = remember(n) { n.expiryWarningPayload() }
    val title: String
    val body: String
    if (expiry != null) {
        // listingType 已在解析阶段校验为已知枚举，这里 valueOf 安全。
        val typeNoun = ListingType.valueOf(expiry.listingType).titleLabel()
        title = stringResource(R.string.notif_expiry_title, typeNoun)
        body = stringResource(R.string.notif_expiry_body, typeNoun, expiry.listingTitle, expiry.hours)
    } else {
        title = n.title
        body = n.body
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(if (n.isAnnouncement) R.string.notif_chip_announcement else R.string.notif_chip_reminder)) },
                )
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!n.read) Badge()
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text(formatTimestampMinute(n.createdAt), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
