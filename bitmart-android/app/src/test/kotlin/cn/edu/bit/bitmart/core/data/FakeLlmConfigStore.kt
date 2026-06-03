package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.LlmConfigStore
import cn.edu.bit.bitmart.llm.LlmConfig
import kotlinx.coroutines.flow.MutableStateFlow

/** 内存 LLM 配置存储，供 JVM 单测使用。 */
class FakeLlmConfigStore(initial: LlmConfig = LlmConfig()) : LlmConfigStore {
    private val flow = MutableStateFlow(initial)
    override val configFlow = flow

    override suspend fun save(config: LlmConfig) {
        flow.value = config
    }

    override suspend fun clear() {
        flow.value = LlmConfig()
    }
}
