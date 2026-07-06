package com.btg.news.ui.list

import com.btg.news.data.model.NewsItem

data class NewsListUiState(
    val isLoading: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null
)
