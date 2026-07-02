package com.btg.common.ui

import com.btg.common.result.UiState
import com.btg.widget.StateLayout

/**
 * 把 [UiState] 渲染到 [StateLayout]：Loading→loading，Success→content，Empty→empty，Error→error。
 * onRetry 传入后，错误态点击重试会回调。
 */
fun StateLayout.render(state: UiState<*>, onRetry: (() -> Unit)? = null) {
    onRetry?.let { setOnRetryListener(it) }
    when (state) {
        is UiState.Loading -> showLoading()
        is UiState.Success -> showContent()
        is UiState.Empty -> showEmpty()
        is UiState.Error -> showError(state.message)
    }
}
