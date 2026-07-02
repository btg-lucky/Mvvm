package com.btg.common.base

import com.btg.common.result.ApiResult
import com.btg.common.result.UiState
import com.btg.common.util.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BaseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class TestViewModel : BaseViewModel() {
        val listState = MutableStateFlow<UiState<List<Int>>>(UiState.Loading)
        fun load(block: suspend () -> ApiResult<List<Int>>) = launchListWithState(listState, block)
        fun raise(message: String) = postError(message)
    }

    @Test
    fun `launchListWithState ends in Success for non-empty`() = runTest {
        val vm = TestViewModel()
        vm.load { ApiResult.Success(listOf(1, 2)) }
        assertEquals(UiState.Success(listOf(1, 2)), vm.listState.value)
    }

    @Test
    fun `launchListWithState ends in Empty for empty list`() = runTest {
        val vm = TestViewModel()
        vm.load { ApiResult.Success(emptyList()) }
        assertEquals(UiState.Empty, vm.listState.value)
    }

    @Test
    fun `launchListWithState ends in Error on failure`() = runTest {
        val vm = TestViewModel()
        vm.load { ApiResult.Error(RuntimeException("net down")) }
        assertEquals(UiState.Error("net down"), vm.listState.value)
    }

    @Test
    fun `postError is delivered to errorEvent`() = runTest {
        val vm = TestViewModel()
        val received = mutableListOf<String>()
        val job = launch { vm.errorEvent.take(1).collect { received.add(it) } }
        runCurrent()
        vm.raise("oops")
        job.join()
        assertEquals(listOf("oops"), received)
    }
}
