package com.btg.weather.data.source

import com.btg.common.network.AppException
import com.btg.weather.data.model.ForecastDay
import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.RealtimeWeather
import com.btg.weather.data.model.WeatherCategory
import com.btg.weather.data.model.WeatherSnapshot

/** query 接口 result：{ city, realtime, future }。 */
data class WeatherQueryResult(
    val city: String?,
    val realtime: RealtimeDto?,
    val future: List<FutureDto>?,
)

data class RealtimeDto(
    val info: String?,
    val wid: String?,
    val temperature: String?,
    val humidity: String?,
    val direct: String?,
    val power: String?,
    val aqi: String?,
)

data class FutureDto(
    val date: String?,
    val temperature: String?,
    val weather: String?,
    /** future 的 wid 是对象 {day, night}，与 realtime 的 wid(字符串) 不同。 */
    val wid: FutureWidDto?,
    val direct: String?,
)

data class FutureWidDto(
    val day: String?,
    val night: String?,
)

/** life 接口 result：{ city, life: { chuanyi:{v,des}, ... } }。 */
data class WeatherLifeResult(
    val city: String?,
    val life: Map<String, LifeItemDto>?,
)

data class LifeItemDto(
    val v: String?,
    val des: String?,
)

/** 生活指数键 → 中文展示名，兼作固定展示顺序。 */
private val LIFE_NAMES: Map<String, String> = linkedMapOf(
    "chuanyi" to "穿衣",
    "ziwaixian" to "紫外线",
    "ganmao" to "感冒",
    "yundong" to "运动",
    "xiche" to "洗车",
    "daisan" to "带伞",
    "shushidu" to "舒适度",
    "guomin" to "过敏",
    "kongtiao" to "空调",
    "diaoyu" to "钓鱼",
)

fun WeatherQueryResult.toWeatherSnapshot(): WeatherSnapshot {
    val rt = realtime ?: throw AppException.Parse("天气实况数据为空")
    return WeatherSnapshot(
        city = city.orEmpty(),
        realtime = RealtimeWeather(
            info = rt.info.orEmpty(),
            category = WeatherCategory.fromWid(rt.wid),
            temperature = rt.temperature.orEmpty(),
            humidity = rt.humidity.orEmpty(),
            direct = rt.direct.orEmpty(),
            power = rt.power.orEmpty(),
            aqi = rt.aqi.orEmpty(),
        ),
        future = future.orEmpty().map { it.toModel() },
    )
}

private fun FutureDto.toModel(): ForecastDay = ForecastDay(
    date = date.orEmpty(),
    temperature = temperature.orEmpty(),
    weather = weather.orEmpty(),
    category = WeatherCategory.fromWid(wid?.day),
    direct = direct.orEmpty(),
)

/** 按 LIFE_NAMES 固定顺序输出；v 为空或键未知则跳过。 */
fun WeatherLifeResult.toLifeList(): List<LifeIndex> {
    val source = life ?: return emptyList()
    return LIFE_NAMES.mapNotNull { (key, name) ->
        val item = source[key] ?: return@mapNotNull null
        val level = item.v?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        LifeIndex(name = name, level = level, desc = item.des.orEmpty())
    }
}
