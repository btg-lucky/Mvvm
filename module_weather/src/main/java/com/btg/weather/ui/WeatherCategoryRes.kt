package com.btg.weather.ui

import androidx.annotation.DrawableRes
import com.btg.weather.R
import com.btg.weather.data.model.WeatherCategory

/** 天气档位 → 天气卡实景照片背景（10 档各一张）。 */
@DrawableRes
fun WeatherCategory.backgroundRes(): Int = when (this) {
    WeatherCategory.CLEAR -> R.drawable.w_clear
    WeatherCategory.CLOUDY -> R.drawable.w_cloudy
    WeatherCategory.LIGHT_RAIN -> R.drawable.w_light_rain
    WeatherCategory.STORM_RAIN -> R.drawable.w_storm_rain
    WeatherCategory.THUNDER -> R.drawable.w_thunder
    WeatherCategory.LIGHT_SNOW -> R.drawable.w_light_snow
    WeatherCategory.HEAVY_SNOW -> R.drawable.w_heavy_snow
    WeatherCategory.SLEET -> R.drawable.w_sleet
    WeatherCategory.FOG -> R.drawable.w_fog
    WeatherCategory.DUST -> R.drawable.w_dust
}

/** 天气档位 → 矢量小图标（复用 5 个基础插画，多档共用一图）。用于近 5 天预报小图标。 */
@DrawableRes
fun WeatherCategory.illustrationRes(): Int = when (this) {
    WeatherCategory.CLEAR -> R.drawable.ic_weather_clear
    WeatherCategory.CLOUDY -> R.drawable.ic_weather_cloudy
    WeatherCategory.LIGHT_RAIN, WeatherCategory.STORM_RAIN, WeatherCategory.THUNDER ->
        R.drawable.ic_weather_rain
    WeatherCategory.LIGHT_SNOW, WeatherCategory.HEAVY_SNOW, WeatherCategory.SLEET ->
        R.drawable.ic_weather_snow
    WeatherCategory.FOG, WeatherCategory.DUST -> R.drawable.ic_weather_fog
}
