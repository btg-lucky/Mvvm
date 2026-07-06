package com.btg.news.data.model

data class NewsDetail(
    val title: String,
    val source: String,
    val date: String,
    val category: String,
    /** 接口返回的正文 HTML 片段。 */
    val contentHtml: String,
    /** 原文链接，详情缺失时的降级入口。 */
    val url: String
)
