package cn.edu.bit.bitmart.core.data.repository

import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.map
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import javax.inject.Inject

/** ProfileRepository 实现：调用 /me 接口并映射为领域模型。 */
class ProfileRepositoryImpl @Inject constructor(
    private val api: BitMartApi,
) : ProfileRepository {

    override suspend fun getMe(): DomainResult<User> =
        api.getMe().map { it.toDomain() }
}
