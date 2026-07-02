# 框架能力补齐 · 阶段 5（可靠性）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全局崩溃捕获 + App 前后台监听，并把最后一个 Java 文件 `BaseApplication.java` 转成 Kotlin（收官后工程零 Java），在 `BaseApplication.onCreate` 里统一初始化日志/崩溃捕获/前后台监听。

**Architecture:** 崩溃捕获与前后台监听放 `lib_common/app`；`BaseApplication`（Kotlin）作为统一初始化入口，app 的 `App` 继承它。依赖 Android framework，只做编译验证 + 冒烟。

**Tech Stack:** Kotlin / lifecycle-process（ProcessLifecycleOwner）/ Logger / 协程 StateFlow。

## Global Constraints

- 语言全 Kotlin；本阶段结束后工程零 Java。
- 新代码放 `com.btg.common.app`。
- 不加新依赖（lifecycle-process 已在 Phase 0 聚合）。
- 不改 `minSdk`/`targetSdk`/`compileSdk`。
- 每任务末尾提交（Conventional Commits），message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 工作分支：`feat/framework-buildout`。

---

### Task 1: 全局崩溃捕获 + App 前后台监听

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/app/CrashHandler.kt`
- Create: `lib_common/src/main/java/com/btg/common/app/AppForegroundObserver.kt`

**Interfaces:**
- Produces:
  - `class CrashHandler` + `CrashHandler.install(onCrash)`——设为默认未捕获异常处理器，记录日志后回调并转交原处理器。
  - `object AppForegroundObserver`：`isForeground: StateFlow<Boolean>` + `init()`（注册到 ProcessLifecycleOwner）。

- [ ] **Step 1: 创建 CrashHandler**

创建 `lib_common/src/main/java/com/btg/common/app/CrashHandler.kt`：

```kotlin
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
```

- [ ] **Step 2: 创建 AppForegroundObserver**

创建 `lib_common/src/main/java/com/btg/common/app/AppForegroundObserver.kt`：

```kotlin
package com.btg.common.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App 前后台状态监听（基于 ProcessLifecycleOwner）。
 * isForeground=true 表示应用处于前台。init() 需在 Application.onCreate 调用一次。
 */
object AppForegroundObserver : DefaultLifecycleObserver {

    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/app/
git commit -m "feat: add global crash handler and app foreground observer

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: BaseApplication 转 Kotlin 并统一初始化

**Files:**
- Delete: `lib_common/src/main/java/com/btg/common/base/BaseApplication.java`
- Create: `lib_common/src/main/java/com/btg/common/base/BaseApplication.kt`

**Interfaces:**
- Consumes: `CrashHandler`/`AppForegroundObserver`（Task 1）、Logger、`com.btg.common.BuildConfig`。
- Produces: `open class BaseApplication : Application()`——onCreate 初始化日志（PrettyFormatStrategy + tag BTG_LOG，仅 DEBUG）、安装崩溃处理器、启动前后台监听；`companion object { instance }` 提供全局 Application。app 的 `App` 继续继承它（FQN 不变，App.kt 无需改）。

- [ ] **Step 1: 删除 Java 版**

```bash
git rm lib_common/src/main/java/com/btg/common/base/BaseApplication.java
```

- [ ] **Step 2: 创建 Kotlin 版**

创建 `lib_common/src/main/java/com/btg/common/base/BaseApplication.kt`：

```kotlin
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
```

- [ ] **Step 3: 编译验证（含 app，确认 App 继承与 Hilt 正常）**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 确认工程零 Java**

Run: `find . -name "*.java" -not -path "*/build/*" -not -path "*/.gradle/*"`
Expected: 无输出（全部 Kotlin）。

- [ ] **Step 5: 提交**

```bash
git add -A lib_common/src/main/java/com/btg/common/base/
git commit -m "refactor: convert BaseApplication to Kotlin, wire logger/crash/foreground init

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 全量编译 + 单测 + 真机冒烟

**Files:** 无改动（纯验证）。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 全部单元测试**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`（既有测试全绿）。

- [ ] **Step 3: 真机冒烟**

Run: `./gradlew installDebug`
手动：启动 app，确认正常、Logcat 有 BTG_LOG 初始化正常、无崩溃。
Expected: 正常启动。（无设备时跳过并注明。）

- [ ] **Step 4: 收尾**

无代码改动则不提交，记录验证结果。

---

## Self-Review（对照 spec 第 7 节可靠性）

- ✅ 全局崩溃捕获（Task 1 CrashHandler）。
- ✅ App 前后台监听（Task 1 AppForegroundObserver）。
- ✅ BaseApplication 转 Kotlin + 统一初始化（Task 2）；收官后工程零 Java。
- 阶段边界：只做可靠性 + BaseApplication 转换；演示台留阶段 6。
