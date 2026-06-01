package cn.edu.bit.bitmart.db

import cn.edu.bit.bitmart.domain.Contact
import cn.edu.bit.bitmart.domain.ContactChannel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * 数据库迁移与 schema 集成测试，跑在内嵌真实 PostgreSQL 上：
 * 验证迁移可执行、表存在、tsvector 触发器生效、JSONB 往返、CHECK 约束、Exposed 查询可用。
 */
class MigrationIntegrationTest : FunSpec({

    val db = EmbeddedPostgresSupport.db()

    test("迁移创建了全部预期表") {
        transaction(db) {
            val tables = exec(
                """SELECT table_name FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"""
            ) { rs ->
                val names = mutableListOf<String>()
                while (rs.next()) names += rs.getString(1)
                names
            } ?: emptyList()
            tables shouldContainAll listOf(
                "app_user", "session", "notification", "push_token",
                "listing", "listing_image", "tag", "listing_tag",
                "book_meta", "listing_book",
            )
        }
    }

    test("插入用户后可查回") {
        val sid = "112020${(1000..9999).random()}"
        transaction(db) {
            val userId = Users.insertAndGetId {
                it[studentId] = sid
                it[passwordHash] = "\$argon2id\$dummy"
                it[role] = 0
                it[status] = 0
                it[createdAt] = OffsetDateTime.now()
            }
            userId.value shouldBeGreaterThan 0L

            val found = Users.selectAll().where { Users.studentId eq sid }.single()
            found[Users.studentId] shouldBe sid
        }
    }

    test("listing 的 contact JSONB 往返正确") {
        transaction(db) {
            val uid = Users.insertAndGetId {
                it[studentId] = "jsonbtest${(1000..9999).random()}"
                it[passwordHash] = "h"
                it[role] = 0; it[status] = 0
                it[createdAt] = OffsetDateTime.now()
            }.value
            val contacts = listOf(
                Contact(ContactChannel.WECHAT, "wxid_abc"),
                Contact(ContactChannel.QQ, "10001"),
            )
            val lid = Listings.insertAndGetId {
                it[type] = 0; it[category] = 0
                it[userId] = uid
                it[title] = "测试商品"
                it[description] = "二手书一本"
                it[unitPrice] = BigDecimal("30.00")
                it[quantityTotal] = 1; it[quantitySold] = 0
                it[contact] = contacts
                it[expiresAt] = OffsetDateTime.now().plusDays(30)
                it[createdAt] = OffsetDateTime.now()
                it[updatedAt] = OffsetDateTime.now()
                it[sourceType] = 0
            }.value
            val row = Listings.selectAll().where { Listings.id eq lid }.single()
            row[Listings.contact] shouldBe contacts
        }
    }

    test("tsvector 触发器为标题/描述填充 search_tsv") {
        transaction(db) {
            val uid = Users.insertAndGetId {
                it[studentId] = "tsv${(1000..9999).random()}"
                it[passwordHash] = "h"; it[role] = 0; it[status] = 0
                it[createdAt] = OffsetDateTime.now()
            }.value
            Listings.insert {
                it[type] = 0; it[category] = 0; it[userId] = uid
                it[title] = "数据结构与算法"
                it[description] = "经典教材，九成新"
                it[quantityTotal] = 1; it[quantitySold] = 0
                it[contact] = listOf(Contact(ContactChannel.QQ, "123"))
                it[expiresAt] = OffsetDateTime.now().plusDays(10)
                it[createdAt] = OffsetDateTime.now(); it[updatedAt] = OffsetDateTime.now()
                it[sourceType] = 0
            }
            // 触发器应已填充 search_tsv（非空）。
            val nonEmpty = exec(
                "SELECT count(*) FROM listing WHERE search_tsv IS NOT NULL AND title = '数据结构与算法'"
            ) { rs -> rs.next(); rs.getInt(1) } ?: 0
            (nonEmpty >= 1).shouldBeTrue()
        }
    }

    test("CHECK 约束拒绝 quantity_sold > quantity_total") {
        var rejected = false
        try {
            transaction(db) {
                val uid = Users.insertAndGetId {
                    it[studentId] = "chk${(1000..9999).random()}"
                    it[passwordHash] = "h"; it[role] = 0; it[status] = 0
                    it[createdAt] = OffsetDateTime.now()
                }.value
                Listings.insert {
                    it[type] = 0; it[category] = 0; it[userId] = uid
                    it[title] = "x"; it[description] = ""
                    it[quantityTotal] = 1; it[quantitySold] = 5      // 违反 CHECK
                    it[contact] = listOf(Contact(ContactChannel.QQ, "1"))
                    it[expiresAt] = OffsetDateTime.now().plusDays(1)
                    it[createdAt] = OffsetDateTime.now(); it[updatedAt] = OffsetDateTime.now()
                    it[sourceType] = 0
                }
            }
        } catch (e: Exception) {
            rejected = true
        }
        rejected.shouldBeTrue()
    }
})
