package com.btg.news.data.source

import com.btg.news.data.model.NewsItem

/** 数据源统一入口。实现：失败时抛异常，由 Repository 捕获包装。 */
interface NewsDataSource {
    suspend fun fetchNews(): List<NewsItem>
}
