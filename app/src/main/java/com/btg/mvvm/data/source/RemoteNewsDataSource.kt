package com.btg.mvvm.data.source

import com.btg.mvvm.data.model.NewsItem

/**
 * 真实网络数据源骨架。
 *
 * TODO: 有真实 API key 后实现——调用 [NewsApi] 并把响应映射为 List<NewsItem>。
 * 届时只需在 MainActivity 的手动 DI 处，把 FakeNewsDataSource 换成
 * RemoteNewsDataSource(newsApi)，Repository / ViewModel / View 都不用改。
 */
class RemoteNewsDataSource(private val api: NewsApi) : NewsDataSource {
    override suspend fun fetchNews(): List<NewsItem> {
        TODO("接入真实新闻接口后实现：调用 api.getNews(...) 并映射为 List<NewsItem>")
    }
}
