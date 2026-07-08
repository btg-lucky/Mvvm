package com.btg.weather.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btg.weather.data.model.LifeIndex
import com.btg.weather.databinding.ItemLifeBinding

class LifeAdapter(
    private val onClick: (LifeIndex) -> Unit,
) : ListAdapter<LifeIndex, LifeAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLifeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val binding: ItemLifeBinding,
        private val onClick: (LifeIndex) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LifeIndex) {
            binding.lifeNameText.text = item.name
            binding.lifeLevelText.text = item.level
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<LifeIndex>() {
            override fun areItemsTheSame(oldItem: LifeIndex, newItem: LifeIndex) =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: LifeIndex, newItem: LifeIndex) =
                oldItem == newItem
        }
    }
}
