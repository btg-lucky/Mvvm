package com.btg.news.ui.list

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsCategory
import com.btg.news.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NewsListViewModel @Inject constructor(
    private val repository: NewsRepository,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(NewsListUiState())
    val uiState: StateFlow<NewsListUiState> = _uiState.asStateFlow()

    /** 当前已加载到的页码（1 起）。 */
    private var page = 1

    init {
        refresh()
    }

    fun selectCategory(category: NewsCategory) {
        if (category == _uiState.value.category) return
        _uiState.update {
            it.copy(category = category, items = emptyList(), noMoreData = false, errorMessage = null)
        }
        refresh()
    }

    fun refresh() {
        val category = _uiState.value.category
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.getNews(category.type, 1)
            // 防串台：结果返回时若分类已切换，丢弃过期结果
            if (_uiState.value.category != category) return@launch
            when (result) {
                is ApiResult.Success -> {
                    page = 1
                    _uiState.update {
                        it.copy(isRefreshing = false, items = result.data, noMoreData = result.data.isEmpty())
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isRefreshing = false, errorMessage = result.throwable.message ?: "加载失败")
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoadingMore || state.noMoreData || page >= MAX_PAGE) return
        val category = state.category
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result = repository.getNews(category.type, page + 1)
            if (_uiState.value.category != category) return@launch
            when (result) {
                is ApiResult.Success -> {
                    page += 1
                    _uiState.update {
                        it.copy(isLoadingMore = false, items = it.items + result.data, noMoreData = result.data.isEmpty())
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    postError(result.throwable.message ?: "加载失败")
                }
            }
        }
    }

    private companion object {
        /** 聚合接口 page 上限。 */
        const val MAX_PAGE = 50
    }
}
