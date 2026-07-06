package com.btg.news.data.repository

import com.btg.common.network.AppException
import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.source.NewsDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRepositoryTest {

    private val sampleItem = NewsItem("k1", "t", "s", "d", "top", null, "https://e.com/1")
    private val sampleDetail = NewsDetail("t", "s", "d", "top", "<p>x</p>", "https://e.com/1")

    private class ThrowingSource(private val throwable: Throwable) : NewsDataSource {
        override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> = throw throwable
        override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail = throw throwable
    }

    private inner class SuccessSource : NewsDataSource {
        override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> = listOf(sampleItem)
        override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail = sampleDetail
    }

    @Test
    fun `getNews wraps success`() = runTest {
        val repo = NewsRepository(SuccessSource(), StandardTestDispatcher(testScheduler))
        val result = repo.getNews("top", 1)
        assertEquals(ApiResult.Success(listOf(sampleItem)), result)
    }

    @Test
    fun `getNews maps exception to AppException`() = runTest {
        val repo = NewsRepository(ThrowingSource(RuntimeException("boom")), StandardTestDispatcher(testScheduler))
        val result = repo.getNews("top", 1)
        assertTrue(result is ApiResult.Error && (result as ApiResult.Error).throwable is AppException)
    }

    @Test
    fun `getNewsDetail wraps success`() = runTest {
        val repo = NewsRepository(SuccessSource(), StandardTestDispatcher(testScheduler))
        assertEquals(ApiResult.Success(sampleDetail), repo.getNewsDetail("k1"))
    }
}
