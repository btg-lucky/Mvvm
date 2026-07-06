package com.btg.news.data.source

import com.btg.common.network.AppException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteNewsDataSourceTest {

    private class FakeApi : NewsApi {
        var lastListParams: List<Any>? = null
        var listResponse = JuheResponse(
            errorCode = 0, reason = "success",
            result = NewsListResult(
                data = listOf(
                    NewsListItemDto("k1", "标题1", "2026-07-06", "头条", "来源", "https://e.com/1", "https://img/1"),
                )
            )
        )
        var detailResponse = JuheResponse(
            errorCode = 0, reason = "success",
            result = NewsDetailResult(
                uniquekey = "k1", content = "<p>正文</p>",
                detail = NewsDetailDto("标题1", "2026-07-06", "头条", "来源", "https://e.com/1")
            )
        )

        override suspend fun getNewsList(key: String, type: String, page: Int, pageSize: Int, isFilter: Int): JuheResponse<NewsListResult> {
            lastListParams = listOf(key, type, page, pageSize, isFilter)
            return listResponse
        }

        override suspend fun getNewsDetail(key: String, uniquekey: String): JuheResponse<NewsDetailResult> =
            detailResponse
    }

    @Test
    fun `fetchNews passes key and params and maps items`() = runTest {
        val api = FakeApi()
        val source = RemoteNewsDataSource(api, apiKey = "test-key")

        val items = source.fetchNews("yule", page = 2, pageSize = 30)

        assertEquals(listOf<Any>("test-key", "yule", 2, 30, 1), api.lastListParams)
        assertEquals(1, items.size)
        assertEquals("k1", items.first().uniquekey)
    }

    @Test
    fun `fetchNews returns empty list when data is null`() = runTest {
        val api = FakeApi().apply {
            listResponse = JuheResponse(0, "success", NewsListResult(data = null))
        }
        val source = RemoteNewsDataSource(api, "test-key")

        assertEquals(emptyList<Any>(), source.fetchNews("top", 1, 30))
    }

    @Test
    fun `fetchNewsDetail maps content and detail`() = runTest {
        val source = RemoteNewsDataSource(FakeApi(), "test-key")

        val detail = source.fetchNewsDetail("k1")

        assertEquals("标题1", detail.title)
        assertEquals("<p>正文</p>", detail.contentHtml)
    }

    @Test
    fun `fetchNewsDetail throws Parse when content is blank`() = runTest {
        val api = FakeApi().apply {
            detailResponse = JuheResponse(
                0, "success",
                NewsDetailResult("k1", content = "", detail = NewsDetailDto("t", "d", "c", "a", "u"))
            )
        }
        val source = RemoteNewsDataSource(api, "test-key")

        assertThrows(AppException.Parse::class.java) {
            kotlinx.coroutines.runBlocking { source.fetchNewsDetail("k1") }
        }
    }
}
