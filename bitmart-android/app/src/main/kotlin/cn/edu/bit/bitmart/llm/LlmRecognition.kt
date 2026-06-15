package cn.edu.bit.bitmart.llm

import kotlinx.serialization.Serializable

/**
 * LLM 识图的单项解析结果。按品类分两种形态。一次识别可产出多项（一图多项），
 * 客户端返回 `List<LlmRecognition>`，发布流程把每项各成一条草稿追加到暂存区。
 */
sealed interface LlmRecognition {

    /** 书籍识别结果，对应批量扫描书籍的编辑界面字段。 */
    data class Book(
        val title: String,
        val author: String,
        val publisher: String,
        val edition: String,
        /** ISBN，可空（提示词允许为空字符串，解析时归一化）。 */
        val isbn: String?,
        /** 图中可见的标价/吊牌原价（人民币元，文本），看不到价格时为 null。 */
        val originalPrice: String?,
    ) : LlmRecognition

    /** 一般商品识别结果。 */
    data class General(
        val title: String,
        val description: String,
        /** 图中可见的商品标价/吊牌原价（人民币元，文本），看不到价格时为 null。售价不由 LLM 产生。 */
        val originalPrice: String?,
        val tags: List<String>,
    ) : LlmRecognition
}

/**
 * 书籍识别 JSON 载荷（与 system 提示词约束的结构一致）。
 * 可空字段一律给默认值：服务端 explicitNulls=false 可能省略字段，
 * 缺省值避免反序列化抛 MissingFieldException（见项目约定）。
 */
@Serializable
internal data class BookPayload(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val edition: String = "",
    val isbn: String = "",
    val originalPrice: String = "",
)

/** 一般商品识别 JSON 载荷。 */
@Serializable
internal data class GeneralPayload(
    val title: String = "",
    val description: String = "",
    val originalPrice: String = "",
    val tags: List<String> = emptyList(),
)
