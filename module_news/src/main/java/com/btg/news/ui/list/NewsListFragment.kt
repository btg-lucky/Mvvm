package com.btg.news.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ui.onRefresh
import com.btg.news.databinding.FragmentNewsListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewsListFragment : BaseFragment<FragmentNewsListBinding>() {

    private val viewModel: NewsListViewModel by viewModels()
    private val newsAdapter = NewsAdapter { }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsListBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = newsAdapter
        binding.stateLayout.setOnRetryListener { viewModel.refresh() }
        binding.swipeRefresh.onRefresh { viewModel.refresh() }

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
    }

    private fun render(state: NewsListUiState) {
        when {
            state.isRefreshing && state.items.isEmpty() -> binding.stateLayout.showLoading()
            state.errorMessage != null && state.items.isEmpty() ->
                binding.stateLayout.showError(state.errorMessage)
            state.items.isEmpty() -> binding.stateLayout.showEmpty()
            else -> binding.stateLayout.showContent()
        }
        newsAdapter.submitList(state.items)
        if (!state.isRefreshing) binding.swipeRefresh.isRefreshing = false
    }
}
