package cn.edu.bit.bitmart.core.data.repository

import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.data.remote.NotificationDto
import cn.edu.bit.bitmart.core.data.remote.NotificationPageDto
import cn.edu.bit.bitmart.core.data.remote.UpdateMeRequest
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.map
import cn.edu.bit.bitmart.core.domain.model.Notification
import cn.edu.bit.bitmart.core.domain.model.NotificationPage
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import javax.inject.Inject

/** ProfileRepository 实现：调用 /me 与通知接口并映射为领域模型。 */
class ProfileRepositoryImpl @Inject constructor(
    private val api: BitMartApi,
) : ProfileRepository {

    override suspend fun getMe(): DomainResult<User> =
        api.getMe().map { it.toDomain() }

    override suspend fun updateNickname(nickname: String?): DomainResult<User> =
        api.updateMe(UpdateMeRequest(nickname)).map { it.toDomain() }

    override suspend fun notifications(cursor: String?, limit: Int): DomainResult<NotificationPage> =
        api.notifications(cursor, limit).map { it.toDomain() }

    override suspend fun markNotificationRead(id: Long): DomainResult<Unit> =
        api.markNotificationRead(id)
}

private fun NotificationPageDto.toDomain() =
    NotificationPage(items = items.map { it.toDomain() }, nextCursor = nextCursor)

private fun NotificationDto.toDomain() = Notification(
    id = id,
    category = category,
    title = title,
    body = body,
    payload = payload,
    read = read,
    createdAt = createdAt,
    isAnnouncement = isAnnouncement,
)
