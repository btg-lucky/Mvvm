package com.btg.mvvm.ui.news

import com.btg.mvvm.data.model.NewsItem

data class NewsUiState(
    val isLoading: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null
)
