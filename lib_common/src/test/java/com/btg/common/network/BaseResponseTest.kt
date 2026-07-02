package com.btg.common.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseResponseTest {

    @Test
    fun `unwrap returns data on success code`() {
        val resp = BaseResponse(code = 0, message = "ok", data = "payload")
        assertEquals("payload", resp.unwrap())
    }

    @Test
    fun `unwrap throws Business on non-success code`() {
        val resp = BaseResponse(code = 401, message = "未登录", data = null)
        val ex = assertThrows(AppException.Business::class.java) { resp.unwrap() }
        assertEquals(401, ex.code)
        assertEquals("未登录", ex.message)
    }

    @Test
    fun `unwrap throws Parse when success but data is null`() {
        val resp = BaseResponse<String>(code = 0, message = "ok", data = null)
        assertThrows(AppException.Parse::class.java) { resp.unwrap() }
    }

    @Test
    fun `unwrap honors custom success code`() {
        val resp = BaseResponse(code = 200, message = "ok", data = 42)
        assertEquals(42, resp.unwrap(successCode = 200))
    }
}
