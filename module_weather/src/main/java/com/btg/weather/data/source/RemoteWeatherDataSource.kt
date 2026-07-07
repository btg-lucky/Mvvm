package com.btg.weather.data.source

import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.WeatherData

/** 真实网络数据源：调用聚合数据接口并映射为领域模型。 */
class RemoteWeatherDataSource(
    private val api: WeatherApi,
    private val apiKey: String,
) : WeatherDataSource {

    override suspend fun fetchWeather(city: String): WeatherData =
        api.query(apiKey, city).unwrap().toWeatherData()

    override suspend fun fetchLife(city: String): List<LifeIndex> =
        api.life(apiKey, city).unwrap().toLifeList()
}
