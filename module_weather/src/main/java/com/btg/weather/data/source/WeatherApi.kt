package com.btg.weather.data.source

import retrofit2.http.GET
import retrofit2.http.Query

/** 聚合数据「天气预报」接口。baseUrl = https://apis.juhe.cn/ */
interface WeatherApi {

    @GET("simpleWeather/query")
    suspend fun query(
        @Query("key") key: String,
        @Query("city") city: String,
    ): JuheResponse<WeatherQueryResult>

    @GET("simpleWeather/life")
    suspend fun life(
        @Query("key") key: String,
        @Query("city") city: String,
    ): JuheResponse<WeatherLifeResult>
}
