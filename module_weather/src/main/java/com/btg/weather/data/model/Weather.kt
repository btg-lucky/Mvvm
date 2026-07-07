package com.btg.weather.data.model

/** 实况天气。category 用于选背景渐变/插画；其余为展示文案（可能为空串）。 */
data class RealtimeWeather(
    val info: String,
    val category: WeatherCategory,
    val temperature: String,
    val humidity: String,
    val direct: String,
    val power: String,
    val aqi: String,
)

/** 近 5 天单日预报。category 取白天 wid。 */
data class ForecastDay(
    val date: String,
    val temperature: String,
    val weather: String,
    val category: WeatherCategory,
    val direct: String,
)

/** 生活指数单项：name 展示名（如「穿衣」），level 档位(v)，desc 详情(des)。 */
data class LifeIndex(
    val name: String,
    val level: String,
    val desc: String,
)

/** 天气总览：一次加载的完整领域模型。life 可能为空（生活指数接口失败时）。 */
data class WeatherOverview(
    val city: String,
    val realtime: RealtimeWeather,
    val future: List<ForecastDay>,
    val life: List<LifeIndex>,
)

/** 数据源层载体：query 接口解出的天气部分（不含 life）。city 取接口 result.city。 */
data class WeatherData(
    val city: String,
    val realtime: RealtimeWeather,
    val future: List<ForecastDay>,
)
