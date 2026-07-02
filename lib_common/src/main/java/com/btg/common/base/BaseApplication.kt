package com.btg.common.base

import android.app.Application
import com.btg.common.BuildConfig
import com.btg.common.app.AppForegroundObserver
import com.btg.common.app.CrashHandler
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy

/**
 * Application 基类：统一初始化日志、全局崩溃捕获、App 前后台监听。
 * 具体 app 的 Application 继承它（并加 @HiltAndroidApp），在 manifest 用 android:name 注册。
 */
open class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initLogger()
        CrashHandler.install()
        AppForegroundObserver.init()
    }

    private fun initLogger() {
        val formatStrategy = PrettyFormatStrategy.newBuilder()
            .showThreadInfo(false)
            .methodCount(0)
            .methodOffset(7)
            .tag("BTG_LOG")
            .build()
        Logger.addLogAdapter(object : AndroidLogAdapter(formatStrategy) {
            override fun isLoggable(priority: Int, tag: String?): Boolean = BuildConfig.DEBUG
        })
    }

    companion object {
        /** 全局 Application 实例（onCreate 后可用）。 */
        lateinit var instance: BaseApplication
            private set
    }
}
