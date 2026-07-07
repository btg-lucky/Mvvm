package com.btg.weather.data.source

import com.btg.common.network.AppException
import com.btg.weather.data.model.WeatherCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDtoTest {

    @Test
    fun `query result maps to domain with category from wid`() {
        val result = WeatherQueryResult(
            city = "苏州",
            realtime = RealtimeDto("阴", "02", "4", "82", "西北风", "3级", "80"),
            future = listOf(
                FutureDto("2019-02-22", "1/7℃", "小雨转多云", FutureWidDto("07", "01"), "北风转西北风"),
            ),
        )

        val data = result.toWeatherData()

        assertEquals("苏州", data.city)
        assertEquals("阴", data.realtime.info)
        assertEquals(WeatherCategory.CLOUDY, data.realtime.category)
        assertEquals("80", data.realtime.aqi)
        assertEquals(1, data.future.size)
        assertEquals("1/7℃", data.future[0].temperature)
        // future 档位取白天 wid=07 → RAIN
        assertEquals(WeatherCategory.RAIN, data.future[0].category)
    }

    @Test
    fun `query result with null realtime throws Parse`() {
        val result = WeatherQueryResult(city = "苏州", realtime = null, future = emptyList())
        assertThrows(AppException.Parse::class.java) { result.toWeatherData() }
    }

    @Test
    fun `null fields fall back to empty string`() {
        val result = WeatherQueryResult(
            city = null,
            realtime = RealtimeDto(null, null, null, null, null, null, null),
            future = null,
        )
        val data = result.toWeatherData()
        assertEquals("", data.city)
        assertEquals("", data.realtime.info)
        assertEquals(WeatherCategory.CLOUDY, data.realtime.category) // null wid 兜底
        assertTrue(data.future.isEmpty())
    }

    @Test
    fun `life result maps known keys in fixed order skipping blanks`() {
        val result = WeatherLifeResult(
            city = "北京",
            life = mapOf(
                "chuanyi" to LifeItemDto("冷", "天气冷，建议着棉服。"),
                "ziwaixian" to LifeItemDto("弱", "紫外线强度较弱。"),
                "diaoyu" to LifeItemDto(null, null), // v 空 → 跳过
                "unknownKey" to LifeItemDto("x", "y"), // 未知键 → 跳过
            ),
        )

        val list = result.toLifeList()

        assertEquals(2, list.size)
        // 固定展示顺序：穿衣在紫外线之前
        assertEquals("穿衣", list[0].name)
        assertEquals("冷", list[0].level)
        assertEquals("紫外线", list[1].name)
    }
}
