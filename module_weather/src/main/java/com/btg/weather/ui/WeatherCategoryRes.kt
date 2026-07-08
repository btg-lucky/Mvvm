package com.btg.weather.ui

import androidx.annotation.DrawableRes
import com.btg.weather.R
import com.btg.weather.data.model.WeatherCategory

/** 天气档位 → 背景渐变资源。 */
@DrawableRes
fun WeatherCategory.backgroundRes(): Int = when (this) {
    WeatherCategory.CLEAR -> R.drawable.bg_weather_clear
    WeatherCategory.CLOUDY -> R.drawable.bg_weather_cloudy
    WeatherCategory.RAIN -> R.drawable.bg_weather_rain
    WeatherCategory.SNOW -> R.drawable.bg_weather_snow
    WeatherCategory.FOG -> R.drawable.bg_weather_fog
}

/** 天气档位 → 矢量插画资源。 */
@DrawableRes
fun WeatherCategory.illustrationRes(): Int = when (this) {
    WeatherCategory.CLEAR -> R.drawable.ic_weather_clear
    WeatherCategory.CLOUDY -> R.drawable.ic_weather_cloudy
    WeatherCategory.RAIN -> R.drawable.ic_weather_rain
    WeatherCategory.SNOW -> R.drawable.ic_weather_snow
    WeatherCategory.FOG -> R.drawable.ic_weather_fog
}
