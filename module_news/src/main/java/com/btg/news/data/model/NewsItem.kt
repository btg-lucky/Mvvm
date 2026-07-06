package com.btg.news.data.model

data class NewsItem(
    val uniquekey: String,
    val title: String,
    val source: String,
    val date: String,
    val category: String,
    val imageUrl: String?,
    val url: String
)
