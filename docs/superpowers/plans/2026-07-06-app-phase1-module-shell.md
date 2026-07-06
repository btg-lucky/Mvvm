# 阶段 1:业务模块骨架 + 底部导航壳

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 module_news / module_weather / module_mine 三个 feature module,app 退化为「BottomNavigationView + 导航装配」的壳;新闻演示代码整体迁入 module_news(仍走假数据),演示台页面删除。

**Architecture:** 依赖方向 `app → 3 个 feature → lib_common(api → lib_opensource/lib_widget)`,feature 互不依赖。每个 feature 自带 navigation graph,app 的 `nav_main.xml` `<include>` 三个子 graph,BottomNav menu item id 与子 graph 根 id 一致。

**Tech Stack:** Kotlin 2.0.21 / AGP 8.6.1 / Hilt(KSP) / Navigation 2.8.4 / ViewBinding / Material

## Global Constraints

- 全 Kotlin,禁止 Java;遵循官方编码规范
- 不新增第三方依赖、不改 `gradle/libs.versions.toml` 版本号(只用已有别名)
- `compileSdk 35` / `minSdk 24` / JDK 17 / `jvmTarget = "17"`
- 模块 namespace:news=`com.btg.news`,weather=`com.btg.weather`,mine=`com.btg.mine`
- 用 ViewBinding,禁止 findViewById;Fragment 一律继承 `BaseFragment<VB>`(onDestroyView 自动释放 binding)
- 每个任务结束提交 commit(Conventional Commits)
- 验证命令:`./gradlew assembleDebug`、`./gradlew testDebugUnitTest`

---

### Task 1: 三个 feature module 的 Gradle 骨架

**Files:**
- Modify: `settings.gradle.kts`
- Create: `module_news/build.gradle.kts`
- Create: `module_news/src/main/AndroidManifest.xml`
- Create: `module_weather/build.gradle.kts`
- Create: `module_weather/src/main/AndroidManifest.xml`
- Create: `module_mine/build.gradle.kts`
- Create: `module_mine/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: 三个空的 Android library module,后续任务往里放代码。news/mine 已接 Hilt+KSP;weather 是纯占位不接 Hilt。

- [ ] **Step 1: 修改 settings.gradle.kts 的 include**

```kotlin
include(":app", ":lib_common", ":lib_opensource", ":lib_widget",
    ":module_news", ":module_weather", ":module_mine")
```

- [ ] **Step 2: 创建 module_news/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.btg.news"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":lib_common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 3: 创建 module_news/src/main/AndroidManifest.xml**

```xml
<manifest />
```

- [ ] **Step 4: 创建 module_weather/build.gradle.kts(不接 Hilt)**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.btg.weather"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":lib_common"))
}
```

- [ ] **Step 5: 创建 module_weather/src/main/AndroidManifest.xml**

```xml
<manifest />
```

- [ ] **Step 6: 创建 module_mine/build.gradle.kts(接 Hilt,阶段 3 再加 Room 编译器)**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.btg.mine"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":lib_common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 7: 创建 module_mine/src/main/AndroidManifest.xml**

```xml
<manifest />
```

- [ ] **Step 8: 验证编译**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL(三个新模块参与构建)

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts module_news module_weather module_mine
git commit -m "build: add module_news/module_weather/module_mine gradle skeletons"
```

---

### Task 2: 新闻代码整体迁入 module_news

把 app 里现有的新闻演示代码(数据层 + UI 层 + 布局 + 测试)搬进 `module_news`,包名 `com.btg.mvvm.*` → `com.btg.news.*`,类名改为最终命名(`NewsFragment→NewsListFragment`、`NewsViewModel→NewsListViewModel`、`NewsUiState→NewsListUiState`)。本任务**行为不变**(仍走 FakeNewsDataSource),真实接口在阶段 2。

**Files:**
- Create: `module_news/src/main/java/com/btg/news/data/model/NewsItem.kt`
- Create: `module_news/src/main/java/com/btg/news/data/source/NewsDataSource.kt`
- Create: `module_news/src/main/java/com/btg/news/data/source/FakeNewsDataSource.kt`
- Create: `module_news/src/main/java/com/btg/news/data/source/NewsApi.kt`
- Create: `module_news/src/main/java/com/btg/news/data/source/RemoteNewsDataSource.kt`
- Create: `module_news/src/main/java/com/btg/news/data/repository/NewsRepository.kt`
- Create: `module_news/src/main/java/com/btg/news/di/NewsModule.kt`
- Create: `module_news/src/main/java/com/btg/news/ui/list/NewsAdapter.kt`
- Create: `module_news/src/main/java/com/btg/news/ui/list/NewsEvent.kt`
- Create: `module_news/src/main/java/com/btg/news/ui/list/NewsListUiState.kt`
- Create: `module_news/src/main/java/com/btg/news/ui/list/NewsListViewModel.kt`
- Create: `module_news/src/main/java/com/btg/news/ui/list/NewsListFragment.kt`
- Create: `module_news/src/main/res/layout/fragment_news_list.xml`
- Create: `module_news/src/main/res/layout/item_news.xml`
- Create: `module_news/src/main/res/navigation/nav_news.xml`
- Create: `module_news/src/main/res/values/strings.xml`
- Create: `module_news/src/test/java/com/btg/news/util/MainDispatcherRule.kt`
- Create: `module_news/src/test/java/com/btg/news/data/repository/NewsRepositoryTest.kt`
- Create: `module_news/src/test/java/com/btg/news/data/source/FakeNewsDataSourceTest.kt`
- Create: `module_news/src/test/java/com/btg/news/ui/list/NewsListViewModelTest.kt`
- Delete: `app/src/main/java/com/btg/mvvm/data/model/NewsItem.kt`、`app/src/main/java/com/btg/mvvm/data/source/`(4 个文件)、`app/src/main/java/com/btg/mvvm/data/repository/NewsRepository.kt`、`app/src/main/java/com/btg/mvvm/di/NewsModule.kt`、`app/src/main/java/com/btg/mvvm/ui/news/`(5 个文件)、`app/src/main/res/layout/fragment_news.xml`、`app/src/main/res/layout/item_news.xml`、`app/src/test/java/com/btg/mvvm/data/`、`app/src/test/java/com/btg/mvvm/ui/`、`app/src/test/java/com/btg/mvvm/util/MainDispatcherRule.kt`
- Modify: `app/build.gradle.kts`(加 `implementation(project(":module_news"))`)
- Modify: `app/src/main/res/navigation/nav_graph.xml`(newsFragment 指向新类)

**Interfaces:**
- Consumes: lib_common 的 `BaseFragment<VB>`、`BaseViewModel`、`ApiResult`、`collectOnStarted`、`onRefresh`、`loadUrl`、`StateLayout`
- Produces:
  - `NewsItem(title: String, source: String, date: String, imageUrl: String?, url: String)`(阶段 2 会扩字段)
  - `NewsDataSource { suspend fun fetchNews(): List<NewsItem> }`(阶段 2 改签名)
  - `NewsRepository(dataSource: NewsDataSource, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) { suspend fun getNews(): ApiResult<List<NewsItem>> }`
  - `NewsListFragment`(nav_news.xml 的 startDestination,id `newsListFragment`)
  - 导航图根 id `@+id/nav_news`

- [ ] **Step 1: 迁移数据层 6 个文件**

每个文件内容 = app 里对应原文件,仅改 `package` 与 `import`(`com.btg.mvvm.data.*` → `com.btg.news.data.*`,`com.btg.mvvm.di` → `com.btg.news.di`)。以 `NewsRepository.kt` 为例,其余同理:

```kotlin
package com.btg.news.data.repository

import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsItem
import com.btg.news.data.source.NewsDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository(
    private val dataSource: NewsDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getNews(): ApiResult<List<NewsItem>> = withContext(ioDispatcher) {
        runCatching { dataSource.fetchNews() }
            .fold(
                onSuccess = { ApiResult.Success(it) },
                onFailure = { ApiResult.Error(it) }
            )
    }
}
```

迁移清单(原文件 → 新文件,内容除包名外不变):
- `data/model/NewsItem.kt` → `com/btg/news/data/model/NewsItem.kt`
- `data/source/NewsDataSource.kt` → `com/btg/news/data/source/NewsDataSource.kt`
- `data/source/FakeNewsDataSource.kt` → `com/btg/news/data/source/FakeNewsDataSource.kt`
- `data/source/NewsApi.kt` → `com/btg/news/data/source/NewsApi.kt`
- `data/source/RemoteNewsDataSource.kt` → `com/btg/news/data/source/RemoteNewsDataSource.kt`
- `di/NewsModule.kt` → `com/btg/news/di/NewsModule.kt`

- [ ] **Step 2: 迁移 UI 层 5 个文件(带改名)**

`NewsListUiState.kt`:

```kotlin
package com.btg.news.ui.list

import com.btg.news.data.model.NewsItem

data class NewsListUiState(
    val isLoading: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null
)
```

`NewsEvent.kt`:

```kotlin
package com.btg.news.ui.list

sealed interface NewsEvent {
    data class OpenLink(val url: String) : NewsEvent
}
```

`NewsListViewModel.kt`(原 NewsViewModel,状态类型改为 NewsListUiState,其余逻辑不变):

```kotlin
package com.btg.news.ui.list

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsItem
import com.btg.news.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NewsListViewModel @Inject constructor(
    private val repository: NewsRepository,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(NewsListUiState())
    val uiState: StateFlow<NewsListUiState> = _uiState.asStateFlow()

    private val _events = Channel<NewsEvent>(Channel.BUFFERED)
    val events: Flow<NewsEvent> = _events.receiveAsFlow()

    init {
        loadNews()
    }

    fun loadNews() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getNews()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, items = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.throwable.message)
                }
            }
        }
    }

    fun onNewsClick(item: NewsItem) {
        viewModelScope.launch { _events.send(NewsEvent.OpenLink(item.url)) }
    }
}
```

`NewsListFragment.kt`(原 NewsFragment,binding 类型改 `FragmentNewsListBinding`,状态类型改 `NewsListUiState`,其余不变):

```kotlin
package com.btg.news.ui.list

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
import com.btg.news.databinding.FragmentNewsListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewsListFragment : BaseFragment<FragmentNewsListBinding>() {

    private val viewModel: NewsListViewModel by viewModels()
    private val newsAdapter = NewsAdapter { viewModel.onNewsClick(it) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsListBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = newsAdapter
        binding.stateLayout.setOnRetryListener { viewModel.loadNews() }
        binding.swipeRefresh.onRefresh { viewModel.loadNews() }

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
        viewModel.events.collectOnStarted(viewLifecycleOwner) { handleEvent(it) }
    }

    private fun render(state: NewsListUiState) {
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

`NewsAdapter.kt`:原文件改包名 + binding import 改为 `com.btg.news.databinding.ItemNewsBinding`、model import 改为 `com.btg.news.data.model.NewsItem`,其余不变。

- [ ] **Step 3: 迁移布局与新建导航图/字符串**

`module_news/src/main/res/layout/fragment_news_list.xml`(原 fragment_news.xml,去掉根节点 `android:fitsSystemWindows`——由 app 的 activity 根布局统一处理):

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

`item_news.xml`:原样复制到 `module_news/src/main/res/layout/item_news.xml`。

`module_news/src/main/res/navigation/nav_news.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_news"
    app:startDestination="@id/newsListFragment">

    <fragment
        android:id="@+id/newsListFragment"
        android:name="com.btg.news.ui.list.NewsListFragment"
        android:label="@string/news_title" />

</navigation>
```

`module_news/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="news_title">新闻</string>
    <string name="news_load_error">加载失败，请稍后重试</string>
</resources>
```

- [ ] **Step 4: 迁移三个测试 + MainDispatcherRule**

`module_news/src/test/java/com/btg/news/util/MainDispatcherRule.kt`:原 app 版本改包名 `com.btg.news.util`,内容不变。

`NewsRepositoryTest.kt`、`FakeNewsDataSourceTest.kt`:改包名与 import(`com.btg.mvvm` → `com.btg.news`),断言不变。

`NewsListViewModelTest.kt`:原 NewsViewModelTest 改包名、类名(`NewsViewModelTest→NewsListViewModelTest`)、被测类(`NewsViewModel→NewsListViewModel`),断言不变。

- [ ] **Step 5: 删除 app 中的旧新闻代码,app 接入 module_news**

删除 Files 清单里列出的 app 旧文件。`app/build.gradle.kts` dependencies 顶部加:

```kotlin
implementation(project(":module_news"))
```

`app/src/main/res/navigation/nav_graph.xml` 中 newsFragment 节点改为:

```xml
<fragment
    android:id="@+id/newsFragment"
    android:name="com.btg.news.ui.list.NewsListFragment"
    android:label="@string/demo_news" />
```

注意:`HomeFragment` 里如有对旧 `NewsFragment` 的导航调用不用改(它通过 nav id 导航);本任务后 app 其余演示页保持原样,Task 4 再删。

- [ ] **Step 6: 运行 module_news 单测**

Run: `./gradlew :module_news:testDebugUnitTest`
Expected: BUILD SUCCESSFUL,3 个测试类全部通过

- [ ] **Step 7: 全量编译**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: migrate news demo code from app into module_news"
```

---

### Task 3: 天气与我的占位页

**Files:**
- Create: `module_weather/src/main/java/com/btg/weather/ui/WeatherFragment.kt`
- Create: `module_weather/src/main/res/layout/fragment_weather.xml`
- Create: `module_weather/src/main/res/navigation/nav_weather.xml`
- Create: `module_weather/src/main/res/values/strings.xml`
- Create: `module_mine/src/main/java/com/btg/mine/ui/MineFragment.kt`
- Create: `module_mine/src/main/res/layout/fragment_mine.xml`
- Create: `module_mine/src/main/res/navigation/nav_mine.xml`
- Create: `module_mine/src/main/res/values/strings.xml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: 导航图根 id `@+id/nav_weather`(startDestination `weatherFragment`)、`@+id/nav_mine`(startDestination `mineFragment`)。MineFragment 阶段 3 会重写,本阶段只是占位。

- [ ] **Step 1: WeatherFragment 与资源**

`module_weather/src/main/java/com/btg/weather/ui/WeatherFragment.kt`:

```kotlin
package com.btg.weather.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.weather.databinding.FragmentWeatherBinding

/** 天气占位页:功能未开发,仅展示提示文案。 */
class WeatherFragment : BaseFragment<FragmentWeatherBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentWeatherBinding.inflate(inflater, container, false)
}
```

`module_weather/src/main/res/layout/fragment_weather.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="@string/weather_developing"
        android:textSize="16sp" />

</FrameLayout>
```

`module_weather/src/main/res/navigation/nav_weather.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_weather"
    app:startDestination="@id/weatherFragment">

    <fragment
        android:id="@+id/weatherFragment"
        android:name="com.btg.weather.ui.WeatherFragment"
        android:label="@string/weather_title" />

</navigation>
```

`module_weather/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="weather_title">天气</string>
    <string name="weather_developing">天气功能开发中…</string>
</resources>
```

- [ ] **Step 2: MineFragment 占位与资源**

`module_mine/src/main/java/com/btg/mine/ui/MineFragment.kt`:

```kotlin
package com.btg.mine.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.mine.databinding.FragmentMineBinding

/** 我的页占位:阶段 3 实现登录/注册。 */
class MineFragment : BaseFragment<FragmentMineBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMineBinding.inflate(inflater, container, false)
}
```

`module_mine/src/main/res/layout/fragment_mine.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="@string/mine_title"
        android:textSize="16sp" />

</FrameLayout>
```

`module_mine/src/main/res/navigation/nav_mine.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_mine"
    app:startDestination="@id/mineFragment">

    <fragment
        android:id="@+id/mineFragment"
        android:name="com.btg.mine.ui.MineFragment"
        android:label="@string/mine_title" />

</navigation>
```

`module_mine/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="mine_title">我的</string>
</resources>
```

- [ ] **Step 3: app 接入两个模块**

`app/build.gradle.kts` dependencies 加:

```kotlin
implementation(project(":module_weather"))
implementation(project(":module_mine"))
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add weather and mine placeholder feature modules"
```

---

### Task 4: app 壳改造——删演示台,接 BottomNavigationView

**Files:**
- Delete: `app/src/main/java/com/btg/mvvm/ui/home/HomeFragment.kt`
- Delete: `app/src/main/java/com/btg/mvvm/ui/demo/ComponentsFragment.kt`、`DemoBottomSheet.kt`、`StorageFragment.kt`
- Delete: `app/src/main/java/com/btg/mvvm/data/local/AppDatabase.kt`、`FavoriteDao.kt`、`NewsFavorite.kt`
- Delete: `app/src/main/res/layout/fragment_home.xml`、`fragment_components.xml`、`fragment_storage.xml`、`fragment_demo_bottom_sheet.xml`
- Delete: `app/src/main/res/navigation/nav_graph.xml`
- Create: `app/src/main/res/navigation/nav_main.xml`
- Create: `app/src/main/res/menu/bottom_nav_menu.xml`
- Create: `app/src/main/res/drawable/ic_tab_news.xml`、`ic_tab_weather.xml`、`ic_tab_mine.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/btg/mvvm/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: 三个子 graph 根 id `nav_news`/`nav_weather`/`nav_mine`;顶级页面 id `newsListFragment`/`weatherFragment`/`mineFragment`
- Produces: app 壳完成;后续阶段只在 feature module 内改动

- [ ] **Step 1: 删除演示代码**

```bash
rm -r app/src/main/java/com/btg/mvvm/ui app/src/main/java/com/btg/mvvm/data
rm app/src/main/res/layout/fragment_home.xml app/src/main/res/layout/fragment_components.xml \
   app/src/main/res/layout/fragment_storage.xml app/src/main/res/layout/fragment_demo_bottom_sheet.xml
rm app/src/main/res/navigation/nav_graph.xml
```

- [ ] **Step 2: 新建主导航图 app/src/main/res/navigation/nav_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_main"
    app:startDestination="@id/nav_news">

    <include app:graph="@navigation/nav_news" />
    <include app:graph="@navigation/nav_weather" />
    <include app:graph="@navigation/nav_mine" />

</navigation>
```

- [ ] **Step 3: 新建底部导航 menu(item id 必须与子 graph 根 id 一致)**

`app/src/main/res/menu/bottom_nav_menu.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">

    <item
        android:id="@id/nav_news"
        android:icon="@drawable/ic_tab_news"
        android:title="@string/tab_news" />

    <item
        android:id="@id/nav_weather"
        android:icon="@drawable/ic_tab_weather"
        android:title="@string/tab_weather" />

    <item
        android:id="@id/nav_mine"
        android:icon="@drawable/ic_tab_mine"
        android:title="@string/tab_mine" />

</menu>
```

- [ ] **Step 4: 三个 Tab 图标(Material Icons 标准路径)**

`app/src/main/res/drawable/ic_tab_news.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM14,17H7v-2h7v2zM17,13H7v-2h10v2zM17,9H7V7h10v2z" />
</vector>
```

`app/src/main/res/drawable/ic_tab_weather.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M6.76,4.84l-1.8,-1.79 -1.41,1.41 1.79,1.79 1.42,-1.41zM4,10.5L1,10.5v2h3v-2zM13,0.55h-2L11,3.5h2L13,0.55zM20.45,4.46l-1.41,-1.41 -1.79,1.79 1.41,1.41 1.79,-1.79zM17.24,18.16l1.79,1.8 1.41,-1.41 -1.8,-1.79 -1.4,1.4zM20,10.5v2h3v-2h-3zM12,5.5c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6 -2.69,-6 -6,-6zM11,22.45h2L13,19.5h-2v2.95zM3.55,18.54l1.41,1.41 1.79,-1.8 -1.41,-1.41 -1.79,1.8z" />
</vector>
```

`app/src/main/res/drawable/ic_tab_mine.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" />
</vector>
```

- [ ] **Step 5: 重写 activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:fitsSystemWindows="true">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/nav_host"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_main" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_nav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:labelVisibilityMode="labeled"
        app:menu="@menu/bottom_nav_menu" />

</LinearLayout>
```

- [ ] **Step 6: 重写 MainActivity.kt**

```kotlin
package com.btg.mvvm

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.btg.common.base.BaseActivity
import com.btg.mvvm.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import com.btg.mine.R as MineR
import com.btg.news.R as NewsR
import com.btg.weather.R as WeatherR

/** 单 Activity 壳：底部导航承载 新闻 / 天气 / 我的 三个业务模块。 */
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {

    /** 三个 Tab 的顶级页面：只有停留在这些页面时才显示底部导航。 */
    private val topLevelDestinations = setOf(
        NewsR.id.newsListFragment,
        WeatherR.id.weatherFragment,
        MineR.id.mineFragment,
    )

    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id in topLevelDestinations
        }
    }
}
```

- [ ] **Step 7: 精简 app 字符串与构建脚本**

`app/src/main/res/values/strings.xml` 整体替换为:

```xml
<resources>
    <string name="app_name">Mvvm</string>
    <string name="tab_news">新闻</string>
    <string name="tab_weather">天气</string>
    <string name="tab_mine">我的</string>
</resources>
```

`app/build.gradle.kts` 的 dependencies 整体替换为(壳只留必要项;Room/Retrofit/RecyclerView 等已不直接使用):

```kotlin
dependencies {
    implementation(project(":module_news"))
    implementation(project(":module_weather"))
    implementation(project(":module_mine"))
    implementation(project(":lib_common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

- [ ] **Step 8: 全量验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL(module_news 3 个测试类 + lib_common 既有测试全过;app 已无测试)

- [ ] **Step 9: 手动验证(装机)**

Run: `./gradlew installDebug`(需连接设备)
检查:启动进新闻 Tab(假数据 3 条);底部三 Tab 可切换;天气页显示"天气功能开发中…";我的页显示"我的"。

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: replace demo showcase with bottom-nav app shell (news/weather/mine)"
```
