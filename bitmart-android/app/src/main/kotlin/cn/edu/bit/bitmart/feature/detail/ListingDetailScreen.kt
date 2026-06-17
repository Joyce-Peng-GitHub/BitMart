package cn.edu.bit.bitmart.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.ui.AdjustQuantityDialog
import cn.edu.bit.bitmart.core.ui.ExpiryStatus
import cn.edu.bit.bitmart.core.ui.ExpiryWarnColor
import cn.edu.bit.bitmart.core.ui.ImageViewer
import cn.edu.bit.bitmart.core.ui.ListingTimeInfo
import cn.edu.bit.bitmart.core.ui.absoluteMediaUrl
import cn.edu.bit.bitmart.core.ui.expiryStatusOf
import coil3.compose.AsyncImage

/** 详情屏。展示卖家昵称、联系方式、完整描述、书籍信息；含防诈骗提示、图片轮播、调整数量/编辑/删除按钮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: Long,
    onBack: () -> Unit = {},
    onEditClick: (Long) -> Unit = {},
    onDeleteSuccess: () -> Unit = {},
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: ListingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var viewerPage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(listingId) { viewModel.load(listingId) }

    // 编辑保存返回后重新加载（标题/价格等可能已变），并通知上层（“我的”列表）刷新。
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.load(listingId)
            onRefreshConsumed()
        }
    }

    LaunchedEffect(state.deleteSuccess) {
        if (state.deleteSuccess) {
            onDeleteSuccess()
        }
    }

    val isBuy = state.detail?.type == ListingType.BUY

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(if (isBuy) "求购详情" else "商品详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isOwner) {
                        if (state.adjusting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        } else {
                            IconButton(onClick = { showAdjustDialog = true }) {
                                Icon(Icons.Default.Numbers, contentDescription = "调整数量")
                            }
                        }
                        IconButton(onClick = { state.detail?.let { onEditClick(it.id) } }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.needLogin -> Text(
                    "请登录后查看详情",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.error != null -> Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.detail != null -> DetailContent(
                    detail = state.detail!!,
                    onImageClick = { viewerPage = it },
                )
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        val noun = if (isBuy) "收购" else "商品"
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这条${noun}吗？") },
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

    // 调整数量对话框（仅本人）
    state.detail?.let { d ->
        if (showAdjustDialog) {
            AdjustQuantityDialog(
                quantityTotal = d.quantityTotal,
                currentQuantitySold = d.quantitySold,
                buy = d.type == ListingType.BUY,
                onDismiss = { showAdjustDialog = false },
                onConfirm = { qty ->
                    viewModel.adjustSold(qty)
                    showAdjustDialog = false
                },
            )
        }
    }

    // 全屏图片查看器：单击缩略图打开，支持双指缩放与左右滑动切换。
    viewerPage?.let { page ->
        ImageViewer(
            imageUrls = state.detail?.imageUrls?.mapNotNull { absoluteMediaUrl(it) } ?: emptyList(),
            initialPage = page,
            onDismiss = { viewerPage = null },
        )
    }
}

/** 详情正文：全宽轮播 + 概要/信息/描述/书籍/联系方式分组卡片。 */
@Composable
private fun DetailContent(
    detail: ListingDetail,
    onImageClick: (Int) -> Unit,
) {
    val d = detail
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 图片轮播（全宽，无图时不渲染）。
        if (d.imageUrls.isNotEmpty()) {
            ImageCarousel(d.imageUrls, onImageClick = onImageClick)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(d)
            InfoCard(d)
            TimeCard(d)
            d.book?.let { BookCard(it) }
            PrivacyReminderBanner()
        }
    }
}

/** 概要卡：徽章 + 标题 + 放大价格 + 数量进度 + 标签。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(d: ListingDetail) {
    val isBuy = d.type == ListingType.BUY
    val soldOut = d.quantitySold >= d.quantityTotal
    val expiryStatus = expiryStatusOf(d.expiresAt)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 徽章行：类型 / 品类 / 状态。
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val cs = MaterialTheme.colorScheme
                if (isBuy) {
                    Pill("求购", cs.tertiaryContainer, cs.onTertiaryContainer)
                } else {
                    Pill("出售", cs.primaryContainer, cs.onPrimaryContainer)
                }
                Pill(
                    if (d.category == ListingCategory.BOOK) "书籍" else "物品",
                    cs.secondaryContainer,
                    cs.onSecondaryContainer,
                )
                if (soldOut) {
                    Pill(if (isBuy) "已收满" else "售罄", cs.error, cs.onError)
                }
                when (expiryStatus) {
                    ExpiryStatus.EXPIRED -> Pill("已过期", cs.error, cs.onError)
                    ExpiryStatus.NEAR_EXPIRY -> Pill("临期", ExpiryWarnColor, Color.White)
                    ExpiryStatus.NORMAL -> Unit
                }
            }

            Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            // 价格行：大号售价 + 删除线原价。
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(
                        if (isBuy) "期望价" else "售价",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        d.unitPrice?.let { "￥$it" } ?: "面议",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                d.originalPrice?.let {
                    Text(
                        "原价￥$it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            // 数量进度。
            val soldVerb = if (isBuy) "已收" else "已售"
            val qtyColor = if (soldOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$soldVerb ${d.quantitySold}/${d.quantityTotal}", style = MaterialTheme.typography.bodyMedium, color = qtyColor)
                val progress = if (d.quantityTotal > 0) (d.quantitySold.toFloat() / d.quantityTotal).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (soldOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }

            // 标签（只读展示）。
            if (d.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val cs = MaterialTheme.colorScheme
                    d.tags.forEach { Pill("# $it", cs.surfaceVariant, cs.onSurfaceVariant) }
                }
            }

            // 描述（标签下方）。
            if (d.description.isNotBlank()) {
                Text(d.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 信息卡：发布者 / 联系方式 / 交易地点。 */
@Composable
private fun InfoCard(d: ListingDetail) {
    SectionCard {
        InfoRow(Icons.Default.Person, "发布者", d.nickname ?: "匿名")
        d.contacts.forEach { c ->
            val label = channelLabel(c.channel)
            InfoRow(Icons.Default.Phone, "联系方式", label?.let { "$it：${c.value}" } ?: c.value)
        }
        d.pickupLocation?.let { InfoRow(Icons.Default.LocationOn, "交易地点", it) }
    }
}

/** 时间卡：发布时间 / 过期时间（含临期、过期着色）。 */
@Composable
private fun TimeCard(d: ListingDetail) {
    SectionCard {
        ListingTimeInfo(
            createdAtIso = d.createdAt,
            expiresAtIso = d.expiresAt,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 书籍信息卡。 */
@Composable
private fun BookCard(book: BookInfo) {
    SectionCard(title = "书籍信息") {
        book.title?.let { InfoLine("书名", it) }
        book.authors?.let { InfoLine("作者", it) }
        book.publisher?.let { InfoLine("出版社", it) }
        book.edition?.let { InfoLine("版次", it) }
        book.isbn?.let { InfoLine("ISBN", it) }
    }
}

/** 统一卡片容器：可选小节标题。 */
@Composable
private fun SectionCard(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

/** 带前置图标的「标签 + 值」行。 */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 无图标的「标签：值」行（书籍信息用）。 */
@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 小药丸：徽章 / 渠道标签 / 标签共用。 */
@Composable
private fun Pill(text: String, container: Color, contentColor: Color) {
    Surface(color = container, shape = CircleShape, contentColor = contentColor) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** 渠道字符串 → 中文标签；未知/空返回 null（不显示药丸）。 */
private fun channelLabel(channel: String): String? = when (channel.trim().uppercase()) {
    "WECHAT" -> "微信"
    "QQ" -> "QQ"
    "PHONE" -> "手机"
    "EMAIL" -> "邮箱"
    "OTHER" -> "其他"
    else -> null
}

@Composable
fun ImageCarousel(
    imageUrls: List<String>,
    onImageClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })

    Box(modifier = modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            AsyncImage(
                model = absoluteMediaUrl(imageUrls[page]),
                contentDescription = "商品图片 ${page + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onImageClick(page) },
                contentScale = ContentScale.Crop,
            )
        }

        // 页面指示器：当前页加宽为药丸。
        if (imageUrls.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(imageUrls.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (active) 18.dp else 6.dp)
                            .clip(if (active) RoundedCornerShape(3.dp) else CircleShape)
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.5f)),
                    )
                }
            }
        }
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
