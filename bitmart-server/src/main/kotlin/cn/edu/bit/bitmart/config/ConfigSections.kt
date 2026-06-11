package cn.edu.bit.bitmart.config

import cn.edu.bit.bitmart.config.ConfigReader.int
import cn.edu.bit.bitmart.config.ConfigReader.long
import cn.edu.bit.bitmart.config.ConfigReader.require
import cn.edu.bit.bitmart.config.ConfigReader.string
import cn.edu.bit.bitmart.config.ConfigReader.stringList
import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
) {
    companion object {
        fun from(c: ApplicationConfig) = DatabaseConfig(
            url = c.string("url"),
            user = c.string("user"),
            password = c.string("password"),
            maxPoolSize = c.int("maxPoolSize"),
        ).also {
            require(it.maxPoolSize >= 1) { "database.maxPoolSize 必须 >= 1" }
        }
    }
}

data class SessionConfig(val ttlDays: Long) {
    companion object {
        fun from(c: ApplicationConfig) = SessionConfig(ttlDays = c.long("ttlDays"))
            .also { require(it.ttlDays >= 1) { "session.ttlDays 必须 >= 1" } }
    }
}

data class VerifyTicketConfig(val ttlMinutes: Long) {
    companion object {
        fun from(c: ApplicationConfig) = VerifyTicketConfig(ttlMinutes = c.long("ttlMinutes"))
            .also { require(it.ttlMinutes >= 1) { "verifyTicket.ttlMinutes 必须 >= 1" } }
    }
}

data class PasswordPolicyConfig(val minLength: Int, val minCharClasses: Int) {
    companion object {
        fun from(c: ApplicationConfig) = PasswordPolicyConfig(
            minLength = c.int("minLength"),
            minCharClasses = c.int("minCharClasses"),
        ).also {
            require(it.minLength >= 1) { "password.minLength 必须 >= 1" }
            require(it.minCharClasses in 1..4) { "password.minCharClasses 必须在 1..4" }
        }
    }
}

data class Argon2Config(val memoryKb: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        fun from(c: ApplicationConfig) = Argon2Config(
            memoryKb = c.int("memoryKb"),
            iterations = c.int("iterations"),
            parallelism = c.int("parallelism"),
        ).also {
            require(it.memoryKb >= 1024) { "argon2.memoryKb 过小（建议 >= 1024）" }
            require(it.iterations >= 1) { "argon2.iterations 必须 >= 1" }
            require(it.parallelism >= 1) { "argon2.parallelism 必须 >= 1" }
        }
    }
}

data class ExpiryConfig(val minDays: Int, val maxDays: Int, val defaultDays: Int) {
    companion object {
        fun from(c: ApplicationConfig) = ExpiryConfig(
            minDays = c.int("minDays"),
            maxDays = c.int("maxDays"),
            defaultDays = c.int("defaultDays"),
        ).also {
            require(it.minDays >= 1) { "expiry.minDays 必须 >= 1" }
            require(it.maxDays >= it.minDays) { "expiry.maxDays 必须 >= minDays" }
            require(it.defaultDays in it.minDays..it.maxDays) {
                "expiry.defaultDays 必须落在 [minDays, maxDays] 内"
            }
        }
    }
}

data class TagConfig(val maxPerListing: Int, val maxNameLength: Int) {
    companion object {
        fun from(c: ApplicationConfig) = TagConfig(
            maxPerListing = c.int("maxPerListing"),
            maxNameLength = c.int("maxNameLength"),
        ).also {
            require(it.maxPerListing >= 1) { "tag.maxPerListing 必须 >= 1" }
            require(it.maxNameLength >= 1) { "tag.maxNameLength 必须 >= 1" }
        }
    }
}

data class PaginationConfig(val defaultPageSize: Int, val maxPageSize: Int) {
    companion object {
        fun from(c: ApplicationConfig) = PaginationConfig(
            defaultPageSize = c.int("defaultPageSize"),
            maxPageSize = c.int("maxPageSize"),
        ).also {
            require(it.defaultPageSize >= 1) { "pagination.defaultPageSize 必须 >= 1" }
            require(it.maxPageSize >= it.defaultPageSize) {
                "pagination.maxPageSize 必须 >= defaultPageSize"
            }
        }
    }
}

data class UploadConfig(
    val maxFileBytes: Long,
    val maxFilesPerListing: Int,
    val allowedMimeTypes: List<String>,
) {
    companion object {
        fun from(c: ApplicationConfig) = UploadConfig(
            maxFileBytes = c.long("maxFileBytes"),
            maxFilesPerListing = c.int("maxFilesPerListing"),
            allowedMimeTypes = c.stringList("allowedMimeTypes"),
        ).also {
            require(it.maxFileBytes >= 1) { "upload.maxFileBytes 必须 >= 1" }
            require(it.maxFilesPerListing >= 1) { "upload.maxFilesPerListing 必须 >= 1" }
            require(it.allowedMimeTypes.isNotEmpty()) { "upload.allowedMimeTypes 不能为空" }
        }
    }
}

/** 应用内通知 / 过期提醒定时任务（架构 §9：每小时检查 24h 内到期）。 */
data class NotificationConfig(val expiryWarnWindowHours: Long, val expiryWarnIntervalMinutes: Long) {
    companion object {
        fun from(c: ApplicationConfig) = NotificationConfig(
            expiryWarnWindowHours = c.long("expiryWarnWindowHours"),
            expiryWarnIntervalMinutes = c.long("expiryWarnIntervalMinutes"),
        ).also {
            require(it.expiryWarnWindowHours >= 1) { "notification.expiryWarnWindowHours 必须 >= 1" }
            require(it.expiryWarnIntervalMinutes >= 1) { "notification.expiryWarnIntervalMinutes 必须 >= 1" }
        }
    }
}

data class StorageConfig(val root: String, val publicBaseUrl: String) {
    companion object {
        fun from(c: ApplicationConfig) = StorageConfig(
            root = c.string("root"),
            publicBaseUrl = c.string("publicBaseUrl"),
        )
    }
}

data class Bit101Config(val baseUrl: String, val requestTimeoutMs: Long) {
    companion object {
        fun from(c: ApplicationConfig) = Bit101Config(
            baseUrl = c.string("baseUrl"),
            requestTimeoutMs = c.long("requestTimeoutMs"),
        ).also { require(it.requestTimeoutMs >= 1) { "bit101.requestTimeoutMs 必须 >= 1" } }
    }
}

data class ShowApiConfig(val baseUrl: String, val appKey: String, val requestTimeoutMs: Long) {
    companion object {
        fun from(c: ApplicationConfig) = ShowApiConfig(
            baseUrl = c.string("baseUrl"),
            appKey = c.string("appKey"),
            requestTimeoutMs = c.long("requestTimeoutMs"),
        ).also { require(it.requestTimeoutMs >= 1) { "showapi.requestTimeoutMs 必须 >= 1" } }
    }
}
