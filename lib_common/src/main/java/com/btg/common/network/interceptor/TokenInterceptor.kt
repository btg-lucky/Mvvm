package com.btg.common.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Token 来源。项目接入真实 token（如从 DataStore 读）时提供实现。 */
fun interface TokenProvider {
    fun token(): String?
}

/** 有 token 时附加认证头（默认 Authorization: Bearer <token>）。 */
class TokenInterceptor(
    private val tokenProvider: TokenProvider,
    private val headerName: String = "Authorization",
    private val scheme: String = "Bearer",
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.token()
        val request = if (token.isNullOrEmpty()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header(headerName, "$scheme $token")
                .build()
        }
        return chain.proceed(request)
    }
}
