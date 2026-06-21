package cn.edu.bit.bitmart

import cn.edu.bit.bitmart.auth.authRoutes
import cn.edu.bit.bitmart.auth.bitmartBearer
import cn.edu.bit.bitmart.config.BitmartConfig
import cn.edu.bit.bitmart.listing.listingRoutes
import cn.edu.bit.bitmart.storage.uploadRoutes
import cn.edu.bit.bitmart.user.meRoutes
import cn.edu.bit.bitmart.shared.ApiError
import cn.edu.bit.bitmart.shared.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.http.content.staticFiles
import org.slf4j.event.Level
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** Ktor 入口：由 application.conf 的 EngineMain 装载。 */
fun main(args: Array<String>) = EngineMain.main(args)

/** 生产入口：从配置装配组件后安装模块。由 application.conf 按名引用。 */
fun Application.module() {
    val config = BitmartConfig.from(environment.config)
    val components = AppComponents.production(config)
    configureApp(components)
    // 后台定时任务仅在生产入口启动；测试经 configureApp 装配，不启动循环。
    // Application 即 CoroutineScope，停机时任务随之取消。
    components.expiryWarningJob.start(this)
}

/**
 * 实际装配逻辑。独立命名（非 module 重载），避免 Ktor 按名加载模块时
 * 误选带 AppComponents 参数的函数而触发参数注入失败。测试可直接调用并注入组件。
 */
fun Application.configureApp(components: AppComponents) {
    val log = LoggerFactory.getLogger("bitmart.Application")

    install(CallId) {
        generate { it.request.headers["X-Request-ID"] ?: java.util.UUID.randomUUID().toString() }
        replyToHeader("X-Request-ID")
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
        format { call ->
            val status = call.response.status()?.value ?: 0
            "${call.request.httpMethod.value} ${call.request.path()} -> $status"
        }
        filter { call -> call.request.path() != "/health" }
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }

    install(Authentication) {
        bitmartBearer(components.tokenAuthenticator)
    }

    install(StatusPages) {
        // 请求体解析失败（缺字段/类型不符）→ 400。
        exception<ContentTransformationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError.of(ErrorCode.VALIDATION_FAILED, "Malformed request body: ${cause.message}"),
            )
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError.of(ErrorCode.INTERNAL_ERROR, "Internal server error"),
            )
        }
    }

    routing {
        // 健康检查不带版本前缀，便于探活。
        get("/health") { call.respond(HealthResponse(status = "ok")) }

        // 全部业务 API 统一挂在 /api/v1 前缀下（架构 §6），与客户端约定一致。
        route(API_PREFIX) {
            authRoutes(components.authService)
            listingRoutes(components.listingService, components.listingRequestMapper, components.config.pagination)
            uploadRoutes(components.uploadService)
            meRoutes(components.userService, components.config.pagination.defaultPageSize, components.config.pagination.maxPageSize)
        }

        // 静态文件：图片等 Blob 通过 publicBaseUrl（默认 /static）暴露（架构 §8）。
        staticFiles(components.config.storage.publicBaseUrl, java.io.File(components.config.storage.root))
    }
}

/** API 统一前缀（架构 §6）。客户端与服务端共同遵守。 */
const val API_PREFIX = "/api/v1"

@Serializable
data class HealthResponse(val status: String)
