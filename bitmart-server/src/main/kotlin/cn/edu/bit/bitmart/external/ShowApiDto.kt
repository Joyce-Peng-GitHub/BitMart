package cn.edu.bit.bitmart.external

/** ShowAPI（万维易源 1626）ISBN 查询结果（领域层视图）。 */
sealed interface IsbnLookupResult {
    /** 查询成功，返回书籍元数据与原始 JSON（供服务端缓存）。 */
    data class Found(val meta: BookMeta, val rawJson: String) : IsbnLookupResult

    /** 上游明确返回"未找到该 ISBN"。 */
    data object NotFound : IsbnLookupResult

    /** 上游服务异常（网络、超时、配额、响应格式异常等）。 */
    data class ServiceError(val message: String) : IsbnLookupResult
}

/**
 * 书籍元数据。字段对应 ShowAPI data 对象（见 demo IsbnQueryDemo、docs/ISBN-Query.md）。
 * 除 isbn 外均可空，因不同书籍返回字段不全。
 */
data class BookMeta(
    val isbn: String,
    val title: String?,
    val author: String?,
    val publisher: String?,
    val pubdate: String?,
    val edition: String?,
    val price: String?,
    val page: String?,
    val binding: String?,
    val format: String?,
    val img: String?,
    val summary: String?,
)
