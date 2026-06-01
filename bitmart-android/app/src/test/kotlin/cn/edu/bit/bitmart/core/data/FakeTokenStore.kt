package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** 内存令牌存储，供 JVM 单测使用。 */
class FakeTokenStore(initial: String? = null) : TokenStore {
    private val flow = MutableStateFlow(initial)
    override val tokenFlow = flow
    override suspend fun current(): String? = flow.first()
    override suspend fun save(token: String) { flow.value = token }
    override suspend fun clear() { flow.value = null }
}
