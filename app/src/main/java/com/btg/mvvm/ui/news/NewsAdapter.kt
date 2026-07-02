package com.btg.mvvm.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btg.common.ext.loadUrl
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.databinding.ItemNewsBinding

class NewsAdapter(
    private val onClick: (NewsItem) -> Unit
) : ListAdapter<NewsItem, NewsAdapter.NewsViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NewsViewHolder(
        private val binding: ItemNewsBinding,
        private val onClick: (NewsItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NewsItem) {
            binding.titleText.text = item.title
            binding.sourceText.text = item.source
            binding.dateText.text = item.date
            if (item.imageUrl.isNullOrEmpty()) {
                binding.newsImage.visibility = View.GONE
            } else {
                binding.newsImage.visibility = View.VISIBLE
                binding.newsImage.loadUrl(item.imageUrl)
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<NewsItem>() {
            override fun areItemsTheSame(oldItem: NewsItem, newItem: NewsItem) =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: NewsItem, newItem: NewsItem) =
                oldItem == newItem
        }
    }
}
