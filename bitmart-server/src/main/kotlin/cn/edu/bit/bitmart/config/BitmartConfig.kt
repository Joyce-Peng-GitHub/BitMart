package cn.edu.bit.bitmart.config

import io.ktor.server.config.ApplicationConfig

/**
 * 服务端类型化配置。从 HOCON（application.conf）解析并在加载期校验，集中所有业务阈值。
 * 环境变量覆盖由 HOCON 的 ${?ENV} 占位在解析前完成，本层只读取最终值。
 */
data class BitmartConfig(
    val database: DatabaseConfig,
    val session: SessionConfig,
    val verifyTicket: VerifyTicketConfig,
    val password: PasswordPolicyConfig,
    val argon2: Argon2Config,
    val expiry: ExpiryConfig,
    val tag: TagConfig,
    val pagination: PaginationConfig,
    val upload: UploadConfig,
    val storage: StorageConfig,
    val notification: NotificationConfig,
    val bit101: Bit101Config,
    val showapi: ShowApiConfig,
) {
    companion object {
        /** 从 Ktor ApplicationConfig 的 `bitmart` 子树构建并校验。 */
        fun from(root: ApplicationConfig): BitmartConfig = try {
            val c = root.config("bitmart")
            BitmartConfig(
                database = DatabaseConfig.from(c.config("database")),
                session = SessionConfig.from(c.config("session")),
                verifyTicket = VerifyTicketConfig.from(c.config("verifyTicket")),
                password = PasswordPolicyConfig.from(c.config("password")),
                argon2 = Argon2Config.from(c.config("argon2")),
                expiry = ExpiryConfig.from(c.config("expiry")),
                tag = TagConfig.from(c.config("tag")),
                pagination = PaginationConfig.from(c.config("pagination")),
                upload = UploadConfig.from(c.config("upload")),
                storage = StorageConfig.from(c.config("storage")),
                notification = NotificationConfig.from(c.config("notification")),
                bit101 = Bit101Config.from(c.config("bit101")),
                showapi = ShowApiConfig.from(c.config("showapi")),
            )
        } catch (e: com.typesafe.config.ConfigException) {
            // 将底层 HOCON 解析/缺失错误统一为本项目的 ConfigException，便于调用方一致处理。
            throw ConfigException("Config load failed: ${e.message}")
        }
    }
}
