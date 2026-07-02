package com.btg.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** 通用标题栏：左返回、中标题、右操作。程序化构建，无布局资源。 */
class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backView: TextView
    private val titleView: TextView
    private val actionView: TextView

    init {
        minimumHeight = dp(56)

        backView = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            visibility = View.GONE
        }
        addView(
            backView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL),
        )

        titleView = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
        addView(
            titleView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER),
        )

        actionView = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            visibility = View.GONE
        }
        addView(
            actionView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL),
        )
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun showBack(text: CharSequence = "返回", onClick: () -> Unit) {
        backView.text = text
        backView.visibility = View.VISIBLE
        backView.setOnClickListener { onClick() }
    }

    fun setAction(text: CharSequence, onClick: () -> Unit) {
        actionView.text = text
        actionView.visibility = View.VISIBLE
        actionView.setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
