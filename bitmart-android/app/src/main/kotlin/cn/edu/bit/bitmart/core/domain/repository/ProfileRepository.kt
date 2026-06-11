package cn.edu.bit.bitmart.core.domain.repository

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.NotificationPage
import cn.edu.bit.bitmart.core.domain.model.User

/** 用户个人资料仓储接口。 */
interface ProfileRepository {
    /** 获取当前用户信息（/me）。 */
    suspend fun getMe(): DomainResult<User>

    /** 修改昵称（PATCH /me）。传 null 表示清空昵称（显示为匿名）。 */
    suspend fun updateNickname(nickname: String?): DomainResult<User>

    /** 拉取通知（公告 + 个人提醒合并流）。cursor 原样回传上一页的 nextCursor。 */
    suspend fun notifications(cursor: String?, limit: Int): DomainResult<NotificationPage>

    /** 将某条通知标记为已读。 */
    suspend fun markNotificationRead(id: Long): DomainResult<Unit>

    /** 未读通知数（仅个人未读；公告不可标记已读，故不计入）。 */
    suspend fun unreadNotificationCount(): DomainResult<Int>
}
