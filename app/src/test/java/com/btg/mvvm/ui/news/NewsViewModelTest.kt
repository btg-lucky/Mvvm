package com.btg.mvvm.ui.news

import com.btg.common.result.ApiResult
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.data.repository.NewsRepository
import com.btg.mvvm.data.source.NewsDataSource
import com.btg.mvvm.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sample = listOf(
        NewsItem("t", "s", "d", null, "https://example.com/1")
    )

    /** 用真实 NewsRepository + 受控 fake 数据源，通过数据源控制成功/失败。 */
    private fun repoReturning(result: ApiResult<List<NewsItem>>): NewsRepository {
        val dataSource = object : NewsDataSource {
            override suspend fun fetchNews(): List<NewsItem> = when (result) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> throw result.throwable
            }
        }
        return NewsRepository(dataSource, mainDispatcherRule.testDispatcher)
    }

    @Test
    fun `loadNews success updates uiState with items`() = runTest {
        val viewModel = NewsViewModel(repoReturning(ApiResult.Success(sample)))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(sample, state.items)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadNews error updates uiState with errorMessage`() = runTest {
        val viewModel = NewsViewModel(repoReturning(ApiResult.Error(RuntimeException("boom"))))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertEquals("boom", state.errorMessage)
    }

    @Test
    fun `onNewsClick emits OpenLink event`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = NewsViewModel(repoReturning(ApiResult.Success(sample)))
        val received = mutableListOf<NewsEvent>()
        val job = launch { viewModel.events.collect { received.add(it) } }

        viewModel.onNewsClick(sample.first())

        assertEquals(NewsEvent.OpenLink(sample.first().url), received.first())
        job.cancel()
    }
}
