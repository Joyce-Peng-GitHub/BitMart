package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.db.BookMetas
import cn.edu.bit.bitmart.external.BookMeta
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/** 书籍元数据缓存仓储（book_meta）。ISBN 元数据视为不可变事实。须在 transaction 内调用。 */
class BookMetaRepository {

    /** 按 ISBN 查缓存。 */
    fun find(isbn: String): BookMeta? =
        BookMetas.selectAll().where { BookMetas.isbn eq isbn }.singleOrNull()?.let {
            BookMeta(
                isbn = it[BookMetas.isbn],
                title = it[BookMetas.title],
                author = it[BookMetas.authors],
                publisher = it[BookMetas.publisher],
                edition = it[BookMetas.edition],
                pubdate = null, price = null, page = null,
                binding = null, format = null, img = null, summary = null,
            )
        }

    /** 写入或更新缓存（upsert）。 */
    fun save(meta: BookMeta, rawJson: String) {
        val exists = BookMetas.selectAll().where { BookMetas.isbn eq meta.isbn }.any()
        if (exists) {
            BookMetas.update({ BookMetas.isbn eq meta.isbn }) {
                it[title] = meta.title
                it[authors] = meta.author
                it[publisher] = meta.publisher
                it[edition] = meta.edition
                it[raw] = rawJson
                it[fetchedAt] = OffsetDateTime.now()
            }
        } else {
            BookMetas.insert {
                it[isbn] = meta.isbn
                it[title] = meta.title
                it[authors] = meta.author
                it[publisher] = meta.publisher
                it[edition] = meta.edition
                it[raw] = rawJson
                it[fetchedAt] = OffsetDateTime.now()
            }
        }
    }
}
