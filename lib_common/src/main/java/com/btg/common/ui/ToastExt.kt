package com.btg.common.ui

import android.content.Context
import android.os.SystemClock
import android.widget.Toast

private var lastToastText: CharSequence? = null
private var lastToastTime = 0L

/** 统一 Toast，防重复刷屏：500ms 内相同内容不重复弹。 */
fun Context.toast(message: CharSequence, long: Boolean = false) {
    val now = SystemClock.elapsedRealtime()
    if (message == lastToastText && now - lastToastTime < 500L) return
    lastToastText = message
    lastToastTime = now
    Toast.makeText(applicationContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
