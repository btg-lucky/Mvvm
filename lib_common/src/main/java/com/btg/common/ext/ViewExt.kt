package com.btg.common.ext

import android.os.SystemClock
import android.view.View

/** 显示 View。 */
fun View.visible() {
    visibility = View.VISIBLE
}

/** 占位隐藏（仍占布局空间）。 */
fun View.invisible() {
    visibility = View.INVISIBLE
}

/** 隐藏并不占布局空间。 */
fun View.gone() {
    visibility = View.GONE
}

/**
 * 防抖点击：默认 500ms 内的重复点击被忽略，避免连点触发多次。
 */
fun View.setOnDebouncedClickListener(intervalMs: Long = 500L, action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener { v ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime >= intervalMs) {
            lastClickTime = now
            action(v)
        }
    }
}
