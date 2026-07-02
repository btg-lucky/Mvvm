package com.btg.mvvm.ui.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ui.onRefresh
import com.btg.mvvm.databinding.FragmentNewsBinding
import dagger.hilt.android.AndroidEntryPoint

/** 新闻列表演示：Hilt VM + StateLayout 四态 + 下拉刷新 + Coil 图片。 */
@AndroidEntryPoint
class NewsFragment : BaseFragment<FragmentNewsBinding>() {

    private val viewModel: NewsViewModel by viewModels()
    private val newsAdapter = NewsAdapter { viewModel.onNewsClick(it) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = newsAdapter
        binding.stateLayout.setOnRetryListener { viewModel.loadNews() }
        binding.swipeRefresh.onRefresh { viewModel.loadNews() }

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
        viewModel.events.collectOnStarted(viewLifecycleOwner) { handleEvent(it) }
    }

    private fun render(state: NewsUiState) {
        when {
            state.isLoading && state.items.isEmpty() -> binding.stateLayout.showLoading()
            state.errorMessage != null && state.items.isEmpty() ->
                binding.stateLayout.showError(state.errorMessage)
            state.items.isEmpty() -> binding.stateLayout.showEmpty()
            else -> binding.stateLayout.showContent()
        }
        newsAdapter.submitList(state.items)
        if (!state.isLoading) binding.swipeRefresh.isRefreshing = false
    }

    private fun handleEvent(event: NewsEvent) {
        when (event) {
            is NewsEvent.OpenLink ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
        }
    }
}
