package com.btg.news.di

import com.btg.news.BuildConfig
import com.btg.news.data.repository.NewsRepository
import com.btg.news.data.source.NewsApi
import com.btg.news.data.source.NewsDataSource
import com.btg.news.data.source.RemoteNewsDataSource
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 聚合数据专用 Retrofit 限定符（baseUrl 与框架默认不同，演示多 baseUrl 模式）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JuheRetrofit

/**
 * 新闻数据装配。数据源唯一装配点：无 key 调试时把 RemoteNewsDataSource
 * 换回 FakeNewsDataSource()，上层不动。
 */
@Module
@InstallIn(SingletonComponent::class)
object NewsModule {

    private const val JUHE_BASE_URL = "https://v.juhe.cn/"

    @Provides
    @Singleton
    @JuheRetrofit
    fun provideJuheRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(JUHE_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideNewsApi(@JuheRetrofit retrofit: Retrofit): NewsApi =
        retrofit.create(NewsApi::class.java)

    @Provides
    @Singleton
    fun provideNewsDataSource(api: NewsApi): NewsDataSource =
        RemoteNewsDataSource(api, BuildConfig.JUHE_API_KEY)

    @Provides
    @Singleton
    fun provideNewsRepository(dataSource: NewsDataSource): NewsRepository =
        NewsRepository(dataSource)
}
