package com.btg.mvvm.data.local

import androidx.room.Dao
import androidx.room.Query
import com.btg.common.storage.BaseDao
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao : BaseDao<NewsFavorite> {

    @Query("SELECT * FROM news_favorite ORDER BY date DESC")
    fun getAll(): Flow<List<NewsFavorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM news_favorite WHERE url = :url)")
    suspend fun exists(url: String): Boolean
}
