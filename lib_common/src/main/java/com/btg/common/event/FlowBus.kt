package com.btg.common.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 Flow 的全局事件总线，替代 EventBus。按事件运行时类型分流。
 *
 * 语义：replay = 0（不重放给后来的订阅者），DROP_OLDEST（发送端不阻塞、无订阅者时丢弃）。
 * 适用于真正跨页面/跨组件的全局事件；页面内一次性事件仍优先用 ViewModel 的事件通道。
 */
object FlowBus {

    private val flows = ConcurrentHashMap<Class<*>, MutableSharedFlow<Any>>()

    private fun flowFor(clazz: Class<*>): MutableSharedFlow<Any> =
        flows.getOrPut(clazz) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }

    /** 挂起发送事件。 */
    suspend fun <T : Any> post(event: T) {
        flowFor(event.javaClass).emit(event)
    }

    /** 非挂起发送事件，返回是否成功投递到缓冲/订阅者。 */
    fun <T : Any> tryPost(event: T): Boolean = flowFor(event.javaClass).tryEmit(event)

    /** 订阅指定类型的事件流。 */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> subscribe(clazz: Class<T>): Flow<T> = flowFor(clazz).asSharedFlow() as Flow<T>

    /** 订阅指定类型的事件流（reified 便捷版）。 */
    inline fun <reified T : Any> subscribe(): Flow<T> = subscribe(T::class.java)
}
