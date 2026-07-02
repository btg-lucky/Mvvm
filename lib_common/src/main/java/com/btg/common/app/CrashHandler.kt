package com.btg.common.app

import com.orhanobut.logger.Logger

/**
 * 全局未捕获异常兜底：记录日志、回调（可上报），再转交系统默认处理器（保留崩溃行为）。
 */
class CrashHandler private constructor(
    private val onCrash: ((Thread, Throwable) -> Unit)?,
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Logger.e(throwable, "Uncaught exception on thread ${thread.name}")
        runCatching { onCrash?.invoke(thread, throwable) }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        /** 安装全局崩溃处理器。onCrash 可用于崩溃上报（本框架不内置上报 SDK）。 */
        fun install(onCrash: ((Thread, Throwable) -> Unit)? = null) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(onCrash))
        }
    }
}
