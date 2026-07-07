package com.btg.weather.data.repository

import com.btg.common.network.safeApiCall
import com.btg.common.result.ApiResult
import com.btg.weather.data.model.WeatherOverview
import com.btg.weather.data.source.WeatherDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 天气数据唯一入口。query（实况+预报）为必需，life（生活指数）为次要：
 * life 失败则吞成空列表，不阻塞主天气展示。
 */
class WeatherRepository(
    private val dataSource: WeatherDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getWeatherOverview(city: String): ApiResult<WeatherOverview> =
        withContext(ioDispatcher) {
            safeApiCall {
                val weather = dataSource.fetchWeather(city)
                val life = try {
                    dataSource.fetchLife(city)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                WeatherOverview(
                    city = weather.city,
                    realtime = weather.realtime,
                    future = weather.future,
                    life = life,
                )
            }
        }
}
