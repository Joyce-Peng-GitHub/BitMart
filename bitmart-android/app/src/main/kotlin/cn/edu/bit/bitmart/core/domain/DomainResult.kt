package cn.edu.bit.bitmart.core.domain

/**
 * 领域层操作结果。区分成功与三类失败，使 UI 能据此给出不同提示，而不暴露底层 HTTP 细节：
 * - [Failure]：服务器以非 2xx 响应（HTTP/业务错误）。
 * - [InvalidResponse]：已连通且服务器以 2xx 响应，但响应体无法按预期解析。
 * - [NetworkError]：无法与服务器通信（连接失败、超时、IO 异常等）。
 *
 * 三类失败共享 [Error] 父类型并暴露 [Error.message]，需要统一展示错误时可只匹配 `is Error`。
 */
sealed interface DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>

    /** 任意失败的公共父类型：UI 可统一取 [message] 展示，无需关心具体子类。 */
    sealed interface Error : DomainResult<Nothing> {
        val message: String
    }

    /**
     * 业务/HTTP 错误：服务器以非 2xx 响应。
     * [code] 为后端 error.code（无结构化错误信封时为 `HTTP_<status>` 兜底），[httpStatus] 为 HTTP 状态码。
     */
    data class Failure(val code: String, override val message: String, val httpStatus: Int) : Error

    /**
     * 响应内容无效：服务器以 2xx 响应，但响应体无法按预期解析（结构缺失/坏数据/非预期 JSON）。
     * 区别于 [Failure]（HTTP 层有错误状态）与 [NetworkError]（根本未连通）。
     */
    data class InvalidResponse(override val message: String, val cause: Throwable? = null) : Error

    /** 网络/IO 异常（无法连通服务器等）。 */
    data class NetworkError(override val message: String, val cause: Throwable? = null) : Error
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(data))
    is DomainResult.Error -> this
}

/** HTTP 状态码（仅 [DomainResult.Failure] 有），其余失败/成功为 null。集中"以状态码判定"的判断点。 */
val DomainResult<*>.httpStatusOrNull: Int?
    get() = (this as? DomainResult.Failure)?.httpStatus

/** 是否为未授权（401）错误。 */
fun DomainResult<*>.isUnauthorized(): Boolean = httpStatusOrNull == 401
