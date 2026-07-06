package com.btg.news.data.source

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNewsDataSourceTest {

    private val source = FakeNewsDataSource()

    @Test
    fun `fetchNews returns pageSize items for first page`() = runTest {
        val items = source.fetchNews("top", page = 1, pageSize = 30)
        assertEquals(30, items.size)
        assertTrue(items.all { it.category == "top" })
    }

    @Test
    fun `fetchNews returns empty beyond page 2`() = runTest {
        assertTrue(source.fetchNews("top", page = 3, pageSize = 30).isEmpty())
    }

    @Test
    fun `fetchNewsDetail returns detail with content`() = runTest {
        val detail = source.fetchNewsDetail("k1")
        assertTrue(detail.contentHtml.isNotBlank())
    }
}
