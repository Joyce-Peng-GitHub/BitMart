package cn.edu.bit.bitmart.core.domain.repository

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User

/** 用户个人资料仓储接口。 */
interface ProfileRepository {
    /** 获取当前用户信息（/me）。 */
    suspend fun getMe(): DomainResult<User>
}
