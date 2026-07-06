package com.btg.news.ui.detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.fragment.app.viewModels
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.result.UiState
import com.btg.news.data.model.NewsDetail
import com.btg.news.databinding.FragmentNewsDetailBinding
import dagger.hilt.android.AndroidEntryPoint

/** 新闻详情：原生头部 + WebView 渲染 content HTML；详情缺失时降级加载原文 url。 */
@AndroidEntryPoint
class NewsDetailFragment : BaseFragment<FragmentNewsDetailBinding>() {

    private val viewModel: NewsDetailViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsDetailBinding.inflate(inflater, container, false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 降级加载原文网页需要 JS；只加载可信新闻源链接
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = WebViewClient()

        viewModel.detailState.collectOnStarted(viewLifecycleOwner) { render(it) }
    }

    private fun render(state: UiState<NewsDetail>) {
        when (state) {
            is UiState.Loading -> binding.stateLayout.showLoading()
            is UiState.Success -> showDetail(state.data)
            is UiState.Error, is UiState.Empty -> fallbackToUrl()
        }
    }

    private fun showDetail(detail: NewsDetail) {
        binding.stateLayout.showContent()
        binding.titleText.text = detail.title
        binding.metaText.text = listOf(detail.category, detail.source)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        binding.dateText.text = detail.date
        // content 中图片多为 // 协议相对地址，baseUrl 用 https: 使其可加载
        binding.webView.loadDataWithBaseURL("https:", wrapHtml(detail.contentHtml), "text/html", "utf-8", null)
    }

    /** 详情接口失败（如 223502 查不到详情）时，降级用 WebView 直接加载原文。 */
    private fun fallbackToUrl() {
        val url = arguments?.getString(NewsDetailArgs.URL).orEmpty()
        if (url.isBlank()) {
            binding.stateLayout.showError("详情加载失败")
            binding.stateLayout.setOnRetryListener { viewModel.load() }
            return
        }
        binding.stateLayout.showContent()
        binding.titleText.text = arguments?.getString(NewsDetailArgs.TITLE).orEmpty()
        binding.metaText.text = ""
        binding.dateText.text = ""
        binding.webView.loadUrl(url)
    }

    private fun wrapHtml(content: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { margin: 12px; font-size: 16px; line-height: 1.6; word-wrap: break-word; }
                img { max-width: 100%; height: auto; }
            </style>
        </head>
        <body>$content</body>
        </html>
    """.trimIndent()

    override fun onDestroyView() {
        // WebView 需在 binding 释放前手动销毁，避免泄漏
        binding.webView.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroyView()
    }
}
