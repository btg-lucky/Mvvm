package com.btg.weather.data.repository

import com.btg.common.result.ApiResult
import com.btg.weather.data.source.FakeWeatherDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryTest {

    @Test
    fun `getWeatherOverview returns Success with realtime future and life`() = runTest {
        val repo = WeatherRepository(FakeWeatherDataSource(), StandardTestDispatcher(testScheduler))

        val result = repo.getWeatherOverview("杭州")

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("杭州", data.city)
        assertEquals("多云", data.realtime.info)
        assertEquals(5, data.future.size)
        assertEquals(4, data.life.size)
    }

    @Test
    fun `life failure still returns Success with empty life`() = runTest {
        val repo = WeatherRepository(FakeWeatherDataSource(failLife = true), StandardTestDispatcher(testScheduler))

        val result = repo.getWeatherOverview("杭州")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.life.isEmpty())
    }

    @Test
    fun `weather failure returns Error`() = runTest {
        val repo = WeatherRepository(FakeWeatherDataSource(failWeather = true), StandardTestDispatcher(testScheduler))

        val result = repo.getWeatherOverview("火星")

        assertTrue(result is ApiResult.Error)
    }
}
