package cn.edu.bit.bitmart.feature.publish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.scale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.ui.blobKeyToMediaUrl
import coil3.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 发布屏（多草稿批量模型）：先选书籍/一般商品，填写字段后加入批次，最后一并提交。
 * 书籍可扫码 ISBN → lookupBook 预填；可拍照/上传图片 → uploadImage / LLM 识别。
 * @param initialType 发布类型，由入口决定（商品 tab→SELL，收购 tab→BUY），页内不再切换。
 * @param onPublished 批次提交成功后回调（pop back）。
 * @param onNavigateToLlmSettings LLM 未配置时跳转 LLM 设置页。
 * @param onNavigateToBookScan 打开书籍条码扫描页。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    initialType: ListingType = ListingType.SELL,
    onPublished: () -> Unit,
    onNavigateToLlmSettings: () -> Unit,
    onNavigateToBookScan: () -> Unit,
    /** 非空表示编辑模式：编辑该 listing（字段与发布页相同）。 */
    editListingId: Long? = null,
    /** 从条码扫描页回传的 ISBN（NavHost 观察 savedStateHandle 后传入）。 */
    scannedIsbn: String? = null,
    onIsbnConsumed: () -> Unit = {},
    viewModel: PublishViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val editMode = editListingId != null

    // 发布：类型由入口决定（进入时设置一次）；编辑：拉取详情预填表单。
    LaunchedEffect(Unit) {
        if (editListingId != null) viewModel.loadForEdit(editListingId) else viewModel.setType(initialType)
    }

    // 从条码扫描页收到 ISBN → 预填书籍草稿。
    LaunchedEffect(scannedIsbn) {
        if (scannedIsbn != null) {
            viewModel.lookupBook(scannedIsbn)
            onIsbnConsumed()
        }
    }

    LaunchedEffect(state.batchSubmitted) {
        if (state.batchSubmitted) onPublished()
    }

    // 编辑保存成功后返回（并触发上游列表刷新，由 NavHost 接线）。
    LaunchedEffect(state.saved) {
        if (state.saved) onPublished()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PublishEvent.NavigateToLlmSettings -> onNavigateToLlmSettings()
                PublishEvent.NavigateToBookScan -> onNavigateToBookScan()
            }
        }
    }

    // 校验/暂存/提交等瞬时错误用 Toast 展示：本页较长且提交按钮在底部，输入区上方的提示在提交时常已滚出视野。
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    // 仅上传图片（加入当前草稿的 imageKeys）。
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = compressImage(context, it)
            if (bytes != null) viewModel.uploadImage(bytes, "image.jpg")
        }
    }

    // 拍照识别：同一张图片既上传（得 blobKey）又交给 LLM 识别合并到当前草稿。
    val recognizePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = compressImage(context, it)
            if (bytes != null) {
                viewModel.uploadImage(bytes, "image.jpg")
                viewModel.recognizeWithLlm(bytes)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 类型由入口决定（商品/收购），标题随之；页内不再提供卖/买切换。编辑模式标题为"编辑…"。
        Text(
            when {
                editMode && state.type == ListingType.BUY -> "编辑收购"
                editMode -> "编辑商品"
                state.type == ListingType.BUY -> "发布收购（批量）"
                else -> "发布商品（批量）"
            },
            style = MaterialTheme.typography.headlineSmall,
        )

        // 书籍/一般商品 类别选择。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                state.selectedCategory == ListingCategory.GENERAL,
                { viewModel.setCategory(ListingCategory.GENERAL) },
                { Text("一般商品") },
            )
            FilterChip(
                state.selectedCategory == ListingCategory.BOOK,
                { viewModel.setCategory(ListingCategory.BOOK) },
                { Text("书籍") },
            )
        }

        // 当前编辑的草稿字段。
        val draft = state.currentDraft
        if (draft.category == ListingCategory.BOOK) {
            // 书籍专属字段。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.isbn ?: "",
                    onValueChange = viewModel::onIsbn,
                    label = { Text("ISBN") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { draft.isbn?.takeIf { it.isNotBlank() }?.let(viewModel::lookupBook) },
                    enabled = !state.lookingUpBook && !draft.isbn.isNullOrBlank(),
                ) {
                    Icon(Icons.Default.Search, contentDescription = "查询书籍")
                }
                IconButton(onClick = viewModel::openBookScan) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码")
                }
            }
            OutlinedTextField(draft.author ?: "", viewModel::onAuthor, label = { Text("作者") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.publisher ?: "", viewModel::onPublisher, label = { Text("出版社") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.edition ?: "", viewModel::onEdition, label = { Text("版本") }, modifier = Modifier.fillMaxWidth())
        }

        OutlinedTextField(draft.title, viewModel::onTitle, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.description, viewModel::onDescription, label = { Text("描述") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        val priceLabel = if (state.type == ListingType.BUY) "期望价（可留空面议）" else "售价（可留空面议）"
        OutlinedTextField(draft.unitPrice, viewModel::onUnitPrice, label = { Text(priceLabel) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.quantityTotal, viewModel::onQuantity, label = { Text("件数") }, modifier = Modifier.fillMaxWidth())

        // 有效期：下拉选择"有效天数 / 过期日期"，右侧输入随之切换（天数框 / 日期选择）。
        var expiryModeMenu by remember { mutableStateOf(false) }
        var showDatePicker by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { expiryModeMenu = true }) {
                    Text(if (draft.expiryMode == ExpiryMode.DAYS) "有效天数" else "过期日期")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "切换有效期方式")
                }
                DropdownMenu(expanded = expiryModeMenu, onDismissRequest = { expiryModeMenu = false }) {
                    DropdownMenuItem(text = { Text("有效天数") }, onClick = { viewModel.onExpiryMode(ExpiryMode.DAYS); expiryModeMenu = false })
                    DropdownMenuItem(text = { Text("过期日期") }, onClick = { viewModel.onExpiryMode(ExpiryMode.DATE); expiryModeMenu = false })
                }
            }
            when (draft.expiryMode) {
                ExpiryMode.DAYS -> OutlinedTextField(
                    draft.expiresInDays,
                    viewModel::onExpiresInDays,
                    label = { Text("默认${PublishConfig.EXPIRY_DEFAULT_DAYS}天") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                ExpiryMode.DATE -> OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(draft.expiresOn ?: "选择过期日期")
                }
            }
        }
        if (showDatePicker) {
            val today = remember { LocalDate.now() }
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = draft.expiresOn?.let {
                    runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
                },
                // DatePicker 的毫秒按 UTC 解读，故此处用 UTC 还原日期再做边界判断。
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        return d.isAfter(today) && !d.isAfter(today.plusDays(PublishConfig.EXPIRY_MAX_DAYS.toLong()))
                    }
                },
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.onExpiresOn(picked.toString())
                        }
                        showDatePicker = false
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
            ) {
                DatePicker(state = datePickerState)
            }
        }
        OutlinedTextField(draft.pickupLocation, viewModel::onPickup, label = { Text("取货地点") }, modifier = Modifier.fillMaxWidth())

        // 常用联系方式快速填入（来自"我的-常用联系方式"，仅本机）。空列表时不展示。显示在输入框上方。
        if (state.commonContacts.isNotEmpty()) {
            Text("常用联系方式（点击填入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.commonContacts.forEach { c ->
                    FilterChip(
                        selected = draft.contact.trim() == c,
                        onClick = { viewModel.onContact(c) },
                        label = { Text(c) },
                    )
                }
            }
        }
        OutlinedTextField(draft.contact, viewModel::onContact, label = { Text("联系方式（微信/QQ/手机号等）") }, modifier = Modifier.fillMaxWidth())

        // 标签（热门 + 自定义）。
        Text("标签（最多${PublishConfig.MAX_TAGS}个）", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.popularTags.forEach { tag ->
                FilterChip(tag in draft.tags, { viewModel.toggleTag(tag) }, { Text(tag) })
            }
        }
        var customTagInput by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customTagInput,
                onValueChange = { customTagInput = it },
                label = { Text("自定义标签") },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.addCustomTag(customTagInput); customTagInput = "" },
                enabled = customTagInput.isNotBlank() && draft.tags.size < PublishConfig.MAX_TAGS,
            ) {
                Text("添加")
            }
        }
        if (draft.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                draft.tags.forEach { tag ->
                    FilterChip(true, { viewModel.toggleTag(tag) }, { Text(tag) })
                }
            }
        }

        // 图片上传 / LLM 识别。达到张数上限后两个入口都禁用。
        val canAddImage = draft.imageKeys.size < PublishConfig.MAX_IMAGES
        Text("图片（${draft.imageKeys.size}/${PublishConfig.MAX_IMAGES}）", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !state.uploadingImage && canAddImage,
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("选择图片")
            }
            OutlinedButton(
                onClick = { recognizePicker.launch("image/*") },
                enabled = !state.llmRecognizing && canAddImage,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("拍照识别")
            }
        }

        // 已上传图片缩略图：点击放大预览，右上角垃圾桶删除。
        var previewImageKey by remember { mutableStateOf<String?>(null) }
        if (draft.imageKeys.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.imageKeys.forEachIndexed { index, key ->
                    Box {
                        AsyncImage(
                            model = blobKeyToMediaUrl(key),
                            contentDescription = "图片${index + 1}",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { previewImageKey = key },
                            contentScale = ContentScale.Crop,
                        )
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除图片${index + 1}",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { viewModel.removeImage(index) }
                                .padding(6.dp)
                                .size(18.dp),
                        )
                    }
                }
            }
        }

        // 全屏图片预览（点击任意处关闭）。
        previewImageKey?.let { key ->
            Dialog(
                onDismissRequest = { previewImageKey = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { previewImageKey = null },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = blobKeyToMediaUrl(key),
                        contentDescription = "图片预览",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }

        // 输入了未保存的新联系方式时，询问是否加入常用联系方式。
        state.pendingSaveContact?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPendingContact() },
                title = { Text("保存到常用联系方式？") },
                text = { Text("将“$pending”加入常用联系方式，方便下次直接选择。") },
                confirmButton = { TextButton(onClick = { viewModel.savePendingContact() }) { Text("保存") } },
                dismissButton = { TextButton(onClick = { viewModel.dismissPendingContact() }) { Text("暂不") } },
            )
        }

        if (state.uploadingImage || state.llmRecognizing || state.lookingUpBook) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.uploadingImage -> "上传中..."
                        state.llmRecognizing -> "识别中..."
                        state.lookingUpBook -> "查询书籍中..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (editMode) {
            // 编辑模式：单条保存。
            Button(
                onClick = viewModel::saveEdit,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                else Text("保存")
            }
        } else {
            // 加入批次按钮。
            Button(onClick = viewModel::addDraftToBatch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("加入待发布列表")
            }

            // 已加入的批次列表。
            if (state.draftBatch.isNotEmpty()) {
                Text("待发布列表（${state.draftBatch.size}项）", style = MaterialTheme.typography.titleMedium)
                state.draftBatch.forEachIndexed { index, item ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.editDraft(index) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${item.category.name} · ${item.quantityTotal}件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Row {
                                IconButton(onClick = { viewModel.editDraft(index) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                                }
                                IconButton(onClick = { viewModel.removeDraft(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }

                // 批量提交按钮。
                Button(
                    onClick = viewModel::submitBatch,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    else Text("提交全部（${state.draftBatch.size}项）")
                }
            }
        }
    }
    }
}

/** 压缩并转换图片为 JPEG 字节（1024px 上限，80% 质量）。 */
private fun compressImage(context: Context, uri: Uri): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val maxDimension = 1024
        val scaledBitmap = if (originalBitmap.width > maxDimension || originalBitmap.height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
            val newWidth = (originalBitmap.width * scale).toInt()
            val newHeight = (originalBitmap.height * scale).toInt()
            originalBitmap.scale(newWidth, newHeight).also {
                if (it != originalBitmap) originalBitmap.recycle()
            }
        } else originalBitmap

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        scaledBitmap.recycle()

        outputStream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
