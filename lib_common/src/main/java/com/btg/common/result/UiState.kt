package com.btg.common.result

/**
 * UI 层状态四态，供 View 用 when 穷尽渲染（加载 / 内容 / 空 / 错误）。
 *
 * 简单单次内容页用它；需要"已有数据 + 正在刷新"组合态的复杂列表页，
 * 可另用 data class 快照（见 app 的 NewsUiState）。
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}

/**
 * 把数据层的 [ApiResult] 映射为 UI 层 [UiState]（不做空判定）。
 * 注：阶段 2 引入 ExceptionHandler 后，Error 文案会是本地化的友好文案，
 * 此处的 fallbackMessage 仅为 throwable.message 为 null 时的兜底。
 */
fun <T> ApiResult<T>.toUiState(fallbackMessage: String = "加载失败"): UiState<T> = when (this) {
    is ApiResult.Success -> UiState.Success(data)
    is ApiResult.Error -> UiState.Error(throwable.message ?: fallbackMessage)
}

/**
 * 列表专用映射：成功且非空 → Success；成功但空列表 → Empty；失败 → Error。
 */
fun <E> ApiResult<List<E>>.toListUiState(fallbackMessage: String = "加载失败"): UiState<List<E>> =
    when (this) {
        is ApiResult.Success -> if (data.isEmpty()) UiState.Empty else UiState.Success(data)
        is ApiResult.Error -> UiState.Error(throwable.message ?: fallbackMessage)
    }
