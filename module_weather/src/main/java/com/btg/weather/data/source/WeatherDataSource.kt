package com.btg.weather.data.source

import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.WeatherSnapshot

/** 天气数据源统一入口。实现失败时抛异常，由 Repository 经 safeApiCall 捕获包装。 */
interface WeatherDataSource {
    suspend fun fetchWeather(city: String): WeatherSnapshot
    suspend fun fetchLife(city: String): List<LifeIndex>
}
