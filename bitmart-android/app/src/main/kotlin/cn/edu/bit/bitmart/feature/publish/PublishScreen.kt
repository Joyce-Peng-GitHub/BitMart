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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import cn.edu.bit.bitmart.R
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
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show()
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
        else Toast.makeText(context, context.getString(R.string.publish_cd_camera_permission_needed), Toast.LENGTH_SHORT).show()
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
        editMode && state.type == ListingType.BUY -> stringResource(R.string.publish_title_edit_buy)
        editMode -> stringResource(R.string.publish_title_edit_sell)
        state.type == ListingType.BUY -> stringResource(R.string.publish_title_new_buy)
        else -> stringResource(R.string.publish_title_new_sell)
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text(screenTitle) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                                    contentDescription = stringResource(R.string.publish_cd_staging_list, displayItems.size),
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
        // 商品类别：一般商品 / 书籍，用标签页切换，置于表单最顶部。切换会更新当前草稿的类别，
        // 进而决定下方"基本信息"显示的字段与图片识别时告知 LLM 的类别。
        val selectedCategoryTab = if (state.selectedCategory == ListingCategory.BOOK) 1 else 0
        PrimaryTabRow(selectedTabIndex = selectedCategoryTab, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = selectedCategoryTab == 0,
                onClick = { viewModel.setCategory(ListingCategory.GENERAL) },
                text = { Text(stringResource(R.string.publish_category_general)) },
            )
            Tab(
                selected = selectedCategoryTab == 1,
                onClick = { viewModel.setCategory(ListingCategory.BOOK) },
                text = { Text(stringResource(R.string.publish_category_book)) },
            )
        }

        // 图片识别：拍照或从相册选图，自动识别其中的商品并分别生成草稿。独立卡片置于最上方。
        // 识别通常耗时数十秒，进行中用转圈替换两个按钮与说明。
        FormSection(stringResource(R.string.publish_section_image_recognition)) {
            if (state.llmRecognizing) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.publish_recognizing),
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
                        Text(stringResource(R.string.common_take_photo))
                    }
                    Button(
                        onClick = onRecognizeFromGallery,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.common_album))
                    }
                }
                Text(
                    stringResource(R.string.publish_recognition_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 基本信息：标题/书籍字段 + 描述（类别已上移到顶部标签页）。
        FormSection(stringResource(R.string.publish_section_basic_info)) {
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
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.publish_cd_lookup_book))
                    }
                    IconButton(onClick = viewModel::openBookScan) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.publish_cd_scan))
                    }
                }
                TitleField(draft.title, viewModel::onTitle, Modifier.fillMaxWidth())
                OutlinedTextField(draft.author ?: "", viewModel::onAuthor, label = { Text(stringResource(R.string.publish_field_author)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.publisher ?: "", viewModel::onPublisher, label = { Text(stringResource(R.string.publish_field_publisher)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.edition ?: "", viewModel::onEdition, label = { Text(stringResource(R.string.publish_field_edition)) }, modifier = Modifier.fillMaxWidth())
            } else {
                TitleField(draft.title, viewModel::onTitle, Modifier.fillMaxWidth())
            }
            DescriptionField(draft.description, viewModel::onDescription, Modifier.fillMaxWidth())
        }

        // 价格与数量：原价/售价并排 + 件数 + 有效期。
        FormSection(stringResource(R.string.publish_section_price_quantity)) {
            val priceLabel = if (state.type == ListingType.BUY) stringResource(R.string.publish_price_label_buy) else stringResource(R.string.publish_price_label_sell)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.originalPrice, viewModel::onOriginalPrice, label = { Text(stringResource(R.string.publish_field_original_price)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(draft.unitPrice, viewModel::onUnitPrice, label = { Text(priceLabel) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(draft.quantityTotal, viewModel::onQuantity, label = { Text(stringResource(R.string.publish_field_quantity)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // 有效期：下拉选择"有效天数 / 过期日期"，右侧输入随之切换（天数框 / 日期选择）。
            var expiryModeMenu by remember { mutableStateOf(false) }
            var showDatePicker by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expiryModeMenu = true }) {
                        Text(if (draft.expiryMode == ExpiryMode.DAYS) stringResource(R.string.publish_expiry_mode_days) else stringResource(R.string.publish_expiry_mode_date))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.publish_cd_switch_expiry_mode))
                    }
                    DropdownMenu(expanded = expiryModeMenu, onDismissRequest = { expiryModeMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.publish_expiry_mode_days)) }, onClick = { viewModel.onExpiryMode(ExpiryMode.DAYS); expiryModeMenu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.publish_expiry_mode_date)) }, onClick = { viewModel.onExpiryMode(ExpiryMode.DATE); expiryModeMenu = false })
                    }
                }
                when (draft.expiryMode) {
                    ExpiryMode.DAYS -> OutlinedTextField(
                        draft.expiresInDays,
                        viewModel::onExpiresInDays,
                        label = { Text(stringResource(R.string.publish_expiry_days_default, PublishConfig.EXPIRY_DEFAULT_DAYS)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    ExpiryMode.DATE -> OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(draft.expiresOn ?: stringResource(R.string.publish_pick_expiry_date))
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
                        }) { Text(stringResource(R.string.common_confirm)) }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) } },
                ) {
                    DatePicker(state = datePickerState)
                }
            }
        }

        // 交易信息：取货地点 + 联系方式。
        FormSection(stringResource(R.string.publish_section_trade_info)) {
            OutlinedTextField(draft.pickupLocation, viewModel::onPickup, label = { Text(stringResource(R.string.publish_field_pickup)) }, modifier = Modifier.fillMaxWidth())

            // 常用联系方式快速填入（来自"我的-常用联系方式"，仅本机）。空列表时不展示。显示在输入框上方。
            if (state.commonContacts.isNotEmpty()) {
                Text(stringResource(R.string.publish_common_contacts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            OutlinedTextField(draft.contact, viewModel::onContact, label = { RequiredLabel(stringResource(R.string.publish_field_contact)) }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.publish_contact_privacy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 标签（热门 + 自定义）。
        FormSection(stringResource(R.string.publish_section_tags, PublishConfig.MAX_TAGS)) {
            if (state.popularTags.isNotEmpty()) {
                Text(stringResource(R.string.filter_popular_tags), style = MaterialTheme.typography.labelMedium)
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
                    label = { Text(stringResource(R.string.publish_field_custom_tag)) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.addCustomTag(customTagInput); customTagInput = "" },
                    enabled = customTagInput.isNotBlank() && draft.tags.size < PublishConfig.MAX_TAGS,
                ) {
                    Text(stringResource(R.string.common_add))
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
        FormSection(stringResource(R.string.publish_section_images, draft.imageKeys.size, PublishConfig.MAX_IMAGES)) {
            val canAddImage = draft.imageKeys.size < PublishConfig.MAX_IMAGES
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickImageFromCamera,
                    enabled = !state.uploadingImage && canAddImage,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_take_photo))
                }
                OutlinedButton(
                    onClick = onPickImageFromGallery,
                    enabled = !state.uploadingImage && canAddImage,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_album))
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
                                contentDescription = stringResource(R.string.publish_cd_image_index, index + 1),
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { previewImageKey = key },
                                contentScale = ContentScale.Crop,
                            )
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.publish_cd_delete_image_index, index + 1),
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
                            contentDescription = stringResource(R.string.publish_cd_image_preview),
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
                title = { Text(stringResource(R.string.publish_save_contact_dialog_title)) },
                text = { Text(stringResource(R.string.publish_save_contact_dialog_text, pending)) },
                confirmButton = { TextButton(onClick = { viewModel.savePendingContact() }) { Text(stringResource(R.string.common_save)) } },
                dismissButton = { TextButton(onClick = { viewModel.dismissPendingContact() }) { Text(stringResource(R.string.publish_save_contact_decline)) } },
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
                        state.uploadingImage -> stringResource(R.string.publish_uploading)
                        state.lookingUpBook -> stringResource(R.string.publish_looking_up_book)
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
                else Text(stringResource(R.string.common_save))
            }
        } else {
            // 加入暂存区：编辑既有项时写回原位（按钮文案随之切换），否则追加新项。
            val editing = state.editingIndex != null
            Button(onClick = viewModel::addDraftToBatch, modifier = Modifier.fillMaxWidth()) {
                Icon(if (editing) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (editing) stringResource(R.string.publish_action_save_edit) else stringResource(R.string.publish_action_add_to_batch))
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

/** 必填字段标签：文案后接红色星号，用 error 配色标识必填。 */
@Composable
private fun RequiredLabel(text: String) {
    Text(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append(" *") }
        },
    )
}

/**
 * 标题输入框：随输入展示「当前长度/上限」计数（超限时 isError 置位，计数随 Material3 自动转红）。
 * 不硬性截断输入，与价格字段一致——允许越限输入并在提交时再校验。书籍/一般商品两处共用，避免分叉。
 */
@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { RequiredLabel(stringResource(R.string.publish_field_title)) },
        isError = value.length > PublishConfig.MAX_TITLE_LENGTH,
        supportingText = { Text(stringResource(R.string.publish_field_counter, value.length, PublishConfig.MAX_TITLE_LENGTH)) },
        modifier = modifier,
    )
}

/** 描述输入框：同 [TitleField] 的越限提示与计数，上限为 [PublishConfig.MAX_DESCRIPTION_LENGTH]。 */
@Composable
private fun DescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.publish_field_description)) },
        minLines = 2,
        isError = value.length > PublishConfig.MAX_DESCRIPTION_LENGTH,
        supportingText = { Text(stringResource(R.string.publish_field_counter, value.length, PublishConfig.MAX_DESCRIPTION_LENGTH)) },
        modifier = modifier,
    )
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
            stringResource(R.string.publish_cd_staging_list, items.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.publish_staging_empty),
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
                                    item.title.ifBlank { stringResource(R.string.publish_unnamed) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    draftSubtitle(item) + if (isCurrent) stringResource(R.string.publish_editing_suffix) else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            IconButton(onClick = { onDelete(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
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
            Text(stringResource(R.string.publish_new_item), style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onSubmitAll,
            enabled = items.isNotEmpty() && !loading,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text(stringResource(R.string.publish_submit_all, items.size))
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
@Composable
private fun draftSubtitle(item: DraftItem): String {
    val category = stringResource(
        if (item.category == ListingCategory.BOOK) R.string.publish_category_book else R.string.publish_category_general,
    )
    return stringResource(R.string.publish_subtitle, category, item.quantityTotal)
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
        title = { Text(stringResource(R.string.publish_recognition_dialog_title, count)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.publish_recognition_dialog_hint, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { attachImage = !attachImage },
                ) {
                    Checkbox(checked = attachImage, onCheckedChange = { attachImage = it })
                    Text(stringResource(R.string.publish_recognition_attach_image))
                }
                OutlinedTextField(originalPrice, { originalPrice = it }, label = { Text(stringResource(R.string.publish_field_original_price)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    unitPrice,
                    { unitPrice = it },
                    label = { Text(if (isBuy) stringResource(R.string.publish_price_label_buy) else stringResource(R.string.publish_price_label_sell)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(expiresInDays, { expiresInDays = it }, label = { Text(stringResource(R.string.publish_expiry_mode_days)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pickupLocation, { pickupLocation = it }, label = { Text(stringResource(R.string.publish_field_trade_location)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (commonContacts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        commonContacts.forEach { c ->
                            FilterChip(selected = contact.trim() == c, onClick = { contact = c }, label = { Text(c) })
                        }
                    }
                }
                OutlinedTextField(contact, { contact = it }, label = { Text(stringResource(R.string.publish_field_contact)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.publish_contact_privacy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 标签：与发布页一致——热门标签可点选，自定义标签输入后"添加"，已选标签点击移除。
                Text(stringResource(R.string.publish_section_tags, PublishConfig.MAX_TAGS), style = MaterialTheme.typography.titleSmall)
                if (popularTags.isNotEmpty()) {
                    Text(stringResource(R.string.filter_popular_tags), style = MaterialTheme.typography.labelMedium)
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
                        label = { Text(stringResource(R.string.publish_field_custom_tag)) },
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
                        Text(stringResource(R.string.common_add))
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
            }) { Text(stringResource(R.string.publish_action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.publish_action_skip)) } },
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
