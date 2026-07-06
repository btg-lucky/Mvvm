package com.btg.news.ui.list

import com.btg.news.data.model.NewsCategory
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.repository.NewsRepository
import com.btg.news.data.source.NewsDataSource
import com.btg.news.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 可控分页 fake：每页 pageSize 条，maxPage 之后返回空；failOnFetch 时抛异常。 */
    private class PagingSource(
        private val maxPage: Int = 2,
        var failOnFetch: Boolean = false,
    ) : NewsDataSource {
        override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> {
            if (failOnFetch) throw RuntimeException("boom")
            if (page > maxPage) return emptyList()
            return (1..pageSize).map { i ->
                NewsItem("$type-$page-$i", "t$i", "s", "d", type, null, "https://e.com/$page/$i")
            }
        }
        override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail =
            throw UnsupportedOperationException()
    }

    private fun viewModel(source: PagingSource = PagingSource()) =
        NewsListViewModel(NewsRepository(source, mainDispatcherRule.testDispatcher))

    @Test
    fun `init loads first page of TOP`() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(NewsCategory.TOP, state.category)
        assertEquals(30, state.items.size)
    }

    @Test
    fun `loadMore appends next page`() = runTest {
        val vm = viewModel()
        vm.loadMore()
        assertEquals(60, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.noMoreData)
    }

    @Test
    fun `loadMore beyond last page sets noMoreData`() = runTest {
        val vm = viewModel()
        vm.loadMore() // page 2
        vm.loadMore() // page 3 -> empty
        val state = vm.uiState.value
        assertEquals(60, state.items.size)
        assertTrue(state.noMoreData)
    }

    @Test
    fun `selectCategory resets list and reloads`() = runTest {
        val vm = viewModel()
        vm.loadMore()
        vm.selectCategory(NewsCategory.YULE)
        val state = vm.uiState.value
        assertEquals(NewsCategory.YULE, state.category)
        assertEquals(30, state.items.size)
        assertTrue(state.items.all { it.category == "yule" })
    }

    @Test
    fun `selectCategory with same category is no-op`() = runTest {
        val vm = viewModel()
        vm.loadMore()
        vm.selectCategory(NewsCategory.TOP)
        assertEquals(60, vm.uiState.value.items.size)
    }

    @Test
    fun `refresh error sets errorMessage`() = runTest {
        val source = PagingSource(failOnFetch = true)
        val vm = viewModel(source)
        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertTrue(state.items.isEmpty())
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `refresh after error recovers`() = runTest {
        val source = PagingSource(failOnFetch = true)
        val vm = viewModel(source)
        source.failOnFetch = false
        vm.refresh()
        assertEquals(30, vm.uiState.value.items.size)
    }
}
