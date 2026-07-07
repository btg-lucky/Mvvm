package com.btg.weather.data.source

import com.btg.common.network.AppException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JuheResponseTest {

    @Test
    fun `unwrap returns result on success`() {
        val resp = JuheResponse(errorCode = 0, reason = "查询成功", result = "ok")
        assertEquals("ok", resp.unwrap())
    }

    @Test
    fun `unwrap throws Business with code and reason on error_code non-zero`() {
        val resp = JuheResponse(errorCode = 207301, reason = "错误的查询城市名", result = null)
        val ex = assertThrows(AppException.Business::class.java) { resp.unwrap() }
        assertEquals(207301, ex.code)
        assertEquals("错误的查询城市名", ex.message)
    }

    @Test
    fun `unwrap throws Parse when result null but code success`() {
        val resp = JuheResponse<String>(errorCode = 0, reason = "查询成功", result = null)
        assertThrows(AppException.Parse::class.java) { resp.unwrap() }
    }
}
