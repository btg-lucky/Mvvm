package com.btg.weather.ui

import com.btg.common.result.UiState
import com.btg.weather.data.CityStore
import com.btg.weather.data.repository.WeatherRepository
import com.btg.weather.data.source.FakeWeatherDataSource
import com.btg.weather.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeCityStore(initial: String = "") : CityStore {
        private val state = MutableStateFlow(initial)
        override val currentCity: Flow<String> = state.asStateFlow()
        override suspend fun save(city: String) { state.value = city }
    }

    private fun repo(failWeather: Boolean = false) =
        WeatherRepository(FakeWeatherDataSource(failWeather = failWeather), mainDispatcherRule.testDispatcher)

    @Test
    fun `init loads default city Hangzhou when store empty`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore(""))

        val state = vm.uiState.value
        assertEquals("杭州", state.city)
        assertTrue(state.content is UiState.Success)
    }

    @Test
    fun `init loads stored city`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore("上海"))

        assertEquals("上海", vm.uiState.value.city)
    }

    @Test
    fun `selectCity updates city and reloads`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore(""))

        vm.selectCity("北京")

        val state = vm.uiState.value
        assertEquals("北京", state.city)
        assertTrue(state.content is UiState.Success)
    }

    @Test
    fun `selectCity ignores blank input`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore("广州"))

        vm.selectCity("   ")

        assertEquals("广州", vm.uiState.value.city)
    }

    @Test
    fun `weather failure yields Error content`() = runTest {
        val vm = WeatherViewModel(repo(failWeather = true), FakeCityStore(""))

        assertTrue(vm.uiState.value.content is UiState.Error)
    }
}
