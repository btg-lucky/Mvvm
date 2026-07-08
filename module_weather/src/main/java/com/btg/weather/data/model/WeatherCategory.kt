package com.btg.weather.data.model

/**
 * 天气档位：把聚合接口 30+ 个 wid 归成 10 档，用于选天气卡实景照片背景与矢量小图标。
 * 保持纯净——不含 Android 资源 id，档位→资源映射在 UI 层。wid 表见天气种类接口文档。
 */
enum class WeatherCategory {
    CLEAR,       // 晴
    CLOUDY,      // 多云 / 阴
    LIGHT_RAIN,  // 阵雨 / 小雨 / 中雨
    STORM_RAIN,  // 大雨 ~ 特大暴雨
    THUNDER,     // 雷阵雨 / 雷阵雨伴冰雹
    LIGHT_SNOW,  // 阵雪 / 小雪
    HEAVY_SNOW,  // 中雪 ~ 暴雪
    SLEET,       // 雨夹雪 / 冻雨
    FOG,         // 雾 / 霾
    DUST;        // 沙尘暴 / 浮尘 / 扬沙

    companion object {
        fun fromWid(wid: String?): WeatherCategory = when (wid?.trim()) {
            "00" -> CLEAR
            "01", "02" -> CLOUDY
            "03", "07", "08", "21" -> LIGHT_RAIN
            "09", "10", "11", "12", "22", "23", "24", "25" -> STORM_RAIN
            "04", "05" -> THUNDER
            "13", "14", "26" -> LIGHT_SNOW
            "15", "16", "17", "27", "28" -> HEAVY_SNOW
            "06", "19" -> SLEET
            "18", "53" -> FOG
            "20", "29", "30", "31" -> DUST
            else -> CLOUDY
        }
    }
}
