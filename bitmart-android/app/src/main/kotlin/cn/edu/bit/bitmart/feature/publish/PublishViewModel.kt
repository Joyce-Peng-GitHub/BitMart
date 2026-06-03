package cn.edu.bit.bitmart.feature.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.data.local.LlmConfigStore
import cn.edu.bit.bitmart.core.data.local.current
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ContactChannel
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
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
import javax.inject.Inject

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
    val pickupLocation: String = "",
    val contactChannel: ContactChannel = ContactChannel.WECHAT,
    val contactValue: String = "",
    val tags: List<String> = emptyList(),
    // 书籍专属字段（category=BOOK 时有效）。
    val isbn: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val edition: String? = null,
    // 图片 blobKey 列表（已上传到服务端）。
    val imageKeys: List<String> = emptyList(),
)

/** 发布页 UI 状态（多草稿批量发布模型）。 */
data class PublishUiState(
    val type: ListingType = ListingType.SELL,
    val selectedCategory: ListingCategory = ListingCategory.GENERAL,
    // 当前编辑的草稿；与 draftBatch 一起构成本地暂存清单（架构 §5.3）。
    // TODO(persist drafts): 草稿目前仅存于 ViewModel 状态，能在配置变更下存活，但应用进程被杀后丢失。
    //   后续可将 draftBatch 持久化到 DataStore/Room 以实现跨进程死亡的草稿恢复（§5.3 NICE-TO-HAVE）。
    val currentDraft: DraftItem = DraftItem(ListingCategory.GENERAL),
    val draftBatch: List<DraftItem> = emptyList(),
    val popularTags: List<String> = emptyList(),
    val loading: Boolean = false,
    val llmRecognizing: Boolean = false,
    val uploadingImage: Boolean = false,
    val lookingUpBook: Boolean = false,
    val error: String? = null,
    val batchSubmitted: Boolean = false,
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
) : ViewModel() {

    private val _state = MutableStateFlow(PublishUiState())
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PublishEvent>()
    val events = _events.asSharedFlow()

    init {
        loadPopularTags()
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
    fun onPickup(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(pickupLocation = v)) }
    fun onContactChannel(c: ContactChannel) = _state.update { it.copy(currentDraft = it.currentDraft.copy(contactChannel = c)) }
    fun onContactValue(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(contactValue = v)) }

    fun onIsbn(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(isbn = v)) }
    fun onAuthor(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(author = v)) }
    fun onPublisher(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(publisher = v)) }
    fun onEdition(v: String) = _state.update { it.copy(currentDraft = it.currentDraft.copy(edition = v)) }

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
            when (val r = listingRepository.popularTags(20)) {
                is DomainResult.Success -> _state.update { it.copy(popularTags = r.data) }
                else -> {} // 失败降级为空列表，不阻塞发布流程。
            }
        }
    }

    /**
     * 上传图片（从 URI 读取字节后调用此方法）。
     * 成功后将 blobKey 加入当前草稿的 imageKeys。
     */
    fun uploadImage(bytes: ByteArray, filename: String) {
        viewModelScope.launch {
            _state.update { it.copy(uploadingImage = true, error = null) }
            when (val r = listingRepository.uploadImage(bytes, filename)) {
                is DomainResult.Success -> _state.update { st ->
                    st.copy(
                        uploadingImage = false,
                        currentDraft = st.currentDraft.copy(imageKeys = st.currentDraft.imageKeys + r.data),
                    )
                }
                is DomainResult.Failure -> _state.update { it.copy(uploadingImage = false, error = "上传失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(uploadingImage = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /**
     * LLM 识别图片。未配置 → 发射导航事件；已配置 → 调用识别并合并结果到当前草稿。
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
                    _state.update { st -> st.copy(llmRecognizing = false, currentDraft = mergeLlmRecognition(st.currentDraft, r.data)) }
                }
                is DomainResult.Failure -> _state.update { it.copy(llmRecognizing = false, error = "识别失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(llmRecognizing = false, error = "网络异常：${r.message}") }
            }
        }
    }

    private fun mergeLlmRecognition(draft: DraftItem, recognition: LlmRecognition): DraftItem = when (recognition) {
        is LlmRecognition.Book -> draft.copy(
            title = recognition.title.ifBlank { draft.title },
            author = recognition.author.ifBlank { draft.author },
            publisher = recognition.publisher.ifBlank { draft.publisher },
            edition = recognition.edition.ifBlank { draft.edition },
            isbn = recognition.isbn ?: draft.isbn,
        )
        is LlmRecognition.General -> draft.copy(
            title = recognition.title.ifBlank { draft.title },
            description = recognition.description.ifBlank { draft.description },
            unitPrice = recognition.suggestedPrice?.ifBlank { null } ?: draft.unitPrice,
            tags = (draft.tags + recognition.tags).distinct().take(PublishConfig.MAX_TAGS),
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
                            ) else st.currentDraft.copy(isbn = isbn),
                        )
                    }
                }
                is DomainResult.Failure -> _state.update { it.copy(lookingUpBook = false, error = "查询失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(lookingUpBook = false, error = "网络异常：${r.message}") }
            }
        }
    }

    /** 打开扫码页（发射导航事件）。 */
    fun openBookScan() {
        viewModelScope.launch { _events.emit(PublishEvent.NavigateToBookScan) }
    }

    /**
     * 将当前草稿加入批次（校验通过后）并重置编辑器。
     * 可编辑已加入的草稿：点击列表项 → 从 batch 移除并载入到 currentDraft。
     */
    fun addDraftToBatch() {
        val draft = _state.value.currentDraft
        // 客户端基础校验。
        if (draft.title.isBlank()) { _state.update { it.copy(error = "请填写标题") }; return }
        if (draft.contactValue.isBlank()) { _state.update { it.copy(error = "请填写联系方式") }; return }
        val qty = draft.quantityTotal.toIntOrNull()
        if (qty == null || qty < 1) { _state.update { it.copy(error = "件数必须为正整数") }; return }

        _state.update { st ->
            st.copy(
                draftBatch = st.draftBatch + draft,
                currentDraft = DraftItem(st.selectedCategory), // 重置编辑器，类型保持。
                error = null,
            )
        }
    }

    /** 从批次移除某草稿。 */
    fun removeDraft(index: Int) {
        _state.update { st -> st.copy(draftBatch = st.draftBatch.filterIndexed { i, _ -> i != index }) }
    }

    /** 编辑某草稿：移除并载入到编辑器。 */
    fun editDraft(index: Int) {
        _state.update { st ->
            st.copy(
                currentDraft = st.draftBatch[index],
                draftBatch = st.draftBatch.filterIndexed { i, _ -> i != index },
            )
        }
    }

    /**
     * 批量提交：将 draftBatch 转为 PublishDraft 列表并调用 publishBatch。
     * 成功后标记 batchSubmitted（UI 监听后 popBackStack）。
     */
    fun submitBatch() {
        val st = _state.value
        if (st.draftBatch.isEmpty()) {
            _state.update { it.copy(error = "请至少添加一项到待发布列表") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val drafts = st.draftBatch.map { it.toPublishDraft(st.type) }
            when (val r = listingRepository.publishBatch(drafts)) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, batchSubmitted = true) }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }

    private fun DraftItem.toPublishDraft(type: ListingType) = PublishDraft(
        type = type,
        category = category,
        title = title.trim(),
        description = description.trim(),
        unitPrice = unitPrice.trim().ifBlank { null },
        quantityTotal = quantityTotal.toInt(),
        pickupLocation = pickupLocation.trim().ifBlank { null },
        contacts = listOf(Contact(contactChannel, contactValue.trim())),
        tags = tags,
        book = if (category == ListingCategory.BOOK) BookInfo(
            isbn = isbn?.trim()?.ifBlank { null },
            title = title.trim().ifBlank { null },
            authors = author?.trim()?.ifBlank { null },
            publisher = publisher?.trim()?.ifBlank { null },
            edition = edition?.trim()?.ifBlank { null },
        ) else null,
        imageKeys = imageKeys,
    )
}
