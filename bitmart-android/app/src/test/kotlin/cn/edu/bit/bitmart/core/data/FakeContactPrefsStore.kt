package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.ContactPrefsStore
import cn.edu.bit.bitmart.core.data.local.StoredContact
import kotlinx.coroutines.flow.MutableStateFlow

/** 内存常用联系方式存储，供 JVM 单测使用。 */
class FakeContactPrefsStore(initial: List<StoredContact> = emptyList()) : ContactPrefsStore {
    private val flow = MutableStateFlow(initial)
    override val contactsFlow = flow

    override suspend fun add(contact: StoredContact) {
        if (flow.value.none { it.channel == contact.channel && it.value == contact.value }) {
            flow.value = flow.value + contact
        }
    }

    override suspend fun removeAt(index: Int) {
        if (index in flow.value.indices) {
            flow.value = flow.value.filterIndexed { i, _ -> i != index }
        }
    }
}
