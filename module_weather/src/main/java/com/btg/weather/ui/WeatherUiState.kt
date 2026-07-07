package com.btg.weather.ui

import com.btg.common.result.UiState
import com.btg.weather.data.model.WeatherOverview

/**
 * 天气页 UI 状态快照。
 * content 为四态；isRefreshing 表示"已有内容 + 下拉刷新中"，与首次 Loading 区分。
 */
data class WeatherUiState(
    val city: String = "",
    val content: UiState<WeatherOverview> = UiState.Loading,
    val isRefreshing: Boolean = false,
)
