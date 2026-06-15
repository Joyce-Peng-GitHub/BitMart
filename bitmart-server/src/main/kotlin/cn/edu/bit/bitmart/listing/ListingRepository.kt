package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.db.ListingBooks
import cn.edu.bit.bitmart.db.ListingImages
import cn.edu.bit.bitmart.db.Listings
import cn.edu.bit.bitmart.db.Users
import cn.edu.bit.bitmart.domain.BookInfo
import cn.edu.bit.bitmart.domain.Contact
import cn.edu.bit.bitmart.domain.ListingCategory
import cn.edu.bit.bitmart.domain.ListingDetail
import cn.edu.bit.bitmart.domain.ListingFilter
import cn.edu.bit.bitmart.domain.ListingSummary
import cn.edu.bit.bitmart.domain.ListingType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 列表（卖品/求购）仓储。须在 transaction 内调用。查询统一排除软删除行。
 * 标签由 [TagRepository] 处理，本仓储负责 listing 与 listing_book 主体。
 */
class ListingRepository {

    /** 插入一条 listing（及书籍信息），返回生成的 id。标签关联由 Service 另行处理。 */
    fun create(input: CreateListingInput): Long {
        val now = OffsetDateTime.now()
        val id = Listings.insertAndGetId {
            it[type] = input.type
            it[category] = input.category
            it[userId] = input.userId
            it[title] = input.title
            it[description] = input.description
            it[unitPrice] = input.unitPrice
            it[quantityTotal] = input.quantityTotal
            it[quantitySold] = 0
            it[pickupLocation] = input.pickupLocation
            it[contact] = input.contacts
            it[expiresAt] = input.expiresAt
            it[createdAt] = now
            it[updatedAt] = now
            it[sourceType] = input.source
        }.value

        if (input.category == ListingCategory.BOOK.ordinal && input.book != null) {
            ListingBooks.insert {
                it[listingId] = id
                it[isbn] = input.book.isbn
                it[title] = input.book.title
                it[authors] = input.book.authors
                it[publisher] = input.book.publisher
                it[edition] = input.book.edition
            }
        }

        // 图片：按携带顺序写入 listing_image，ord 从 0 递增。blobKey 来自 /uploads/images。
        input.imageKeys.forEachIndexed { index, key ->
            cn.edu.bit.bitmart.db.ListingImages.insert {
                it[listingId] = id
                it[blobKey] = key
                it[ord] = index
            }
        }
        return id
    }

    /** 读取详情（含书籍、卖家昵称、图片）。仅返回未软删除项。 */
    fun findDetail(id: Long): ListingDetail? {
        val row = Listings.join(Users, JoinType.INNER, onColumn = Listings.userId, otherColumn = Users.id)
            .selectAll()
            .where { (Listings.id eq id) and Listings.deletedAt.isNull() }
            .singleOrNull() ?: return null

        val book = if (row[Listings.category] == ListingCategory.BOOK.ordinal) {
            ListingBooks.selectAll().where { ListingBooks.listingId eq id }.singleOrNull()?.let {
                BookInfo(
                    isbn = it[ListingBooks.isbn],
                    title = it[ListingBooks.title],
                    authors = it[ListingBooks.authors],
                    publisher = it[ListingBooks.publisher],
                    edition = it[ListingBooks.edition],
                )
            }
        } else null

        val images = cn.edu.bit.bitmart.db.ListingImages.selectAll()
            .where { cn.edu.bit.bitmart.db.ListingImages.listingId eq id }
            .orderBy(cn.edu.bit.bitmart.db.ListingImages.ord)
            .map { "/static/${it[cn.edu.bit.bitmart.db.ListingImages.blobKey]}" }

        return row.toDetail(book, emptyList(), images)   // 标签由 Service 填充
    }

    /** 校验 listing 归属与存在性（用于修改/删除前的鉴权）。返回 ownerId 或 null。 */
    fun findOwner(id: Long): Long? =
        Listings.selectAll()
            .where { (Listings.id eq id) and Listings.deletedAt.isNull() }
            .singleOrNull()
            ?.get(Listings.userId)

    /** 软删除（仅本人或管理员，鉴权在 Service）。返回受影响行数。 */
    fun softDelete(id: Long): Int =
        Listings.update({ (Listings.id eq id) and Listings.deletedAt.isNull() }) {
            it[deletedAt] = OffsetDateTime.now()
            it[updatedAt] = OffsetDateTime.now()
        }

    /**
     * 更新可变字段。返回受影响行数。售出数量与延期由专门方法处理以施加并发约束。
     * 全字段编辑还可改 category/quantityTotal/contacts；标签、图片、书籍由专门方法处理。
     */
    fun updateFields(id: Long, input: UpdateListingInput): Int =
        Listings.update({ (Listings.id eq id) and Listings.deletedAt.isNull() }) {
            input.title?.let { v -> it[title] = v }
            input.description?.let { v -> it[description] = v }
            input.pickupLocation?.let { v -> it[pickupLocation] = v }
            when {
                input.clearUnitPrice -> it[unitPrice] = null
                input.unitPrice != null -> it[unitPrice] = input.unitPrice
            }
            input.expiresAt?.let { v -> it[expiresAt] = v }
            input.category?.let { v -> it[category] = v }
            input.quantityTotal?.let { v -> it[quantityTotal] = v }
            input.contacts?.let { v -> it[contact] = v }
            it[updatedAt] = OffsetDateTime.now()
        }

    /** 整体替换某 listing 的图片（编辑用）：删除原图，按顺序重新写入。 */
    fun replaceImages(id: Long, blobKeys: List<String>) {
        ListingImages.deleteWhere { ListingImages.listingId eq id }
        blobKeys.forEachIndexed { index, key ->
            ListingImages.insert {
                it[listingId] = id
                it[blobKey] = key
                it[ord] = index
            }
        }
    }

    /** 写入/更新书籍信息（category=BOOK 时）：以 listing_id 为主键先删后插，幂等。 */
    fun upsertBook(id: Long, book: BookInput?) {
        ListingBooks.deleteWhere { ListingBooks.listingId eq id }
        ListingBooks.insert {
            it[listingId] = id
            it[isbn] = book?.isbn
            it[title] = book?.title
            it[authors] = book?.authors
            it[publisher] = book?.publisher
            it[edition] = book?.edition
        }
    }

    /** 删除某 listing 的书籍信息（类别改为非书籍时）。 */
    fun deleteBook(id: Long) {
        ListingBooks.deleteWhere { ListingBooks.listingId eq id }
    }

    /** 更新售出数量（允许增减，范围由 Service 层校验保证）。返回受影响行数。 */
    fun updateQuantitySold(id: Long, newSold: Int): Int =
        Listings.update({ (Listings.id eq id) and Listings.deletedAt.isNull() }) {
            it[quantitySold] = newSold
            it[updatedAt] = OffsetDateTime.now()
        }

    /**
     * 关键字搜索 + 过滤 + keyset 分页。用参数化原生 SQL 以完整控制 tsvector/trgm
     * 与游标条件，避免在 Exposed 表达式层拼接全文检索操作符。
     *
     * 公开列表（架构 §6.3）默认仅返回未过期、未售罄项；我的列表（§6.2）通过
     * filter.ownerId + includeExpired + includeSold 放宽，返回本人全部未软删项。
     */
    fun list(filter: ListingFilter): List<ListingSummary> {
        val sql = StringBuilder(
            """
            SELECT l.id, l.type, l.category, l.title, l.unit_price,
                   l.quantity_total, l.quantity_sold, u.nickname, l.created_at, l.expires_at,
                   (SELECT '/static/' || li.blob_key FROM listing_image li
                     WHERE li.listing_id = l.id ORDER BY li.ord LIMIT 1) AS first_image
            FROM listing l
            JOIN app_user u ON u.id = l.user_id
            WHERE l.deleted_at IS NULL
            """.trimIndent(),
        )
        // exec 的参数为 (IColumnType, value) 列表，类型安全且自动转义。
        val intType = IntegerColumnType()
        val longType = LongColumnType()
        val textType = TextColumnType()
        val decimalType = Listings.unitPrice.columnType        // NUMERIC(10,2)
        val tsType = Listings.expiresAt.columnType             // timestamptz
        val params = mutableListOf<Pair<IColumnType<*>, Any?>>()

        // 过期过滤：公开列表排除已过期；我的列表（includeExpired）不限。
        if (!filter.includeExpired) {
            sql.append("\n  AND l.expires_at > now()")
        }
        // 归属过滤：我的列表仅返回本人发布项。
        filter.ownerId?.let {
            sql.append("\n  AND l.user_id = ?")
            params += longType to it
        }
        // type 可选：省略则买卖两类都返回。
        filter.type?.let {
            sql.append("\n  AND l.type = ?")
            params += intType to it.ordinal
        }

        filter.category?.let {
            sql.append("\n  AND l.category = ?")
            params += intType to it.ordinal
        }
        if (!filter.includeSold) {
            sql.append("\n  AND l.quantity_sold < l.quantity_total")
        }
        if (filter.minPrice != null || filter.maxPrice != null) {
            val inner = mutableListOf<String>()
            if (filter.minPrice != null) {
                inner += "l.unit_price >= ?"; params += decimalType to filter.minPrice
            }
            if (filter.maxPrice != null) {
                inner += "l.unit_price <= ?"; params += decimalType to filter.maxPrice
            }
            var priceClause = "(" + inner.joinToString(" AND ") + ")"
            if (filter.includeNoPrice) priceClause += " OR l.unit_price IS NULL"
            sql.append("\n  AND ($priceClause)")
        } else if (!filter.includeNoPrice) {
            sql.append("\n  AND l.unit_price IS NOT NULL")
        }
        if (filter.tagIds.isNotEmpty()) {
            val placeholders = filter.tagIds.joinToString(",") { "?" }
            sql.append("\n  AND EXISTS (SELECT 1 FROM listing_tag lt WHERE lt.listing_id = l.id AND lt.tag_id IN ($placeholders))")
            filter.tagIds.forEach { params += longType to it }
        }
        if (!filter.query.isNullOrBlank()) {
            sql.append(
                "\n  AND (l.search_tsv @@ plainto_tsquery(bitmart_search_config()::regconfig, ?)" +
                    " OR l.title ILIKE ? OR l.description ILIKE ?)",
            )
            params += textType to filter.query
            params += textType to "%${filter.query}%"
            params += textType to "%${filter.query}%"
        }
        filter.cursor?.let {
            sql.append("\n  AND (l.created_at, l.id) < (?, ?)")
            params += tsType to it.createdAt
            params += longType to it.id
        }
        sql.append("\nORDER BY l.created_at DESC, l.id DESC")
        sql.append("\nLIMIT ?")
        params += intType to filter.limit

        return TransactionManager.current().exec(sql.toString(), params, StatementType.SELECT) { rs ->
            val out = mutableListOf<ListingSummary>()
            while (rs.next()) {
                out += ListingSummary(
                    id = rs.getLong("id"),
                    type = ListingType.entries[rs.getInt("type")],
                    category = ListingCategory.entries[rs.getInt("category")],
                    title = rs.getString("title"),
                    unitPrice = rs.getBigDecimal("unit_price"),
                    quantityTotal = rs.getInt("quantity_total"),
                    quantitySold = rs.getInt("quantity_sold"),
                    nickname = rs.getString("nickname"),
                    firstImageUrl = rs.getString("first_image"),
                    tags = emptyList(),    // 由 Service 批量填充
                    createdAt = rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC),
                    expiresAt = rs.getTimestamp("expires_at").toInstant().atOffset(ZoneOffset.UTC),
                )
            }
            out
        } ?: emptyList()
    }

    internal fun ResultRow.toDetail(book: BookInfo?, tags: List<String>, imageUrls: List<String>): ListingDetail =
        ListingDetail(
            id = this[Listings.id].value,
            type = ListingType.entries[this[Listings.type]],
            category = ListingCategory.entries[this[Listings.category]],
            userId = this[Listings.userId],
            nickname = this[Users.nickname],
            title = this[Listings.title],
            description = this[Listings.description],
            unitPrice = this[Listings.unitPrice],
            quantityTotal = this[Listings.quantityTotal],
            quantitySold = this[Listings.quantitySold],
            pickupLocation = this[Listings.pickupLocation],
            contacts = this[Listings.contact],
            tags = tags,
            imageUrls = imageUrls,
            expiresAt = this[Listings.expiresAt],
            createdAt = this[Listings.createdAt],
            updatedAt = this[Listings.updatedAt],
            book = book,
        )
}
