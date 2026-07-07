package com.btg.weather.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCategoryTest {

    @Test
    fun `clear wid maps to CLEAR`() {
        assertEquals(WeatherCategory.CLEAR, WeatherCategory.fromWid("00"))
    }

    @Test
    fun `cloudy and overcast map to CLOUDY`() {
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid("01"))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid("02"))
    }

    @Test
    fun `rain family maps to RAIN`() {
        listOf("03", "04", "05", "07", "10", "12", "19", "21", "25").forEach {
            assertEquals("wid=$it", WeatherCategory.RAIN, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `snow family maps to SNOW`() {
        listOf("06", "13", "16", "17", "26", "28").forEach {
            assertEquals("wid=$it", WeatherCategory.SNOW, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `fog dust haze map to FOG`() {
        listOf("18", "20", "29", "30", "31", "53").forEach {
            assertEquals("wid=$it", WeatherCategory.FOG, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `null blank and unknown fall back to CLOUDY`() {
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid(null))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid(""))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid("99"))
    }
}
