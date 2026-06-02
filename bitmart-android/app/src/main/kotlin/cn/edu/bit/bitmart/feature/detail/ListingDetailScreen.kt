package cn.edu.bit.bitmart.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ContactChannel
import cn.edu.bit.bitmart.core.domain.model.ListingType
import coil3.compose.AsyncImage

/** 详情屏。展示卖家昵称、联系方式、完整描述、书籍信息；含防诈骗提示、图片轮播、编辑/删除按钮。 */
@Composable
fun ListingDetailScreen(
    listingId: Long,
    onEditClick: (Long) -> Unit = {},
    onDeleteSuccess: () -> Unit = {},
    viewModel: ListingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(listingId) { viewModel.load(listingId) }

    LaunchedEffect(state.deleteSuccess) {
        if (state.deleteSuccess) {
            onDeleteSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        when {
            state.loading -> CircularProgressIndicator()
            state.needLogin -> Text("请登录后查看详情", color = MaterialTheme.colorScheme.error)
            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
            state.detail != null -> {
                val d = state.detail!!

                // 图片轮播
                if (d.imageUrls.isNotEmpty()) {
                    ImageCarousel(d.imageUrls)
                    Spacer(Modifier.height(16.dp))
                }

                // 标题与操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(d.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (state.isOwner) {
                        IconButton(onClick = { onEditClick(d.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                val priceLabel = if (d.type == ListingType.BUY) "期望价" else "售价"
                Text("$priceLabel：${d.unitPrice?.let { "￥$it" } ?: "面议"}")
                Text("数量：${d.quantitySold}/${d.quantityTotal} 已售")
                d.pickupLocation?.let { Text("取货地点：$it") }
                Text("发布者：${d.nickname ?: "匿名"}")
                Spacer(Modifier.height(8.dp))
                Text(d.description, style = MaterialTheme.typography.bodyMedium)

                if (d.book != null) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Text("书籍信息", style = MaterialTheme.typography.titleSmall)
                    d.book.title?.let { Text("书名：$it") }
                    d.book.authors?.let { Text("作者：$it") }
                    d.book.publisher?.let { Text("出版社：$it") }
                    d.book.isbn?.let { Text("ISBN：$it") }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Text("联系方式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))

                // 多渠道联系方式展示
                d.contacts.forEach { contact ->
                    ContactItem(contact.channel, contact.value)
                }

                // 隐私提示横幅
                Spacer(Modifier.height(8.dp))
                PrivacyReminderBanner()
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这条商品吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteListing(listingId)
                    },
                    enabled = !state.deleteInProgress,
                ) {
                    if (state.deleteInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("删除")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
fun ImageCarousel(imageUrls: List<String>, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })

    Box(modifier = modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            AsyncImage(
                model = imageUrls[page],
                contentDescription = "商品图片 ${page + 1}",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )
        }

        // 页面指示器
        if (imageUrls.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(imageUrls.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
fun ContactItem(channel: ContactChannel, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val channelLabel = when (channel) {
            ContactChannel.WECHAT -> "微信"
            ContactChannel.QQ -> "QQ"
            ContactChannel.PHONE -> "手机"
            ContactChannel.EMAIL -> "邮箱"
            ContactChannel.OTHER -> "其他"
        }
        Text("$channelLabel：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PrivacyReminderBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "隐私提示",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "请自行核实对方身份，谨防诈骗。优先使用微信/QQ 联系，使用手机号存在隐私风险。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
