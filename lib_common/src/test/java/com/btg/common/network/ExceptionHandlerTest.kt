package com.btg.common.network

import com.google.gson.JsonSyntaxException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ExceptionHandlerTest {

    @Test
    fun `unknown host maps to Network`() {
        val result = ExceptionHandler.handle(UnknownHostException("no dns"))
        assertTrue(result is AppException.Network)
    }

    @Test
    fun `socket timeout maps to Timeout`() {
        val result = ExceptionHandler.handle(SocketTimeoutException())
        assertTrue(result is AppException.Timeout)
    }

    @Test
    fun `http exception maps to Server with code`() {
        val body = "err".toResponseBody(null)
        val httpEx = HttpException(Response.error<Any>(503, body))
        val result = ExceptionHandler.handle(httpEx)
        assertTrue(result is AppException.Server)
        assertEquals(503, (result as AppException.Server).httpCode)
    }

    @Test
    fun `json syntax maps to Parse`() {
        val result = ExceptionHandler.handle(JsonSyntaxException("bad json"))
        assertTrue(result is AppException.Parse)
    }

    @Test
    fun `existing AppException is returned as-is`() {
        val original = AppException.Business(401, "未登录")
        assertSame(original, ExceptionHandler.handle(original))
    }

    @Test
    fun `other throwable maps to Unknown`() {
        val result = ExceptionHandler.handle(IllegalStateException("boom"))
        assertTrue(result is AppException.Unknown)
    }
}
