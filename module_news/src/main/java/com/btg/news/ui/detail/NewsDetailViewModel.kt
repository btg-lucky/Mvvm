package com.btg.news.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.btg.common.base.BaseViewModel
import com.btg.common.result.UiState
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NewsRepository,
) : BaseViewModel() {

    private val uniquekey: String = savedStateHandle[NewsDetailArgs.UNIQUEKEY] ?: ""

    private val _detailState = MutableStateFlow<UiState<NewsDetail>>(UiState.Loading)
    val detailState: StateFlow<UiState<NewsDetail>> = _detailState.asStateFlow()

    init {
        load()
    }

    fun load() = launchWithState(_detailState) { repository.getNewsDetail(uniquekey) }
}
