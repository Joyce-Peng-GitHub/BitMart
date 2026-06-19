package cn.edu.bit.bitmart.feature.publish

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val CONTACT_PRIVACY_HINT = "手机号可能泄露隐私，建议使用微信、QQ 等其他联系方式。"

/**
 * 发布屏（多草稿批量模型）：填写字段后逐条加入暂存区，最后一并提交。
 * 暂存区不再内联在表单下方，而是由右下角悬浮按钮（角标显示项数）或从右侧边缘滑入唤出的右抽屉承载：
 * 抽屉内可点项进入编辑、点「新建项」清空表单新建、点「提交全部」批量发布。
 * 书籍可扫码 ISBN → lookupBook 预填；可拍照/上传图片 → uploadImage / LLM 识别。
 * @param initialType 发布类型，由入口决定（商品 tab→SELL，收购 tab→BUY），页内不再切换。
 * @param onPublished 批次提交成功后回调（pop back）。
 * @param onNavigateToLlmSettings LLM 未配置时跳转 LLM 设置页。
 * @param onNavigateToBookScan 打开书籍条码扫描页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    initialType: ListingType = ListingType.SELL,
    onPublished: () -> Unit,
    onNavigateToLlmSettings: () -> Unit,
    onNavigateToBookScan: () -> Unit,
    onBack: () -> Unit = {},
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

    // 从相册选取上传（加入当前草稿的 imageKeys）。
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = compressImage(context, it)
            if (bytes != null) viewModel.uploadImage(bytes, "image.jpg")
        }
    }

    // 从相册选取识别：识别图中所有项 → 各成一条草稿入暂存区；是否把该图作为商品图由识别后的确认弹窗决定。
    val recognizePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = compressImage(context, it)
            if (bytes != null) {
                viewModel.recognizeWithLlm(bytes)
            }
        }
    }

    // 相机拍摄：写入临时文件的 content URI，拍摄成功后读取压缩。识图与加图各一个启动器，共用最近一次的 URI。
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val takePhotoForUpload = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraImageUri
        if (success && uri != null) {
            val bytes = compressImage(context, uri)
            if (bytes != null) viewModel.uploadImage(bytes, "image.jpg")
        }
    }
    val takePhotoForRecognize = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraImageUri
        if (success && uri != null) {
            val bytes = compressImage(context, uri)
            if (bytes != null) viewModel.recognizeWithLlm(bytes)
        }
    }

    // 拍照需运行时相机权限（清单已声明 CAMERA，ACTION_IMAGE_CAPTURE 因此要求授权）。授权后执行挂起的拍摄动作。
    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingCameraAction?.invoke()
        else Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        pendingCameraAction = null
    }
    val ensureCameraThen = { action: () -> Unit ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val onPickImageFromGallery = { imagePicker.launch("image/*"); Unit }
    val onRecognizeFromGallery = { recognizePicker.launch("image/*"); Unit }
    val onPickImageFromCamera = {
        ensureCameraThen {
            val uri = createCaptureUri(context)
            cameraImageUri = uri
            takePhotoForUpload.launch(uri)
        }
    }
    val onRecognizeFromCamera = {
        ensureCameraThen {
            val uri = createCaptureUri(context)
            cameraImageUri = uri
            takePhotoForRecognize.launch(uri)
        }
    }

    val screenTitle = when {
        editMode && state.type == ListingType.BUY -> "编辑收购"
        editMode -> "编辑商品"
        state.type == ListingType.BUY -> "发布收购（批量）"
        else -> "发布商品（批量）"
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text(screenTitle) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
    }

    if (editMode) {
        // 编辑模式：单条编辑保存，无暂存区/悬浮按钮/抽屉。
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = topBar) { innerPadding ->
            PublishFormColumn(
                state = state,
                viewModel = viewModel,
                editMode = true,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onPickImageFromGallery = onPickImageFromGallery,
                onPickImageFromCamera = onPickImageFromCamera,
                onRecognizeFromGallery = onRecognizeFromGallery,
                onRecognizeFromCamera = onRecognizeFromCamera,
            )
        }
        return
    }

    // 发布模式：右侧抽屉承载暂存区。把布局方向临时翻转为 RTL，使 ModalNavigationDrawer 从右侧滑出，
    // 并让 gesturesEnabled 的滑动开合落在屏幕右缘；抽屉内容与正文再翻回原始方向，避免镜像。
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    // 抽屉打开时拦截返回键 → 先关抽屉而非退出发布页。
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    // 展示用列表：把表单中"尚未保存的临时项"也作为一项（编辑既有项时实时替换该槽位，
    // 编写新项且非空白时追加到末尾）；currentIndex 标记其在列表中的位置。
    val displayItems = stagingDisplayItems(state)
    val currentIndex = stagingCurrentIndex(state)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    // 抽屉只占右侧大部分屏幕，左侧少部分露出被阴影覆盖的表单。
                    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.85f)) {
                        StagingDrawerBody(
                            items = displayItems,
                            currentIndex = currentIndex,
                            loading = state.loading,
                            onSelect = { index ->
                                if (index != currentIndex) viewModel.editDraft(index)
                                scope.launch { drawerState.close() }
                            },
                            onDelete = { index ->
                                // 末尾那条"临时项"（编辑新项时）只是表单内容，删除即丢弃；其余为真实槽位。
                                if (state.editingIndex == null && index == state.draftBatch.size) viewModel.discardDraft()
                                else viewModel.removeDraft(index)
                            },
                            onNew = {
                                viewModel.newDraft()
                                scope.launch { drawerState.close() }
                            },
                            onSubmitAll = {
                                scope.launch { drawerState.close() }
                                viewModel.submitBatch()
                            },
                        )
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = topBar,
                    floatingActionButton = {
                        FloatingActionButton(onClick = { scope.launch { drawerState.open() } }) {
                            BadgedBox(badge = {
                                if (displayItems.isNotEmpty()) Badge { Text("${displayItems.size}") }
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = "待发布列表（${displayItems.size} 项）",
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    PublishFormColumn(
                        state = state,
                        viewModel = viewModel,
                        editMode = false,
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        onPickImageFromGallery = onPickImageFromGallery,
                        onPickImageFromCamera = onPickImageFromCamera,
                        onRecognizeFromGallery = onRecognizeFromGallery,
                        onRecognizeFromCamera = onRecognizeFromCamera,
                    )
                }
            }
        }
    }
}

/**
 * 发布/编辑表单主体（可滚动）。暂存区列表与「提交全部」已移入右抽屉，此处仅保留单条编辑与
 * 「加入待发布列表 / 保存修改 / 保存」按钮。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PublishFormColumn(
    state: PublishUiState,
    viewModel: PublishViewModel,
    editMode: Boolean,
    modifier: Modifier,
    onPickImageFromGallery: () -> Unit,
    onPickImageFromCamera: () -> Unit,
    onRecognizeFromGallery: () -> Unit,
    onRecognizeFromCamera: () -> Unit,
) {
    val draft = state.currentDraft
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 图片识别：拍照或从相册选图，自动识别其中的商品并分别生成草稿。独立卡片置于最上方。
        // 识别通常耗时数十秒，进行中用转圈替换两个按钮与说明。
        FormSection("图片识别") {
            if (state.llmRecognizing) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        "识别中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRecognizeFromCamera,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("拍照")
                    }
                    Button(
                        onClick = onRecognizeFromGallery,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("相册")
                    }
                }
                Text(
                    "拍摄或选择一张图片，自动识别其中的商品并分别生成待发布草稿。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 基本信息：类别 + 标题/书籍字段 + 描述。
        FormSection("基本信息") {
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
            if (draft.category == ListingCategory.BOOK) {
                // 书籍专属字段：ISBN → 标题 → 作者 → 出版社 → 版本。
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
                OutlinedTextField(draft.title, viewModel::onTitle, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.author ?: "", viewModel::onAuthor, label = { Text("作者") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.publisher ?: "", viewModel::onPublisher, label = { Text("出版社") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.edition ?: "", viewModel::onEdition, label = { Text("版本") }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(draft.title, viewModel::onTitle, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(draft.description, viewModel::onDescription, label = { Text("描述") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        }

        // 价格与数量：原价/售价并排 + 件数 + 有效期。
        FormSection("价格与数量") {
            val priceLabel = if (state.type == ListingType.BUY) "期望价（可留空面议）" else "售价（可留空面议）"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.originalPrice, viewModel::onOriginalPrice, label = { Text("原价") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(draft.unitPrice, viewModel::onUnitPrice, label = { Text(priceLabel) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(draft.quantityTotal, viewModel::onQuantity, label = { Text("件数") }, singleLine = true, modifier = Modifier.fillMaxWidth())

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
        }

        // 交易信息：取货地点 + 联系方式。
        FormSection("交易信息") {
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
            OutlinedTextField(draft.contact, viewModel::onContact, label = { Text("联系方式") }, modifier = Modifier.fillMaxWidth())
            Text(
                CONTACT_PRIVACY_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 标签（热门 + 自定义）。
        FormSection("标签（最多${PublishConfig.MAX_TAGS}个）") {
            if (state.popularTags.isNotEmpty()) {
                Text("热门标签", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.popularTags.forEach { tag ->
                        FilterChip(tag in draft.tags, { viewModel.toggleTag(tag) }, { Text(tag) })
                    }
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
        }

        // 图片上传。达到张数上限后入口禁用。
        FormSection("图片（${draft.imageKeys.size}/${PublishConfig.MAX_IMAGES}）") {
            val canAddImage = draft.imageKeys.size < PublishConfig.MAX_IMAGES
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickImageFromCamera,
                    enabled = !state.uploadingImage && canAddImage,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("拍照")
                }
                OutlinedButton(
                    onClick = onPickImageFromGallery,
                    enabled = !state.uploadingImage && canAddImage,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("相册")
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

        // 识别完成后批量填充：勾选是否附图，并可统一填写原价/价格/有效期/交易地点/联系方式/标签。
        if (state.pendingRecognitionImage != null) {
            RecognitionBatchDialog(
                count = state.recognizedCount,
                isBuy = state.type == ListingType.BUY,
                commonContacts = state.commonContacts,
                popularTags = state.popularTags,
                onApply = { attach, originalPrice, unitPrice, days, pickup, contact, tags ->
                    viewModel.applyToRecognized(attach, originalPrice, unitPrice, days, pickup, contact, tags)
                },
                onDismiss = { viewModel.dismissRecognitionImage() },
            )
        }

        // 识别进度由顶部「图片识别」卡承载，这里只覆盖上传 / 查询书籍。
        if (state.uploadingImage || state.lookingUpBook) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.uploadingImage -> "上传中..."
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
            // 加入暂存区：编辑既有项时写回原位（按钮文案随之切换），否则追加新项。
            val editing = state.editingIndex != null
            Button(onClick = viewModel::addDraftToBatch, modifier = Modifier.fillMaxWidth()) {
                Icon(if (editing) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (editing) "保存修改" else "加入待发布列表")
            }
            // 底部留白，避免最后一个按钮被悬浮按钮遮挡。
            Spacer(Modifier.height(72.dp))
        }
    }
}

/** 表单分区卡片：小节标题 + 字段列。 */
@Composable
private fun FormSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/**
 * 右抽屉内的暂存区：可滚动的列表（含表单中尚未保存的临时项），点项进入编辑、尾部垃圾桶删除，
 * 当前正在编辑的项高亮并标注"（编辑中）"；其下固定「新建项」与「提交全部」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StagingDrawerBody(
    items: List<DraftItem>,
    currentIndex: Int?,
    loading: Boolean,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onNew: () -> Unit,
    onSubmitAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "待发布列表（${items.size}项）",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无待发布项\n点击下方“新建项”开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items) { index, item ->
                    val isCurrent = index == currentIndex
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(
                                    item.title.ifBlank { "未命名" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    draftSubtitle(item) + if (isCurrent) " · 编辑中" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            IconButton(onClick = { onDelete(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 新建项：把当前项并入列表后清空表单，进入一条新草稿。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onNew() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("新建项", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onSubmitAll,
            enabled = items.isNotEmpty() && !loading,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("提交全部（${items.size}项）")
        }
    }
}

/**
 * 展示用列表：在暂存项基础上纳入表单中的当前临时项。
 * - 正在编辑既有项 → 用实时的 currentDraft 替换该槽位（数量不变）。
 * - 编写新项且非空白 → 把 currentDraft 追加到末尾。
 * - 空白新项 → 不显示额外项。
 */
private fun stagingDisplayItems(state: PublishUiState): List<DraftItem> {
    val idx = state.editingIndex
    return when {
        idx != null && idx in state.draftBatch.indices ->
            state.draftBatch.toMutableList().also { it[idx] = state.currentDraft }
        idx == null && !state.currentDraft.isBlankDraft() ->
            state.draftBatch + state.currentDraft
        else -> state.draftBatch
    }
}

/** 当前表单项在展示列表中的下标；空白新项时为 null（列表不含临时项）。 */
private fun stagingCurrentIndex(state: PublishUiState): Int? = when {
    state.editingIndex != null -> state.editingIndex
    !state.currentDraft.isBlankDraft() -> state.draftBatch.size
    else -> null
}

/** 暂存项副标题：类别 + 件数。 */
private fun draftSubtitle(item: DraftItem): String {
    val category = if (item.category == ListingCategory.BOOK) "书籍" else "一般商品"
    return "$category · ${item.quantityTotal}件"
}

/**
 * 识别完成后的批量填充对话框：勾选是否把识别图作为所有项的图片，并可统一填写
 * 原价/售价(期望价)/有效天数/交易地点/联系方式/标签。留空的字段不覆盖各项原值。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecognitionBatchDialog(
    count: Int,
    isBuy: Boolean,
    commonContacts: List<String>,
    popularTags: List<String>,
    onApply: (
        attachImage: Boolean,
        originalPrice: String,
        unitPrice: String,
        expiresInDays: String,
        pickupLocation: String,
        contact: String,
        tags: List<String>,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var attachImage by remember { mutableStateOf(true) }
    var originalPrice by remember { mutableStateOf("") }
    var unitPrice by remember { mutableStateOf("") }
    var expiresInDays by remember { mutableStateOf("") }
    var pickupLocation by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var customTagInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("识别完成 · 批量填写（$count 项）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "以下信息将统一应用到识别出的 $count 项；留空的项保持不变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { attachImage = !attachImage },
                ) {
                    Checkbox(checked = attachImage, onCheckedChange = { attachImage = it })
                    Text("将这张图片作为所有项的图片")
                }
                OutlinedTextField(originalPrice, { originalPrice = it }, label = { Text("原价") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    unitPrice,
                    { unitPrice = it },
                    label = { Text(if (isBuy) "期望价（可留空面议）" else "售价（可留空面议）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(expiresInDays, { expiresInDays = it }, label = { Text("有效天数") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pickupLocation, { pickupLocation = it }, label = { Text("交易地点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (commonContacts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        commonContacts.forEach { c ->
                            FilterChip(selected = contact.trim() == c, onClick = { contact = c }, label = { Text(c) })
                        }
                    }
                }
                OutlinedTextField(contact, { contact = it }, label = { Text("联系方式") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    CONTACT_PRIVACY_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 标签：与发布页一致——热门标签可点选，自定义标签输入后"添加"，已选标签点击移除。
                Text("标签（最多${PublishConfig.MAX_TAGS}个）", style = MaterialTheme.typography.titleSmall)
                if (popularTags.isNotEmpty()) {
                    Text("热门标签", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        popularTags.forEach { tag ->
                            FilterChip(
                                selected = tag in tags,
                                onClick = {
                                    tags = if (tag in tags) tags - tag
                                    else if (tags.size >= PublishConfig.MAX_TAGS) tags else tags + tag
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customTagInput,
                        onValueChange = { customTagInput = it },
                        label = { Text("自定义标签") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val t = customTagInput.trim()
                            if (t.isNotEmpty() && t !in tags && tags.size < PublishConfig.MAX_TAGS) tags = tags + t
                            customTagInput = ""
                        },
                        enabled = customTagInput.isNotBlank() && tags.size < PublishConfig.MAX_TAGS,
                    ) {
                        Text("添加")
                    }
                }
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { tag ->
                            FilterChip(selected = true, onClick = { tags = tags - tag }, label = { Text(tag) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(attachImage, originalPrice, unitPrice, expiresInDays, pickupLocation, contact, tags)
            }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("跳过") } },
    )
}

/** 在缓存目录新建临时文件并返回其 FileProvider content URI，供相机应用写入拍摄结果。 */
private fun createCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** 压缩并转换图片为 JPEG 字节（1024px 上限，80% 质量）。 */
private fun compressImage(context: Context, uri: Uri): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val maxDimension = PublishConfig.IMAGE_MAX_DIMENSION_PX
        val scaledBitmap = if (originalBitmap.width > maxDimension || originalBitmap.height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
            val newWidth = (originalBitmap.width * scale).toInt()
            val newHeight = (originalBitmap.height * scale).toInt()
            originalBitmap.scale(newWidth, newHeight).also {
                if (it != originalBitmap) originalBitmap.recycle()
            }
        } else originalBitmap

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, PublishConfig.IMAGE_JPEG_QUALITY, outputStream)
        scaledBitmap.recycle()

        outputStream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
