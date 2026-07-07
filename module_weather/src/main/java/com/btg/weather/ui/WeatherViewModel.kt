package com.btg.weather.ui

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.UiState
import com.btg.common.result.toUiState
import com.btg.weather.data.CityStore
import com.btg.weather.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val cityStore: CityStore,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val city = cityStore.currentCity.first().ifBlank { DEFAULT_CITY }
            _uiState.update { it.copy(city = city) }
            load(city)
        }
    }

    /** 切换城市：忽略空白与相同城市；持久化后重新加载。 */
    fun selectCity(name: String) {
        val city = name.trim()
        if (city.isEmpty() || city == _uiState.value.city) return
        viewModelScope.launch {
            cityStore.save(city)
            _uiState.update { it.copy(city = city) }
            load(city)
        }
    }

    fun refresh() = load(_uiState.value.city, refreshing = true)

    private fun load(city: String, refreshing: Boolean = false) {
        _uiState.update {
            it.copy(
                isRefreshing = refreshing,
                content = if (refreshing) it.content else UiState.Loading,
            )
        }
        viewModelScope.launch {
            val result = repository.getWeatherOverview(city).toUiState()
            // 防串台：结果返回时城市已切换则丢弃
            if (_uiState.value.city != city) return@launch
            _uiState.update { it.copy(isRefreshing = false, content = result) }
            if (result is UiState.Error) postError(result.message)
        }
    }

    companion object {
        const val DEFAULT_CITY = "杭州"
    }
}
