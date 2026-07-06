package com.btg.news.data.repository

import com.btg.common.network.safeApiCall
import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.source.NewsDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository(
    private val dataSource: NewsDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getNews(type: String, page: Int, pageSize: Int = 30): ApiResult<List<NewsItem>> =
        withContext(ioDispatcher) {
            safeApiCall { dataSource.fetchNews(type, page, pageSize) }
        }

    suspend fun getNewsDetail(uniquekey: String): ApiResult<NewsDetail> =
        withContext(ioDispatcher) {
            safeApiCall { dataSource.fetchNewsDetail(uniquekey) }
        }
}
