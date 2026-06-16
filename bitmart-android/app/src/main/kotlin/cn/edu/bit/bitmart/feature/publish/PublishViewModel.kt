package cn.edu.bit.bitmart.feature.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.data.local.ContactPrefsStore
import cn.edu.bit.bitmart.core.data.local.LlmConfigStore
import cn.edu.bit.bitmart.core.data.local.current
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import cn.edu.bit.bitmart.llm.LlmClient
import cn.edu.bit.bitmart.llm.LlmRecognition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

/** 有效期输入方式：按天数（从今天起 N 天）或按绝对过期日期（[今天, 过期日) 内有效）。 */
enum class ExpiryMode { DAYS, DATE }

/**
 * 当前编辑的单条草稿（尚未加入批次列表）。
 * 书籍与一般商品共用部分字段；category 决定展示哪些字段。
 */
data class DraftItem(
    val category: ListingCategory,
    val title: String = "",
    val description: String = "",
    val unitPrice: String = "",
    val quantityTotal: String = "1",
    // 有效期（天）。留空 → 由服务端取默认值（30 天）。仅 expiryMode=DAYS 时使用。
    val expiresInDays: String = "",
    // 有效期输入方式与按日期模式下选定的过期日（ISO yyyy-MM-dd）。
    val expiryMode: ExpiryMode = ExpiryMode.DAYS,
    val expiresOn: String? = null,
    val pickupLocation: String = "",
    val contact: String = "",
    val tags: List<String> = emptyList(),
    // 书籍专属字段（category=BOOK 时有效）。
    val isbn: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val edition: String? = null,
    // 图片 blobKey 列表（已上传到服务端）。
    val imageKeys: List<String> = emptyList(),
    val originalPrice: String = "",
)

/**
 * 是否为"未经编辑的空白草稿"（与同类别的全默认草稿结构相等）。
 * 用于判断表单中的临时项是否值得作为一项并入列表：空白则忽略，非空白则展示/入列。
 */
internal fun DraftItem.isBlankDraft(): Boolean = this == DraftItem(category)

/** 发布页 UI 状态（多草稿批量发布模型）。 */
data class PublishUiState(
    val type: ListingType = ListingType.SELL,
    val selectedCategory: ListingCategory = ListingCategory.GENERAL,
    // 当前编辑的草稿；与 draftBatch 一起构成本地暂存清单（架构 §5.3）。
    // TODO(persist drafts): 草稿目前仅存于 ViewModel 状态，能在配置变更下存活，但应用进程被杀后丢失。
    //   后续可将 draftBatch 持久化到 DataStore/Room 以实现跨进程死亡的草稿恢复（§5.3 NICE-TO-HAVE）。
    val currentDraft: DraftItem = DraftItem(ListingCategory.GENERAL),
    val draftBatch: List<DraftItem> = emptyList(),
    /**
     * 当前表单正在编辑的暂存项下标；null 表示正在编写一条尚未加入列表的新项。
     * 非空时「加入待发布列表」改为写回该槽位（编辑既有项不再产生重复项，且不破坏列表顺序）。
     */
    val editingIndex: Int? = null,
    val popularTags: List<String> = emptyList(),
    /** 本地保存的常用联系方式，供发布页快速选择填入（来自 ContactPrefsStore）。 */
    val commonContacts: List<String> = emptyList(),
    /** 待询问"是否保存到常用联系方式"的新联系方式；非空时 UI 弹确认框。 */
    val pendingSaveContact: String? = null,
    /**
     * 识别成功后暂存的原图字节：非空时 UI 弹「是否把这张图片加入识别出的商品」确认框。
     * 用户确认则上传并附到刚识别出的 [recognizedCount] 条草稿。
     */
    val pendingRecognitionImage: ByteArray? = null,
    /** 最近一次识别新增到 draftBatch 末尾的草稿条数（供附图时定位）。 */
    val recognizedCount: Int = 0,
    val loading: Boolean = false,
    val llmRecognizing: Boolean = false,
    val uploadingImage: Boolean = false,
    val lookingUpBook: Boolean = false,
    val error: String? = null,
    val batchSubmitted: Boolean = false,
    /** 编辑模式：被编辑 listing 的 id（null = 新建发布模式）。 */
    val editingId: Long? = null,
    /** 编辑保存成功（UI 监听后 popBackStack 并刷新列表）。 */
    val saved: Boolean = false,
)

/** 一次性事件：导航到 LLM 设置页（LLM 未配置时触发）。 */
sealed interface PublishEvent {
    data object NavigateToLlmSettings : PublishEvent
    data object NavigateToBookScan : PublishEvent
}

/**
 * 发布 ViewModel（重构为多草稿批量模型，架构 §5.2/5.4/5.5）：
 * - 用户先选书籍/一般商品类型，展示对应可填字段。
 * - 可拍照/上传图片 → uploadImage（得 blobKey）；可用 LLM 识别图片 → 合并结果到当前草稿。
 * - 书籍可扫码 ISBN → lookupBook → 预填字段。
 * - 每填写完一条调 addDraftToBatch，暂存到本地列表；最后 submitBatch 一并提交。
 * - 草稿持久化（跨应用重启）是 NICE-TO-HAVE，当前仅在 ViewModel 状态中（配置变化不丢失）。
 */
@HiltViewModel
class PublishViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
    private val llmClient: LlmClient,
    private val llmConfigStore: LlmConfigStore,
    private val contactPrefsStore: ContactPrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PublishUiState())
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PublishEvent>()
    val events = _events.asSharedFlow()

    // 本会话内已就"是否保存为常用联系方式"询问过的值（无论保存或拒绝），避免重复打扰。
    private val askedContacts = mutableSetOf<String>()

    init {
        loadPopularTags()
        loadCommonContacts()
    }

    fun setType(t: ListingType) = _state.update { it.copy(type = t) }

    fun setCategory(cat: ListingCategory) {
        _state.update { st ->
            st.copy(
                selectedCategory = cat,
                currentDraft = st.currentDraft.copy(category = cat),
            )
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(title = v), error = null) }
    fun onDescription(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(description = v)) }
    fun onUnitPrice(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(unitPrice = v)) }
    fun onQuantity(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(quantityTotal = v)) }
    fun onExpiresInDays(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(expiresInDays = v), error = null) }
    fun onExpiryMode(mode: ExpiryMode) = _state.update { it.copy(currentDraft = it.currentDraft.copy(expiryMode = mode), error = null) }
    fun onExpiresOn(localDateIso: String?) = _state.update { it.copy(currentDraft = it.currentDraft.copy(expiresOn = localDateIso), error = null) }
    fun onPickup(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(pickupLocation = v)) }
    fun onContact(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(contact = v)) }

    fun onIsbn(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(isbn = v)) }
    fun onAuthor(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(author = v)) }
    fun onPublisher(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(publisher = v)) }
    fun onEdition(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(edition = v)) }
    fun onOriginalPrice(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(originalPrice = v)) }

    /**
     * 消费一次性错误提示：UI 以 Toast 展示后调用，将 error 置空，
     * 以便相同的错误（如再次提交仍为空标题）能再次触发 Toast。
     */
    fun consumeError() = _state.update { it.copy(error = null) }

    fun toggleTag(tag: String) {
        _state.update { st ->
            val current = st.currentDraft.tags
            val updated = if (tag in current) current - tag else {
                if (current.size >= PublishConfig.MAX_TAGS) current else current + tag
            }
            st.copy(currentDraft = st.currentDraft.copy(tags = updated))
        }
    }

    fun addCustomTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        _state.update { st ->
            val current = st.currentDraft.tags
            if (trimmed in current || current.size >= PublishConfig.MAX_TAGS) st
            else st.copy(currentDraft = st.currentDraft.copy(tags = current + trimmed))
        }
    }

    private fun loadPopularTags() {
        viewModelScope.launch {
            when (val r = listingRepository.popularTags(PublishConfig.POPULAR_TAGS_LIMIT)) {
                is DomainResult.Success -> _state.update { it.copy(popularTags = r.data.map { t -> t.name }) }
                else -> {} // 失败降级为空列表，不阻塞发布流程。
            }
        }
    }

    /** 载入本地常用联系方式（进入发布页时读取一次）。 */
    private fun loadCommonContacts() {
        viewModelScope.launch {
            _state.update { it.copy(commonContacts = contactPrefsStore.current()) }
        }
    }

    /** 确认将待保存联系方式加入常用联系方式（去重由存储层保证），并即时反映到可选 chips。 */
    fun savePendingContact() {
        val value = _state.value.pendingSaveContact ?: return
        askedContacts += value
        viewModelScope.launch { contactPrefsStore.add(value) }
        _state.update {
            it.copy(
                pendingSaveContact = null,
                commonContacts = if (value in it.commonContacts) it.commonContacts else it.commonContacts + value,
            )
        }
    }

    /** 放弃保存：记下已询问，避免同一会话内重复弹窗。 */
    fun dismissPendingContact() {
        _state.value.pendingSaveContact?.let { askedContacts += it }
        _state.update { it.copy(pendingSaveContact = null) }
    }

    /**
     * 上传图片（从 URI 读取字节后调用此方法）。
     * 成功后将 blobKey 加入当前草稿的 imageKeys；超过 [PublishConfig.MAX_IMAGES] 时拒绝。
     */
    fun uploadImage(bytes: ByteArray, filename: String) {
        if (_state.value.currentDraft.imageKeys.size >= PublishConfig.MAX_IMAGES) {
            _state.update { it.copy(error = "最多上传${PublishConfig.MAX_IMAGES}张图片") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploadingImage = true, error = null) }
            when (val r = listingRepository.uploadImage(bytes, filename)) {
                is DomainResult.Success -> _state.update { st ->
                    // 再查一次上限：选图与拍照识别可能并发上传，两者都通过入口校验后仍可能越限。
                    val keys = st.currentDraft.imageKeys
                    if (keys.size >= PublishConfig.MAX_IMAGES) {
                        st.copy(uploadingImage = false, error = "最多上传${PublishConfig.MAX_IMAGES}张图片")
                    } else {
                        st.copy(uploadingImage = false, currentDraft = st.currentDraft.copy(imageKeys = keys + r.data))
                    }
                }
                is DomainResult.Failure -> _state.update { it.copy(uploadingImage = false, error = "上传失败：${r.message}") }
                is DomainResult.InvalidResponse -> _state.update { it.copy(uploadingImage = false, error = "上传失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(uploadingImage = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 删除当前草稿中的某张已上传图片（按下标）。 */
    fun removeImage(index: Int) {
        _state.update { st ->
            st.copy(currentDraft = st.currentDraft.copy(imageKeys = st.currentDraft.imageKeys.filterIndexed { i, _ -> i != index }))
        }
    }

    /**
     * LLM 批量识别图片。未配置 → 发射导航事件；已配置 → 识别图中所有项，每项各成一条草稿
     * 追加到待发布列表（暂存区），**不**改写当前表单。识别后置 pendingRecognitionImage 询问是否附图。
     * @param imageBytes 图片字节（JPEG/PNG）。
     */
    fun recognizeWithLlm(imageBytes: ByteArray) {
        viewModelScope.launch {
            val config = llmConfigStore.current()
            if (!config.isConfigured) {
                _events.emit(PublishEvent.NavigateToLlmSettings)
                return@launch
            }

            _state.update { it.copy(llmRecognizing = true, error = null) }
            val category = _state.value.selectedCategory
            when (val r = llmClient.recognize(config, imageBytes, category)) {
                is DomainResult.Success -> {
                    val newDrafts = r.data.map { it.toDraftItem() }
                    if (newDrafts.isEmpty()) {
                        _state.update { it.copy(llmRecognizing = false, error = "未识别到可发布的商品") }
                    } else {
                        _state.update { st ->
                            st.copy(
                                llmRecognizing = false,
                                draftBatch = st.draftBatch + newDrafts,
                                pendingRecognitionImage = imageBytes,
                                recognizedCount = newDrafts.size,
                            )
                        }
                    }
                }
                is DomainResult.Failure -> _state.update { it.copy(llmRecognizing = false, error = "识别失败：${r.message}") }
                is DomainResult.InvalidResponse -> _state.update { it.copy(llmRecognizing = false, error = "识别失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(llmRecognizing = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 确认把识别用的图片加入识别出的商品：上传后将 blobKey 附到末尾 recognizedCount 条草稿。 */
    fun confirmAttachRecognitionImage() {
        val bytes = _state.value.pendingRecognitionImage ?: return
        val count = _state.value.recognizedCount
        _state.update { it.copy(pendingRecognitionImage = null) }
        if (count <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(uploadingImage = true, error = null) }
            when (val r = listingRepository.uploadImage(bytes, "image.jpg")) {
                is DomainResult.Success -> _state.update { st ->
                    val from = (st.draftBatch.size - count).coerceAtLeast(0)
                    val updated = st.draftBatch.mapIndexed { i, d ->
                        if (i >= from && d.imageKeys.size < PublishConfig.MAX_IMAGES) d.copy(imageKeys = d.imageKeys + r.data) else d
                    }
                    st.copy(uploadingImage = false, draftBatch = updated)
                }
                is DomainResult.Failure -> _state.update { it.copy(uploadingImage = false, error = "上传失败：${r.message}") }
                is DomainResult.InvalidResponse -> _state.update { it.copy(uploadingImage = false, error = "上传失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(uploadingImage = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 放弃把识别用的图片加入商品：仅清空待确认状态。 */
    fun dismissRecognitionImage() {
        _state.update { it.copy(pendingRecognitionImage = null) }
    }

    /** 把单条识别结果映射为一条草稿（不含图片）。售价不由 LLM 产生。 */
    private fun LlmRecognition.toDraftItem(): DraftItem = when (this) {
        is LlmRecognition.Book -> DraftItem(
            category = ListingCategory.BOOK,
            title = title,
            isbn = isbn,
            author = author.ifBlank { null },
            publisher = publisher.ifBlank { null },
            edition = edition.ifBlank { null },
            originalPrice = originalPrice ?: "",
        )
        is LlmRecognition.General -> DraftItem(
            category = ListingCategory.GENERAL,
            title = title,
            description = description,
            originalPrice = originalPrice ?: "",
            tags = tags.distinct().take(PublishConfig.MAX_TAGS),
        )
    }

    /**
     * 扫码得到 ISBN 后调用，查询书籍元数据并预填当前草稿（仅书籍类别）。
     * 404 → 保持原值供用户手动填写。
     */
    fun lookupBook(isbn: String) {
        if (_state.value.selectedCategory != ListingCategory.BOOK) return
        viewModelScope.launch {
            _state.update { it.copy(lookingUpBook = true, error = null) }
            when (val r = listingRepository.lookupBook(isbn)) {
                is DomainResult.Success -> {
                    val book = r.data
                    _state.update { st ->
                        st.copy(
                            lookingUpBook = false,
                            currentDraft = if (book != null) st.currentDraft.copy(
                                isbn = book.isbn,
                                title = book.title ?: "",
                                author = book.authors,
                                publisher = book.publisher,
                                edition = book.edition,
                                originalPrice = book.price ?: "",
                            ) else st.currentDraft.copy(isbn = isbn),
                        )
                    }
                }
                is DomainResult.Failure -> _state.update { it.copy(lookingUpBook = false, error = "查询失败：${r.message}") }
                is DomainResult.InvalidResponse -> _state.update { it.copy(lookingUpBook = false, error = "查询失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(lookingUpBook = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 打开扫码页（发射导航事件）。 */
    fun openBookScan() {
        viewModelScope.launch { _events.emit(PublishEvent.NavigateToBookScan) }
    }

    /**
     * 把当前草稿固化到待发布列表（校验通过后）并重置编辑器。
     * - 新建项（editingIndex==null）：追加到列表末尾。
     * - 编辑既有项（editingIndex!=null）：写回原槽位（不产生重复、不打乱顺序）。
     */
    fun addDraftToBatch() {
        val draft = _state.value.currentDraft
        val err = validateDraft(draft)
        if (err != null) { _state.update { it.copy(error = err) }; return }

        val contact = draft.contact.trim()
        _state.update { st ->
            val idx = st.editingIndex
            val newBatch =
                if (idx != null && idx in st.draftBatch.indices)
                    st.draftBatch.toMutableList().also { it[idx] = draft }
                else st.draftBatch + draft
            st.copy(
                draftBatch = newBatch,
                currentDraft = DraftItem(st.selectedCategory), // 重置编辑器，类型保持。
                editingIndex = null,
                error = null,
                // 输入了尚未保存且本会话未询问过的新联系方式 → 提示是否加入常用联系方式。
                pendingSaveContact =
                    if (contact.isNotEmpty() && contact !in st.commonContacts && contact !in askedContacts) contact
                    else null,
            )
        }
    }

    /** 新建一条草稿：先把当前项并入列表（保留临时项），再清空表单进入新建态。 */
    fun newDraft() {
        _state.update { st ->
            val parked = parkCurrent(st)
            parked.copy(currentDraft = DraftItem(parked.selectedCategory), editingIndex = null, error = null)
        }
    }

    /** 丢弃当前正在编写、尚未并入列表的临时项（清空表单，不写回也不追加）。 */
    fun discardDraft() {
        _state.update { it.copy(currentDraft = DraftItem(it.selectedCategory), editingIndex = null, error = null) }
    }

    /**
     * 把表单中的当前项固化进列表，使"尚未保存的临时项"与暂存项一视同仁：
     * - 编辑既有项（editingIndex 有效）→ 写回该槽位（不改变长度）。
     * - 编写新项且非空白 → 追加到末尾，并令 editingIndex 指向它（避免展示层重复计数）。
     * - 空白新项 → 原样返回。
     * 不做字段校验（草稿允许暂不完整，提交时再整体校验）。
     */
    private fun parkCurrent(st: PublishUiState): PublishUiState {
        val idx = st.editingIndex
        return when {
            idx != null && idx in st.draftBatch.indices ->
                st.copy(draftBatch = st.draftBatch.toMutableList().also { it[idx] = st.currentDraft })
            idx == null && !st.currentDraft.isBlankDraft() -> {
                val newBatch = st.draftBatch + st.currentDraft
                st.copy(draftBatch = newBatch, editingIndex = newBatch.lastIndex)
            }
            else -> st
        }
    }

    /** 从批次移除某草稿，并相应修正正在编辑的下标。 */
    fun removeDraft(index: Int) {
        _state.update { st ->
            if (index !in st.draftBatch.indices) return@update st
            val newBatch = st.draftBatch.filterIndexed { i, _ -> i != index }
            val editing = st.editingIndex
            st.copy(
                draftBatch = newBatch,
                // 删除的正是在编辑项 → 退回新建态并清空表单；删除其之前的项 → 下标左移。
                editingIndex = when {
                    editing == null -> null
                    editing == index -> null
                    editing > index -> editing - 1
                    else -> editing
                },
                currentDraft = if (editing == index) DraftItem(st.selectedCategory) else st.currentDraft,
            )
        }
    }

    /** 选中某暂存项进入表单编辑（非破坏）：先把当前项并入列表，再载入目标项；项始终保留在列表中。 */
    fun editDraft(index: Int) {
        _state.update { st ->
            val parked = parkCurrent(st)
            if (index !in parked.draftBatch.indices) return@update parked
            val item = parked.draftBatch[index]
            parked.copy(
                currentDraft = item,
                selectedCategory = item.category, // 同步类别，使顶部 chips 与字段布局一致。
                editingIndex = index,
                error = null,
            )
        }
    }

    /**
     * 批量提交：将 draftBatch 转为 PublishDraft 列表并调用 publishBatch。
     * 成功后标记 batchSubmitted（UI 监听后 popBackStack）。
     */
    fun submitBatch() {
        // 先把表单中的临时项并入列表，使"未保存的当前项"也被一并提交。
        _state.update { parkCurrent(it) }
        val st = _state.value
        if (st.draftBatch.isEmpty()) {
            _state.update { it.copy(error = "请至少添加一项到待发布列表") }
            return
        }

        // 每条都需通过客户端校验：识别入暂存区的草稿可能缺联系方式等，用户须逐条补全后再提交。
        st.draftBatch.forEachIndexed { i, d ->
            val err = validateDraft(d)
            if (err != null) {
                _state.update { it.copy(error = "第 ${i + 1} 项：$err") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val drafts = st.draftBatch.map { it.toPublishDraft(st.type) }
            when (val r = listingRepository.publishBatch(drafts)) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, batchSubmitted = true) }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.InvalidResponse -> _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /**
     * 单条草稿的客户端校验（发布加入批次与编辑保存共用）。返回错误消息，null 表示通过。
     */
    private fun validateDraft(draft: DraftItem): String? {
        if (draft.title.isBlank()) return "请填写标题"
        if (draft.contact.isBlank()) return "请填写联系方式"
        // 件数：用 Long 解析以便把超 Int 的"过大整数"正确归类为超上界。
        val qty = draft.quantityTotal.toLongOrNull()
        if (qty == null || qty < 1) return "件数必须为正整数"
        if (qty > PublishConfig.MAX_QUANTITY) return "件数不能超过 ${PublishConfig.MAX_QUANTITY}"
        val priceRaw = draft.unitPrice.trim()
        if (priceRaw.isNotEmpty()) {
            val price = priceRaw.toBigDecimalOrNull() ?: return "价格格式不正确"
            if (price > PublishConfig.MAX_UNIT_PRICE.toBigDecimal()) return "价格不能超过 ${PublishConfig.MAX_UNIT_PRICE}"
        }
        when (draft.expiryMode) {
            ExpiryMode.DAYS -> {
                val expiryRaw = draft.expiresInDays.trim()
                if (expiryRaw.isNotEmpty()) {
                    val days = expiryRaw.toIntOrNull()
                    if (days == null || days !in PublishConfig.EXPIRY_MIN_DAYS..PublishConfig.EXPIRY_MAX_DAYS)
                        return "有效期必须为 ${PublishConfig.EXPIRY_MIN_DAYS}-${PublishConfig.EXPIRY_MAX_DAYS} 的整数天数"
                }
            }
            ExpiryMode.DATE -> {
                val date = draft.expiresOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "请选择过期日期"
                val today = LocalDate.now()
                if (!date.isAfter(today) || date.isAfter(today.plusDays(PublishConfig.EXPIRY_MAX_DAYS.toLong())))
                    return "过期日期须晚于今天且不超过 ${PublishConfig.EXPIRY_MAX_DAYS} 天后"
            }
        }
        return null
    }

    /** 进入编辑模式：拉取详情并把字段预填到 currentDraft（与发布共用同一表单）。 */
    fun loadForEdit(id: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = listingRepository.detail(id)) {
                is DomainResult.Success -> {
                    val d = r.data
                    _state.update {
                        it.copy(
                            loading = false,
                            editingId = id,
                            type = d.type,
                            selectedCategory = d.category,
                            currentDraft = d.toDraftItem(),
                        )
                    }
                }
                is DomainResult.Failure ->
                    _state.update { it.copy(loading = false, error = if (r.httpStatus == 401) "请登录后编辑" else r.message) }
                is DomainResult.InvalidResponse -> _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 编辑保存：校验后整体更新该 listing；成功标记 saved（UI 监听后 popBackStack）。 */
    fun saveEdit() {
        val id = _state.value.editingId ?: return
        val draft = _state.value.currentDraft
        val err = validateDraft(draft)
        if (err != null) { _state.update { it.copy(error = err) }; return }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = listingRepository.update(id, draft.toUpdateDraft())) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, saved = true) }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, error = "保存失败：${r.message}") }
                is DomainResult.InvalidResponse -> _state.update { it.copy(loading = false, error = "保存失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 详情 → 草稿（编辑预填）。过期以"日期模式"预填为当前过期日；图片由 URL 还原 blobKey。 */
    private fun ListingDetail.toDraftItem() = DraftItem(
        category = category,
        title = title,
        description = description,
        unitPrice = unitPrice ?: "",
        quantityTotal = quantityTotal.toString(),
        expiresInDays = "",
        expiryMode = ExpiryMode.DATE,
        expiresOn = runCatching {
            OffsetDateTime.parse(expiresAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString()
        }.getOrNull(),
        pickupLocation = pickupLocation ?: "",
        contact = contacts.firstOrNull()?.value ?: "",
        tags = tags,
        isbn = book?.isbn,
        author = book?.authors,
        publisher = book?.publisher,
        edition = book?.edition,
        imageKeys = imageUrls.map { it.removePrefix("/static/") },
        originalPrice = originalPrice ?: "",
    )

    /** 草稿 → 全字段更新（编辑保存）。编辑为整体替换，故标量字段均下发（空串清除）。 */
    private fun DraftItem.toUpdateDraft(): UpdateDraft {
        val priceRaw = unitPrice.trim()
        val absoluteExpiry = absoluteExpiryIso()
        return UpdateDraft(
            title = title.trim(),
            description = description.trim(),
            unitPrice = priceRaw.ifBlank { null },
            clearUnitPrice = priceRaw.isBlank(),
            pickupLocation = pickupLocation.trim(),
            expiresInDays = if (absoluteExpiry != null) null else expiresInDays.trim().toIntOrNull(),
            expiresAtIso = absoluteExpiry,
            category = category,
            quantityTotal = quantityTotal.toInt(),
            contacts = listOf(Contact("", contact.trim())),
            tags = tags,
            imageKeys = imageKeys,
            book = if (category == ListingCategory.BOOK) BookInfo(
                isbn = isbn?.trim()?.ifBlank { null },
                title = title.trim().ifBlank { null },
                authors = author?.trim()?.ifBlank { null },
                publisher = publisher?.trim()?.ifBlank { null },
                edition = edition?.trim()?.ifBlank { null },
            ) else null,
            originalPrice = originalPrice.trim().ifBlank { null },
        )
    }

    /** 按过期日期模式换算的绝对过期瞬时（ISO）；天数模式返回 null。发布与编辑共用。 */
    private fun DraftItem.absoluteExpiryIso(): String? =
        if (expiryMode == ExpiryMode.DATE && expiresOn != null) {
            runCatching {
                LocalDate.parse(expiresOn).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString()
            }.getOrNull()
        } else null

    private fun DraftItem.toPublishDraft(type: ListingType): PublishDraft {
        // 按过期日期发布时，换算为该日 00:00（设备时区）的绝对瞬时，优先于天数。
        val absoluteExpiry = absoluteExpiryIso()
        return PublishDraft(
            type = type,
            category = category,
            title = title.trim(),
            description = description.trim(),
            unitPrice = unitPrice.trim().ifBlank { null },
            quantityTotal = quantityTotal.toInt(),
            expiresInDays = if (absoluteExpiry != null) null else expiresInDays.trim().toIntOrNull(),
            expiresAtIso = absoluteExpiry,
            pickupLocation = pickupLocation.trim().ifBlank { null },
            contacts = listOf(Contact("", contact.trim())),
            tags = tags,
            book = if (category == ListingCategory.BOOK) BookInfo(
                isbn = isbn?.trim()?.ifBlank { null },
                title = title.trim().ifBlank { null },
                authors = author?.trim()?.ifBlank { null },
                publisher = publisher?.trim()?.ifBlank { null },
                edition = edition?.trim()?.ifBlank { null },
            ) else null,
            imageKeys = imageKeys,
            originalPrice = originalPrice.trim().ifBlank { null },
        )
    }
}
