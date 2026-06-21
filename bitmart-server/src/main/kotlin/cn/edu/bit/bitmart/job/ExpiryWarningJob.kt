package cn.edu.bit.bitmart.job

import cn.edu.bit.bitmart.db.Listings
import cn.edu.bit.bitmart.domain.ListingType
import cn.edu.bit.bitmart.user.NotificationCategory
import cn.edu.bit.bitmart.user.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime

/** 即将到期的发布项（本任务的查询投影）。 */
private data class DueListing(
    val id: Long,
    val userId: Long,
    val type: Int,
    val title: String,
    val expiresAt: OffsetDateTime,
)

/**
 * 过期提醒定时任务（架构 §9/§10）：每 [interval] 扫描一次，为 [warnWindow] 内到期、
 * 未删除且未售罄的 listing 给发布者创建一条 EXPIRY_WARN 站内通知。
 *
 * - 去重键为 payload 中的 (listingId, expiresAt)：同一到期时间只提醒一次；
 *   用户延期后 expires_at 变化，临近新到期时间会再次提醒（Request.md 超时提醒）。
 * - 已售罄项不提醒（无延期价值）；quantity_sold 允许回退，回到在售后下一周期仍会提醒。
 * - 推送（FCM/SSE）尚未实现，当前仅站内信。
 */
class ExpiryWarningJob(
    private val database: Database,
    private val notificationRepository: NotificationRepository,
    private val warnWindow: Duration,
    private val interval: Duration,
) {
    private val log = LoggerFactory.getLogger(ExpiryWarningJob::class.java)

    /**
     * 启动后台循环。[scope] 应为应用级协程作用域（Ktor Application 自身），停机时随之取消。
     * 单次失败只记日志，不中断循环。
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                runCatching { runOnce() }
                    .onSuccess { created -> if (created > 0) log.info("Expiry warning: created {} notifications", created) }
                    .onFailure { log.error("Expiry warning job failed, will retry next cycle", it) }
                delay(interval.toMillis())
            }
        }
    }

    /** 单次扫描。返回新建通知数。[now] 可注入以便测试。 */
    fun runOnce(now: OffsetDateTime = OffsetDateTime.now()): Int = transaction(database) {
        val due = findDue(now)
        val warnHours = warnWindow.toHours().toInt()
        due.forEach { l ->
            val kind = if (l.type == 0) "item" else "want-to-buy"
            // listingType: Listings.type 与 domain.ListingType ordinal 对齐（0=SELL/商品，1=BUY/求购）。
            val listingType = ListingType.entries[l.type].name
            // 结构化 payload 供客户端本地化渲染；title/body 写英文兜底（向后兼容）。
            // 去重键 listingId/expiresAt 必须保留（findDue 的 NOT EXISTS 依赖它们）。
            val payload = buildJsonObject {
                put("listingId", l.id)
                put("expiresAt", l.expiresAt.toString())
                put("templateKey", "EXPIRY_WARNING")
                put("listingTitle", l.title)
                put("hours", warnHours)
                put("listingType", listingType)
            }
            notificationRepository.create(
                userId = l.userId,
                category = NotificationCategory.EXPIRY_WARN,
                title = "$kind expiring soon",
                body = "Your $kind \"${l.title}\" will expire within $warnHours hours. Go to \"My posts\" to extend it.",
                payload = payload.toString(),
            )
        }
        due.size
    }

    /**
     * 查询 (now, now+warnWindow] 内到期且尚未就当前 expires_at 提醒过的发布项。
     * 去重用 NOT EXISTS 反连接 notification 的 payload（JSONB）字段，
     * timestamptz 比较按时刻进行，payload 中 ISO 字符串往返不损失精度。
     */
    private fun findDue(now: OffsetDateTime): List<DueListing> {
        val tsType = Listings.expiresAt.columnType
        val sql = """
            SELECT l.id, l.user_id, l.type, l.title, l.expires_at
            FROM listing l
            WHERE l.deleted_at IS NULL
              AND l.quantity_sold < l.quantity_total
              AND l.expires_at > ?
              AND l.expires_at <= ?
              AND NOT EXISTS (
                SELECT 1 FROM notification n
                WHERE n.user_id = l.user_id
                  AND n.category = ?
                  AND n.payload->>'listingId' = l.id::text
                  AND (n.payload->>'expiresAt')::timestamptz = l.expires_at
              )
            ORDER BY l.expires_at
        """.trimIndent()
        val params = listOf<Pair<IColumnType<*>, Any?>>(
            tsType to now,
            tsType to now.plus(warnWindow),
            IntegerColumnType() to NotificationCategory.EXPIRY_WARN,
        )
        return TransactionManager.current().exec(sql, params, StatementType.SELECT) { rs ->
            val out = mutableListOf<DueListing>()
            while (rs.next()) {
                out += DueListing(
                    id = rs.getLong("id"),
                    userId = rs.getLong("user_id"),
                    type = rs.getInt("type"),
                    title = rs.getString("title"),
                    expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
                )
            }
            out
        } ?: emptyList()
    }
}
