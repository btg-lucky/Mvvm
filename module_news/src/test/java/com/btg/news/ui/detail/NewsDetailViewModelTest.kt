package com.btg.news.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.btg.common.result.UiState
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.repository.NewsRepository
import com.btg.news.data.source.NewsDataSource
import com.btg.news.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val detail = NewsDetail("标题", "来源", "2026-07-06", "top", "<p>正文</p>", "https://e.com/1")

    private fun repo(fail: Boolean): NewsRepository {
        val source = object : NewsDataSource {
            override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> =
                throw UnsupportedOperationException()
            override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail =
                if (fail) throw RuntimeException("boom") else detail
        }
        return NewsRepository(source, mainDispatcherRule.testDispatcher)
    }

    private fun handle() = SavedStateHandle(mapOf(NewsDetailArgs.UNIQUEKEY to "k1"))

    @Test
    fun `init loads detail into Success state`() = runTest {
        val vm = NewsDetailViewModel(handle(), repo(fail = false))
        assertEquals(UiState.Success(detail), vm.detailState.value)
    }

    @Test
    fun `load failure lands in Error state`() = runTest {
        val vm = NewsDetailViewModel(handle(), repo(fail = true))
        assertTrue(vm.detailState.value is UiState.Error)
    }
}
