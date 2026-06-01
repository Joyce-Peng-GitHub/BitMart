package cn.edu.bit.bitmart.core.domain

/**
 * 领域层操作结果。区分成功、领域错误（带错误码/消息）与网络异常，
 * 使 UI 能据此展示不同提示，而不暴露底层 HTTP 细节。
 */
sealed interface DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>

    /** 业务/HTTP 错误：携带后端 error.code 与可展示 message。 */
    data class Failure(val code: String, val message: String, val httpStatus: Int) : DomainResult<Nothing>

    /** 网络/IO 异常（无法连通服务器等）。 */
    data class NetworkError(val message: String) : DomainResult<Nothing>
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(data))
    is DomainResult.Failure -> this
    is DomainResult.NetworkError -> this
}
