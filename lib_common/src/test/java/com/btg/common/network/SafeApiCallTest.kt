package com.btg.common.network

import com.btg.common.result.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class SafeApiCallTest {

    @Test
    fun `returns Success when block succeeds`() = runTest {
        val result = safeApiCall { "ok" }
        assertEquals(ApiResult.Success("ok"), result)
    }

    @Test
    fun `returns Error with mapped AppException when block throws`() = runTest {
        val result = safeApiCall { throw SocketTimeoutException() }
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).throwable is AppException.Timeout)
    }

    @Test
    fun `keeps business exception from unwrap`() = runTest {
        val result = safeApiCall { BaseResponse(code = 1, message = "失败", data = null).unwrap() }
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).throwable is AppException.Business)
    }
}
