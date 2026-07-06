package com.btg.news.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ui.onLoadMore
import com.btg.common.ui.onRefresh
import com.btg.common.ui.toast
import com.btg.news.R
import com.btg.news.data.model.NewsCategory
import com.btg.news.databinding.FragmentNewsListBinding
import com.btg.news.ui.detail.NewsDetailArgs
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

/** 新闻列表：分类 Tab + 下拉刷新 + 上拉分页 + StateLayout 四态。 */
@AndroidEntryPoint
class NewsListFragment : BaseFragment<FragmentNewsListBinding>() {

    private val viewModel: NewsListViewModel by viewModels()
    private val newsAdapter = NewsAdapter { item ->
        findNavController().navigate(R.id.action_newsList_to_newsDetail, NewsDetailArgs.of(item))
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsListBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupList()

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }

    private fun setupTabs() {
        NewsCategory.entries.forEach { category ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(category.label))
        }
        binding.tabLayout.getTabAt(viewModel.uiState.value.category.ordinal)?.select()
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectCategory(NewsCategory.entries[tab.position])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupList() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = newsAdapter
        binding.stateLayout.setOnRetryListener { viewModel.refresh() }
        binding.swipeRefresh.onRefresh { viewModel.refresh() }
        binding.recyclerView.onLoadMore(
            canLoadMore = {
                val s = viewModel.uiState.value
                !s.isRefreshing && !s.isLoadingMore && !s.noMoreData
            },
            onLoadMore = { viewModel.loadMore() },
        )
    }

    private fun render(state: NewsListUiState) {
        binding.swipeRefresh.isRefreshing = state.isRefreshing && state.items.isNotEmpty()
        when {
            state.isRefreshing && state.items.isEmpty() -> binding.stateLayout.showLoading()
            state.errorMessage != null && state.items.isEmpty() ->
                binding.stateLayout.showError(state.errorMessage)
            state.items.isEmpty() && !state.isRefreshing -> binding.stateLayout.showEmpty()
            else -> binding.stateLayout.showContent()
        }
        newsAdapter.submitList(state.items)
    }
}
