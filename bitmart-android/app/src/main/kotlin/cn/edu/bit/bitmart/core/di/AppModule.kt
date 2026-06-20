package cn.edu.bit.bitmart.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import cn.edu.bit.bitmart.BuildConfig
import cn.edu.bit.bitmart.core.data.local.ContactPrefsStore
import cn.edu.bit.bitmart.core.data.local.DataStoreContactPrefsStore
import cn.edu.bit.bitmart.core.data.local.DataStoreLanguagePrefsStore
import cn.edu.bit.bitmart.core.data.local.DataStoreLlmConfigStore
import cn.edu.bit.bitmart.core.data.local.DataStoreThemePrefsStore
import cn.edu.bit.bitmart.core.data.local.DataStoreTokenStore
import cn.edu.bit.bitmart.core.data.local.LanguagePrefsStore
import cn.edu.bit.bitmart.core.data.local.LlmConfigStore
import cn.edu.bit.bitmart.core.data.local.ThemePrefsStore
import cn.edu.bit.bitmart.core.data.local.TokenStore
import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.data.repository.AuthRepositoryImpl
import cn.edu.bit.bitmart.core.data.repository.ListingRepositoryImpl
import cn.edu.bit.bitmart.core.data.repository.ProfileRepositoryImpl
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.llm.LlmClient
import cn.edu.bit.bitmart.llm.OpenAiCompatibleLlmClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bitmart_prefs")

/** 主后端 API 客户端的整请求超时（毫秒）。上游卡住时避免协程无限挂起（审查项 H4）。 */
private const val BACKEND_REQUEST_TIMEOUT_MS = 60_000L

/** 提供网络、存储、仓储等单例依赖。 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideTokenStore(dataStore: DataStore<Preferences>): TokenStore = DataStoreTokenStore(dataStore)

    @Provides
    @Singleton
    fun provideContactPrefsStore(dataStore: DataStore<Preferences>): ContactPrefsStore =
        DataStoreContactPrefsStore(dataStore)

    @Provides
    @Singleton
    fun provideLlmConfigStore(dataStore: DataStore<Preferences>): LlmConfigStore =
        DataStoreLlmConfigStore(dataStore)

    @Provides
    @Singleton
    fun provideThemePrefsStore(dataStore: DataStore<Preferences>): ThemePrefsStore =
        DataStoreThemePrefsStore(dataStore)

    @Provides
    @Singleton
    fun provideLanguagePrefsStore(dataStore: DataStore<Preferences>): LanguagePrefsStore =
        DataStoreLanguagePrefsStore(dataStore)

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = BACKEND_REQUEST_TIMEOUT_MS }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) { android.util.Log.d("BitMartHttp", message) }
            }
            level = LogLevel.INFO
        }
    }

    /**
     * LLM 直连专用 HttpClient：安装 HttpTimeout 以便按用户配置的超时阈值逐次设定请求超时。
     * 与后端 API 客户端区分，避免影响其默认超时行为。
     */
    @Provides
    @Singleton
    @Named("llm")
    fun provideLlmHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout)
    }

    @Provides
    @Singleton
    fun provideLlmClient(@Named("llm") client: HttpClient): LlmClient =
        OpenAiCompatibleLlmClient(client)

    @Provides
    @Singleton
    fun provideApi(client: HttpClient, tokenStore: TokenStore): BitMartApi =
        BitMartApi(client, BuildConfig.API_BASE_URL, { tokenStore.current() }, tokenStore)
}

/** 绑定仓储接口到实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindListingRepository(impl: ListingRepositoryImpl): ListingRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}
