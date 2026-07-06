package com.btg.news.ui.list

import com.btg.news.data.model.NewsCategory
import com.btg.news.data.model.NewsItem

data class NewsListUiState(
    val category: NewsCategory = NewsCategory.TOP,
    /** 首次加载 / 下拉刷新 / 切分类中。 */
    val isRefreshing: Boolean = false,
    /** 上拉加载下一页中。 */
    val isLoadingMore: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null,
    val noMoreData: Boolean = false,
)
