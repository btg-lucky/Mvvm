package com.btg.news.data.source

import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.google.gson.annotations.SerializedName

/** 列表接口 result：{ stat, data: [...] }，无数据时 data 为 null。 */
data class NewsListResult(
    val data: List<NewsListItemDto>?,
)

data class NewsListItemDto(
    val uniquekey: String?,
    val title: String?,
    val date: String?,
    val category: String?,
    @SerializedName("author_name") val authorName: String?,
    val url: String?,
    @SerializedName("thumbnail_pic_s") val thumbnail: String?,
)

fun NewsListItemDto.toModel(): NewsItem = NewsItem(
    uniquekey = uniquekey.orEmpty(),
    title = title.orEmpty(),
    source = authorName.orEmpty(),
    date = date.orEmpty(),
    category = category.orEmpty(),
    imageUrl = thumbnail?.takeIf { it.isNotBlank() },
    url = url.orEmpty(),
)

/** 详情接口 result：{ uniquekey, content, detail: {...} }。 */
data class NewsDetailResult(
    val uniquekey: String?,
    val content: String?,
    val detail: NewsDetailDto?,
)

data class NewsDetailDto(
    val title: String?,
    val date: String?,
    val category: String?,
    @SerializedName("author_name") val authorName: String?,
    val url: String?,
)

fun NewsDetailResult.toModel(): NewsDetail = NewsDetail(
    title = detail?.title.orEmpty(),
    source = detail?.authorName.orEmpty(),
    date = detail?.date.orEmpty(),
    category = detail?.category.orEmpty(),
    contentHtml = content.orEmpty(),
    url = detail?.url.orEmpty(),
)
