package com.btg.common.ui

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** 设置下拉刷新回调。 */
fun SwipeRefreshLayout.onRefresh(listener: () -> Unit) {
    setOnRefreshListener { listener() }
}

/**
 * RecyclerView 上拉加载更多：滚动到距底部 [threshold] 项内且 [canLoadMore] 为真时触发 [onLoadMore]。
 * canLoadMore 由调用方控制（加载中 / 无更多数据时返回 false），避免重复触发。
 * 仅支持 LinearLayoutManager。
 */
fun RecyclerView.onLoadMore(
    threshold: Int = 3,
    canLoadMore: () -> Boolean,
    onLoadMore: () -> Unit,
) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            if (!canLoadMore()) return
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            val total = layoutManager.itemCount
            if (lastVisible >= total - 1 - threshold) {
                onLoadMore()
            }
        }
    })
}
