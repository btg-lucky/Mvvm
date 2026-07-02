package com.btg.common.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionResultTest {

    @Test
    fun `allGranted is true when every permission granted`() {
        val result = PermissionResult(mapOf("A" to true, "B" to true))
        assertTrue(result.allGranted)
        assertTrue(result.denied.isEmpty())
    }

    @Test
    fun `allGranted is false when any permission denied`() {
        val result = PermissionResult(mapOf("A" to true, "B" to false))
        assertFalse(result.allGranted)
    }

    @Test
    fun `denied lists only ungranted permissions`() {
        val result = PermissionResult(mapOf("A" to true, "B" to false, "C" to false))
        assertEquals(listOf("B", "C"), result.denied)
    }

    @Test
    fun `empty result is treated as all granted`() {
        val result = PermissionResult(emptyMap())
        assertTrue(result.allGranted)
    }
}
