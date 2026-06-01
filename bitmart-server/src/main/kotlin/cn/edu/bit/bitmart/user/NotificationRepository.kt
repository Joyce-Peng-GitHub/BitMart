package cn.edu.bit.bitmart.user

import cn.edu.bit.bitmart.db.Notifications
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/** 通知项（合并个人通知与全员公告）。 */
data class NotificationItem(
    val id: Long,
    val category: Int,
    val title: String,
    val body: String,
    val payload: String?,
    val readAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val isAnnouncement: Boolean,
)

/** 通知游标（keyset，按 created_at, id 倒序）。 */
data class NotificationCursor(val createdAt: OffsetDateTime, val id: Long)

/**
 * 通知仓储。须在 transaction 内调用。
 * 列表合并 user_id = :uid 的个人通知与 user_id IS NULL 的全员公告，按时间统一排序。
 */
class NotificationRepository {

    /** 创建一条通知（user_id 为 null 表示全员公告）。 */
    fun create(userId: Long?, category: Int, title: String, body: String, payload: String?): Long =
        Notifications.insertAndGetId {
            it[Notifications.userId] = userId
            it[Notifications.category] = category
            it[Notifications.title] = title
            it[Notifications.body] = body
            it[Notifications.payload] = payload
            it[createdAt] = OffsetDateTime.now()
        }.value

    /** 拉取用户可见通知（个人 + 公告），keyset 分页。 */
    fun listForUser(userId: Long, cursor: NotificationCursor?, limit: Int): List<NotificationItem> {
        val visible = (Notifications.userId eq userId) or Notifications.userId.isNull()
        var query = Notifications.selectAll().where { visible }
        cursor?.let { c ->
            query = query.andWhere {
                (Notifications.createdAt less c.createdAt) or
                    ((Notifications.createdAt eq c.createdAt) and (Notifications.id less c.id))
            }
        }
        return query
            .orderBy(Notifications.createdAt to SortOrder.DESC, Notifications.id to SortOrder.DESC)
            .limit(limit)
            .map {
                NotificationItem(
                    id = it[Notifications.id].value,
                    category = it[Notifications.category],
                    title = it[Notifications.title],
                    body = it[Notifications.body],
                    payload = it[Notifications.payload],
                    readAt = it[Notifications.readAt],
                    createdAt = it[Notifications.createdAt],
                    isAnnouncement = it[Notifications.userId] == null,
                )
            }
    }

    /**
     * 标记已读：仅允许标记本人通知（公告不可标记已读，因其无归属，返回 0）。
     * 返回受影响行数。
     */
    fun markRead(userId: Long, notificationId: Long): Int =
        Notifications.update({
            (Notifications.id eq notificationId) and (Notifications.userId eq userId)
        }) {
            it[readAt] = OffsetDateTime.now()
        }
}
