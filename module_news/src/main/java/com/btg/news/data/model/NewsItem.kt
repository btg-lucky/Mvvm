package com.btg.news.data.model

data class NewsItem(
    val title: String,
    val source: String,
    val date: String,
    val imageUrl: String?,
    val url: String
)
