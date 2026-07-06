package com.btg.news.data.source

import com.btg.common.network.AppException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JuheResponseTest {

    @Test
    fun `unwrap returns result when error_code is 0`() {
        val response = JuheResponse(errorCode = 0, reason = "success", result = "data")
        assertEquals("data", response.unwrap())
    }

    @Test
    fun `unwrap throws Business when error_code is not 0`() {
        val response = JuheResponse<String>(errorCode = 10012, reason = "请求超过次数限制", result = null)
        val e = assertThrows(AppException.Business::class.java) { response.unwrap() }
        assertEquals(10012, e.code)
        assertEquals("请求超过次数限制", e.message)
    }

    @Test
    fun `unwrap throws Parse when result is null on success code`() {
        val response = JuheResponse<String>(errorCode = 0, reason = "success", result = null)
        assertThrows(AppException.Parse::class.java) { response.unwrap() }
    }
}
