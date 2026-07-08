package com.btg.weather.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.result.UiState
import com.btg.common.ui.onRefresh
import com.btg.common.ui.showAlertDialog
import com.btg.common.ui.toast
import com.btg.weather.R
import com.btg.weather.data.model.WeatherOverview
import com.btg.weather.databinding.FragmentWeatherBinding
import dagger.hilt.android.AndroidEntryPoint

/** 天气首页：实况卡（背景随天气档位）+ 近 5 天 + 生活指数；顶部可切换城市。 */
@AndroidEntryPoint
class WeatherFragment : BaseFragment<FragmentWeatherBinding>() {

    private val viewModel: WeatherViewModel by viewModels()
    private val forecastAdapter = ForecastAdapter()
    private val lifeAdapter = LifeAdapter { index ->
        requireContext().showAlertDialog(title = index.name, message = index.desc)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentWeatherBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.forecastRecycler.adapter = forecastAdapter
        binding.lifeRecycler.adapter = lifeAdapter
        binding.swipeRefresh.onRefresh { viewModel.refresh() }
        binding.stateLayout.setOnRetryListener { viewModel.refresh() }

        binding.changeCityText.setOnClickListener { showCityDialog() }
        binding.cityText.setOnClickListener { showCityDialog() }

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }

    private fun render(state: WeatherUiState) {
        binding.cityText.text = state.city
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        when (val content = state.content) {
            is UiState.Loading -> binding.stateLayout.showLoading()
            // WeatherOverview 非列表，toUiState() 不会产出 Empty；保留分支满足 when 穷尽性
            is UiState.Empty -> binding.stateLayout.showEmpty()
            is UiState.Error -> {
                if (state.isRefreshing) binding.stateLayout.showContent()
                else binding.stateLayout.showError(content.message)
            }
            is UiState.Success -> {
                binding.stateLayout.showContent()
                bindOverview(content.data)
            }
        }
    }

    private fun bindOverview(data: WeatherOverview) {
        val rt = data.realtime
        binding.currentCard.setBackgroundResource(rt.category.backgroundRes())
        binding.illustrationImage.setImageResource(rt.category.illustrationRes())
        binding.temperatureText.text = getString(R.string.weather_temperature_unit, rt.temperature)
        binding.infoText.text = rt.info
        binding.detailText.text = buildString {
            val parts = mutableListOf<String>()
            if (rt.humidity.isNotBlank()) parts += getString(R.string.weather_humidity, rt.humidity)
            if (rt.direct.isNotBlank() || rt.power.isNotBlank()) {
                parts += getString(R.string.weather_wind, rt.direct, rt.power).trim()
            }
            if (rt.aqi.isNotBlank()) parts += getString(R.string.weather_aqi, rt.aqi)
            append(parts.joinToString("   "))
        }
        forecastAdapter.submitList(data.future)
        binding.lifeTitle.visibility = if (data.life.isEmpty()) View.GONE else View.VISIBLE
        binding.lifeRecycler.visibility = if (data.life.isEmpty()) View.GONE else View.VISIBLE
        lifeAdapter.submitList(data.life)
    }

    private fun showCityDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.weather_city_hint)
            setText(viewModel.uiState.value.city)
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.weather_change_city)
            .setView(input)
            .setPositiveButton(R.string.weather_confirm) { _, _ ->
                viewModel.selectCity(input.text.toString())
            }
            .setNegativeButton(R.string.weather_cancel, null)
            .show()
    }
}
