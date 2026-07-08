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
    fun `light rain family maps to LIGHT_RAIN`() {
        listOf("03", "07", "08", "21").forEach {
            assertEquals("wid=$it", WeatherCategory.LIGHT_RAIN, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `heavy rain family maps to STORM_RAIN`() {
        listOf("09", "10", "11", "12", "22", "23", "24", "25").forEach {
            assertEquals("wid=$it", WeatherCategory.STORM_RAIN, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `thunder family maps to THUNDER`() {
        listOf("04", "05").forEach {
            assertEquals("wid=$it", WeatherCategory.THUNDER, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `light snow family maps to LIGHT_SNOW`() {
        listOf("13", "14", "26").forEach {
            assertEquals("wid=$it", WeatherCategory.LIGHT_SNOW, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `heavy snow family maps to HEAVY_SNOW`() {
        listOf("15", "16", "17", "27", "28").forEach {
            assertEquals("wid=$it", WeatherCategory.HEAVY_SNOW, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `sleet and freezing rain map to SLEET`() {
        listOf("06", "19").forEach {
            assertEquals("wid=$it", WeatherCategory.SLEET, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `fog and haze map to FOG`() {
        listOf("18", "53").forEach {
            assertEquals("wid=$it", WeatherCategory.FOG, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `dust family maps to DUST`() {
        listOf("20", "29", "30", "31").forEach {
            assertEquals("wid=$it", WeatherCategory.DUST, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `null blank and unknown fall back to CLOUDY`() {
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid(null))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid(""))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid("99"))
    }
}
