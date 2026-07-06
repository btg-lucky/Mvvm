package com.btg.news.data.source

import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem

/** 新闻数据源统一入口。实现：失败时抛异常，由 Repository 经 safeApiCall 捕获包装。 */
interface NewsDataSource {
    suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem>
    suspend fun fetchNewsDetail(uniquekey: String): NewsDetail
}
