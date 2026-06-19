package cn.edu.bit.bitmart.llm

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory

/**
 * LLM 识图客户端：客户端直连用户配置的 OpenAI-Compatible 服务（架构 §5.4）。
 * 后端不参与，API Key 永不离开设备。
 */
interface LlmClient {
    /**
     * 对一张图片发起识别。一张图可能含多本书/多件商品（一图多项）。
     * @param config 用户在「LLM 设置」中保存的配置（含 base URL/key/model/超时/提示词）。
     * @param imageBytes 原始图片字节（JPEG/PNG 等），内部编码为 base64 data URL。
     * @param category 品类：BOOK / GENERAL，决定使用的提示词与响应 schema。
     * @param languageTag 应用语言标签（如 "zh"/"en"）：决定默认提示词、user 文案与响应 schema 的语言。
     *   以 "zh" 开头选中文，否则选英文（与应用「非 zh → 英文」规则一致）。默认 "zh" 以保持旧调用点行为不变。
     * @return 解析后的识别结果列表（每项各成一条草稿）；HTTP/解析失败映射为 Failure，网络异常映射为 NetworkError。
     */
    suspend fun recognize(
        config: LlmConfig,
        imageBytes: ByteArray,
        category: ListingCategory,
        languageTag: String = "zh",
    ): DomainResult<List<LlmRecognition>>
}
