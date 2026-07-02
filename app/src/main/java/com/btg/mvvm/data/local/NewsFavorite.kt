package com.btg.mvvm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 收藏的新闻（以 url 为主键，重复收藏覆盖）。 */
@Entity(tableName = "news_favorite")
data class NewsFavorite(
    @PrimaryKey val url: String,
    val title: String,
    val source: String,
    val date: String,
    val imageUrl: String?,
)
