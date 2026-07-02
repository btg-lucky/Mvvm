package com.btg.common.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.btg.common.result.ApiResult
import com.btg.common.result.UiState
import com.btg.common.result.toListUiState
import com.btg.common.result.toUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * MVVM 分层锚点 + 通用帮手：统一错误事件下发、把一次数据加载映射为 [UiState] 写入目标状态流。
 * 保持可单元测试：不持有 View/Context。
 */
open class BaseViewModel : ViewModel() {

    private val _errorEvent = Channel<String>(Channel.BUFFERED)

    /** 统一的一次性错误事件流，View 层收集后弹 Toast / 提示。 */
    val errorEvent: Flow<String> = _errorEvent.receiveAsFlow()

    /** 下发一条错误事件（非挂起）。 */
    protected fun postError(message: String) {
        _errorEvent.trySend(message)
    }

    /**
     * 执行一次数据加载：先置 [UiState.Loading]，完成后按结果映射为 Success/Error 写入 [target]。
     */
    protected fun <T> launchWithState(
        target: MutableStateFlow<UiState<T>>,
        block: suspend () -> ApiResult<T>,
    ) {
        target.value = UiState.Loading
        viewModelScope.launch {
            target.value = block().toUiState()
        }
    }

    /**
     * 列表版加载：空列表结果映射为 [UiState.Empty]。
     */
    protected fun <E> launchListWithState(
        target: MutableStateFlow<UiState<List<E>>>,
        block: suspend () -> ApiResult<List<E>>,
    ) {
        target.value = UiState.Loading
        viewModelScope.launch {
            target.value = block().toListUiState()
        }
    }
}
