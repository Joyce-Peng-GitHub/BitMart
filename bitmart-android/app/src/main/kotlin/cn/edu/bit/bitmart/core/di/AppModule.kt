package cn.edu.bit.bitmart.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import cn.edu.bit.bitmart.BuildConfig
import cn.edu.bit.bitmart.core.data.local.DataStoreTokenStore
import cn.edu.bit.bitmart.core.data.local.TokenStore
import cn.edu.bit.bitmart.core.data.remote.BitMartApi
import cn.edu.bit.bitmart.core.data.repository.AuthRepositoryImpl
import cn.edu.bit.bitmart.core.data.repository.ListingRepositoryImpl
import cn.edu.bit.bitmart.core.data.repository.ProfileRepositoryImpl
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bitmart_prefs")

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
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
        }
    }

    @Provides
    @Singleton
    fun provideApi(client: HttpClient, tokenStore: TokenStore): BitMartApi =
        BitMartApi(client, BuildConfig.API_BASE_URL) { tokenStore.current() }
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
