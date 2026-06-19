package cn.edu.bit.bitmart.user

import cn.edu.bit.bitmart.domain.User
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** /me 相关服务：资料查询/更新、通知列表与已读。 */
class UserService(
    private val database: Database,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
) {
    /** 当前用户资料。 */
    fun profile(userId: Long): User? = transaction(database) { userRepository.findById(userId) }

    /** 更新昵称（null/空白 → 清空，昵称留空；超 32 字符自动截断以匹配 DB VARCHAR(32)）。返回更新后的用户。 */
    fun updateNickname(userId: Long, nickname: String?): User? = transaction(database) {
        val normalized = nickname?.trim()?.takeIf { it.isNotEmpty() }?.take(32)
        userRepository.updateNickname(userId, normalized)
        userRepository.findById(userId)
    }

    /** 通知列表（个人 + 公告），keyset 分页。 */
    fun notifications(userId: Long, cursor: NotificationCursor?, limit: Int): List<NotificationItem> =
        transaction(database) { notificationRepository.listForUser(userId, cursor, limit) }

    /** 个人未读通知数（不含公告，理由见 [NotificationRepository.unreadCountFor]）。 */
    fun unreadNotificationCount(userId: Long): Long =
        transaction(database) { notificationRepository.unreadCountFor(userId) }

    /** 标记通知已读。返回是否成功（公告或非本人通知返回 false）。 */
    fun markNotificationRead(userId: Long, notificationId: Long): Boolean =
        transaction(database) { notificationRepository.markRead(userId, notificationId) > 0 }
}
