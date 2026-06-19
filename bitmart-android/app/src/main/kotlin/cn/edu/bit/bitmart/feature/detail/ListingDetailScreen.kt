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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.R
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
import cn.edu.bit.bitmart.core.ui.priceLabel
import cn.edu.bit.bitmart.core.ui.soldOutLabel
import cn.edu.bit.bitmart.core.ui.soldVerbLabel
import cn.edu.bit.bitmart.core.ui.titleLabel
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
                title = { Text(stringResource(if (isBuy) R.string.detail_title_buy else R.string.detail_title_sell)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.isOwner) {
                        if (state.adjusting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        } else {
                            IconButton(onClick = { showAdjustDialog = true }) {
                                Icon(Icons.Default.Numbers, contentDescription = stringResource(R.string.detail_cd_adjust_quantity))
                            }
                        }
                        IconButton(onClick = { state.detail?.let { onEditClick(it.id) } }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
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
                    stringResource(R.string.detail_need_login),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.error != null -> Text(
                    state.error!!.asString(),
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
        val noun = (state.detail?.type ?: ListingType.SELL).titleLabel()
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.detail_delete_confirm_text, noun)) },
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
                        Text(stringResource(R.string.common_delete))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                    Pill(stringResource(R.string.detail_badge_type_buy), cs.tertiaryContainer, cs.onTertiaryContainer)
                } else {
                    Pill(stringResource(R.string.detail_badge_type_sell), cs.primaryContainer, cs.onPrimaryContainer)
                }
                Pill(
                    stringResource(if (d.category == ListingCategory.BOOK) R.string.detail_badge_category_book else R.string.detail_badge_category_general),
                    cs.secondaryContainer,
                    cs.onSecondaryContainer,
                )
                if (soldOut) {
                    Pill(d.type.soldOutLabel(), cs.error, cs.onError)
                }
                when (expiryStatus) {
                    ExpiryStatus.EXPIRED -> Pill(stringResource(R.string.detail_badge_expired), cs.error, cs.onError)
                    ExpiryStatus.NEAR_EXPIRY -> Pill(stringResource(R.string.detail_badge_near_expiry), ExpiryWarnColor, Color.White)
                    ExpiryStatus.NORMAL -> Unit
                }
            }

            Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            // 价格行：大号售价 + 删除线原价。
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(
                        d.type.priceLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        d.unitPrice?.let { "￥$it" } ?: stringResource(R.string.common_negotiable),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                d.originalPrice?.let {
                    Text(
                        stringResource(R.string.detail_original_price, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            // 数量进度。
            val soldVerb = d.type.soldVerbLabel()
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
        InfoRow(Icons.Default.Person, stringResource(R.string.detail_info_publisher), d.nickname ?: stringResource(R.string.detail_anonymous))
        d.contacts.forEach { c ->
            val label = channelLabel(c.channel)
            InfoRow(
                Icons.Default.Phone,
                stringResource(R.string.detail_info_contact),
                label?.let { stringResource(R.string.detail_info_contact_value, it, c.value) } ?: c.value,
            )
        }
        d.pickupLocation?.let { InfoRow(Icons.Default.LocationOn, stringResource(R.string.detail_info_location), it) }
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
    SectionCard(title = stringResource(R.string.detail_section_book)) {
        book.title?.let { InfoLine(stringResource(R.string.detail_book_title), it) }
        book.authors?.let { InfoLine(stringResource(R.string.detail_book_author), it) }
        book.publisher?.let { InfoLine(stringResource(R.string.detail_book_publisher), it) }
        book.edition?.let { InfoLine(stringResource(R.string.detail_book_edition), it) }
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
        Text(stringResource(R.string.detail_book_label, label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** 渠道字符串 → 本地化标签；未知/空返回 null（不显示药丸）。 */
@Composable
private fun channelLabel(channel: String): String? = when (channel.trim().uppercase()) {
    "WECHAT" -> stringResource(R.string.detail_channel_wechat)
    "QQ" -> stringResource(R.string.detail_channel_qq)
    "PHONE" -> stringResource(R.string.detail_channel_phone)
    "EMAIL" -> stringResource(R.string.detail_channel_email)
    "OTHER" -> stringResource(R.string.detail_channel_other)
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
                contentDescription = stringResource(R.string.image_viewer_image_description, page + 1),
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
                stringResource(R.string.detail_privacy_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.detail_privacy_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
