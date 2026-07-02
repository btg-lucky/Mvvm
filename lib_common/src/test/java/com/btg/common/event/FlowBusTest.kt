package com.btg.common.event

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowBusTest {

    private data class Ping(val n: Int)

    @Test
    fun `subscriber receives posted event`() = runTest {
        val received = mutableListOf<Ping>()
        val job = launch { FlowBus.subscribe<Ping>().take(1).collect { received.add(it) } }
        runCurrent() // 让收集者先完成订阅
        FlowBus.post(Ping(7))
        job.join()
        assertEquals(listOf(Ping(7)), received)
    }

    @Test
    fun `tryPost with no subscriber does not throw and returns true`() {
        val delivered = FlowBus.tryPost(Ping(1))
        assertTrue(delivered)
    }
}
