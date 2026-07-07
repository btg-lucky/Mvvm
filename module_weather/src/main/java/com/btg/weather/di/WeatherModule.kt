package com.btg.weather.di

import android.content.Context
import com.btg.common.storage.PreferenceStore
import com.btg.weather.BuildConfig
import com.btg.weather.data.CityStore
import com.btg.weather.data.DataStoreCityStore
import com.btg.weather.data.repository.WeatherRepository
import com.btg.weather.data.source.RemoteWeatherDataSource
import com.btg.weather.data.source.WeatherApi
import com.btg.weather.data.source.WeatherDataSource
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 天气聚合数据专用 Retrofit 限定符（baseUrl apis.juhe.cn，与 news 的 v.juhe.cn 不同）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WeatherJuheRetrofit

/**
 * 天气数据装配。数据源唯一装配点：无 key 调试时把 RemoteWeatherDataSource
 * 换回 FakeWeatherDataSource()，上层不动。
 */
@Module
@InstallIn(SingletonComponent::class)
object WeatherModule {

    private const val WEATHER_BASE_URL = "https://apis.juhe.cn/"

    @Provides
    @Singleton
    @WeatherJuheRetrofit
    fun provideWeatherRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(WEATHER_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideWeatherApi(@WeatherJuheRetrofit retrofit: Retrofit): WeatherApi =
        retrofit.create(WeatherApi::class.java)

    @Provides
    @Singleton
    fun provideWeatherDataSource(api: WeatherApi): WeatherDataSource =
        RemoteWeatherDataSource(api, BuildConfig.JUHE_API_KEY)

    @Provides
    @Singleton
    fun provideWeatherRepository(dataSource: WeatherDataSource): WeatherRepository =
        WeatherRepository(dataSource)

    /** DataStore 同名文件只能有一个实例，必须单例提供。 */
    @Provides
    @Singleton
    fun provideCityStore(@ApplicationContext context: Context): CityStore =
        DataStoreCityStore(PreferenceStore(context, "weather_prefs"))
}
