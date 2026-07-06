package com.btg.news.data.source

import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import kotlinx.coroutines.delay

/** 假数据源：无 key 时演示/测试用。前 2 页有数据，之后为空模拟"没有更多"。 */
class FakeNewsDataSource : NewsDataSource {

    override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> {
        delay(400)
        if (page > 2) return emptyList()
        return (1..pageSize).map { i ->
            NewsItem(
                uniquekey = "$type-$page-$i",
                title = "[$type] 示范新闻 第${page}页 第${i}条",
                source = "示范来源",
                date = "2026-07-06 10:00",
                category = type,
                imageUrl = null,
                url = "https://example.com/$type/$page/$i"
            )
        }
    }

    override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail {
        delay(200)
        return NewsDetail(
            title = "示范新闻 $uniquekey",
            source = "示范来源",
            date = "2026-07-06 10:00",
            category = "top",
            contentHtml = "<p>这是 $uniquekey 的示范正文。</p>",
            url = ""
        )
    }
}
