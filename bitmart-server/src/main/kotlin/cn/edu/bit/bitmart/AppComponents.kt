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
import cn.edu.bit.bitmart.domain.ListingValidator
import cn.edu.bit.bitmart.domain.PasswordPolicy
import cn.edu.bit.bitmart.external.Bit101Client
import cn.edu.bit.bitmart.external.ShowApiClient
import cn.edu.bit.bitmart.listing.BookMetaRepository
import cn.edu.bit.bitmart.listing.ListingRepository
import cn.edu.bit.bitmart.listing.ListingRequestMapper
import cn.edu.bit.bitmart.listing.ListingService
import cn.edu.bit.bitmart.listing.TagRepository
import cn.edu.bit.bitmart.storage.BlobStorage
import cn.edu.bit.bitmart.storage.LocalDiskBlobStorage
import cn.edu.bit.bitmart.storage.UploadService
import cn.edu.bit.bitmart.user.NotificationRepository
import cn.edu.bit.bitmart.user.UserRepository
import cn.edu.bit.bitmart.user.UserService
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
    showApiHttpClient: HttpClient = bit101HttpClient,
) {
    val userRepository = UserRepository()
    val sessionRepository = SessionRepository()
    val notificationRepository = NotificationRepository()
    val verifyTicketStore = VerifyTicketStore(config.verifyTicket.ttlMinutes)
    val passwordHasher = PasswordHasher(
        memoryKb = config.argon2.memoryKb,
        iterations = config.argon2.iterations,
        parallelism = config.argon2.parallelism,
    )
    val passwordPolicy = PasswordPolicy(config.password)
    val bit101Client = Bit101Client(bit101HttpClient, config.bit101.baseUrl)
    val showApiClient = ShowApiClient(showApiHttpClient, config.showapi.baseUrl, config.showapi.appKey)

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

    // 列表（卖品/求购）相关组件。
    val listingRepository = ListingRepository()
    val tagRepository = TagRepository()
    val bookMetaRepository = BookMetaRepository()
    val listingValidator = ListingValidator(config.expiry, config.tag)
    val listingService = ListingService(
        database = database,
        listingRepository = listingRepository,
        tagRepository = tagRepository,
        bookMetaRepository = bookMetaRepository,
        showApiClient = showApiClient,
        validator = listingValidator,
        tagConfig = config.tag,
    )
    val listingRequestMapper = ListingRequestMapper(config.expiry)

    // 图片存储与上传。
    val blobStorage: BlobStorage = LocalDiskBlobStorage(config.storage.root, config.storage.publicBaseUrl)
    val uploadService = UploadService(blobStorage, config.upload)

    // 用户资料与通知。
    val userService = UserService(database, userRepository, notificationRepository)

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
