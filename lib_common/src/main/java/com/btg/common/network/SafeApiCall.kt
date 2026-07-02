package com.btg.common.network

import com.btg.common.result.ApiResult

/**
 * 统一安全调用：执行挂起 [block]，成功包成 [ApiResult.Success]，
 * 抛出的任何异常经 [ExceptionHandler] 映射为 [AppException] 后包成 [ApiResult.Error]。
 * Repository 一行调用：safeApiCall { api.xxx().unwrap() }。
 */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> =
    runCatching { block() }
        .fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = { ApiResult.Error(ExceptionHandler.handle(it)) },
        )
