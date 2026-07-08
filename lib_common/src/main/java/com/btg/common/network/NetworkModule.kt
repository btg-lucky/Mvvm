package com.btg.common.network

import android.content.Context
import com.btg.common.BuildConfig
import com.btg.common.base.BaseConstant
import com.btg.common.network.cookie.CookieManager
import com.btg.common.network.gson.GsonFactory
import com.btg.common.network.interceptor.HeaderInterceptor
import com.btg.common.network.interceptor.TokenInterceptor
import com.btg.common.network.interceptor.TokenProvider
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络依赖装配。替代旧的双重检查锁单例 ApiRetrofit。
 * 单 baseUrl 默认；如需多 baseUrl，用 @Qualifier 区分再各自 @Provides 一套 Retrofit。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonFactory.create()

    /** Token 来源骨架。TODO: 接真实来源（如从 DataStore 读），替换此默认实现。 */
    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = TokenProvider { null }

    @Provides
    @Singleton
    fun provideCookieJar(@ApplicationContext context: Context): CookieJar = CookieManager(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        tokenProvider: TokenProvider,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .dns(ApiDns())
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(HeaderInterceptor())
        .addInterceptor(TokenInterceptor(tokenProvider))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(BaseConstant.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
