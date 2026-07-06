package com.btg.news.data.repository

import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsItem
import com.btg.news.data.source.NewsDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRepositoryTest {

    private val sample = listOf(
        NewsItem("t", "s", "d", null, "https://example.com/1")
    )

    @Test
    fun `getNews returns Success when data source succeeds`() = runTest {
        val repository = NewsRepository(
            dataSource = object : NewsDataSource {
                override suspend fun fetchNews(): List<NewsItem> = sample
            },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.getNews()

        assertEquals(ApiResult.Success(sample), result)
    }

    @Test
    fun `getNews returns Error when data source throws`() = runTest {
        val boom = RuntimeException("boom")
        val repository = NewsRepository(
            dataSource = object : NewsDataSource {
                override suspend fun fetchNews(): List<NewsItem> = throw boom
            },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.getNews()

        assertTrue(result is ApiResult.Error && result.throwable === boom)
    }
}
