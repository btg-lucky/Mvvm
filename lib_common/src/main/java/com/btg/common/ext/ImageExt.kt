package com.btg.common.ext

import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil.load

/** Coil 加载网络图片，带淡入；placeholder/error 传 0 表示不设置。 */
fun ImageView.loadUrl(
    url: String?,
    @DrawableRes placeholder: Int = 0,
    @DrawableRes error: Int = 0,
) {
    load(url) {
        crossfade(true)
        if (placeholder != 0) placeholder(placeholder)
        if (error != 0) error(error)
    }
}
