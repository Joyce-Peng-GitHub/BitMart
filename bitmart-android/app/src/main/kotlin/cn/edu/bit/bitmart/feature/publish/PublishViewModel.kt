package cn.edu.bit.bitmart.feature.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ContactChannel
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 发布页 UI 状态。买/卖共用，type 决定价格语义。 */
data class PublishUiState(
    val type: ListingType = ListingType.SELL,
    val title: String = "",
    val description: String = "",
    val unitPrice: String = "",
    val quantityTotal: String = "1",
    val pickupLocation: String = "",
    val contactChannel: ContactChannel = ContactChannel.WECHAT,
    val contactValue: String = "",
    val tags: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val publishedId: Long? = null,
)

/** 发布 ViewModel：卖品/求购共用一套表单与提交逻辑（架构 §5.2）。 */
@HiltViewModel
class PublishViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PublishUiState())
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    fun setType(t: ListingType) = _state.update { it.copy(type = t) }
    fun onTitle(v: String) = _state.update { it.copy(title = v, error = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onUnitPrice(v: String) = _state.update { it.copy(unitPrice = v) }
    fun onQuantity(v: String) = _state.update { it.copy(quantityTotal = v) }
    fun onPickup(v: String) = _state.update { it.copy(pickupLocation = v) }
    fun onContactChannel(c: ContactChannel) = _state.update { it.copy(contactChannel = c) }
    fun onContactValue(v: String) = _state.update { it.copy(contactValue = v) }
    fun onTags(v: String) = _state.update { it.copy(tags = v) }

    fun submit() {
        val s = _state.value
        // 客户端基础校验，详细规则以服务端为准。
        if (s.title.isBlank()) { _state.update { it.copy(error = "请填写标题") }; return }
        if (s.contactValue.isBlank()) { _state.update { it.copy(error = "请填写联系方式") }; return }
        val qty = s.quantityTotal.toIntOrNull()
        if (qty == null || qty < 1) { _state.update { it.copy(error = "件数必须为正整数") }; return }

        val draft = PublishDraft(
            type = s.type,
            title = s.title.trim(),
            description = s.description.trim(),
            unitPrice = s.unitPrice.trim().ifBlank { null },
            quantityTotal = qty,
            pickupLocation = s.pickupLocation.trim().ifBlank { null },
            contacts = listOf(Contact(s.contactChannel, s.contactValue.trim())),
            tags = s.tags.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() },
        )
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = listingRepository.publish(draft)) {
                is DomainResult.Success -> _state.update { it.copy(loading = false, publishedId = r.data) }
                is DomainResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, error = "网络异常：${r.message}") }
            }
        }
    }
}
