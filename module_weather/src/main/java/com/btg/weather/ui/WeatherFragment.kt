package com.btg.weather.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.weather.databinding.FragmentWeatherBinding

/** 天气占位页:功能未开发,仅展示提示文案。 */
class WeatherFragment : BaseFragment<FragmentWeatherBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentWeatherBinding.inflate(inflater, container, false)
}
