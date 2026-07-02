package com.btg.common.ext

import android.content.res.Resources

/** dp 转 px（Int，四舍五入）。基于系统 displayMetrics，无需 Context。 */
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

/** dp 转 px（Float）。 */
val Float.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density
