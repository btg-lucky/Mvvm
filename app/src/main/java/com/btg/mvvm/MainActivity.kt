package com.btg.mvvm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.btg.common.base.BaseActivity
import com.btg.mvvm.data.repository.NewsRepository
import com.btg.mvvm.data.source.FakeNewsDataSource
import com.btg.mvvm.databinding.ActivityMainBinding
import com.btg.mvvm.ui.news.NewsAdapter
import com.btg.mvvm.ui.news.NewsEvent
import com.btg.mvvm.ui.news.NewsUiState
import com.btg.mvvm.ui.news.NewsViewModel
import com.btg.mvvm.ui.news.NewsViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val viewModel: NewsViewModel by viewModels {
        // 手动 DI 唯一装配点：将来接真实接口时把 FakeNewsDataSource 换成 RemoteNewsDataSource。
        NewsViewModelFactory(NewsRepository(FakeNewsDataSource()))
    }

    private val newsAdapter = NewsAdapter { viewModel.onNewsClick(it) }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = newsAdapter
        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: NewsUiState) {
        binding.progressBar.isVisible = state.isLoading
        binding.errorText.isVisible = state.errorMessage != null
        newsAdapter.submitList(state.items)
    }

    private fun handleEvent(event: NewsEvent) {
        when (event) {
            is NewsEvent.OpenLink ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
        }
    }
}
