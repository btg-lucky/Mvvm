package com.btg.news.data.source

import com.btg.news.data.model.NewsItem
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 展示 Retrofit suspend 接口形态。有真实新闻接口后按其响应结构完善 [NewsResponse]。
 */
interface NewsApi {
    @GET("index")
    suspend fun getNews(@Query("type") type: String): NewsResponse
}

/** 占位响应体，待接入真实接口后按后端结构补全字段。 */
data class NewsResponse(
    val items: List<NewsItem> = emptyList()
)
