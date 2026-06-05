package cn.edu.bit.bitmart.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.data.local.ContactPrefsStore
import cn.edu.bit.bitmart.core.data.local.StoredContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 常用联系方式页状态：本地保存的联系方式列表。 */
data class ContactsUiState(val contacts: List<StoredContact> = emptyList())

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val store: ContactPrefsStore,
) : ViewModel() {

    val state: StateFlow<ContactsUiState> = store.contactsFlow
        .map { ContactsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun add(value: String) {
        if (value.isBlank()) return
        viewModelScope.launch { store.add(value.trim()) }
    }

    fun removeAt(index: Int) {
        viewModelScope.launch { store.removeAt(index) }
    }
}

