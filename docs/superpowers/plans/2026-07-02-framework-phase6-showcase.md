# 框架能力补齐 · 阶段 6（演示台 + Navigation + Coil + 测试）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 app 从"单页新闻"改造成单 Activity + Navigation 的能力展示台：新闻页跑通 Hilt + UiState/StateLayout + SwipeRefresh + Coil 的端到端；再加弹窗/Toast/BottomSheet、存储（DataStore/加密/Room）、权限三个演示页。收官后整套框架有活文档。

**Architecture:** app 单 Activity（NavHost）+ 多 Fragment（继承 `BaseFragment`）。新闻走 Hilt（`@HiltViewModel` + `NewsModule` 提供 Fake 数据源，装配点从手动 Factory 移到 Hilt 模块）。图片用 Coil（框架扩展）。演示页直接调用框架组件。UI 只做编译验证 + 真机冒烟。

**Tech Stack:** Kotlin / Hilt / Navigation / Coil / SwipeRefresh / Room / DataStore / Material。

## Global Constraints

- 语言全 Kotlin。公共 API 显式可见性。字符串进 resources。
- 不加新依赖（Coil/Navigation/Room/DataStore 均已聚合）。
- 不改 `minSdk`/`targetSdk`/`compileSdk`。
- 新闻页保留 `NewsUiState` 数据类（仅给 `NewsViewModel` 加 Hilt 注解，避免破坏既有单测）。
- 每任务末尾提交（Conventional Commits），message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 工作分支：`feat/framework-buildout`。

---

### Task 1: Coil 图片扩展 + 列表图片

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/ext/ImageExt.kt`
- Modify: `app/src/main/res/layout/item_news.xml`（加 ImageView）
- Modify: `app/src/main/java/com/btg/mvvm/ui/news/NewsAdapter.kt`（Coil 加载）

**Interfaces:**
- Produces: `fun ImageView.loadUrl(url: String?, placeholder: Int = 0, error: Int = 0)`（Coil 封装，含 crossfade）。

- [ ] **Step 1: 创建 ImageExt**

创建 `lib_common/src/main/java/com/btg/common/ext/ImageExt.kt`：

```kotlin
package com.btg.common.ext

import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil.load

/** Coil 加载网络图片，带淡入；placeholder/error 传 0 表示不设置。 */
fun ImageView.loadUrl(
    url: String?,
    @DrawableRes placeholder: Int = 0,
    @DrawableRes error: Int = 0,
) {
    load(url) {
        crossfade(true)
        if (placeholder != 0) placeholder(placeholder)
        if (error != 0) error(error)
    }
}
```

- [ ] **Step 2: item_news 加 ImageView**

把 `app/src/main/res/layout/item_news.xml` 替换为（在标题上方加一张图）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="?android:attr/selectableItemBackground">

    <ImageView
        android:id="@+id/newsImage"
        android:layout_width="match_parent"
        android:layout_height="160dp"
        android:layout_marginBottom="8dp"
        android:scaleType="centerCrop"
        android:visibility="gone"
        android:contentDescription="@null" />

    <TextView
        android:id="@+id/titleText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/sourceText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/dateText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textSize="12sp" />

</LinearLayout>
```

- [ ] **Step 3: NewsAdapter 用 Coil 加载图片**

把 `app/src/main/java/com/btg/mvvm/ui/news/NewsAdapter.kt` 的 `bind` 替换为（其余不变）：

```kotlin
        fun bind(item: NewsItem) {
            binding.titleText.text = item.title
            binding.sourceText.text = item.source
            binding.dateText.text = item.date
            if (item.imageUrl.isNullOrEmpty()) {
                binding.newsImage.visibility = View.GONE
            } else {
                binding.newsImage.visibility = View.VISIBLE
                binding.newsImage.loadUrl(item.imageUrl)
            }
            binding.root.setOnClickListener { onClick(item) }
        }
```

并在文件顶部补充 import：

```kotlin
import android.view.View
import com.btg.common.ext.loadUrl
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/ext/ImageExt.kt app/src/main/res/layout/item_news.xml app/src/main/java/com/btg/mvvm/ui/news/NewsAdapter.kt
git commit -m "feat: add Coil loadUrl extension and news image loading

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 新闻走 Hilt（@HiltViewModel + NewsModule）

**Files:**
- Modify: `app/src/main/java/com/btg/mvvm/ui/news/NewsViewModel.kt`（加 `@HiltViewModel` + `@Inject`）
- Delete: `app/src/main/java/com/btg/mvvm/ui/news/NewsViewModelFactory.kt`
- Create: `app/src/main/java/com/btg/mvvm/di/NewsModule.kt`

**Interfaces:**
- Consumes: Hilt。
- Produces: Hilt 提供 `NewsDataSource`(Fake) 与 `NewsRepository`；`NewsViewModel` 由 Hilt 构造。装配点从 MainActivity 手动 Factory 移到 `NewsModule`（换真实数据源只改这一处 `@Provides`）。

- [ ] **Step 1: NewsViewModel 加 Hilt 注解**

把 `NewsViewModel` 类声明与构造改为：

```kotlin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
```
```kotlin
@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository,
) : BaseViewModel() {
```
（其余方法体不变。）

- [ ] **Step 2: 删除 NewsViewModelFactory**

```bash
git rm app/src/main/java/com/btg/mvvm/ui/news/NewsViewModelFactory.kt
```

- [ ] **Step 3: 创建 NewsModule**

创建 `app/src/main/java/com/btg/mvvm/di/NewsModule.kt`：

```kotlin
package com.btg.mvvm.di

import com.btg.mvvm.data.repository.NewsRepository
import com.btg.mvvm.data.source.FakeNewsDataSource
import com.btg.mvvm.data.source.NewsDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 新闻数据装配。数据源的唯一装配点：换真实接口时把 FakeNewsDataSource
 * 换成 RemoteNewsDataSource(newsApi)，上层不动。
 */
@Module
@InstallIn(SingletonComponent::class)
object NewsModule {

    @Provides
    @Singleton
    fun provideNewsDataSource(): NewsDataSource = FakeNewsDataSource()

    @Provides
    @Singleton
    fun provideNewsRepository(dataSource: NewsDataSource): NewsRepository =
        NewsRepository(dataSource)
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: 失败——`MainActivity` 仍引用已删除的 `NewsViewModelFactory`。这是预期的，Task 3 会重写 MainActivity。**本步骤先只确认错误来自 MainActivity**，然后继续 Task 3；Task 3 末尾再统一编译通过。

- [ ] **Step 5: 提交（与 Task 3 一起编译通过后提交）**

本任务代码随 Task 3 一起提交（因为 MainActivity 重写后才编译通过）。

---

### Task 3: 单 Activity + Navigation 骨架 + 新闻页 Fragment

**Files:**
- Modify: `app/src/main/java/com/btg/mvvm/MainActivity.kt`（改为 @AndroidEntryPoint + NavHost 宿主）
- Modify: `app/src/main/res/layout/activity_main.xml`（改为 NavHostFragment）
- Create: `app/src/main/res/navigation/nav_graph.xml`
- Create: `app/src/main/java/com/btg/mvvm/ui/home/HomeFragment.kt`
- Create: `app/src/main/res/layout/fragment_home.xml`
- Create: `app/src/main/java/com/btg/mvvm/ui/news/NewsFragment.kt`
- Create: `app/src/main/res/layout/fragment_news.xml`
- Modify: `app/src/main/res/values/strings.xml`（加文案）

**Interfaces:**
- Consumes: Task 2 的 Hilt 新闻装配；框架 `BaseFragment`/`StateLayout`/`SwipeRefreshLayout.onRefresh`/`collectOnStarted`。
- Produces: 单 Activity NavHost；`HomeFragment`（演示入口）；`NewsFragment`（新闻列表：StateLayout 四态 + 下拉刷新 + Coil）。

- [ ] **Step 1: 加字符串**

把 `app/src/main/res/values/strings.xml` 替换为（保留 app_name / news_load_error，追加演示文案）：

```xml
<resources>
    <string name="app_name">Mvvm</string>
    <string name="news_load_error">加载失败</string>

    <string name="home_title">框架能力演示</string>
    <string name="demo_news">新闻列表（网络+列表+下拉刷新+图片）</string>
    <string name="demo_components">弹窗 / Toast / BottomSheet / 权限</string>
    <string name="demo_storage">存储（DataStore / 加密 / Room）</string>
</resources>
```

- [ ] **Step 2: 重写 activity_main.xml 为 NavHost**

把 `app/src/main/res/layout/activity_main.xml` 替换为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.fragment.app.FragmentContainerView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_host"
    android:name="androidx.navigation.fragment.NavHostFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:defaultNavHost="true"
    app:navGraph="@navigation/nav_graph" />
```

- [ ] **Step 3: 创建 nav_graph.xml**

创建 `app/src/main/res/navigation/nav_graph.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/homeFragment">

    <fragment
        android:id="@+id/homeFragment"
        android:name="com.btg.mvvm.ui.home.HomeFragment"
        android:label="@string/home_title" />

    <fragment
        android:id="@+id/newsFragment"
        android:name="com.btg.mvvm.ui.news.NewsFragment"
        android:label="@string/demo_news" />

</navigation>
```

- [ ] **Step 4: 重写 MainActivity**

把 `app/src/main/java/com/btg/mvvm/MainActivity.kt` 替换为：

```kotlin
package com.btg.mvvm

import android.view.LayoutInflater
import com.btg.common.base.BaseActivity
import com.btg.mvvm.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/** 单 Activity 宿主：承载 Navigation 图，各能力演示在 Fragment 中。 */
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {
    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)
}
```

- [ ] **Step 5: 创建 fragment_home.xml**

创建 `app/src/main/res/layout/fragment_home.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <Button
        android:id="@+id/btnNews"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="12dp"
        android:text="@string/demo_news" />

    <Button
        android:id="@+id/btnComponents"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="12dp"
        android:text="@string/demo_components" />

    <Button
        android:id="@+id/btnStorage"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/demo_storage" />

</LinearLayout>
```

- [ ] **Step 6: 创建 HomeFragment**

创建 `app/src/main/java/com/btg/mvvm/ui/home/HomeFragment.kt`：

```kotlin
package com.btg.mvvm.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.btg.common.base.BaseFragment
import com.btg.mvvm.R
import com.btg.mvvm.databinding.FragmentHomeBinding

/** 演示入口：按钮跳转到各能力演示页。 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNews.setOnClickListener { findNavController().navigate(R.id.newsFragment) }
        binding.btnComponents.setOnClickListener { findNavController().navigate(R.id.componentsFragment) }
        binding.btnStorage.setOnClickListener { findNavController().navigate(R.id.storageFragment) }
    }
}
```

> 注：`componentsFragment`/`storageFragment` 目的地在 Task 4/5 加入 nav_graph；本任务先加 `newsFragment` 目的地即可编译（`R.id.componentsFragment` 等在 Task 4/5 前不存在，会编译失败）。为避免跨任务不编译，**本任务的 HomeFragment 先只连 btnNews**，btnComponents/btnStorage 的跳转在 Task 4/5 补上。改为：

```kotlin
    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNews.setOnClickListener { findNavController().navigate(R.id.newsFragment) }
        // btnComponents / btnStorage 的跳转在 Task 4 / Task 5 接入目的地后补上
    }
```

- [ ] **Step 7: 创建 fragment_news.xml**

创建 `app/src/main/res/layout/fragment_news.xml`（StateLayout > SwipeRefresh > RecyclerView）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.btg.widget.StateLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/stateLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipeRefresh"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerView"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

</com.btg.widget.StateLayout>
```

- [ ] **Step 8: 创建 NewsFragment**

创建 `app/src/main/java/com/btg/mvvm/ui/news/NewsFragment.kt`：

```kotlin
package com.btg.mvvm.ui.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ui.onRefresh
import com.btg.mvvm.databinding.FragmentNewsBinding
import dagger.hilt.android.AndroidEntryPoint

/** 新闻列表演示：Hilt VM + StateLayout 四态 + 下拉刷新 + Coil 图片。 */
@AndroidEntryPoint
class NewsFragment : BaseFragment<FragmentNewsBinding>() {

    private val viewModel: NewsViewModel by viewModels()
    private val newsAdapter = NewsAdapter { viewModel.onNewsClick(it) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = newsAdapter
        binding.stateLayout.setOnRetryListener { viewModel.loadNews() }
        binding.swipeRefresh.onRefresh { viewModel.loadNews() }

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
        viewModel.events.collectOnStarted(viewLifecycleOwner) { handleEvent(it) }
    }

    private fun render(state: NewsUiState) {
        when {
            state.isLoading && state.items.isEmpty() -> binding.stateLayout.showLoading()
            state.errorMessage != null && state.items.isEmpty() ->
                binding.stateLayout.showError(state.errorMessage)
            state.items.isEmpty() -> binding.stateLayout.showEmpty()
            else -> binding.stateLayout.showContent()
        }
        newsAdapter.submitList(state.items)
        if (!state.isLoading) binding.swipeRefresh.isRefreshing = false
    }

    private fun handleEvent(event: NewsEvent) {
        when (event) {
            is NewsEvent.OpenLink ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
        }
    }
}
```

- [ ] **Step 9: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`（Task 2 + Task 3 合并编译通过）。

- [ ] **Step 10: 提交（含 Task 2 改动）**

```bash
git add -A app/src/main/java/com/btg/mvvm/ app/src/main/res/
git commit -m "feat: single-activity Navigation showcase with Hilt-wired news fragment

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: 弹窗 / Toast / BottomSheet / 权限 演示页

**Files:**
- Create: `app/src/main/java/com/btg/mvvm/ui/demo/ComponentsFragment.kt`
- Create: `app/src/main/res/layout/fragment_components.xml`
- Modify: `app/src/main/res/navigation/nav_graph.xml`（加 componentsFragment）
- Modify: `app/src/main/java/com/btg/mvvm/ui/home/HomeFragment.kt`（接 btnComponents）
- Modify: `app/src/main/res/values/strings.xml`（按钮文案）

**Interfaces:**
- Consumes: 框架 `showConfirmDialog`/`showAlertDialog`/`toast`/`LoadingDialog`/`PermissionRequester`/`openAppSettings`。
- Produces: `ComponentsFragment` 演示弹窗/Toast/BottomSheet/权限申请。

- [ ] **Step 1: 加按钮文案**

在 `app/src/main/res/values/strings.xml` 的 `</resources>` 前追加：

```xml
    <string name="btn_toast">Toast</string>
    <string name="btn_confirm">确认弹窗</string>
    <string name="btn_loading">Loading 弹窗（2 秒）</string>
    <string name="btn_permission">申请相机权限</string>
```

- [ ] **Step 2: 创建 fragment_components.xml**

创建 `app/src/main/res/layout/fragment_components.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <Button android:id="@+id/btnToast"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_marginBottom="12dp" android:text="@string/btn_toast" />

    <Button android:id="@+id/btnConfirm"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_marginBottom="12dp" android:text="@string/btn_confirm" />

    <Button android:id="@+id/btnLoading"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_marginBottom="12dp" android:text="@string/btn_loading" />

    <Button android:id="@+id/btnPermission"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="@string/btn_permission" />

</LinearLayout>
```

- [ ] **Step 3: 创建 ComponentsFragment**

创建 `app/src/main/java/com/btg/mvvm/ui/demo/ComponentsFragment.kt`：

```kotlin
package com.btg.mvvm.ui.demo

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.common.permission.PermissionRequester
import com.btg.common.permission.openAppSettings
import com.btg.common.ui.LoadingDialog
import com.btg.common.ui.showConfirmDialog
import com.btg.common.ui.toast
import com.btg.mvvm.databinding.FragmentComponentsBinding

/** 弹窗 / Toast / Loading / 权限 演示。 */
class ComponentsFragment : BaseFragment<FragmentComponentsBinding>() {

    private val permissionRequester = PermissionRequester(this)

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentComponentsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnToast.setOnClickListener { requireContext().toast("这是一个 Toast") }

        binding.btnConfirm.setOnClickListener {
            requireContext().showConfirmDialog(
                title = "提示",
                message = "确认执行该操作吗？",
                onConfirm = { requireContext().toast("已确认") },
                onCancel = { requireContext().toast("已取消") },
            )
        }

        binding.btnLoading.setOnClickListener {
            val dialog = LoadingDialog(requireContext())
            dialog.show("加载中…")
            binding.root.postDelayed({ dialog.dismiss() }, 2000)
        }

        binding.btnPermission.setOnClickListener {
            permissionRequester.request(Manifest.permission.CAMERA) { result ->
                if (result.allGranted) {
                    requireContext().toast("相机权限已授予")
                } else {
                    requireContext().showConfirmDialog(
                        message = "相机权限被拒绝，是否去设置页开启？",
                        onConfirm = { requireContext().openAppSettings() },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: nav_graph 加 componentsFragment**

在 `nav_graph.xml` 的 `</navigation>` 前追加：

```xml
    <fragment
        android:id="@+id/componentsFragment"
        android:name="com.btg.mvvm.ui.demo.ComponentsFragment"
        android:label="@string/demo_components" />
```

- [ ] **Step 5: HomeFragment 接 btnComponents**

在 `HomeFragment.onViewCreated` 里补上：

```kotlin
        binding.btnComponents.setOnClickListener { findNavController().navigate(R.id.componentsFragment) }
```

- [ ] **Step 6: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7: 提交**

```bash
git add -A app/src/main/
git commit -m "feat: add components demo fragment (dialog/toast/loading/permission)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: 存储演示页（DataStore / 加密 / Room）

**Files:**
- Create: `app/src/main/java/com/btg/mvvm/ui/demo/StorageFragment.kt`
- Create: `app/src/main/res/layout/fragment_storage.xml`
- Modify: `app/src/main/res/navigation/nav_graph.xml`（加 storageFragment）
- Modify: `app/src/main/java/com/btg/mvvm/ui/home/HomeFragment.kt`（接 btnStorage）
- Modify: `app/src/main/res/values/strings.xml`（按钮文案）

**Interfaces:**
- Consumes: `PreferenceStore`/`SecurePreferences`（框架）、`AppDatabase`/`FavoriteDao`（app Room）、`collectOnStarted`。
- Produces: `StorageFragment` 演示 DataStore 存取、加密存取、Room 收藏增查。

- [ ] **Step 1: 加按钮文案**

在 `strings.xml` 的 `</resources>` 前追加：

```xml
    <string name="btn_ds_save">DataStore 保存计数+1</string>
    <string name="btn_secure_save">加密保存 token</string>
    <string name="btn_room_add">Room 收藏一条</string>
```

- [ ] **Step 2: 创建 fragment_storage.xml**

创建 `app/src/main/res/layout/fragment_storage.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <Button android:id="@+id/btnDataStore"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_marginBottom="12dp" android:text="@string/btn_ds_save" />

    <Button android:id="@+id/btnSecure"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_marginBottom="12dp" android:text="@string/btn_secure_save" />

    <Button android:id="@+id/btnRoom"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_marginBottom="12dp" android:text="@string/btn_room_add" />

    <TextView android:id="@+id/output"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textSize="14sp" />

</LinearLayout>
```

- [ ] **Step 3: 创建 StorageFragment**

创建 `app/src/main/java/com/btg/mvvm/ui/demo/StorageFragment.kt`：

```kotlin
package com.btg.mvvm.ui.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.storage.PreferenceStore
import com.btg.common.storage.SecurePreferences
import com.btg.mvvm.data.local.AppDatabase
import com.btg.mvvm.data.local.NewsFavorite
import com.btg.mvvm.databinding.FragmentStorageBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 存储演示：DataStore 计数、加密存 token、Room 收藏增查。 */
class StorageFragment : BaseFragment<FragmentStorageBinding>() {

    private val prefs by lazy { PreferenceStore(requireContext()) }
    private val secure by lazy { SecurePreferences(requireContext()) }
    private val db by lazy {
        Room.databaseBuilder(requireContext().applicationContext, AppDatabase::class.java, "app.db").build()
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentStorageBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDataStore.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val current = prefs.getInt("count").first()
                prefs.putInt("count", current + 1)
                appendOutput("DataStore count = ${current + 1}")
            }
        }

        binding.btnSecure.setOnClickListener {
            secure.putString("token", "secret-token-123")
            appendOutput("加密读回 token = ${secure.getString("token")}")
        }

        binding.btnRoom.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                db.favoriteDao().insert(
                    NewsFavorite(
                        url = "https://example.com/${System.currentTimeMillis()}",
                        title = "收藏示例",
                        source = "demo",
                        date = "2026-07-02",
                        imageUrl = null,
                    ),
                )
                val count = db.favoriteDao().getAll().first().size
                appendOutput("Room 收藏总数 = $count")
            }
        }

        // 演示 Room Flow 观察
        db.favoriteDao().getAll().collectOnStarted(viewLifecycleOwner) { list ->
            appendOutput("收藏 Flow 更新，共 ${list.size} 条")
        }
    }

    private fun appendOutput(line: String) {
        binding.output.text = "${binding.output.text}\n$line".trim()
    }
}
```

> 注：`System.currentTimeMillis()` 用于生成不同主键，属演示；框架代码不用它。

- [ ] **Step 4: nav_graph 加 storageFragment**

在 `nav_graph.xml` 的 `</navigation>` 前追加：

```xml
    <fragment
        android:id="@+id/storageFragment"
        android:name="com.btg.mvvm.ui.demo.StorageFragment"
        android:label="@string/demo_storage" />
```

- [ ] **Step 5: HomeFragment 接 btnStorage**

在 `HomeFragment.onViewCreated` 里补上：

```kotlin
        binding.btnStorage.setOnClickListener { findNavController().navigate(R.id.storageFragment) }
```

- [ ] **Step 6: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7: 提交**

```bash
git add -A app/src/main/
git commit -m "feat: add storage demo fragment (DataStore/encrypted/Room)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: 全量编译 + 单测 + 真机冒烟（走查各演示页）

**Files:** 无改动（纯验证；有问题回对应任务修）。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 全部单元测试**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`；`NewsViewModelTest`（Hilt 注解不影响手动构造）/`NewsRepositoryTest` 等全绿。

- [ ] **Step 3: 真机冒烟（走查）**

Run: `./gradlew installDebug`
手动：Home → 新闻页（列表加载、下拉刷新）；返回 → 组件页（Toast/确认弹窗/Loading/权限申请）；返回 → 存储页（DataStore 计数、加密、Room 收藏）。逐一确认无崩溃。
Expected: 各页正常；Logcat 无 FATAL/Hilt 崩溃。（无设备时跳过并注明。）

- [ ] **Step 4: 收尾**

无代码改动则不提交；记录验证结果。

---

## Self-Review（对照 spec 第 9 节演示台）

- ✅ 单 Activity + Navigation（Task 3）。
- ✅ 网络+列表+下拉刷新+Coil 图片（Task 1 + Task 3）；加载更多能力由框架 `RecyclerView.onLoadMore` 提供（Phase 3 已建），新闻 Fake 数据固定故不在此强造分页。
- ✅ 权限申请 + 永久拒绝引导（Task 4）。
- ✅ 弹窗 / Toast / Loading（Task 4）；BottomSheet 基类已在 Phase 3 提供（`BaseBottomSheetFragment`），此处以对话框演示交互，BottomSheet 具体实例项目按需继承。
- ✅ 多状态布局 StateLayout（Task 3 新闻页四态）。
- ✅ 存储 DataStore + 加密 + Room 收藏（Task 5）。
- Hilt 端到端：`@HiltViewModel` + `NewsModule`（Task 2），装配点收敛到 Hilt 模块。
- 类型一致性：`NewsUiState` 保留；`collectOnStarted`/`onRefresh`/`showXxxDialog`/`toast`/`PermissionRequester`/`PreferenceStore`/`SecurePreferences`/`AppDatabase` 均为前序阶段已建 API。
