# 框架能力补齐 · 阶段 3（UI 组件）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐高频 UI 组件：多状态布局 `StateLayout`、`TitleBar`、沉浸式状态栏帮手、Loading/确认/提示 Dialog、BottomSheet 基类、SmartRefresh 下拉刷新+加载更多封装，并提供 `StateLayout` 与 `UiState` 的绑定。

**Architecture:** 纯自定义 View（`StateLayout`/`TitleBar`）放 `lib_widget`（依赖链下游、不依赖框架基类，程序化构建、零布局资源）；依赖 Context/基类/Material 的组件（Dialog、BottomSheet 基类、沉浸式、SmartRefresh 封装、StateLayout↔UiState 绑定）放 `lib_common/ui`。全部依赖 Android framework，按 spec 只做编译验证 + 阶段末真机冒烟。

**Tech Stack:** Kotlin / AndroidX appcompat + core / Material 1.12（BottomSheet）/ SmartRefreshLayout 2.1 / ViewBinding。

## Global Constraints

- 语言全 Kotlin。公共 API 显式可见性。
- 新纯 View 放 `com.btg.widget`；带 Context/基类/Material 的放 `com.btg.common.ui`。
- 加依赖只动 `libs.versions.toml` 既有别名；本阶段不新增 catalog 条目。
- 依赖 Android framework 的类只做编译验证（+ 阶段末冒烟），不写自动化测试。
- 不改 `minSdk`/`targetSdk`/`compileSdk`。
- app 主题从 `Theme.AppCompat` 迁移到 `Theme.MaterialComponents.Light.DarkActionBar`（BottomSheet 等 Material 组件的前置要求；保留 ActionBar，最小改动）。
- 每任务末尾提交（Conventional Commits），message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 工作分支：`feat/framework-buildout`。

---

### Task 1: lib_widget 接入 Kotlin + StateLayout 多状态布局

**Files:**
- Modify: `lib_widget/build.gradle.kts`（加 kotlin 插件 + kotlinOptions）
- Create: `lib_widget/src/main/java/com/btg/widget/StateLayout.kt`

**Interfaces:**
- Consumes: Android `FrameLayout`/`ProgressBar`/`TextView`。
- Produces: `class StateLayout : FrameLayout`——`showContent()`/`showLoading()`/`showEmpty(msg)`/`showError(msg)` + `setOnRetryListener`/`setContentView`。第一个 XML 子 View 视为内容视图；loading/empty/error 默认视图程序化懒创建。

- [ ] **Step 1: lib_widget 加 kotlin 插件**

把 `lib_widget/build.gradle.kts` 替换为：

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.btg.widget"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(libs.androidx.appcompat)
}
```

- [ ] **Step 2: 创建 StateLayout**

创建 `lib_widget/src/main/java/com/btg/widget/StateLayout.kt`：

```kotlin
package com.btg.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 多状态容器：在 加载 / 内容 / 空 / 错误 间切换。
 * XML 里的第一个子 View 视为内容视图；loading/empty/error 默认视图程序化懒创建。
 */
class StateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var contentView: View? = null
    private var loadingView: View? = null
    private var emptyView: View? = null
    private var errorView: View? = null
    private var emptyTextView: TextView? = null
    private var errorTextView: TextView? = null
    private var onRetry: (() -> Unit)? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (contentView == null && childCount > 0) {
            contentView = getChildAt(0)
        }
    }

    /** 代码创建 StateLayout 时指定内容视图。 */
    fun setContentView(view: View) {
        contentView?.let { removeView(it) }
        contentView = view
        if (view.parent == null) addView(view)
        showContent()
    }

    fun setOnRetryListener(listener: () -> Unit) {
        onRetry = listener
    }

    fun showContent() = switchTo(contentView)

    fun showLoading() = switchTo(ensureLoadingView())

    fun showEmpty(message: String = "暂无数据") {
        val view = ensureEmptyView()
        emptyTextView?.text = message
        switchTo(view)
    }

    fun showError(message: String = "加载失败，点击重试") {
        val view = ensureErrorView()
        errorTextView?.text = message
        switchTo(view)
    }

    private fun switchTo(target: View?) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.visibility = if (child === target) View.VISIBLE else View.GONE
        }
    }

    private fun centerParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }

    private fun ensureLoadingView(): View {
        loadingView?.let { return it }
        val container = FrameLayout(context)
        container.addView(
            ProgressBar(context),
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
        addView(container, centerParams())
        loadingView = container
        return container
    }

    private fun ensureEmptyView(): View {
        emptyView?.let { return it }
        val text = buildCenteredText("暂无数据")
        emptyTextView = text
        addView(text, centerParams())
        emptyView = text
        return text
    }

    private fun ensureErrorView(): View {
        errorView?.let { return it }
        val text = buildCenteredText("加载失败，点击重试")
        text.setOnClickListener { onRetry?.invoke() }
        errorTextView = text
        addView(text, centerParams())
        errorView = text
        return text
    }

    private fun buildCenteredText(default: String): TextView = TextView(context).apply {
        text = default
        gravity = Gravity.CENTER
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :lib_widget:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add lib_widget/build.gradle.kts lib_widget/src/main/java/com/btg/widget/StateLayout.kt
git commit -m "feat: add Kotlin plugin to lib_widget and StateLayout multi-state container

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: TitleBar 标题栏

**Files:**
- Create: `lib_widget/src/main/java/com/btg/widget/TitleBar.kt`

**Interfaces:**
- Consumes: Android `FrameLayout`/`TextView`。
- Produces: `class TitleBar : FrameLayout`——`setTitle`、`showBack(text, onClick)`、`setAction(text, onClick)`；默认高度 56dp，返回/右操作默认隐藏。

- [ ] **Step 1: 创建 TitleBar**

创建 `lib_widget/src/main/java/com/btg/widget/TitleBar.kt`：

```kotlin
package com.btg.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** 通用标题栏：左返回、中标题、右操作。程序化构建，无布局资源。 */
class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backView: TextView
    private val titleView: TextView
    private val actionView: TextView

    init {
        val barHeight = dp(56)
        minimumHeight = barHeight

        backView = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            visibility = View.GONE
        }
        addView(
            backView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL),
        )

        titleView = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
        addView(
            titleView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER),
        )

        actionView = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            visibility = View.GONE
        }
        addView(
            actionView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL),
        )
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun showBack(text: CharSequence = "返回", onClick: () -> Unit) {
        backView.text = text
        backView.visibility = View.VISIBLE
        backView.setOnClickListener { onClick() }
    }

    fun setAction(text: CharSequence, onClick: () -> Unit) {
        actionView.text = text
        actionView.visibility = View.VISIBLE
        actionView.setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :lib_widget:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add lib_widget/src/main/java/com/btg/widget/TitleBar.kt
git commit -m "feat: add TitleBar custom view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 沉浸式状态栏帮手

**Files:**
- Modify: `lib_common/build.gradle.kts`（加 `api(libs.androidx.core.ktx)`，确保 `WindowCompat` 可用）
- Create: `lib_common/src/main/java/com/btg/common/ui/ImmersiveExt.kt`

**Interfaces:**
- Consumes: `androidx.core.view.WindowCompat` / `WindowInsetsControllerCompat`。
- Produces: `fun Activity.setStatusBar(color, lightIcons)`、`fun Activity.transparentStatusBar(lightIcons)`。

- [ ] **Step 1: lib_common 加 core-ktx**

在 `lib_common/build.gradle.kts` 的 `dependencies { }` 块内，`api(libs.androidx.lifecycle.runtime.ktx)` 之后追加：

```kotlin
    api(libs.androidx.core.ktx)
```

- [ ] **Step 2: 创建 ImmersiveExt**

创建 `lib_common/src/main/java/com/btg/common/ui/ImmersiveExt.kt`：

```kotlin
package com.btg.common.ui

import android.app.Activity
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 设置状态栏颜色与图标明暗。lightIcons=true 表示深色图标（浅色背景时用）。 */
fun Activity.setStatusBar(@ColorInt color: Int, lightIcons: Boolean) {
    window.statusBarColor = color
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightIcons
}

/** 透明状态栏 + 内容延伸到状态栏后（edge-to-edge）。布局需自行处理 insets。 */
fun Activity.transparentStatusBar(lightIcons: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightIcons
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add lib_common/build.gradle.kts lib_common/src/main/java/com/btg/common/ui/ImmersiveExt.kt
git commit -m "feat: add immersive status bar helpers

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Loading / 确认 / 提示 Dialog

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/ui/LoadingDialog.kt`
- Create: `lib_common/src/main/java/com/btg/common/ui/Dialogs.kt`

**Interfaces:**
- Consumes: `android.app.Dialog`、`androidx.appcompat.app.AlertDialog`。
- Produces:
  - `class LoadingDialog(context) { fun show(message: CharSequence?) }`（不可取消的进度弹窗）
  - `fun Context.showConfirmDialog(...)`、`fun Context.showAlertDialog(...)`

- [ ] **Step 1: 创建 LoadingDialog**

创建 `lib_common/src/main/java/com/btg/common/ui/LoadingDialog.kt`：

```kotlin
package com.btg.common.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** 简单的 Loading 弹窗（不确定进度），程序化构建。 */
class LoadingDialog(context: Context) : Dialog(context) {

    private val messageView: TextView

    init {
        val density = context.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        container.addView(ProgressBar(context))
        messageView = TextView(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
            visibility = View.GONE
        }
        container.addView(messageView)

        setContentView(container)
        setCancelable(false)
    }

    fun show(message: CharSequence?) {
        if (message.isNullOrEmpty()) {
            messageView.visibility = View.GONE
        } else {
            messageView.text = message
            messageView.visibility = View.VISIBLE
        }
        super.show()
    }
}
```

- [ ] **Step 2: 创建 Dialogs（确认/提示）**

创建 `lib_common/src/main/java/com/btg/common/ui/Dialogs.kt`：

```kotlin
package com.btg.common.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog

/** 二次确认弹窗：确定 + 取消。 */
fun Context.showConfirmDialog(
    title: CharSequence? = null,
    message: CharSequence,
    positiveText: CharSequence = "确定",
    negativeText: CharSequence = "取消",
    cancelable: Boolean = true,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
): AlertDialog = AlertDialog.Builder(this)
    .setTitle(title)
    .setMessage(message)
    .setCancelable(cancelable)
    .setPositiveButton(positiveText) { _, _ -> onConfirm() }
    .setNegativeButton(negativeText) { _, _ -> onCancel?.invoke() }
    .show()

/** 单按钮提示弹窗。 */
fun Context.showAlertDialog(
    title: CharSequence? = null,
    message: CharSequence,
    buttonText: CharSequence = "确定",
    onDismiss: (() -> Unit)? = null,
): AlertDialog = AlertDialog.Builder(this)
    .setTitle(title)
    .setMessage(message)
    .setPositiveButton(buttonText) { _, _ -> onDismiss?.invoke() }
    .show()
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/ui/LoadingDialog.kt lib_common/src/main/java/com/btg/common/ui/Dialogs.kt
git commit -m "feat: add LoadingDialog and confirm/alert dialog helpers

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: app 主题迁移 Material + BottomSheet 基类

**Files:**
- Modify: `app/src/main/res/values/styles.xml`（AppTheme 父主题改 Material）
- Create: `lib_common/src/main/java/com/btg/common/ui/BaseBottomSheetFragment.kt`

**Interfaces:**
- Consumes: `com.google.android.material.bottomsheet.BottomSheetDialogFragment`（material，经 lib_opensource api）、`ViewBinding`。
- Produces: `abstract class BaseBottomSheetFragment<VB : ViewBinding>`——binding 自动释放，用法同 `BaseFragment`。

- [ ] **Step 1: 迁移 app 主题到 Material**

把 `app/src/main/res/values/styles.xml` 里 `AppTheme` 的 `parent` 从 `Theme.AppCompat.Light.DarkActionBar` 改为 `Theme.MaterialComponents.Light.DarkActionBar`：

```xml
<resources>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="Theme.MaterialComponents.Light.DarkActionBar">
        <!-- Customize your theme here. -->
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
    </style>

</resources>
```

- [ ] **Step 2: 创建 BaseBottomSheetFragment**

创建 `lib_common/src/main/java/com/btg/common/ui/BaseBottomSheetFragment.kt`：

```kotlin
package com.btg.common.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 承载 ViewBinding 的 BottomSheet 基类。binding 在 onCreateView~onDestroyView 间有效，自动释放。
 * 需 app 主题为 Material 主题（Theme.MaterialComponents.* / Theme.Material3.*）。
 */
abstract class BaseBottomSheetFragment<VB : ViewBinding> : BottomSheetDialogFragment() {

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

- [ ] **Step 3: 编译验证（含 app 主题）**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/values/styles.xml lib_common/src/main/java/com/btg/common/ui/BaseBottomSheetFragment.kt
git commit -m "feat: migrate app theme to MaterialComponents and add BaseBottomSheetFragment

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: SmartRefresh 封装 + StateLayout↔UiState 绑定

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/ui/RefreshExt.kt`
- Create: `lib_common/src/main/java/com/btg/common/ui/StateLayoutExt.kt`

**Interfaces:**
- Consumes: `SmartRefreshLayout` + `ClassicsHeader`/`ClassicsFooter`（SmartRefresh，经 lib_opensource api）；`StateLayout`（lib_widget）、`UiState`（lib_common）。
- Produces:
  - `fun SmartRefreshLayout.setup(onRefresh, onLoadMore?)`（自动装 Classics 头/脚；onLoadMore 为 null 时禁用加载更多）、`fun SmartRefreshLayout.finishAll()`
  - `fun StateLayout.render(state: UiState<*>, onRetry?)`（四态映射到 StateLayout）

- [ ] **Step 1: 创建 RefreshExt**

创建 `lib_common/src/main/java/com/btg/common/ui/RefreshExt.kt`：

```kotlin
package com.btg.common.ui

import com.scwang.smart.refresh.footer.ClassicsFooter
import com.scwang.smart.refresh.header.ClassicsHeader
import com.scwang.smart.refresh.layout.SmartRefreshLayout

/**
 * 配置下拉刷新 + 上拉加载更多，自动装配 Classics 头/脚。
 * onLoadMore 为 null 时禁用加载更多。
 */
fun SmartRefreshLayout.setup(
    onRefresh: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
) {
    setRefreshHeader(ClassicsHeader(context))
    setRefreshFooter(ClassicsFooter(context))
    setOnRefreshListener { onRefresh() }
    if (onLoadMore != null) {
        setEnableLoadMore(true)
        setOnLoadMoreListener { onLoadMore() }
    } else {
        setEnableLoadMore(false)
    }
}

/** 结束刷新与加载动画。 */
fun SmartRefreshLayout.finishAll() {
    finishRefresh()
    finishLoadMore()
}
```

- [ ] **Step 2: 创建 StateLayoutExt**

创建 `lib_common/src/main/java/com/btg/common/ui/StateLayoutExt.kt`：

```kotlin
package com.btg.common.ui

import com.btg.common.result.UiState
import com.btg.widget.StateLayout

/**
 * 把 [UiState] 渲染到 [StateLayout]：Loading→loading，Success→content，Empty→empty，Error→error。
 * onRetry 传入后，错误态点击重试会回调。
 */
fun StateLayout.render(state: UiState<*>, onRetry: (() -> Unit)? = null) {
    onRetry?.let { setOnRetryListener(it) }
    when (state) {
        is UiState.Loading -> showLoading()
        is UiState.Success -> showContent()
        is UiState.Empty -> showEmpty()
        is UiState.Error -> showError(state.message)
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。若 SmartRefresh 的方法名/包名与实际版本不符导致编译失败，按实际 API 调整后再验证（属版本对齐，非设计变更）。

- [ ] **Step 4: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/ui/RefreshExt.kt lib_common/src/main/java/com/btg/common/ui/StateLayoutExt.kt
git commit -m "feat: add SmartRefresh setup helpers and StateLayout-UiState binding

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: 全量编译 + 单测 + 真机冒烟

**Files:** 无改动（纯验证）。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 全部单元测试**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`（本阶段无新单测；确认既有测试不受影响）。

- [ ] **Step 3: 真机冒烟**

Run: `./gradlew installDebug`
手动：启动 app，确认示范页正常、无崩溃（主题迁移到 Material 后 UI 正常）。
Expected: 正常启动、列表展示；Logcat 无崩溃。
（无设备时跳过并注明。）

- [ ] **Step 4: 收尾**

无代码改动则不提交，记录验证结果。

---

## Self-Review（对照 spec 第 6 节 UI 组件）

- ✅ Toast 封装 → **注**：spec 第 6 节含 Toast，但 Toast 封装归在 spec 阶段 1 的 ext/ 描述边界内？核对：Toast 属 UI 交互，spec 未在阶段 1 落地。本计划补入——见下方补充 Task。
- ✅ Loading Dialog（Task 4）、确认/提示 Dialog（Task 4）。
- ✅ BottomSheet 基类（Task 5）。
- ✅ StateLayout 多状态（Task 1）+ UiState 绑定（Task 6）。
- ✅ TitleBar（Task 2）。
- ✅ 沉浸式状态栏（Task 3）。
- ✅ SmartRefresh 下拉刷新+加载更多封装（Task 6）。
- 依赖：仅 lib_widget 加 kotlin 插件、lib_common 加 core-ktx（均为既有别名）；app 主题迁移 Material。
- 阶段边界：纯 UI 组件；不动网络/存储/权限（各自阶段）。

### 补充 Task 8: Toast 封装

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/ui/ToastExt.kt`

**Interfaces:**
- Produces: `fun Context.toast(message: CharSequence, long: Boolean = false)`——同内容 500ms 内防重复刷屏。

- [ ] **Step 1: 创建 ToastExt**

创建 `lib_common/src/main/java/com/btg/common/ui/ToastExt.kt`：

```kotlin
package com.btg.common.ui

import android.content.Context
import android.os.SystemClock
import android.widget.Toast

private var lastToastText: CharSequence? = null
private var lastToastTime = 0L

/** 统一 Toast，防重复刷屏：500ms 内相同内容不重复弹。 */
fun Context.toast(message: CharSequence, long: Boolean = false) {
    val now = SystemClock.elapsedRealtime()
    if (message == lastToastText && now - lastToastTime < 500L) return
    lastToastText = message
    lastToastTime = now
    Toast.makeText(applicationContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/ui/ToastExt.kt
git commit -m "feat: add debounced toast helper

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
