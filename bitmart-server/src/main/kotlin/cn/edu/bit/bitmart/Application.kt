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
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
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
}

/**
 * 实际装配逻辑。独立命名（非 module 重载），避免 Ktor 按名加载模块时
 * 误选带 AppComponents 参数的函数而触发参数注入失败。测试可直接调用并注入组件。
 */
fun Application.configureApp(components: AppComponents) {
    val log = LoggerFactory.getLogger("bitmart.Application")

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
                ApiError.of(ErrorCode.VALIDATION_FAILED, "请求体格式错误: ${cause.message}"),
            )
        }
        exception<Throwable> { call, cause ->
            log.error("未处理异常", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError.of(ErrorCode.INTERNAL_ERROR, "服务器内部错误"),
            )
        }
    }

    routing {
        get("/health") { call.respond(HealthResponse(status = "ok")) }
        authRoutes(components.authService)
        listingRoutes(components.listingService, components.listingRequestMapper, components.config.pagination)
        uploadRoutes(components.uploadService)
        meRoutes(components.userService, components.config.pagination.defaultPageSize, components.config.pagination.maxPageSize)
        // 静态文件：图片等 Blob 通过 /static/<key> 暴露（架构 §8）。
        staticFiles(components.config.storage.publicBaseUrl, java.io.File(components.config.storage.root))
    }
}

@Serializable
data class HealthResponse(val status: String)
