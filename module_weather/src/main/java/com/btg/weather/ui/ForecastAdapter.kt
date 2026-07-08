package com.btg.weather.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btg.weather.data.model.ForecastDay
import com.btg.weather.databinding.ItemForecastBinding

class ForecastAdapter : ListAdapter<ForecastDay, ForecastAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemForecastBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ForecastDay) {
            binding.dateText.text = item.date
            binding.weatherText.text = item.weather
            binding.tempText.text = item.temperature
            binding.directText.text = item.direct
            binding.iconImage.setImageResource(item.category.illustrationRes())
            // 插画本是白色，浅底列表里染成深灰
            binding.iconImage.imageTintList = ColorStateList.valueOf(Color.parseColor("#546E7A"))
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ForecastDay>() {
            override fun areItemsTheSame(oldItem: ForecastDay, newItem: ForecastDay) =
                oldItem.date == newItem.date

            override fun areContentsTheSame(oldItem: ForecastDay, newItem: ForecastDay) =
                oldItem == newItem
        }
    }
}
