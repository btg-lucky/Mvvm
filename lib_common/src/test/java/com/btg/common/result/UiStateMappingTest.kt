package com.btg.common.result

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateMappingTest {

    @Test
    fun `toListUiState maps non-empty success to Success`() {
        val result: ApiResult<List<Int>> = ApiResult.Success(listOf(1, 2, 3))
        assertEquals(UiState.Success(listOf(1, 2, 3)), result.toListUiState())
    }

    @Test
    fun `toListUiState maps empty success to Empty`() {
        val result: ApiResult<List<Int>> = ApiResult.Success(emptyList())
        assertEquals(UiState.Empty, result.toListUiState())
    }

    @Test
    fun `toListUiState maps error to Error with throwable message`() {
        val result: ApiResult<List<Int>> = ApiResult.Error(RuntimeException("boom"))
        assertEquals(UiState.Error("boom"), result.toListUiState())
    }

    @Test
    fun `toListUiState uses fallback when throwable message is null`() {
        val result: ApiResult<List<Int>> = ApiResult.Error(RuntimeException())
        assertEquals(UiState.Error("加载失败"), result.toListUiState())
    }

    @Test
    fun `toUiState maps success to Success without empty check`() {
        val result: ApiResult<String> = ApiResult.Success("ok")
        assertEquals(UiState.Success("ok"), result.toUiState())
    }
}
