package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.domain.ListingDetail
import cn.edu.bit.bitmart.domain.ValidationError
import cn.edu.bit.bitmart.external.BookMeta

/** 发布单条 listing 的结果。 */
sealed interface PublishResult {
    data class Success(val listingId: Long) : PublishResult
    data class ValidationFailed(val errors: List<ValidationError>) : PublishResult
}

/** 批量发布结果：全部成功或全部回滚（架构 §6.3）。 */
sealed interface BatchPublishResult {
    data class Success(val listingIds: List<Long>) : BatchPublishResult
    /** 任一条校验失败则整体拒绝，返回逐条错误（index → errors）。 */
    data class ValidationFailed(val errorsByIndex: Map<Int, List<ValidationError>>) : BatchPublishResult
}

/** 修改 listing 的结果。 */
sealed interface UpdateResult {
    data object Success : UpdateResult
    data object NotFound : UpdateResult
    data object Forbidden : UpdateResult
    data class ValidationFailed(val errors: List<ValidationError>) : UpdateResult
    /** 售出数量并发冲突（UPDATE 影响 0 行）→ 409。 */
    data object QuantityConflict : UpdateResult
}

/** 详情查询结果（区分未找到与未登录由路由层处理）。 */
sealed interface DetailResult {
    data class Found(val detail: ListingDetail) : DetailResult
    data object NotFound : DetailResult
}

/** ISBN 查询结果。 */
sealed interface BookLookupResult {
    data class Found(val meta: BookMeta) : BookLookupResult
    data object NotFound : BookLookupResult
    data class ServiceError(val message: String) : BookLookupResult
}
