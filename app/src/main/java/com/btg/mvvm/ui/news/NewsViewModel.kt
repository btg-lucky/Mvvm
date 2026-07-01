package com.btg.mvvm.ui.news

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.data.repository.NewsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewsViewModel(private val repository: NewsRepository) : BaseViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _events = Channel<NewsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadNews()
    }

    fun loadNews() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getNews()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, items = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.throwable.message)
                }
            }
        }
    }

    fun onNewsClick(item: NewsItem) {
        viewModelScope.launch { _events.send(NewsEvent.OpenLink(item.url)) }
    }
}
