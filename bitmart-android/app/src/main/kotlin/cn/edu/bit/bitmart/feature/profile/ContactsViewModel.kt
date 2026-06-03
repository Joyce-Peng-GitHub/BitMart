package cn.edu.bit.bitmart.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.data.local.ContactPrefsStore
import cn.edu.bit.bitmart.core.data.local.StoredContact
import cn.edu.bit.bitmart.core.domain.model.ContactChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 常用联系方式页状态：本地保存的联系方式列表。 */
data class ContactsUiState(val contacts: List<StoredContact> = emptyList())

/**
 * 常用联系方式 ViewModel：从本地 DataStore 读取/增删联系方式（不上传后端）。
 * 渠道取值复用 ContactChannel 枚举名。
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val store: ContactPrefsStore,
) : ViewModel() {

    val state: StateFlow<ContactsUiState> = store.contactsFlow
        .map { ContactsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    /** 可选渠道列表（供 UI 下拉/选择）。 */
    val channels: List<ContactChannel> = ContactChannel.entries

    /** 新增一条联系方式（内容空白时忽略）。 */
    fun add(channel: ContactChannel, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch { store.add(StoredContact(channel.name, value.trim())) }
    }

    /** 删除指定位置的联系方式。 */
    fun removeAt(index: Int) {
        viewModelScope.launch { store.removeAt(index) }
    }
}
