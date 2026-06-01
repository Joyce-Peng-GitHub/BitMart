package cn.edu.bit.bitmart.db

import cn.edu.bit.bitmart.domain.Contact
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Exposed 表定义（Exposed 1.3.0，包路径 org.jetbrains.exposed.v1.*）。
 *
 * 重要：表结构（含 tsvector 触发器、部分索引、CHECK）由 Flyway SQL 迁移负责创建，
 * 这里的定义仅用于 DSL 查询的列引用，不调用 SchemaUtils.create（SQL 是单一事实来源）。
 * 列名、类型须与迁移脚本保持一致。
 *
 * - 自增主键表用 LongIdTable（id 列由其提供，匹配迁移中的 BIGINT IDENTITY）。
 * - SMALLINT 鉴别列（role/status/type/...）在 Exposed 侧用 integer() 映射，
 *   JDBC 在 Int 与 SMALLINT 间自动转换，避免业务代码到处 .toShort()。
 */

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 用户表（迁移中名为 app_user，因 user 是 PostgreSQL 保留字）。 */
object Users : LongIdTable("app_user") {
    val studentId = varchar("student_id", 20)
    val passwordHash = text("password_hash")
    val nickname = varchar("nickname", 32).nullable()
    val role = integer("role")
    val status = integer("status")
    val createdAt = timestampWithTimeZone("created_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
}

/** 会话表，主键为 SHA-256(token)，非自增 → 用普通 Table。 */
object Sessions : Table("session") {
    val tokenHash = binary("token_hash", 32)
    val userId = long("user_id")
    val createdAt = timestampWithTimeZone("created_at")
    val lastUsedAt = timestampWithTimeZone("last_used_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val userAgent = text("user_agent").nullable()
    val revoked = bool("revoked")
    override val primaryKey = PrimaryKey(tokenHash)
}

object Notifications : LongIdTable("notification") {
    val userId = long("user_id").nullable()        // NULL = 全员公告
    val category = integer("category")
    val title = varchar("title", 120)
    val body = text("body")
    val payload = text("payload").nullable()        // JSONB，按文本读写
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
}

object PushTokens : LongIdTable("push_token") {
    val userId = long("user_id")
    val token = text("token")
    val platform = integer("platform")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}

/** 列表（卖品/求购共用），contact 以 JSONB 数组存储。 */
object Listings : LongIdTable("listing") {
    val type = integer("type")                       // 0 SELL / 1 BUY
    val category = integer("category")               // 0 GENERAL / 1 BOOK
    val userId = long("user_id")
    val title = text("title")
    val description = text("description")
    val unitPrice = decimal("unit_price", 10, 2).nullable()
    val quantityTotal = integer("quantity_total")
    val quantitySold = integer("quantity_sold")
    val pickupLocation = text("pickup_location").nullable()
    val contact = jsonb<List<Contact>>("contact", json, ListSerializer(Contact.serializer()))
    val expiresAt = timestampWithTimeZone("expires_at")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val sourceType = integer("source")               // 0 USER / 1 NAPCAT_BOT
}

object ListingImages : LongIdTable("listing_image") {
    val listingId = long("listing_id")
    val blobKey = text("blob_key")
    val ord = integer("ord")
    val width = integer("width").nullable()
    val height = integer("height").nullable()
}

object Tags : LongIdTable("tag") {
    val name = varchar("name", 20)
    val usageCount = integer("usage_count")
}

object ListingTags : Table("listing_tag") {
    val listingId = long("listing_id")
    val tagId = long("tag_id")
    override val primaryKey = PrimaryKey(listingId, tagId)
}

/** 服务端缓存的 ISBN 元数据（PK 为业务列 isbn）。 */
object BookMetas : Table("book_meta") {
    val isbn = varchar("isbn", 20)
    val title = text("title").nullable()
    val authors = text("authors").nullable()
    val publisher = text("publisher").nullable()
    val edition = text("edition").nullable()
    val raw = text("raw")                            // JSONB 原始返回，按文本读写
    val fetchedAt = timestampWithTimeZone("fetched_at")
    override val primaryKey = PrimaryKey(isbn)
}

/** 书籍专属信息，与 listing 1:1（PK 为 listing_id）。 */
object ListingBooks : Table("listing_book") {
    val listingId = long("listing_id")
    val isbn = varchar("isbn", 20).nullable()
    val title = text("title").nullable()
    val authors = text("authors").nullable()
    val publisher = text("publisher").nullable()
    val edition = text("edition").nullable()
    override val primaryKey = PrimaryKey(listingId)
}
