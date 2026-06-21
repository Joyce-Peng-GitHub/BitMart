package cn.edu.bit.bitmart.job

import cn.edu.bit.bitmart.AppComponents
import cn.edu.bit.bitmart.auth.AuthTestSupport
import cn.edu.bit.bitmart.db.Listings
import cn.edu.bit.bitmart.db.Notifications
import cn.edu.bit.bitmart.domain.Contact
import cn.edu.bit.bitmart.user.NotificationCategory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * 过期提醒定时任务集成测试（内嵌 PG）。
 * 数据库整个测试套件共享，故所有断言都按本测试创建的 userId 隔离，
 * 不对 runOnce 的全局返回值做精确断言。
 */
class ExpiryWarningJobTest : FunSpec({

    fun newUser(components: AppComponents): Long = transaction(components.database) {
        components.userRepository.create("112020" + (100000..999999).random(), "hash", null)
    }

    fun insertListing(
        components: AppComponents,
        userId: Long,
        expiresAt: OffsetDateTime,
        type: Int = 0,
        title: String = "高数教材",
        quantitySold: Int = 0,
        deleted: Boolean = false,
    ): Long = transaction(components.database) {
        Listings.insertAndGetId {
            it[Listings.type] = type
            it[Listings.category] = 0
            it[Listings.userId] = userId
            it[Listings.title] = title
            it[Listings.description] = ""
            it[Listings.quantityTotal] = 3
            it[Listings.quantitySold] = quantitySold
            it[Listings.contact] = listOf(Contact("wechat", "x"))
            it[Listings.expiresAt] = expiresAt
            it[Listings.createdAt] = OffsetDateTime.now()
            it[Listings.updatedAt] = OffsetDateTime.now()
            if (deleted) it[Listings.deletedAt] = OffsetDateTime.now()
            it[Listings.sourceType] = 0
        }.value
    }

    fun expiryWarnsFor(components: AppComponents, userId: Long): List<ResultRow> =
        transaction(components.database) {
            Notifications.selectAll().where {
                (Notifications.userId eq userId) and (Notifications.category eq NotificationCategory.EXPIRY_WARN)
            }.toList()
        }

    test("窗口内到期的发布项生成提醒，payload 携带 listingId 与 expiresAt") {
        val components = AuthTestSupport.components()
        val uid = newUser(components)
        val listingId = insertListing(components, uid, OffsetDateTime.now().plusHours(2))

        components.expiryWarningJob.runOnce()

        val warns = expiryWarnsFor(components, uid)
        warns.size shouldBe 1
        warns[0][Notifications.title] shouldBe "item expiring soon"
        warns[0][Notifications.body] shouldContain "高数教材"
        // JSONB 存储会规范化键序与空白，故解析后按字段断言而非子串匹配。
        val payload = Json.parseToJsonElement(warns[0][Notifications.payload]!!).jsonObject
        payload.getValue("listingId").jsonPrimitive.long shouldBe listingId
        payload.getValue("expiresAt").jsonPrimitive.content.isNotBlank() shouldBe true
    }

    test("payload 携带结构化国际化字段，且仍写中文 title/body 兜底") {
        val components = AuthTestSupport.components()
        val uid = newUser(components)
        val listingId =
            insertListing(components, uid, OffsetDateTime.now().plusHours(2), type = 0, title = "高数教材")

        components.expiryWarningJob.runOnce()

        val warns = expiryWarnsFor(components, uid)
        warns.size shouldBe 1
        // 英文兜底仍在写，且非空。
        warns[0][Notifications.title].isNotBlank() shouldBe true
        warns[0][Notifications.body].isNotBlank() shouldBe true
        warns[0][Notifications.title] shouldBe "item expiring soon"

        val payload = Json.parseToJsonElement(warns[0][Notifications.payload]!!).jsonObject
        payload.getValue("templateKey").jsonPrimitive.content shouldBe "EXPIRY_WARNING"
        payload.getValue("listingId").jsonPrimitive.long shouldBe listingId
        payload.getValue("expiresAt").jsonPrimitive.content.isNotBlank() shouldBe true
        payload.getValue("listingTitle").jsonPrimitive.content shouldBe "高数教材"
        payload.getValue("hours").jsonPrimitive.int shouldBe 24
        payload.getValue("listingType").jsonPrimitive.content shouldBe "SELL"
    }

    test("求购类型的 payload listingType 为 BUY") {
        val components = AuthTestSupport.components()
        val uid = newUser(components)
        insertListing(components, uid, OffsetDateTime.now().plusHours(2), type = 1, title = "求二手自行车")

        components.expiryWarningJob.runOnce()

        val warns = expiryWarnsFor(components, uid)
        warns.size shouldBe 1
        val payload = Json.parseToJsonElement(warns[0][Notifications.payload]!!).jsonObject
        payload.getValue("listingType").jsonPrimitive.content shouldBe "BUY"
        payload.getValue("listingTitle").jsonPrimitive.content shouldBe "求二手自行车"
    }

    test("重复执行不重复提醒") {
        val components = AuthTestSupport.components()
        val uid = newUser(components)
        insertListing(components, uid, OffsetDateTime.now().plusHours(2))

        components.expiryWarningJob.runOnce()
        components.expiryWarningJob.runOnce()

        expiryWarnsFor(components, uid).size shouldBe 1
    }

    test("窗口外、已过期、已删除、已售罄的发布项不提醒") {
        val components = AuthTestSupport.components()
        val uid = newUser(components)
        insertListing(components, uid, OffsetDateTime.now().plusHours(48))                       // 窗口外
        insertListing(components, uid, OffsetDateTime.now().minusHours(1))                       // 已过期
        insertListing(components, uid, OffsetDateTime.now().plusHours(2), deleted = true)        // 已删除
        insertListing(components, uid, OffsetDateTime.now().plusHours(2), quantitySold = 3)      // 已售罄

        components.expiryWarningJob.runOnce()

        expiryWarnsFor(components, uid).size shouldBe 0
    }

    test("延期后临近新到期时间会再次提醒") {
        val components = AuthTestSupport.components()
        val uid = newUser(components)
        val listingId = insertListing(components, uid, OffsetDateTime.now().plusHours(2))

        components.expiryWarningJob.runOnce()
        // 延期到 10 小时后（仍在 24h 窗口内，便于直接验证第二次提醒）。
        transaction(components.database) {
            Listings.update({ Listings.id eq listingId }) { it[expiresAt] = OffsetDateTime.now().plusHours(10) }
        }
        components.expiryWarningJob.runOnce()

        expiryWarnsFor(components, uid).size shouldBe 2
    }

    test("求购类型的提醒文案为求购") {
        val components = AuthTestSupport.components()
        val uid = newUser(components)
        insertListing(components, uid, OffsetDateTime.now().plusHours(2), type = 1, title = "求二手自行车")

        components.expiryWarningJob.runOnce()

        val warns = expiryWarnsFor(components, uid)
        warns.size shouldBe 1
        warns[0][Notifications.title] shouldBe "want-to-buy expiring soon"
        warns[0][Notifications.body] shouldContain "求二手自行车"
    }
})
