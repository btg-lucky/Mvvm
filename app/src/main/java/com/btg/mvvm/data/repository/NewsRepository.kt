package com.btg.mvvm.data.repository

import com.btg.common.result.ApiResult
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.data.source.NewsDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository(
    private val dataSource: NewsDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getNews(): ApiResult<List<NewsItem>> = withContext(ioDispatcher) {
        runCatching { dataSource.fetchNews() }
            .fold(
                onSuccess = { ApiResult.Success(it) },
                onFailure = { ApiResult.Error(it) }
            )
    }
}
