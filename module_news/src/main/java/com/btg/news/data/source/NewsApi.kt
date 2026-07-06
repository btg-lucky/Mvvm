package com.btg.news.data.source

import retrofit2.http.GET
import retrofit2.http.Query

/** 聚合数据「新闻头条」接口。baseUrl = https://v.juhe.cn/ */
interface NewsApi {

    @GET("toutiao/index")
    suspend fun getNewsList(
        @Query("key") key: String,
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("is_filter") isFilter: Int = 1,
    ): JuheResponse<NewsListResult>

    @GET("toutiao/content")
    suspend fun getNewsDetail(
        @Query("key") key: String,
        @Query("uniquekey") uniquekey: String,
    ): JuheResponse<NewsDetailResult>
}
