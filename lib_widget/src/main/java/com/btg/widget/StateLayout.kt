package com.btg.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 多状态容器：在 加载 / 内容 / 空 / 错误 间切换。
 * XML 里的第一个子 View 视为内容视图；loading/empty/error 默认视图程序化懒创建。
 */
class StateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var contentView: View? = null
    private var loadingView: View? = null
    private var emptyView: View? = null
    private var errorView: View? = null
    private var emptyTextView: TextView? = null
    private var errorTextView: TextView? = null
    private var onRetry: (() -> Unit)? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (contentView == null && childCount > 0) {
            contentView = getChildAt(0)
        }
    }

    /** 代码创建 StateLayout 时指定内容视图。 */
    fun setContentView(view: View) {
        contentView?.let { removeView(it) }
        contentView = view
        if (view.parent == null) addView(view)
        showContent()
    }

    fun setOnRetryListener(listener: () -> Unit) {
        onRetry = listener
    }

    fun showContent() = switchTo(contentView)

    fun showLoading() = switchTo(ensureLoadingView())

    fun showEmpty(message: String = "暂无数据") {
        val view = ensureEmptyView()
        emptyTextView?.text = message
        switchTo(view)
    }

    fun showError(message: String = "加载失败，点击重试") {
        val view = ensureErrorView()
        errorTextView?.text = message
        switchTo(view)
    }

    private fun switchTo(target: View?) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.visibility = if (child === target) View.VISIBLE else View.GONE
        }
    }

    private fun centerParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }

    private fun ensureLoadingView(): View {
        loadingView?.let { return it }
        val container = FrameLayout(context)
        container.addView(
            ProgressBar(context),
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
        addView(container, centerParams())
        loadingView = container
        return container
    }

    private fun ensureEmptyView(): View {
        emptyView?.let { return it }
        val text = buildCenteredText("暂无数据")
        emptyTextView = text
        addView(text, centerParams())
        emptyView = text
        return text
    }

    private fun ensureErrorView(): View {
        errorView?.let { return it }
        val text = buildCenteredText("加载失败，点击重试")
        text.setOnClickListener { onRetry?.invoke() }
        errorTextView = text
        addView(text, centerParams())
        errorView = text
        return text
    }

    private fun buildCenteredText(default: String): TextView = TextView(context).apply {
        text = default
        gravity = Gravity.CENTER
    }
}
