package com.btg.common.network

import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把各种底层 Throwable 映射为带友好文案的 [AppException]。
 * 已是 AppException（如 unwrap 抛出的 Business/Parse）则原样返回。
 * 文案为框架默认值；需本地化的项目可在此基础上再包一层。
 */
object ExceptionHandler {

    fun handle(throwable: Throwable): AppException = when (throwable) {
        is AppException -> throwable
        is SocketTimeoutException -> AppException.Timeout("网络请求超时，请稍后重试", throwable)
        is UnknownHostException, is ConnectException ->
            AppException.Network("网络连接失败，请检查网络", throwable)
        is HttpException -> AppException.Server(throwable.code(), "服务器异常(${throwable.code()})", throwable)
        is JsonSyntaxException, is JsonParseException, is JsonIOException ->
            AppException.Parse("数据解析失败", throwable)
        is IOException -> AppException.Network("网络异常，请检查网络", throwable)
        else -> AppException.Unknown(throwable.message ?: "未知错误", throwable)
    }
}
