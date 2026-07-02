package com.btg.common.ui

import android.app.Activity
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 设置状态栏颜色与图标明暗。lightIcons=true 表示深色图标（浅色背景时用）。 */
fun Activity.setStatusBar(@ColorInt color: Int, lightIcons: Boolean) {
    window.statusBarColor = color
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightIcons
}

/** 透明状态栏 + 内容延伸到状态栏后（edge-to-edge）。布局需自行处理 insets。 */
fun Activity.transparentStatusBar(lightIcons: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightIcons
}
