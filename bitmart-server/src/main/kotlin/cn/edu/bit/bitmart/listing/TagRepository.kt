package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.db.ListingTags
import cn.edu.bit.bitmart.db.Tags
import cn.edu.bit.bitmart.domain.TagNormalizer
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** 标签仓储：归一化、get-or-create、用量统计、热门标签。须在 transaction 内调用。 */
class TagRepository {

    /** 归一化后获取或创建标签，返回 tagId。 */
    fun getOrCreate(rawName: String): Long {
        val name = TagNormalizer.normalize(rawName)
        val existing = Tags.selectAll().where { Tags.name eq name }.singleOrNull()
        if (existing != null) return existing[Tags.id].value
        return Tags.insertAndGetId {
            it[Tags.name] = name
            it[usageCount] = 0
        }.value
    }

    /** 关联 listing 与标签，并递增标签用量（去重，忽略已存在的关联）。 */
    fun attach(listingId: Long, tagIds: Collection<Long>) {
        tagIds.distinct().forEach { tagId ->
            val exists = ListingTags.selectAll()
                .where { (ListingTags.listingId eq listingId) and (ListingTags.tagId eq tagId) }
                .any()
            if (!exists) {
                ListingTags.insert {
                    it[ListingTags.listingId] = listingId
                    it[ListingTags.tagId] = tagId
                }
                Tags.update({ Tags.id eq tagId }) {
                    it[usageCount] = Tags.usageCount + 1
                }
            }
        }
    }

    /** 读取某 listing 的标签名列表。 */
    fun namesForListing(listingId: Long): List<String> =
        ListingTags.join(Tags, JoinType.INNER, onColumn = ListingTags.tagId, otherColumn = Tags.id)
            .selectAll()
            .where { ListingTags.listingId eq listingId }
            .map { it[Tags.name] }

    /** 批量读取多个 listing 的标签名（返回 listingId → names）。 */
    fun namesForListings(listingIds: Collection<Long>): Map<Long, List<String>> {
        if (listingIds.isEmpty()) return emptyMap()
        return ListingTags.join(Tags, JoinType.INNER, onColumn = ListingTags.tagId, otherColumn = Tags.id)
            .selectAll()
            .where { ListingTags.listingId inList listingIds }
            .groupBy({ it[ListingTags.listingId] }, { it[Tags.name] })
    }

    /** 热门标签（按用量降序）。 */
    fun popular(limit: Int): List<String> =
        Tags.selectAll()
            .orderBy(Tags.usageCount to SortOrder.DESC)
            .limit(limit)
            .map { it[Tags.name] }
}
