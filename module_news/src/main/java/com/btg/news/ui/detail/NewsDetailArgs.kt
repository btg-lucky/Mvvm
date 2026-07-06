package com.btg.news.ui.detail

import android.os.Bundle
import androidx.core.os.bundleOf
import com.btg.news.data.model.NewsItem

/** 详情页导航参数。ViewModel 通过 SavedStateHandle 用同名 key 读取。 */
object NewsDetailArgs {
    const val UNIQUEKEY = "uniquekey"
    const val TITLE = "title"
    const val URL = "url"

    fun of(item: NewsItem): Bundle = bundleOf(
        UNIQUEKEY to item.uniquekey,
        TITLE to item.title,
        URL to item.url,
    )
}
