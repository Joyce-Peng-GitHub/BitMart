package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.config.TagConfig
import cn.edu.bit.bitmart.domain.ListingCategory
import cn.edu.bit.bitmart.domain.ListingFilter
import cn.edu.bit.bitmart.domain.ListingInput
import cn.edu.bit.bitmart.domain.ListingSummary
import cn.edu.bit.bitmart.domain.ListingUpdateInput
import cn.edu.bit.bitmart.domain.ListingValidator
import cn.edu.bit.bitmart.domain.TagNormalizer
import cn.edu.bit.bitmart.domain.UserRole
import cn.edu.bit.bitmart.external.IsbnLookupResult
import cn.edu.bit.bitmart.external.ShowApiClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * 列表（卖品/求购）服务：发布、批量发布、查询、修改、删除、标签、ISBN 查询。
 * 卖与买共用同一套逻辑，仅 type 不同。校验委托给 [ListingValidator]。
 */
class ListingService(
    private val database: Database,
    private val listingRepository: ListingRepository,
    private val tagRepository: TagRepository,
    private val bookMetaRepository: BookMetaRepository,
    private val showApiClient: ShowApiClient,
    private val validator: ListingValidator,
    private val tagConfig: TagConfig,
) {
    private val log = LoggerFactory.getLogger(ListingService::class.java)

    /**
     * 发布单条。
     * @param now 校验时间基准。须与路由层换算 expiresInDays→expiresAt 用的是同一时刻：
     * 若此处重新取时钟，expiresAt 恰为最小有效期（now+minDays）时会因毫秒级时差恒判
     * EXPIRY_TOO_SOON（且因 JIT 冷热路径耗时不同而表现为"重试就成功"的间歇失败）。
     */
    fun publish(input: CreateListingInput, now: Instant = Instant.now()): PublishResult {
        val result = validator.validateCreate(input.toValidationInput(), now)
        if (!result.isValid) {
            log.warn("Publish validation failed userId={} errors={}", input.userId, result.errors)
            return PublishResult.ValidationFailed(result.errors)
        }
        val id = transaction(database) { insertWithTags(input) }
        log.info("Listing published id={} userId={} type={}", id, input.userId, input.type)
        return PublishResult.Success(id)
    }

    /**
     * 批量发布：任一条校验失败则整体拒绝；全部通过则单事务插入（全成功或全回滚）。
     * @param now 校验时间基准，含义同 [publish]。
     */
    fun publishBatch(inputs: List<CreateListingInput>, now: Instant = Instant.now()): BatchPublishResult {
        val errorsByIndex = inputs.withIndex().mapNotNull { (i, input) ->
            val r = validator.validateCreate(input.toValidationInput(), now)
            if (r.isValid) null else i to r.errors
        }.toMap()
        if (errorsByIndex.isNotEmpty()) {
            log.warn("Batch publish validation failed count={} failedIndexes={}", inputs.size, errorsByIndex.keys)
            return BatchPublishResult.ValidationFailed(errorsByIndex)
        }
        val ids = transaction(database) { inputs.map { insertWithTags(it) } }
        log.info("Batch published count={} ids={}", ids.size, ids)
        return BatchPublishResult.Success(ids)
    }

    /** 查询详情并填充标签。 */
    fun detail(id: Long): DetailResult = transaction(database) {
        val detail = listingRepository.findDetail(id) ?: return@transaction DetailResult.NotFound
        val tags = tagRepository.namesForListing(id)
        DetailResult.Found(detail.copy(tags = tags))
    }

    /** 列表查询并批量填充标签。 */
    fun list(filter: ListingFilter): List<ListingSummary> = transaction(database) {
        val items = listingRepository.list(filter)
        if (items.isEmpty()) return@transaction items
        val tagMap = tagRepository.namesForListings(items.map { it.id })
        items.map { it.copy(tags = tagMap[it.id] ?: emptyList()) }
    }

    /**
     * 我的列表（架构 §6.2）：返回 ownerId 用户自己发布的项（排除软删除），供"我的商品/我的收购"管理。
     * 是否含已售罄/已过期由 [filter] 决定（路由层默认 true，客户端可经筛选收窄）。复用 list() 的标签填充。
     */
    fun myListings(ownerId: Long, filter: ListingFilter): List<ListingSummary> =
        list(filter.copy(ownerId = ownerId))

    /** 热门标签。 */
    fun popularTags(limit: Int): List<PopularTag> = transaction(database) {
        tagRepository.popular(limit)
    }

    /**
     * 修改 listing。仅本人或管理员可改。售出数量可增可减（范围 0..quantityTotal）；
     * 延期须落在过期窗口内。
     * @param now 校验时间基准，须与路由层换算延期天数用的同一时刻（见 [publish]）。
     */
    fun update(
        id: Long,
        requesterId: Long,
        requesterRole: UserRole,
        input: UpdateListingInput,
        now: Instant = Instant.now(),
    ): UpdateResult = try {
        transaction(database) {
            val ownerId = listingRepository.findOwner(id) ?: return@transaction UpdateResult.NotFound.also {
                log.warn("Update listing not found id={}", id)
            }
            if (ownerId != requesterId && requesterRole != UserRole.ADMIN) {
                log.warn("Update listing forbidden id={} requesterId={}", id, requesterId)
                return@transaction UpdateResult.Forbidden
            }

            // 当前明细：已售出数量用于件数下界校验与售出 CAS 的期望值，类别用于书籍协调。
            val current = listingRepository.findDetail(id) ?: return@transaction UpdateResult.NotFound

            // 全字段校验：仅校验提供（非 null）的字段，与发布同规则。
            val vr = validator.validateUpdate(input.toUpdateValidation(), current.quantitySold, now)
            if (!vr.isValid) {
                log.warn("Update listing invalid id={} errors={}", id, vr.errors)
                return@transaction UpdateResult.ValidationFailed(vr.errors)
            }
            // 售出数量范围校验前置到任何写入之前：失败时尚未写入，直接返回不会提交半成品。
            // 上界取新件数（若本次同时改件数），与发布同规则。
            input.quantitySold?.let { newSold ->
                val total = input.quantityTotal ?: current.quantityTotal
                val r = validator.validateQuantitySoldUpdate(current.quantitySold, newSold, total)
                if (!r.isValid) return@transaction UpdateResult.ValidationFailed(r.errors)
            }

            // 标量字段（标题/描述/价格/取货地点/过期/类别/件数/联系方式）。
            if (hasFieldUpdates(input)) listingRepository.updateFields(id, input)
            // 标签整体替换（差异更新）。
            input.tags?.let { tagRepository.setTags(id, it) }
            // 图片整体替换。
            input.imageKeys?.let { listingRepository.replaceImages(id, it) }
            // 类别/书籍协调：书籍则写入书籍信息，非书籍则删除。
            input.category?.let { cat ->
                if (cat == ListingCategory.BOOK.ordinal) listingRepository.upsertBook(id, input.book)
                else listingRepository.deleteBook(id)
            }

            // 售出数量乐观锁更新：以读到的 current.quantitySold 为 CAS 期望值。须置于 quantityTotal
            // 写入之后（先增总量再增售出，满足 CHECK 约束）。affected==0 即「读-改-写」并发冲突，
            // 抛异常回滚整个事务（含上面已写入的字段），避免"报冲突却又提交了部分改动"。
            input.quantitySold?.let { newSold ->
                val affected = listingRepository.updateQuantitySold(id, current.quantitySold, newSold)
                if (affected == 0) throw QuantitySoldConflictException()
            }
            log.info("Listing updated id={} requesterId={}", id, requesterId)
            UpdateResult.Success
        }
    } catch (_: QuantitySoldConflictException) {
        log.warn("Update listing quantity conflict id={} requesterId={}", id, requesterId)
        UpdateResult.QuantityConflict
    }

    /** 软删除。仅本人或管理员。 */
    fun delete(id: Long, requesterId: Long, requesterRole: UserRole): UpdateResult =
        transaction(database) {
            val ownerId = listingRepository.findOwner(id) ?: return@transaction UpdateResult.NotFound.also {
                log.warn("Delete listing not found id={}", id)
            }
            if (ownerId != requesterId && requesterRole != UserRole.ADMIN) {
                log.warn("Delete listing forbidden id={} requesterId={}", id, requesterId)
                return@transaction UpdateResult.Forbidden
            }
            listingRepository.softDelete(id)
            log.info("Listing deleted id={} requesterId={}", id, requesterId)
            UpdateResult.Success
        }

    /**
     * 按 ISBN 查询书籍：先查本地缓存，命中直接返回；否则调用 ShowAPI 并回写缓存
     * （架构 §6.4）。网络/缓存操作分离，避免长事务包住外部调用。
     */
    suspend fun lookupBook(isbn: String): BookLookupResult {
        val cached = transaction(database) { bookMetaRepository.find(isbn) }
        if (cached != null) {
            log.debug("Book cache hit isbn={}", isbn)
            return BookLookupResult.Found(cached)
        }
        log.debug("Book cache miss isbn={}, fetching from ShowAPI", isbn)
        return when (val r = showApiClient.lookup(isbn)) {
            is IsbnLookupResult.Found -> {
                transaction(database) { bookMetaRepository.save(r.meta, r.rawJson) }
                log.info("Book fetched and cached isbn={} title={}", isbn, r.meta.title)
                BookLookupResult.Found(r.meta)
            }
            is IsbnLookupResult.NotFound -> {
                log.info("Book not found isbn={}", isbn)
                BookLookupResult.NotFound
            }
            is IsbnLookupResult.ServiceError -> {
                log.warn("Book lookup service error isbn={} msg={}", isbn, r.message)
                BookLookupResult.ServiceError(r.message)
            }
        }
    }

    private fun hasFieldUpdates(input: UpdateListingInput): Boolean =
        input.title != null || input.description != null || input.pickupLocation != null ||
            input.expiresAt != null || input.unitPrice != null || input.originalPrice != null || input.clearUnitPrice ||
            input.clearOriginalPrice ||
            input.category != null || input.quantityTotal != null || input.contacts != null

    private fun UpdateListingInput.toUpdateValidation() = ListingUpdateInput(
        title = title,
        quantityTotal = quantityTotal,
        unitPrice = unitPrice,
        originalPrice = originalPrice,
        expiresAt = expiresAt?.toInstant(),
        expiryIsAbsolute = expiryIsAbsolute,
        contacts = contacts,
        tags = tags,
    )

    /** 在事务内插入 listing 及其标签关联（标签去重 + 上限已由校验保证）。 */
    private fun insertWithTags(input: CreateListingInput): Long {
        val id = listingRepository.create(input)
        val tagIds = TagNormalizer.normalizeDistinct(input.tags).map { tagRepository.getOrCreate(it) }
        tagRepository.attach(id, tagIds)
        return id
    }

    private fun CreateListingInput.toValidationInput() = ListingInput(
        title = title,
        quantityTotal = quantityTotal,
        unitPrice = unitPrice,
        originalPrice = originalPrice,
        contacts = contacts,
        tags = tags,
        expiresAt = expiresAt.toInstant(),
        expiryIsAbsolute = expiryIsAbsolute,
    )
}

/**
 * 售出数量 CAS 命中 0 行（并发「读-改-写」冲突）时抛出，用于回滚整个事务并在 [ListingService.update]
 * 外层映射为 [UpdateResult.QuantityConflict]。仅作控制流之用，不对外暴露。
 */
private class QuantitySoldConflictException : RuntimeException()
