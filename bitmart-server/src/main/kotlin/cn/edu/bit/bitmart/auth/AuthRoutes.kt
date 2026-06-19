package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.domain.ValidationError
import cn.edu.bit.bitmart.shared.ApiError
import cn.edu.bit.bitmart.shared.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Bearer 鉴权方案名。 */
const val AUTH_BEARER = "bearer-token"

/** 安装基于不透明令牌的 Bearer 鉴权。 */
fun AuthenticationConfig.bitmartBearer(authenticator: TokenAuthenticator) {
    bearer(AUTH_BEARER) {
        authenticate { credential ->
            authenticator.authenticate(credential.token)
        }
    }
}

/** /auth 路由。需登录的端点（登出/全部登出/注销）置于 authenticate 块内。 */
fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/bit101/verify") {
            val req = call.receive<VerifyRequest>()
            when (val r = authService.verify(req.studentId, req.password)) {
                is VerifyResult.Success -> call.respond(VerifyResponse(r.verifyTicket))
                is VerifyResult.InvalidCredentials ->
                    call.fail(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "学号或统一身份认证密码错误")
                is VerifyResult.ServiceUnavailable ->
                    call.fail(HttpStatusCode.BadGateway, ErrorCode.EXTERNAL_SERVICE_ERROR, r.message)
            }
        }

        post("/register") {
            val req = call.receive<RegisterRequest>()
            when (val r = authService.register(
                req.verifyTicket, req.studentId, req.password, req.nickname, call.request.userAgent(),
            )) {
                is RegisterResult.Success -> call.respond(AuthResponse(r.token, UserDto.from(r.user)))
                is RegisterResult.InvalidTicket ->
                    call.fail(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "验证票无效或已过期，请重新验证身份")
                is RegisterResult.StudentAlreadyRegistered ->
                    call.fail(HttpStatusCode.Conflict, ErrorCode.CONFLICT, "该学号已注册")
                is RegisterResult.PasswordPolicyViolation ->
                    call.failValidation(r.errors)
            }
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            when (val r = authService.login(req.studentId, req.password, call.request.userAgent())) {
                is LoginResult.Success -> call.respond(AuthResponse(r.token, UserDto.from(r.user)))
                is LoginResult.InvalidCredentials ->
                    call.fail(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "学号或密码错误")
                is LoginResult.Banned ->
                    call.fail(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "账号已被封禁")
            }
        }

        post("/reset-password") {
            val req = call.receive<ResetPasswordRequest>()
            when (val r = authService.resetPassword(req.verifyTicket, req.studentId, req.newPassword)) {
                is ResetPasswordResult.Success -> call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                is ResetPasswordResult.InvalidTicket ->
                    call.fail(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "验证票无效或已过期")
                is ResetPasswordResult.UserNotFound ->
                    call.fail(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "该学号尚未注册")
                is ResetPasswordResult.PasswordPolicyViolation ->
                    call.failValidation(r.errors)
            }
        }

        authenticate(AUTH_BEARER) {
            delete("/session") {
                val principal = call.principal<UserPrincipal>()!!
                authService.logout(principal.token)
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            }
            delete("/sessions") {
                val principal = call.principal<UserPrincipal>()!!
                authService.logoutAll(principal.userId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            }
            delete("/account") {
                val principal = call.principal<UserPrincipal>()!!
                authService.deleteAccount(principal.userId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            }
        }
    }
}

/** 统一错误响应辅助。 */
internal suspend fun ApplicationCall.fail(status: HttpStatusCode, code: ErrorCode, message: String) {
    respond(status, ApiError.of(code, message))
}

/** 校验类错误：除通用 message 外，附带结构化 details 供客户端本地化。 */
internal suspend fun ApplicationCall.failValidation(errors: List<ValidationError>) {
    val details = errors.map { ApiError.ErrorDetail(it.field, it.code, it.params) }
    respond(
        HttpStatusCode.BadRequest,
        ApiError.of(ErrorCode.VALIDATION_FAILED, errors.joinToString("；") { it.message }, details),
    )
}
