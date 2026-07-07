package com.btg.weather.data.model

/**
 * 天气档位：把聚合接口 30+ 个 wid 归成 5 档，用于选背景渐变与矢量插画。
 * 保持纯净——不含 Android 资源 id，档位→资源映射在 UI 层。wid 表见天气种类接口文档。
 */
enum class WeatherCategory {
    CLEAR, CLOUDY, RAIN, SNOW, FOG;

    companion object {
        fun fromWid(wid: String?): WeatherCategory = when (wid?.trim()) {
            "00" -> CLEAR
            "01", "02" -> CLOUDY
            "03", "04", "05", "07", "08", "09", "10", "11", "12",
            "19", "21", "22", "23", "24", "25" -> RAIN
            "06", "13", "14", "15", "16", "17", "26", "27", "28" -> SNOW
            "18", "20", "29", "30", "31", "53" -> FOG
            else -> CLOUDY
        }
    }
}
