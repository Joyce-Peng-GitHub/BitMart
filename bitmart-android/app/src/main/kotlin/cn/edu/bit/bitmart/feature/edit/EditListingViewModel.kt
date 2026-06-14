package cn.edu.bit.bitmart.feature.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 编辑 listing 的 UI 状态。仅承载后端 PATCH 支持修改的字段
 * （标题/描述/价格/取货地点/有效期）；图片、标签、联系方式、类别、总数量不可在此修改。
 * @property expiresInDays 留空表示不修改原有效期；填写则按“从现在起 N 天”重置。
 */
data class EditUiState(
    val loading: Boolean = true,
    val type: ListingType = ListingType.SELL,
    val title: String = "",
    val description: String = "",
    val unitPrice: String = "",
    val pickupLocation: String = "",
    val expiresInDays: String = "",
    val saving: Boolean = false,
    val loadError: String? = null,
    val formError: String? = null,
    val saved: Boolean = false,
)

/**
 * 编辑 listing ViewModel：从详情接口预填表单，校验后经 update(id, UpdateDraft) 保存。
 * 价格留空 → 清空价格（面议）；有效期留空 → 不改动到期时间。
 */
@HiltViewModel
class EditListingViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private var listingId: Long = -1L

    fun load(id: Long) {
        listingId = id
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            when (val r = listingRepository.detail(id)) {
                is DomainResult.Success -> {
                    val d = r.data
                    _state.update {
                        it.copy(
                            loading = false,
                            type = d.type,
                            title = d.title,
                            description = d.description,
                            unitPrice = d.unitPrice ?: "",
                            pickupLocation = d.pickupLocation ?: "",
                            expiresInDays = "",
                        )
                    }
                }
                is DomainResult.Failure ->
                    if (r.httpStatus == 401) _state.update { it.copy(loading = false, loadError = "请登录后编辑") }
                    else _state.update { it.copy(loading = false, loadError = r.message) }
                is DomainResult.NetworkError -> _state.update { it.copy(loading = false, loadError = "网络异常：${r.message}") }
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v, formError = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onUnitPrice(v: String) = _state.update { it.copy(unitPrice = v, formError = null) }
    fun onPickup(v: String) = _state.update { it.copy(pickupLocation = v) }
    fun onExpiresInDays(v: String) = _state.update { it.copy(expiresInDays = v, formError = null) }

    /**
     * 消费一次性表单错误：UI 以 Toast 展示后调用，将 formError 置空，
     * 以便相同的错误能再次触发 Toast。
     */
    fun consumeFormError() = _state.update { it.copy(formError = null) }

    /** 校验并保存。成功后标记 saved（UI 监听后 popBackStack）。 */
    fun save() {
        val s = _state.value
        if (s.title.isBlank()) { _state.update { it.copy(formError = "请填写标题") }; return }
        val priceRaw = s.unitPrice.trim()
        if (priceRaw.isNotEmpty() && priceRaw.toBigDecimalOrNull() == null) {
            _state.update { it.copy(formError = "价格格式不正确") }; return
        }
        // 上界对齐服务端 NUMERIC(10,2)，避免入库时溢出（服务端亦有同等校验）。
        priceRaw.toBigDecimalOrNull()?.let { price ->
            if (price > PublishConfig.MAX_UNIT_PRICE.toBigDecimal()) {
                _state.update { it.copy(formError = "价格不能超过 ${PublishConfig.MAX_UNIT_PRICE}") }; return
            }
        }
        val expiryRaw = s.expiresInDays.trim()
        if (expiryRaw.isNotEmpty()) {
            val days = expiryRaw.toIntOrNull()
            if (days == null || days !in PublishConfig.EXPIRY_MIN_DAYS..PublishConfig.EXPIRY_MAX_DAYS) {
                _state.update { it.copy(formError = "有效期必须为 ${PublishConfig.EXPIRY_MIN_DAYS}-${PublishConfig.EXPIRY_MAX_DAYS} 的整数天数") }
                return
            }
        }

        val draft = UpdateDraft(
            title = s.title.trim(),
            description = s.description.trim(),
            unitPrice = priceRaw.ifBlank { null },
            clearUnitPrice = priceRaw.isBlank(),
            pickupLocation = s.pickupLocation.trim().ifBlank { null },
            expiresInDays = expiryRaw.toIntOrNull(),
        )
        viewModelScope.launch {
            _state.update { it.copy(saving = true, formError = null) }
            when (val r = listingRepository.update(listingId, draft)) {
                is DomainResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                is DomainResult.Failure -> _state.update { it.copy(saving = false, formError = "保存失败：${r.message}") }
                is DomainResult.NetworkError -> _state.update { it.copy(saving = false, formError = "网络异常：${r.message}") }
            }
        }
    }
}
