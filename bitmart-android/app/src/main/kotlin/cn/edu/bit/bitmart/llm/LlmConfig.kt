package cn.edu.bit.bitmart.llm

import kotlinx.serialization.Serializable

/**
 * LLM 直连协议。目前仅支持 OpenAI Compatible，
 * 以可选列表形式建模以便未来扩展其他协议（架构 §5.4）。
 */
enum class LlmProtocol(val displayName: String) {
    OPENAI_COMPATIBLE("OpenAI Compatible"),
}

/**
 * 客户端直连 LLM 的配置（架构 §5.4）。整份配置（含 API Key）仅保存在本地，
 * 永不上传 BitMart 服务器；后端不感知用户的 LLM 配置。
 *
 * 字段对应「LLM 设置」表单：协议、Base URL、API Key、模型名、超时阈值、
 * 书籍/一般商品识别提示词。可序列化以便 JSON 持久化于 DataStore。
 */
@Serializable
data class LlmConfig(
    val protocol: LlmProtocol = LlmProtocol.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    /** API Key（敏感）。仅存于本地，不随任何业务请求上传 BitMart 服务器。 */
    val apiKey: String = "",
    val model: String = "",
    /** 单次识别请求超时阈值（秒）。 */
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    /** 书籍识别提示词（system 角色）。 */
    val bookPrompt: String = DEFAULT_BOOK_PROMPT,
    /** 一般商品识别提示词（system 角色）。 */
    val generalPrompt: String = DEFAULT_GENERAL_PROMPT,
) {
    /** 是否已填齐可发起识别所需的关键字段。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS: Int = 60
    }
}
