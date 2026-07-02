package com.btg.common.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** 简单的 Loading 弹窗（不确定进度），程序化构建。 */
class LoadingDialog(context: Context) : Dialog(context) {

    private val messageView: TextView

    init {
        val density = context.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        container.addView(ProgressBar(context))
        messageView = TextView(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
            visibility = View.GONE
        }
        container.addView(messageView)

        setContentView(container)
        setCancelable(false)
    }

    fun show(message: CharSequence?) {
        if (message.isNullOrEmpty()) {
            messageView.visibility = View.GONE
        } else {
            messageView.text = message
            messageView.visibility = View.VISIBLE
        }
        super.show()
    }
}
