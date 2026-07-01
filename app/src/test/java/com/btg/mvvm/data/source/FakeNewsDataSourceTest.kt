package com.btg.mvvm.data.source

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNewsDataSourceTest {

    @Test
    fun `fetchNews returns non-empty list`() = runTest {
        val dataSource = FakeNewsDataSource()

        val result = dataSource.fetchNews()

        assertTrue("假数据源应返回非空列表", result.isNotEmpty())
    }
}
