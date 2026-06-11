package cn.edu.bit.bitmart.user

import cn.edu.bit.bitmart.auth.AUTH_BEARER
import cn.edu.bit.bitmart.auth.UserPrincipal
import cn.edu.bit.bitmart.auth.UserDto
import cn.edu.bit.bitmart.auth.fail
import cn.edu.bit.bitmart.shared.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class UpdateProfileRequest(val nickname: String? = null)

@Serializable
data class NotificationDto(
    val id: Long,
    val category: Int,
    val title: String,
    val body: String,
    val payload: String? = null,
    val read: Boolean,
    val createdAt: String,
    val isAnnouncement: Boolean,
)

@Serializable
data class NotificationPageDto(val items: List<NotificationDto>, val nextCursor: String? = null)

@Serializable
data class UnreadCountDto(val count: Long)

/** /me 路由（全部需登录）。 */
fun Route.meRoutes(userService: UserService, paginationDefault: Int, paginationMax: Int) {
    authenticate(AUTH_BEARER) {
        route("/me") {
            get {
                val principal = call.principal<UserPrincipal>()!!
                val user = userService.profile(principal.userId)
                    ?: return@get call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "用户不存在")
                call.respond(UserDto.from(user))
            }

            patch {
                val principal = call.principal<UserPrincipal>()!!
                val req = call.receive<UpdateProfileRequest>()
                val user = userService.updateNickname(principal.userId, req.nickname)
                    ?: return@patch call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "用户不存在")
                call.respond(UserDto.from(user))
            }

            get("/notifications") {
                val principal = call.principal<UserPrincipal>()!!
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: paginationDefault)
                    .coerceIn(1, paginationMax)
                val cursor = call.request.queryParameters["cursor"]?.let { parseNotifCursor(it) }
                val items = userService.notifications(principal.userId, cursor, limit)
                val next = if (items.size >= limit) items.lastOrNull()?.let { "${it.createdAt}|${it.id}" } else null
                call.respond(NotificationPageDto(items.map { it.toDto() }, next))
            }

            // 未读角标用。仅统计个人未读，公告不计入（公告不可标记已读）。
            get("/notifications/unread-count") {
                val principal = call.principal<UserPrincipal>()!!
                call.respond(UnreadCountDto(userService.unreadNotificationCount(principal.userId)))
            }

            post("/notifications/{id}/read") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.fail(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "id 非法")
                val ok = userService.markNotificationRead(principal.userId, id)
                if (ok) call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                else call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "通知不存在或不可标记")
            }
        }
    }
}

private fun NotificationItem.toDto() = NotificationDto(
    id = id, category = category, title = title, body = body, payload = payload,
    read = readAt != null, createdAt = createdAt.toString(), isAnnouncement = isAnnouncement,
)

private fun parseNotifCursor(raw: String): NotificationCursor? {
    val parts = raw.split("|")
    if (parts.size != 2) return null
    val t = runCatching { OffsetDateTime.parse(parts[0]) }.getOrNull() ?: return null
    val id = parts[1].toLongOrNull() ?: return null
    return NotificationCursor(t, id)
}
