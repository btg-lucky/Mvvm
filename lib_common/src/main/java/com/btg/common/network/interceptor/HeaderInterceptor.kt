package com.btg.common.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** 统一附加公共请求头。 */
class HeaderInterceptor(private val headers: Map<String, String> = emptyMap()) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}
