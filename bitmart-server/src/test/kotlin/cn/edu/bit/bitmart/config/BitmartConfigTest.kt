package cn.edu.bit.bitmart.config

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig

class BitmartConfigTest : FunSpec({

    fun configFrom(hocon: String): ApplicationConfig =
        HoconApplicationConfig(ConfigFactory.parseString(hocon))

    test("加载打包的 application.conf 成功并得到预期默认值") {
        // 解析 classpath 中的 application.conf（含 reference.conf 合并）。
        val cfg = BitmartConfig.from(HoconApplicationConfig(ConfigFactory.load()))
        cfg.expiry.minDays shouldBe 1
        cfg.expiry.maxDays shouldBe 365
        cfg.expiry.defaultDays shouldBe 30
        cfg.password.minLength shouldBe 8
        cfg.tag.maxPerListing shouldBe 8
        cfg.session.ttlDays shouldBe 30
        cfg.bit101.baseUrl shouldBe "https://bit101.flwfdd.xyz"
        cfg.showapi.baseUrl shouldBe "https://route.showapi.com"
        cfg.bit101.requestTimeoutMs shouldBe 60000
        cfg.showapi.requestTimeoutMs shouldBe 60000
    }

    test("expiry.defaultDays 超出 [min,max] 时拒绝加载") {
        val hocon = baseConfig(expiryDefault = 500)
        shouldThrow<ConfigException> { BitmartConfig.from(configFrom(hocon)) }
    }

    test("expiry.maxDays < minDays 时拒绝加载") {
        val hocon = baseConfig(expiryMin = 10, expiryMax = 5, expiryDefault = 5)
        shouldThrow<ConfigException> { BitmartConfig.from(configFrom(hocon)) }
    }

    test("pagination.maxPageSize < defaultPageSize 时拒绝加载") {
        val hocon = baseConfig(pageDefault = 50, pageMax = 20)
        shouldThrow<ConfigException> { BitmartConfig.from(configFrom(hocon)) }
    }

    test("password.minCharClasses 超出 1..4 时拒绝加载") {
        val hocon = baseConfig(minCharClasses = 5)
        shouldThrow<ConfigException> { BitmartConfig.from(configFrom(hocon)) }
    }

    test("缺失必填项时抛出 ConfigException") {
        val hocon = """
            bitmart {
              database { url = "x", user = "u", password = "p", maxPoolSize = 10 }
            }
        """.trimIndent()
        shouldThrow<ConfigException> { BitmartConfig.from(configFrom(hocon)) }
    }
})

/** 构造一份默认合法的完整配置，按需覆盖个别字段以触发校验失败。 */
private fun baseConfig(
    expiryMin: Int = 1,
    expiryMax: Int = 365,
    expiryDefault: Int = 30,
    pageDefault: Int = 20,
    pageMax: Int = 50,
    minCharClasses: Int = 2,
): String = """
    bitmart {
      database { url = "jdbc:postgresql://localhost/db", user = "u", password = "p", maxPoolSize = 10 }
      session { ttlDays = 30 }
      verifyTicket { ttlMinutes = 15 }
      password { minLength = 8, minCharClasses = $minCharClasses }
      argon2 { memoryKb = 65536, iterations = 3, parallelism = 1 }
      expiry { minDays = $expiryMin, maxDays = $expiryMax, defaultDays = $expiryDefault }
      tag { maxPerListing = 8, maxNameLength = 20 }
      pagination { defaultPageSize = $pageDefault, maxPageSize = $pageMax }
      upload { maxFileBytes = 5242880, maxFilesPerListing = 9, allowedMimeTypes = ["image/jpeg"] }
      storage { root = "./storage", publicBaseUrl = "/static" }
      bit101 { baseUrl = "https://bit101.flwfdd.xyz", requestTimeoutMs = 15000 }
      showapi { baseUrl = "https://route.showapi.com", appKey = "", requestTimeoutMs = 15000 }
    }
""".trimIndent()
