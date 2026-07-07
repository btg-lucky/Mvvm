package com.btg.weather.data.source

import com.btg.common.network.AppException
import com.btg.weather.data.model.ForecastDay
import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.RealtimeWeather
import com.btg.weather.data.model.WeatherCategory
import com.btg.weather.data.model.WeatherData

/**
 * 假数据源：无 key 时演示 / 单测用。
 * failWeather / failLife 用于测试异常分支（天气失败、生活指数失败）。
 */
class FakeWeatherDataSource(
    private val failWeather: Boolean = false,
    private val failLife: Boolean = false,
) : WeatherDataSource {

    override suspend fun fetchWeather(city: String): WeatherData {
        if (failWeather) throw AppException.Business(207302, "查询不到该城市的相关信息")
        return WeatherData(
            city = city,
            realtime = RealtimeWeather(
                info = "多云", category = WeatherCategory.CLOUDY,
                temperature = "22", humidity = "60", direct = "东南风", power = "3级", aqi = "75",
            ),
            future = (0 until 5).map { i ->
                ForecastDay(
                    date = "2026-07-0${i + 7}",
                    temperature = "${20 + i}/${28 + i}℃",
                    weather = "多云",
                    category = WeatherCategory.CLOUDY,
                    direct = "东南风",
                )
            },
        )
    }

    override suspend fun fetchLife(city: String): List<LifeIndex> {
        if (failLife) throw AppException.Network("网络错误，请重试")
        return listOf(
            LifeIndex("穿衣", "舒适", "建议着薄外套或牛仔裤等服装。"),
            LifeIndex("紫外线", "中等", "外出时建议涂擦防晒霜。"),
            LifeIndex("运动", "较适宜", "较适宜进行户外运动。"),
            LifeIndex("洗车", "适宜", "适宜洗车，未来一天无雨。"),
        )
    }
}
