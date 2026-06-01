package cn.edu.bit.bitmart

import cn.edu.bit.bitmart.auth.AuthService
import cn.edu.bit.bitmart.auth.Bit101PasswordCipher
import cn.edu.bit.bitmart.auth.PasswordHasher
import cn.edu.bit.bitmart.auth.SessionRepository
import cn.edu.bit.bitmart.auth.TokenAuthenticator
import cn.edu.bit.bitmart.auth.VerifyTicketStore
import cn.edu.bit.bitmart.auth.authRoutes
import cn.edu.bit.bitmart.auth.bitmartBearer
import cn.edu.bit.bitmart.config.BitmartConfig
import cn.edu.bit.bitmart.domain.PasswordPolicy
import cn.edu.bit.bitmart.external.Bit101Client
import cn.edu.bit.bitmart.external.ShowApiClient
import cn.edu.bit.bitmart.user.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json as clientJson
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * 应用依赖容器。集中装配各组件，便于在测试中替换（如内嵌数据库、Mock HttpClient）。
 */
class AppComponents(
    val config: BitmartConfig,
    val database: Database,
    bit101HttpClient: HttpClient,
) {
    val userRepository = UserRepository()
    val sessionRepository = SessionRepository()
    val verifyTicketStore = VerifyTicketStore(config.verifyTicket.ttlMinutes)
    val passwordHasher = PasswordHasher(
        memoryKb = config.argon2.memoryKb,
        iterations = config.argon2.iterations,
        parallelism = config.argon2.parallelism,
    )
    val passwordPolicy = PasswordPolicy(config.password)
    val bit101Client = Bit101Client(bit101HttpClient, config.bit101.baseUrl)

    val authService = AuthService(
        database = database,
        userRepository = userRepository,
        sessionRepository = sessionRepository,
        bit101Client = bit101Client,
        verifyTicketStore = verifyTicketStore,
        passwordHasher = passwordHasher,
        passwordPolicy = passwordPolicy,
        sessionConfig = config.session,
    )

    val tokenAuthenticator = TokenAuthenticator(database, sessionRepository, userRepository)

    companion object {
        /** 生产装配：建库、迁移、连接，并创建直连外部服务的 HTTP 客户端。 */
        fun production(config: BitmartConfig): AppComponents {
            val (_, db) = cn.edu.bit.bitmart.db.DatabaseFactory.init(config.database)
            val httpClient = HttpClient(CIO) {
                install(ClientContentNegotiation) { clientJson() }
            }
            return AppComponents(config, db, httpClient)
        }
    }
}
