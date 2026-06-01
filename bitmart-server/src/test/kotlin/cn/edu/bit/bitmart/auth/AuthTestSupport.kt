package cn.edu.bit.bitmart.auth

import cn.edu.bit.bitmart.AppComponents
import cn.edu.bit.bitmart.config.BitmartConfig
import cn.edu.bit.bitmart.db.EmbeddedPostgresSupport
import cn.edu.bit.bitmart.external.MockHttpSupport
import com.typesafe.config.ConfigFactory
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.config.HoconApplicationConfig

/**
 * 认证测试装配：内嵌 PostgreSQL + 可脚本化的 BIT101 Mock 客户端。
 * 使用较小的 argon2 参数加速测试。
 */
object AuthTestSupport {

    /** salt = Base64("0123456789abcdef")，与加密已知答案向量一致。 */
    private const val INIT_BODY =
        """{"salt":"MDEyMzQ1Njc4OWFiY2RlZg==","execution":"e1","cookie":"c1"}"""

    /** 测试用配置：覆盖 argon2 为小参数，其余取打包默认值。 */
    fun testConfig(): BitmartConfig {
        val hocon = """
            bitmart {
              database { url = "x", user = "u", password = "p", maxPoolSize = 4 }
              session { ttlDays = 30 }
              verifyTicket { ttlMinutes = 15 }
              password { minLength = 8, minCharClasses = 2 }
              argon2 { memoryKb = 1024, iterations = 1, parallelism = 1 }
              expiry { minDays = 1, maxDays = 365, defaultDays = 30 }
              tag { maxPerListing = 8, maxNameLength = 20 }
              pagination { defaultPageSize = 20, maxPageSize = 50 }
              upload { maxFileBytes = 5242880, maxFilesPerListing = 9, allowedMimeTypes = ["image/jpeg"] }
              storage { root = "./storage", publicBaseUrl = "/static" }
              bit101 { baseUrl = "https://bit101.test", requestTimeoutMs = 15000 }
              showapi { baseUrl = "https://showapi.test", appKey = "k", requestTimeoutMs = 15000 }
            }
        """.trimIndent()
        return BitmartConfig.from(HoconApplicationConfig(ConfigFactory.parseString(hocon)))
    }

    /**
     * 构建测试组件。
     * @param bit101Succeeds true → BIT101 校验通过；false → 凭据无效。
     */
    fun components(bit101Succeeds: Boolean = true): AppComponents {
        val verifyToken = if (bit101Succeeds) "tok" else ""
        val httpClient = MockHttpSupport.client { request ->
            when {
                request.url.encodedPath.endsWith("/webvpn_verify_init") ->
                    respond(INIT_BODY, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else ->
                    respond(
                        """{"token":"$verifyToken","code":"0","msg":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        }
        return AppComponents(testConfig(), EmbeddedPostgresSupport.db(), httpClient)
    }
}
