package com.btg.news.data.source

import com.btg.common.network.AppException
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem

/** 真实网络数据源：调用聚合数据接口并映射为领域模型。 */
class RemoteNewsDataSource(
    private val api: NewsApi,
    private val apiKey: String,
) : NewsDataSource {

    override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> =
        api.getNewsList(apiKey, type, page, pageSize).unwrap()
            .data.orEmpty()
            .map { it.toModel() }

    override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail {
        val detail = api.getNewsDetail(apiKey, uniquekey).unwrap().toModel()
        if (detail.contentHtml.isBlank()) throw AppException.Parse("新闻详情内容为空")
        return detail
    }
}
