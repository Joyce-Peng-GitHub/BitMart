package cn.edu.bit.bitmart.listing

import cn.edu.bit.bitmart.auth.UserPrincipal
import cn.edu.bit.bitmart.auth.fail
import cn.edu.bit.bitmart.auth.failValidation
import cn.edu.bit.bitmart.config.PaginationConfig
import cn.edu.bit.bitmart.domain.ListingCategory
import cn.edu.bit.bitmart.domain.ListingCursor
import cn.edu.bit.bitmart.domain.ListingFilter
import cn.edu.bit.bitmart.domain.ListingType
import cn.edu.bit.bitmart.domain.TagNormalizer
import cn.edu.bit.bitmart.domain.UserRole
import cn.edu.bit.bitmart.shared.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * /listings 与 /books、/tags 路由。
 * - GET /listings、GET /tags/popular：无需登录。
 * - GET /listings/{id}：未登录返回 401（需求"不对未登录客户端显示详情"）。
 * - 其余写操作需登录。
 */
fun Route.listingRoutes(
    service: ListingService,
    mapper: ListingRequestMapper,
    pagination: PaginationConfig,
) {
    route("/listings") {
        // 列表（公开）。过期项不公开展示：无论客户端传何值，公开列表强制不含已过期项。
        get {
            val parsed = call.parseListingFilter(pagination)
                ?: return@get call.fail(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "查询参数非法")
            val filter = parsed.copy(includeExpired = false)
            val items = service.list(filter)
            val next = if (items.size >= filter.limit) {
                items.lastOrNull()?.let { "${it.createdAt}|${it.id}" }
            } else null
            call.respond(ListingPageDto(items.map(ListingSummaryDto::from), next))
        }

        // 热门标签（公开）。
        // 注：放在 listing 资源之外更合适，这里同时提供 /tags/popular（见下）。

        authenticate(cn.edu.bit.bitmart.auth.AUTH_BEARER) {
            // 详情（需登录）。过期项仅发布者本人/管理员可见，他人按"未找到"处理（不泄露存在性）。
            get("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.pathId() ?: return@get call.fail(
                    HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "id 非法",
                )
                when (val r = service.detail(id)) {
                    is DetailResult.Found -> {
                        val d = r.detail
                        val isOwner = d.userId == principal.userId || principal.role == UserRole.ADMIN
                        val expired = !d.expiresAt.isAfter(OffsetDateTime.now())
                        if (expired && !isOwner) {
                            call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "未找到该条目")
                        } else {
                            call.respond(ListingDetailDto.from(d))
                        }
                    }
                    is DetailResult.NotFound -> call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "未找到该条目")
                }
            }

            // 发布单条。
            post {
                val principal = call.principal<UserPrincipal>()!!
                val req = call.receive<CreateListingRequest>()
                // 映射与校验共用同一时间基准，否则最小有效期会因时钟重取恒判过早。
                val now = OffsetDateTime.now()
                val input = try {
                    mapper.toCreateInput(req, principal.userId, now)
                } catch (e: RequestMappingException) {
                    return@post call.fail(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, e.message ?: "请求非法")
                }
                when (val r = service.publish(input, now.toInstant())) {
                    is PublishResult.Success -> call.respond(HttpStatusCode.Created, CreatedResponse(r.listingId))
                    is PublishResult.ValidationFailed -> call.failValidation(r.errors)
                }
            }

            // 批量发布（全成功或全回滚）。
            post("/batch") {
                val principal = call.principal<UserPrincipal>()!!
                val req = call.receive<BatchCreateRequest>()
                val now = OffsetDateTime.now()
                val inputs = try {
                    req.items.map { mapper.toCreateInput(it, principal.userId, now) }
                } catch (e: RequestMappingException) {
                    return@post call.fail(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, e.message ?: "请求非法")
                }
                when (val r = service.publishBatch(inputs, now.toInstant())) {
                    is BatchPublishResult.Success -> call.respond(HttpStatusCode.Created, BatchCreatedResponse(r.listingIds))
                    is BatchPublishResult.ValidationFailed -> {
                        val flat = r.errorsByIndex.flatMap { (i, errs) -> errs.map { it.copy(field = "items[$i].${it.field}") } }
                        call.failValidation(flat)
                    }
                }
            }

            // 修改。
            patch("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.pathId() ?: return@patch call.fail(
                    HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "id 非法",
                )
                val req = call.receive<UpdateListingRequest>()
                val now = OffsetDateTime.now()
                val input = mapper.toUpdateInput(req, now)
                when (val r = service.update(id, principal.userId, principal.role, input, now.toInstant())) {
                    is UpdateResult.Success -> call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                    is UpdateResult.NotFound -> call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "未找到该条目")
                    is UpdateResult.Forbidden -> call.fail(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "无权修改")
                    is UpdateResult.QuantityConflict -> call.fail(HttpStatusCode.Conflict, ErrorCode.CONFLICT, "售出数量冲突，请刷新后重试")
                    is UpdateResult.ValidationFailed -> call.failValidation(r.errors)
                }
            }

            // 软删除。
            delete("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.pathId() ?: return@delete call.fail(
                    HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "id 非法",
                )
                when (val r = service.delete(id, principal.userId, principal.role)) {
                    is UpdateResult.Success -> call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                    is UpdateResult.NotFound -> call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "未找到该条目")
                    is UpdateResult.Forbidden -> call.fail(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "无权删除")
                    else -> call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                }
            }
        }
    }

    // 我的列表（架构 §6.2）：当前用户自己发布的项，含已售罄/已过期，供"我的商品/我的收购"管理。
    authenticate(cn.edu.bit.bitmart.auth.AUTH_BEARER) {
        get("/me/listings") {
            val principal = call.principal<UserPrincipal>()!!
            // 我的列表默认含已售罄/已过期（供管理）；客户端可经 includeSold/includeExpired 收窄。
            val filter = call.parseListingFilter(
                pagination,
                defaultType = null,
                defaultIncludeSold = true,
                defaultIncludeExpired = true,
            ) ?: return@get call.fail(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "查询参数非法")
            val items = service.myListings(principal.userId, filter)
            val next = if (items.size >= filter.limit) {
                items.lastOrNull()?.let { "${it.createdAt}|${it.id}" }
            } else null
            call.respond(ListingPageDto(items.map(ListingSummaryDto::from), next))
        }
    }

    // 热门标签（公开）。
    get("/tags/popular") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        call.respond(PopularTagsDto(service.popularTags(limit).map { TagDto(it.id, it.name) }))
    }

    // ISBN 查询（需登录，服务端代理 + 缓存）。
    authenticate(cn.edu.bit.bitmart.auth.AUTH_BEARER) {
        post("/books/lookup") {
            val req = call.receive<BookLookupRequest>()
            when (val r = service.lookupBook(req.isbn)) {
                is BookLookupResult.Found -> call.respond(
                    BookMetaDto(
                        isbn = r.meta.isbn, title = r.meta.title, author = r.meta.author,
                        publisher = r.meta.publisher, edition = r.meta.edition, pubdate = r.meta.pubdate,
                        price = r.meta.price, page = r.meta.page, binding = r.meta.binding,
                        format = r.meta.format, img = r.meta.img, summary = r.meta.summary,
                    ),
                )
                is BookLookupResult.NotFound -> call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "未找到该 ISBN 的书籍信息")
                is BookLookupResult.ServiceError -> call.fail(HttpStatusCode.BadGateway, ErrorCode.EXTERNAL_SERVICE_ERROR, r.message)
            }
        }
    }
}

private fun ApplicationCall.pathId(): Long? = parameters["id"]?.toLongOrNull()

/**
 * 从查询参数构造列表过滤条件；非法返回 null。
 * @param defaultType type 参数缺省时的回退值；公开列表回退 SELL，我的列表传 null（不限买卖）。
 */
private fun ApplicationCall.parseListingFilter(
    pagination: PaginationConfig,
    defaultType: ListingType? = ListingType.SELL,
    defaultIncludeSold: Boolean = false,
    defaultIncludeExpired: Boolean = false,
): ListingFilter? {
    val q = request.queryParameters
    val type = q["type"]?.let { raw -> ListingType.entries.firstOrNull { it.name.equals(raw, true) } } ?: defaultType
    val category = q["category"]?.let { raw -> ListingCategory.entries.firstOrNull { it.name.equals(raw, true) } }
    val tagNames = q["tags"]?.split(",")?.let { TagNormalizer.normalizeDistinct(it) } ?: emptyList()
    val minPrice = q["minPrice"]?.toBigDecimalOrNull()
    val maxPrice = q["maxPrice"]?.toBigDecimalOrNull()
    val includeNoPrice = q["includeNoPrice"]?.toBooleanStrictOrNull() ?: true
    val includeSold = q["includeSold"]?.toBooleanStrictOrNull() ?: defaultIncludeSold
    val includeExpired = q["includeExpired"]?.toBooleanStrictOrNull() ?: defaultIncludeExpired
    val limit = (q["limit"]?.toIntOrNull() ?: pagination.defaultPageSize).coerceIn(1, pagination.maxPageSize)
    val cursor = q["cursor"]?.let { parseCursor(it) ?: return null }
    return ListingFilter(
        type = type, category = category, query = q["q"], tagNames = tagNames,
        minPrice = minPrice, maxPrice = maxPrice, includeNoPrice = includeNoPrice,
        includeSold = includeSold, includeExpired = includeExpired, cursor = cursor, limit = limit,
    )
}

/** 游标编码为 "createdAtISO|id"。 */
private fun parseCursor(raw: String): ListingCursor? {
    val parts = raw.split("|")
    if (parts.size != 2) return null
    val t = runCatching { OffsetDateTime.parse(parts[0]) }.getOrNull() ?: return null
    val id = parts[1].toLongOrNull() ?: return null
    return ListingCursor(t, id)
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
