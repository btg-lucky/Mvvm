package com.btg.mvvm.data.source

import com.btg.mvvm.data.model.NewsItem
import kotlinx.coroutines.delay

class FakeNewsDataSource : NewsDataSource {

    override suspend fun fetchNews(): List<NewsItem> {
        delay(600) // 模拟网络耗时
        return SAMPLE_NEWS
    }

    private companion object {
        val SAMPLE_NEWS = listOf(
            NewsItem(
                title = "示范新闻一：MVVM 架构落地",
                source = "示范来源",
                date = "2026-07-01",
                imageUrl = null,
                url = "https://example.com/news/1"
            ),
            NewsItem(
                title = "示范新闻二：协程与 Flow",
                source = "示范来源",
                date = "2026-07-01",
                imageUrl = null,
                url = "https://example.com/news/2"
            ),
            NewsItem(
                title = "示范新闻三：可替换数据源",
                source = "示范来源",
                date = "2026-07-01",
                imageUrl = null,
                url = "https://example.com/news/3"
            )
        )
    }
}
