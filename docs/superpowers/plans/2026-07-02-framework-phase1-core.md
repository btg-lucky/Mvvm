# 框架能力补齐 · 阶段 1（核心基类 + 结果状态 + 扩展 + 事件总线）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `lib_common` 铺好框架的 UI 层地基——`UiState<T>` 四态 + `ApiResult→UiState` 映射、`BaseViewModel` 帮手（`launchWithState`/统一错误事件）、`BaseFragment`（binding 自动释放）、常用扩展函数集、Flow 事件总线；纯逻辑部分用单测覆盖。

**Architecture:** 全部新代码放 `lib_common`，Kotlin。可纯 JVM 测的部分（状态映射、事件总线、ViewModel 帮手）走 TDD；依赖 Android framework 的部分（Fragment、View/尺寸扩展、生命周期收集）只做编译验证，交后续演示台手动验证。不触碰现有 news 业务代码（它到阶段 6 才迁移到这些新基建）。

**Tech Stack:** Kotlin 2.0.21 / 协程 + Flow / AndroidX lifecycle / JUnit4 + kotlinx-coroutines-test。

## Global Constraints

- 语言全 Kotlin；本阶段不转换任何现有 Java 文件。
- 不改 `minSdk`/`targetSdk`/`compileSdk`/`applicationId`。
- 加依赖只动 `gradle/libs.versions.toml` 的既有别名（本阶段不新增 catalog 条目，只在 `lib_common` 引用已声明别名）。
- 依赖方向不变；`lib_common` 用 `api` 暴露给上层的能力用 `api`，仅内部用的用 `implementation`。
- 公共 API 显式可见性；优先 `val`、空安全，避免 `!!`。
- 测试延续现状：`runTest` + 手写 fake，不引 mockk/mockito/Turbine/Robolectric。
- 每个任务末尾提交（Conventional Commits，祈使句），message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 工作分支：`feat/framework-buildout`（延续阶段 0）。

---

### Task 1: UiState<T> 四态 + ApiResult→UiState 映射（TDD）

**Files:**
- Modify: `lib_common/build.gradle.kts`（加 `testImplementation(libs.junit)`）
- Create: `lib_common/src/test/java/com/btg/common/result/UiStateMappingTest.kt`
- Create: `lib_common/src/main/java/com/btg/common/result/UiState.kt`

**Interfaces:**
- Consumes: 现有 `com.btg.common.result.ApiResult`（`Success<T>(val data: T)` / `Error(val throwable: Throwable)`）。
- Produces:
  - `sealed interface UiState<out T>`，成员 `Loading`（data object）、`Success<T>(val data: T)`、`Empty`（data object）、`Error(val message: String)`。
  - `fun <T> ApiResult<T>.toUiState(fallbackMessage: String = "加载失败"): UiState<T>`
  - `fun <E> ApiResult<List<E>>.toListUiState(fallbackMessage: String = "加载失败"): UiState<List<E>>`（空列表 → `Empty`）

- [ ] **Step 1: 给 lib_common 加 JUnit 测试依赖**

在 `lib_common/build.gradle.kts` 的 `dependencies { }` 块末尾（`ksp(libs.hilt.compiler)` 之后）追加：

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 2: 写失败测试**

创建 `lib_common/src/test/java/com/btg/common/result/UiStateMappingTest.kt`：

```kotlin
package com.btg.common.result

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateMappingTest {

    @Test
    fun `toListUiState maps non-empty success to Success`() {
        val result: ApiResult<List<Int>> = ApiResult.Success(listOf(1, 2, 3))
        assertEquals(UiState.Success(listOf(1, 2, 3)), result.toListUiState())
    }

    @Test
    fun `toListUiState maps empty success to Empty`() {
        val result: ApiResult<List<Int>> = ApiResult.Success(emptyList())
        assertEquals(UiState.Empty, result.toListUiState())
    }

    @Test
    fun `toListUiState maps error to Error with throwable message`() {
        val result: ApiResult<List<Int>> = ApiResult.Error(RuntimeException("boom"))
        assertEquals(UiState.Error("boom"), result.toListUiState())
    }

    @Test
    fun `toListUiState uses fallback when throwable message is null`() {
        val result: ApiResult<List<Int>> = ApiResult.Error(RuntimeException())
        assertEquals(UiState.Error("加载失败"), result.toListUiState())
    }

    @Test
    fun `toUiState maps success to Success without empty check`() {
        val result: ApiResult<String> = ApiResult.Success("ok")
        assertEquals(UiState.Success("ok"), result.toUiState())
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.result.UiStateMappingTest"`
Expected: 编译失败，`unresolved reference: UiState` / `toListUiState`（实现尚未创建）。

- [ ] **Step 4: 实现 UiState 与映射扩展**

创建 `lib_common/src/main/java/com/btg/common/result/UiState.kt`：

```kotlin
package com.btg.common.result

/**
 * UI 层状态四态，供 View 用 when 穷尽渲染（加载 / 内容 / 空 / 错误）。
 *
 * 简单单次内容页用它；需要"已有数据 + 正在刷新"组合态的复杂列表页，
 * 可另用 data class 快照（见 app 的 NewsUiState）。
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}

/**
 * 把数据层的 [ApiResult] 映射为 UI 层 [UiState]（不做空判定）。
 * 注：阶段 2 引入 ExceptionHandler 后，Error 文案会是本地化的友好文案，
 * 此处的 fallbackMessage 仅为 throwable.message 为 null 时的兜底。
 */
fun <T> ApiResult<T>.toUiState(fallbackMessage: String = "加载失败"): UiState<T> = when (this) {
    is ApiResult.Success -> UiState.Success(data)
    is ApiResult.Error -> UiState.Error(throwable.message ?: fallbackMessage)
}

/**
 * 列表专用映射：成功且非空 → Success；成功但空列表 → Empty；失败 → Error。
 */
fun <E> ApiResult<List<E>>.toListUiState(fallbackMessage: String = "加载失败"): UiState<List<E>> =
    when (this) {
        is ApiResult.Success -> if (data.isEmpty()) UiState.Empty else UiState.Success(data)
        is ApiResult.Error -> UiState.Error(throwable.message ?: fallbackMessage)
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.result.UiStateMappingTest"`
Expected: `BUILD SUCCESSFUL`，5 个测试全通过。

- [ ] **Step 6: 提交**

```bash
git add lib_common/build.gradle.kts lib_common/src/main/java/com/btg/common/result/UiState.kt lib_common/src/test/java/com/btg/common/result/UiStateMappingTest.kt
git commit -m "feat: add UiState<T> four states and ApiResult->UiState mapping

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: FlowBus 事件总线（TDD）

**Files:**
- Modify: `lib_common/build.gradle.kts`（加 `api(libs.kotlinx.coroutines.android)` 与 `testImplementation(libs.kotlinx.coroutines.test)`）
- Create: `lib_common/src/test/java/com/btg/common/event/FlowBusTest.kt`
- Create: `lib_common/src/main/java/com/btg/common/event/FlowBus.kt`

**Interfaces:**
- Consumes: kotlinx-coroutines（`SharedFlow`/`MutableSharedFlow`）。
- Produces: `object FlowBus`，方法——
  - `suspend fun <T : Any> post(event: T)`
  - `fun <T : Any> tryPost(event: T): Boolean`
  - `fun <T : Any> subscribe(clazz: Class<T>): Flow<T>`
  - `inline fun <reified T : Any> subscribe(): Flow<T>`
  - 事件按运行时 class 分流；`replay = 0`（不重放给后订阅者），`DROP_OLDEST` 溢出策略（发送不阻塞）。

- [ ] **Step 1: 给 lib_common 加协程依赖**

在 `lib_common/build.gradle.kts` 的 `dependencies { }` 块内，`api(libs.androidx.lifecycle.viewmodel.ktx)` 之后追加：

```kotlin
    api(libs.kotlinx.coroutines.android)
```

并在测试依赖处（`testImplementation(libs.junit)` 之后）追加：

```kotlin
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 2: 写失败测试**

创建 `lib_common/src/test/java/com/btg/common/event/FlowBusTest.kt`：

```kotlin
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
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.event.FlowBusTest"`
Expected: 编译失败，`unresolved reference: FlowBus`。

- [ ] **Step 4: 实现 FlowBus**

创建 `lib_common/src/main/java/com/btg/common/event/FlowBus.kt`：

```kotlin
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
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.event.FlowBusTest"`
Expected: `BUILD SUCCESSFUL`，2 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add lib_common/build.gradle.kts lib_common/src/main/java/com/btg/common/event/FlowBus.kt lib_common/src/test/java/com/btg/common/event/FlowBusTest.kt
git commit -m "feat: add Flow-based FlowBus event bus

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: BaseViewModel 增强（TDD）

**Files:**
- Create: `lib_common/src/test/java/com/btg/common/util/MainDispatcherRule.kt`
- Create: `lib_common/src/test/java/com/btg/common/base/BaseViewModelTest.kt`
- Modify: `lib_common/src/main/java/com/btg/common/base/BaseViewModel.kt`

**Interfaces:**
- Consumes: Task 1 的 `UiState` / `toUiState` / `toListUiState`；`ApiResult`；`viewModelScope`（lifecycle-viewmodel-ktx，已在依赖）；协程（Task 2 已加）。
- Produces: `open class BaseViewModel : ViewModel()`，新增 protected 成员——
  - `val errorEvent: Flow<String>`（统一一次性错误事件）
  - `protected fun postError(message: String)`
  - `protected fun <T> launchWithState(target: MutableStateFlow<UiState<T>>, block: suspend () -> ApiResult<T>)`
  - `protected fun <E> launchListWithState(target: MutableStateFlow<UiState<List<E>>>, block: suspend () -> ApiResult<List<E>>)`

- [ ] **Step 1: 在 lib_common 测试源建 MainDispatcherRule**

创建 `lib_common/src/test/java/com/btg/common/util/MainDispatcherRule.kt`：

```kotlin
package com.btg.common.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 把 Main dispatcher 替换为测试 dispatcher，解决 viewModelScope 默认跑在 Main 的问题。
 * 用 UnconfinedTestDispatcher：协程即时执行，测试无需手动 advance。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

- [ ] **Step 2: 写失败测试**

创建 `lib_common/src/test/java/com/btg/common/base/BaseViewModelTest.kt`：

```kotlin
package com.btg.common.base

import com.btg.common.result.ApiResult
import com.btg.common.result.UiState
import com.btg.common.util.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BaseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class TestViewModel : BaseViewModel() {
        val listState = MutableStateFlow<UiState<List<Int>>>(UiState.Loading)
        fun load(block: suspend () -> ApiResult<List<Int>>) = launchListWithState(listState, block)
        fun raise(message: String) = postError(message)
    }

    @Test
    fun `launchListWithState ends in Success for non-empty`() = runTest {
        val vm = TestViewModel()
        vm.load { ApiResult.Success(listOf(1, 2)) }
        assertEquals(UiState.Success(listOf(1, 2)), vm.listState.value)
    }

    @Test
    fun `launchListWithState ends in Empty for empty list`() = runTest {
        val vm = TestViewModel()
        vm.load { ApiResult.Success(emptyList()) }
        assertEquals(UiState.Empty, vm.listState.value)
    }

    @Test
    fun `launchListWithState ends in Error on failure`() = runTest {
        val vm = TestViewModel()
        vm.load { ApiResult.Error(RuntimeException("net down")) }
        assertEquals(UiState.Error("net down"), vm.listState.value)
    }

    @Test
    fun `postError is delivered to errorEvent`() = runTest {
        val vm = TestViewModel()
        val received = mutableListOf<String>()
        val job = launch { vm.errorEvent.take(1).collect { received.add(it) } }
        runCurrent()
        vm.raise("oops")
        job.join()
        assertEquals(listOf("oops"), received)
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.base.BaseViewModelTest"`
Expected: 编译失败，`unresolved reference: launchListWithState` / `postError` / `errorEvent`。

- [ ] **Step 4: 增强 BaseViewModel**

把 `lib_common/src/main/java/com/btg/common/base/BaseViewModel.kt` 整体替换为：

```kotlin
package com.btg.common.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.btg.common.result.ApiResult
import com.btg.common.result.UiState
import com.btg.common.result.toListUiState
import com.btg.common.result.toUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * MVVM 分层锚点 + 通用帮手：统一错误事件下发、把一次数据加载映射为 [UiState] 写入目标状态流。
 * 保持可单元测试：不持有 View/Context。
 */
open class BaseViewModel : ViewModel() {

    private val _errorEvent = Channel<String>(Channel.BUFFERED)

    /** 统一的一次性错误事件流，View 层收集后弹 Toast / 提示。 */
    val errorEvent: Flow<String> = _errorEvent.receiveAsFlow()

    /** 下发一条错误事件（非挂起）。 */
    protected fun postError(message: String) {
        _errorEvent.trySend(message)
    }

    /**
     * 执行一次数据加载：先置 [UiState.Loading]，完成后按结果映射为 Success/Error 写入 [target]。
     */
    protected fun <T> launchWithState(
        target: MutableStateFlow<UiState<T>>,
        block: suspend () -> ApiResult<T>,
    ) {
        target.value = UiState.Loading
        viewModelScope.launch {
            target.value = block().toUiState()
        }
    }

    /**
     * 列表版加载：空列表结果映射为 [UiState.Empty]。
     */
    protected fun <E> launchListWithState(
        target: MutableStateFlow<UiState<List<E>>>,
        block: suspend () -> ApiResult<List<E>>,
    ) {
        target.value = UiState.Loading
        viewModelScope.launch {
            target.value = block().toListUiState()
        }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.base.BaseViewModelTest"`
Expected: `BUILD SUCCESSFUL`，4 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/base/BaseViewModel.kt lib_common/src/test/java/com/btg/common/base/BaseViewModelTest.kt lib_common/src/test/java/com/btg/common/util/MainDispatcherRule.kt
git commit -m "feat: enhance BaseViewModel with launchWithState helpers and unified error event

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: BaseFragment（binding 自动释放，编译验证）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/base/BaseFragment.kt`

**Interfaces:**
- Consumes: `androidx.fragment.app.Fragment`（经 appcompat 传递）、`androidx.viewbinding.ViewBinding`（lib_common 已启用 viewBinding）。
- Produces: `abstract class BaseFragment<VB : ViewBinding> : Fragment()`，子类实现 `inflateBinding(inflater, container)`；`binding` 在 onCreateView~onDestroyView 之间有效，onDestroyView 自动置空防泄漏。

- [ ] **Step 1: 创建 BaseFragment**

创建 `lib_common/src/main/java/com/btg/common/base/BaseFragment.kt`：

```kotlin
package com.btg.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * 承载 ViewBinding 的 Fragment 基类。
 *
 * binding 仅在 onCreateView 与 onDestroyView 之间有效；onDestroyView 自动置空避免内存泄漏。
 * 观察数据请用 viewLifecycleOwner，勿用 Fragment 自身生命周期。
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    protected val binding: VB
        get() = _binding ?: error("binding 只能在 onCreateView 与 onDestroyView 之间访问")

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val vb = inflateBinding(inflater, container)
        _binding = vb
        return vb.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/base/BaseFragment.kt
git commit -m "feat: add BaseFragment with auto-released ViewBinding

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: 扩展函数集 ext/（编译验证）

**Files:**
- Modify: `lib_common/build.gradle.kts`（加 `api(libs.androidx.lifecycle.runtime.ktx)`）
- Create: `lib_common/src/main/java/com/btg/common/ext/ViewExt.kt`
- Create: `lib_common/src/main/java/com/btg/common/ext/DimensionExt.kt`
- Create: `lib_common/src/main/java/com/btg/common/ext/FlowExt.kt`

**Interfaces:**
- Consumes: Android View / Resources / SystemClock；`androidx.lifecycle`（`lifecycleScope`/`repeatOnLifecycle`）。
- Produces:
  - `ViewExt`：`fun View.visible()`、`fun View.invisible()`、`fun View.gone()`、`fun View.setOnDebouncedClickListener(intervalMs: Long = 500L, action: (View) -> Unit)`
  - `DimensionExt`：`val Int.dp: Int`、`val Float.dp: Float`（dp→px）
  - `FlowExt`：`fun <T> Flow<T>.collectOnStarted(owner: LifecycleOwner, action: suspend (T) -> Unit)`

- [ ] **Step 1: 给 lib_common 加 lifecycle-runtime-ktx（供 FlowExt 用）**

在 `lib_common/build.gradle.kts` 的 `dependencies { }` 块内，`api(libs.kotlinx.coroutines.android)` 之后追加：

```kotlin
    api(libs.androidx.lifecycle.runtime.ktx)
```

- [ ] **Step 2: 创建 ViewExt.kt**

创建 `lib_common/src/main/java/com/btg/common/ext/ViewExt.kt`：

```kotlin
package com.btg.common.ext

import android.os.SystemClock
import android.view.View

/** 显示 View。 */
fun View.visible() {
    visibility = View.VISIBLE
}

/** 占位隐藏（仍占布局空间）。 */
fun View.invisible() {
    visibility = View.INVISIBLE
}

/** 隐藏并不占布局空间。 */
fun View.gone() {
    visibility = View.GONE
}

/**
 * 防抖点击：默认 500ms 内的重复点击被忽略，避免连点触发多次。
 */
fun View.setOnDebouncedClickListener(intervalMs: Long = 500L, action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener { v ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime >= intervalMs) {
            lastClickTime = now
            action(v)
        }
    }
}
```

- [ ] **Step 3: 创建 DimensionExt.kt**

创建 `lib_common/src/main/java/com/btg/common/ext/DimensionExt.kt`：

```kotlin
package com.btg.common.ext

import android.content.res.Resources

/** dp 转 px（Int，四舍五入）。基于系统 displayMetrics，无需 Context。 */
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

/** dp 转 px（Float）。 */
val Float.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density
```

- [ ] **Step 4: 创建 FlowExt.kt**

创建 `lib_common/src/main/java/com/btg/common/ext/FlowExt.kt`：

```kotlin
package com.btg.common.ext

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 在 [owner] 处于 STARTED 时收集 Flow，离开 STARTED 自动取消、重新进入自动重启。
 * Fragment 中请传 viewLifecycleOwner；Activity 传自身。
 */
fun <T> Flow<T>.collectOnStarted(owner: LifecycleOwner, action: suspend (T) -> Unit) {
    owner.lifecycleScope.launch {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect { action(it) }
        }
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交**

```bash
git add lib_common/build.gradle.kts lib_common/src/main/java/com/btg/common/ext/
git commit -m "feat: add view/dimension/flow extension helpers

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: 全量编译 + 全部单测回归

**Files:**
- 无改动（纯验证；有问题回对应任务修）。

**Interfaces:**
- Consumes: Task 1–5 的全部产物。
- Produces: 阶段 1 完成标志——全工程编译通过、lib_common 新增单测 + app 现有单测全绿。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 全部单元测试**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`；lib_common 的 `UiStateMappingTest`/`FlowBusTest`/`BaseViewModelTest` 与 app 现有测试全部通过。

- [ ] **Step 3: 收尾**

无代码改动则不提交，在阶段小结记录验证结果；若有修复改动，按所属任务语义提交。

---

## Self-Review（对照 spec 第 12 节"阶段 1"）

- ✅ `BaseFragment` → Task 4。
- ✅ `BaseViewModel` 增强 → Task 3。
- ✅ `UiState<T>` + 映射 → Task 1。
- ✅ `ext/` → Task 5。
- ✅ `event/` → Task 2。
- 类型一致性：`UiState`/`toUiState`/`toListUiState`（Task 1 定义）在 Task 3 的 BaseViewModel 与测试中签名一致；`FlowBus`（Task 2）方法名与测试一致；`ApiResult.Error(throwable)` 与现有源文件一致。
- 依赖增量：`testImplementation(junit)`（Task 1）、`api(coroutines-android)`+`testImplementation(coroutines-test)`（Task 2）、`api(lifecycle-runtime-ktx)`（Task 5）均引用阶段 0 已声明的 catalog 别名，无新增 catalog 条目。
- 不含占位符：所有代码、命令、预期输出均为确切内容。
- 阶段边界：只在 lib_common 加框架基建 + 对应单测；不动 news 业务代码、不转换 Java。
