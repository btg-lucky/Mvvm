package com.btg.mvvm

import com.btg.common.base.BaseApplication
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口 Application。
 *
 * 继承 lib_common 的 [BaseApplication]（日志初始化、后续挂前后台监听/崩溃捕获），
 * 并以 @HiltAndroidApp 生成 Hilt 根组件。必须在 AndroidManifest 里用 android:name 注册，
 * 否则运行时用的是默认 Application，BaseApplication.getApplication() 会返回 null。
 */
@HiltAndroidApp
class App : BaseApplication()
