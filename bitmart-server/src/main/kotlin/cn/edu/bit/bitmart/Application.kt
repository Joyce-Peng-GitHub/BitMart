package cn.edu.bit.bitmart

import cn.edu.bit.bitmart.shared.ApiError
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/** Ktor 入口：由 application.conf 的 EngineMain 装载。 */
fun main(args: Array<String>) = EngineMain.main(args)

/** 应用装配。各功能模块的路由将在此挂载。 */
fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
    }
}

@kotlinx.serialization.Serializable
data class HealthResponse(val status: String)
