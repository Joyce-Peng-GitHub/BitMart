package cn.edu.bit.bitmart.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.Notification
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.ui.UiText
import cn.edu.bit.bitmart.core.ui.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 通知页状态：公告 + 个人提醒合并流，支持游标分页与标记已读。 */
data class NotificationsUiState(
    val items: List<Notification> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
) {
    /** 是否还有下一页。 */
    val canLoadMore: Boolean get() = nextCursor != null
}

/** 每页拉取条数。 */
private const val PAGE_SIZE = 20

/**
 * 通知页 ViewModel：首屏 refresh，下滑 loadMore，点击 markRead。
 * 通知按 isAnnouncement 区分公告与个人提醒由 UI 呈现。
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** 重新拉取第一页（清空游标与既有项）。 */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = profileRepository.notifications(cursor = null, limit = PAGE_SIZE)) {
                is DomainResult.Success -> _state.update {
                    it.copy(loading = false, items = r.data.items, nextCursor = r.data.nextCursor)
                }
                is DomainResult.Error -> _state.update { it.copy(loading = false, error = r.toUiText()) }
            }
        }
    }

    /** 加载下一页（若有游标且未在加载中）。 */
    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            when (val r = profileRepository.notifications(cursor = cursor, limit = PAGE_SIZE)) {
                is DomainResult.Success -> _state.update {
                    it.copy(loadingMore = false, items = it.items + r.data.items, nextCursor = r.data.nextCursor)
                }
                is DomainResult.Error -> _state.update { it.copy(loadingMore = false, error = r.toUiText()) }
            }
        }
    }

    /** 将某条通知标记为已读：成功后本地置 read=true。 */
    fun markRead(id: Long) {
        val target = _state.value.items.find { it.id == id } ?: return
        if (target.read) return
        viewModelScope.launch {
            when (val r = profileRepository.markNotificationRead(id)) {
                is DomainResult.Success -> _state.update { s ->
                    s.copy(items = s.items.map { if (it.id == id) it.copy(read = true) else it })
                }
                is DomainResult.Error -> _state.update { it.copy(error = r.toUiText()) }
            }
        }
    }
}
