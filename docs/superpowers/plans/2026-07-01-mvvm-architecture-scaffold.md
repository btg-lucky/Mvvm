# MVVM 架构补齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `Mvvm` 工程从纯 Java 网络脚手架补齐为一套可复用的 Kotlin MVVM 架构，用"新闻列表"示范页跑通 View → ViewModel → Repository → DataSource 端到端。

**Architecture:** 单向数据流。View（Activity）只渲染 `StateFlow<UiState>` 并转发事件；ViewModel 持有 UI 状态、调用 Repository；Repository 是数据唯一入口，通过可替换的 `NewsDataSource` 取数（本次用 `FakeNewsDataSource`）。手动 DI（`ViewModelProvider.Factory`），无 DI 框架。

**Tech Stack:** Kotlin 2.0.x（K2）、协程 + Flow、AGP 8.x + Gradle 8.x（JDK 17）、Kotlin DSL 构建脚本 + Gradle Version Catalog、ViewBinding、AndroidX lifecycle 2.8.x、Retrofit 2.11 / OkHttp 4.12、JUnit4 + kotlinx-coroutines-test。

## Global Constraints

- 新代码一律 Kotlin；现有 Java 文件（`ApiRetrofit` 等）保留为 Java，仅在必要时最小改动，不重写为 Kotlin。
- `minSdk = 24`，`compileSdk = 35`，`targetSdk = 35`。
- JDK 17（AGP 8.x 强制要求）。
- 异步一律协程 + Flow，无 RxJava。挂起函数可取消；IO 切换在 Repository 用 `withContext(ioDispatcher)`。
- ViewModel 不持有 View/Context/Activity 引用，保持可单元测试。
- 视图访问用 ViewBinding，禁止 `findViewById`。
- 列表用 `RecyclerView` + `ListAdapter` + `DiffUtil`，禁止 `notifyDataSetChanged`。
- 用户可见文案（字符串）进 `res/values/strings.xml`，不硬编码。
- 依赖版本集中在 `gradle/libs.versions.toml`，不在模块脚本里写死坐标。
- 版本号为"起始建议值"（截至编写时的稳定版方向）；若某组合报不兼容，按 Android Studio 提示 / AGP release notes 的兼容矩阵在同版本族内微调，以 `./gradlew` 编译通过为准。
- 单元测试：手写 fake 替身，不引 mockk/mockito/Turbine。
- 模块依赖方向不变：`app → lib_common →(api) lib_opensource + lib_widget`。

---

## 分阶段说明

- **阶段 1（Task 1）**：构建全面现代化。构建迁移本质是原子的（老 AGP 与新 Gradle、新 catalog 与旧模块脚本无法共存于中间态），故合为一个 task，末尾以 `./gradlew assembleDebug` 成功作为唯一验证点。
- **阶段 2（Task 2–7）**：MVVM 代码，逐层 TDD（数据层、ViewModel）或编译/运行验证（View 层）。

---

## 文件结构总览

**阶段 1 改动的构建文件：**
- `gradle/wrapper/gradle-wrapper.properties`（改 distributionUrl → Gradle 8.9）
- `gradle.properties`（AGP 8 flags）
- `settings.gradle` → 删除，新建 `settings.gradle.kts`
- `build.gradle` → 删除，新建 `build.gradle.kts`
- `config.gradle` → 删除（被 version catalog 取代）
- `gradle/libs.versions.toml`（新建）
- `app/build.gradle` → 删除，新建 `app/build.gradle.kts`
- `lib_common/build.gradle` → 删除，新建 `lib_common/build.gradle.kts`
- `lib_opensource/build.gradle` → 删除，新建 `lib_opensource/build.gradle.kts`
- `lib_widget/build.gradle` → 删除，新建 `lib_widget/build.gradle.kts`
- 4 个 `AndroidManifest.xml`（移除 `package` 属性；清理 `lib_widget` 的非法 `/`）
- `lib_common/.../http/api/ApiRetrofit.java`（移除 RxJava2 引用）

**阶段 2 新建的源码文件：**
- `lib_common/.../result/ApiResult.kt`
- `lib_common/.../base/BaseViewModel.kt`
- `lib_common/.../base/BaseActivity.kt`（替换现有空 stub）
- `app/.../data/model/NewsItem.kt`
- `app/.../data/source/NewsDataSource.kt`
- `app/.../data/source/FakeNewsDataSource.kt`
- `app/.../data/source/NewsApi.kt`（Retrofit suspend 示例）
- `app/.../data/source/RemoteNewsDataSource.kt`（TODO 骨架）
- `app/.../data/repository/NewsRepository.kt`
- `app/.../ui/news/NewsUiState.kt`
- `app/.../ui/news/NewsEvent.kt`
- `app/.../ui/news/NewsViewModel.kt`
- `app/.../ui/news/NewsViewModelFactory.kt`
- `app/.../ui/news/NewsAdapter.kt`
- `app/.../MainActivity.kt`（替换 `MainActivity.java`）
- `app/src/main/res/layout/activity_main.xml`（改造）
- `app/src/main/res/layout/item_news.xml`（新建）
- `app/src/main/res/values/strings.xml`（增文案）
- 测试：`app/src/test/.../util/MainDispatcherRule.kt`、`FakeNewsDataSourceTest.kt`、`NewsRepositoryTest.kt`、`NewsViewModelTest.kt`

> 源码目录沿用现有 `src/main/java/...` 结构（Kotlin 文件也放 `java` 目录，AGP 支持）。`app` 包根 `com/btg/mvvm`，`lib_common` 包根 `com/btg/common`。

---

## Task 1: 构建现代化（阶段 1）

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `gradle.properties`
- Delete: `settings.gradle`, `build.gradle`, `config.gradle`, `app/build.gradle`, `lib_common/build.gradle`, `lib_opensource/build.gradle`, `lib_widget/build.gradle`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `lib_common/build.gradle.kts`, `lib_opensource/build.gradle.kts`, `lib_widget/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`, `lib_common/src/main/AndroidManifest.xml`, `lib_opensource/src/main/AndroidManifest.xml`, `lib_widget/src/main/AndroidManifest.xml`
- Modify: `lib_common/src/main/java/com/btg/common/http/api/ApiRetrofit.java`

**Interfaces:**
- Consumes: 无（起点）
- Produces: 现代化构建（catalog 别名 `libs.plugins.android.application` / `libs.plugins.android.library` / `libs.plugins.kotlin.android`，及库别名见下）。各模块 `namespace`：app=`com.btg.mvvm`，lib_common=`com.btg.common`，lib_opensource=`com.btg.opensource`，lib_widget=`com.btg.widget`。ViewBinding 已启用。

- [ ] **Step 1: 确认 JDK 17**

Run: `java -version`
Expected: 显示 `17.x`（如 `openjdk version "17..."`）。若不是 17，先切换到 JDK 17 再继续（AGP 8.x 强制要求；Gradle 8.9 支持 JDK 17）。

- [ ] **Step 2: 升级 Gradle wrapper**

修改 `gradle/wrapper/gradle-wrapper.properties`，把 `distributionUrl` 一行改为：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

其余行不动。

- [ ] **Step 3: 改写 `gradle.properties`**

整个文件替换为：

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

（移除了 `android.enableJetifier=true`——现代 AndroidX 依赖不需要 Jetifier。）

- [ ] **Step 4: 创建 `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.6.1"
kotlin = "2.0.21"
coreKtx = "1.13.1"
appcompat = "1.7.0"
constraintlayout = "2.1.4"
recyclerview = "1.3.2"
lifecycle = "2.8.7"
activity = "1.9.3"
coroutines = "1.9.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
gson = "2.11.0"
logger = "2.2.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
androidx-constraintlayout = { module = "androidx.constraintlayout:constraintlayout", version.ref = "constraintlayout" }
androidx-recyclerview = { module = "androidx.recyclerview:recyclerview", version.ref = "recyclerview" }
androidx-lifecycle-viewmodel-ktx = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-activity-ktx = { module = "androidx.activity:activity-ktx", version.ref = "activity" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { module = "com.squareup.retrofit2:converter-gson", version.ref = "retrofit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
logger = { module = "com.orhanobut:logger", version.ref = "logger" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 5: 删除旧 Groovy 构建脚本**

Run:
```bash
git rm settings.gradle build.gradle config.gradle app/build.gradle lib_common/build.gradle lib_opensource/build.gradle lib_widget/build.gradle
```

- [ ] **Step 6: 创建 `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Mvvm"
include(":app", ":lib_common", ":lib_opensource", ":lib_widget")
```

- [ ] **Step 7: 创建根 `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 8: 创建 `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.btg.mvvm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.btg.mvvm"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":lib_common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

（`release` 设 `isMinifyEnabled = false`：示范页依赖 Gson 反射，开混淆需额外 keep 规则，本次不引入。）

- [ ] **Step 9: 创建 `lib_common/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.btg.common"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(project(":lib_opensource"))
    api(project(":lib_widget"))
    api(libs.androidx.appcompat)
    api(libs.androidx.lifecycle.viewmodel.ktx)
}
```

（`appcompat` 与 `lifecycle-viewmodel-ktx` 用 `api`：`BaseActivity`/`BaseViewModel` 基类要暴露给 `app` 继承。）

- [ ] **Step 10: 创建 `lib_opensource/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.btg.opensource"
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
}

dependencies {
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.retrofit.converter.gson)
    api(libs.gson)
    api(libs.logger)
}
```

（移除了 `adapter-rxjava2` 与 `rxandroid`；`lib_opensource` 无 Kotlin 源码，不加 kotlin 插件。）

- [ ] **Step 11: 创建 `lib_widget/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
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
}

dependencies {
    api(libs.androidx.appcompat)
}
```

- [ ] **Step 12: 从各 AndroidManifest 移除 `package` 属性（AGP 8 用 `namespace` 取代）**

`app/src/main/AndroidManifest.xml`：把根标签
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.btg.mvvm">
```
改为
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
```
（`<application>`/`<activity>` 内容保持不变。）

`lib_common/src/main/AndroidManifest.xml` 整个文件替换为：
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

`lib_opensource/src/main/AndroidManifest.xml` 整个文件替换为：
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

`lib_widget/src/main/AndroidManifest.xml` 整个文件替换为（原文件含非法的 `/` 字符，一并清理）：
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 13: 从 `ApiRetrofit.java` 移除 RxJava2 引用**

在 `lib_common/src/main/java/com/btg/common/http/api/ApiRetrofit.java` 中：

删除这行 import：
```java
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
```

在 `initRetrofit()` 里删除这行（连同其上方注释）：
```java
                    //支持RxJava2
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
```
删除后 `new Retrofit.Builder()...` 链保留 `.baseUrl(...)`、`.addConverterFactory(...)`、`.client(...)`、`.build()`。

- [ ] **Step 14: 同步 Gradle 并全量编译**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`。首次会下载 Gradle 8.9 + AGP，耗时较长。

若报版本不兼容（如 AGP 与 compileSdk 35、或 Kotlin 与 AGP），按报错提示在 `libs.versions.toml` 内同版本族微调（见 Global Constraints 最后一条），重跑直至通过。

- [ ] **Step 15: Commit**

```bash
git add -A
git commit -m "build: migrate to Kotlin DSL + version catalog, AGP 8 / Gradle 8, drop RxJava2"
```

---

## Task 2: 领域类型 NewsItem + ApiResult

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/result/ApiResult.kt`
- Create: `app/src/main/java/com/btg/mvvm/data/model/NewsItem.kt`

**Interfaces:**
- Consumes: 无
- Produces:
  - `com.btg.common.result.ApiResult<out T>`（`sealed interface`）with `ApiResult.Success<T>(val data: T)` 和 `ApiResult.Error(val throwable: Throwable)`
  - `com.btg.mvvm.data.model.NewsItem(title: String, source: String, date: String, imageUrl: String?, url: String)`（`data class`）

> 纯数据/类型，无行为逻辑，不写单元测试，用编译验证。

- [ ] **Step 1: 创建 `ApiResult.kt`**

```kotlin
package com.btg.common.result

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val throwable: Throwable) : ApiResult<Nothing>
}
```

- [ ] **Step 2: 创建 `NewsItem.kt`**

```kotlin
package com.btg.mvvm.data.model

data class NewsItem(
    val title: String,
    val source: String,
    val date: String,
    val imageUrl: String?,
    val url: String
)
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add lib_common/src/main/java/com/btg/common/result/ApiResult.kt app/src/main/java/com/btg/mvvm/data/model/NewsItem.kt
git commit -m "feat: add NewsItem model and ApiResult sealed type"
```

---

## Task 3: NewsDataSource 接口 + FakeNewsDataSource

**Files:**
- Create: `app/src/main/java/com/btg/mvvm/data/source/NewsDataSource.kt`
- Create: `app/src/main/java/com/btg/mvvm/data/source/FakeNewsDataSource.kt`
- Test: `app/src/test/java/com/btg/mvvm/data/source/FakeNewsDataSourceTest.kt`

**Interfaces:**
- Consumes: `NewsItem`（Task 2）
- Produces:
  - `interface NewsDataSource { suspend fun fetchNews(): List<NewsItem> }`（失败抛异常）
  - `class FakeNewsDataSource : NewsDataSource`（返回一批非空假数据，含 `delay(600)`）

- [ ] **Step 1: 创建接口 `NewsDataSource.kt`**

```kotlin
package com.btg.mvvm.data.source

import com.btg.mvvm.data.model.NewsItem

/** 数据源统一入口。实现：失败时抛异常，由 Repository 捕获包装。 */
interface NewsDataSource {
    suspend fun fetchNews(): List<NewsItem>
}
```

- [ ] **Step 2: 写失败测试 `FakeNewsDataSourceTest.kt`**

```kotlin
package com.btg.mvvm.data.source

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNewsDataSourceTest {

    @Test
    fun `fetchNews returns non-empty list`() = runTest {
        val dataSource = FakeNewsDataSource()

        val result = dataSource.fetchNews()

        assertTrue("假数据源应返回非空列表", result.isNotEmpty())
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.btg.mvvm.data.source.FakeNewsDataSourceTest"`
Expected: 编译失败 / FAIL —— `FakeNewsDataSource` 未定义。

- [ ] **Step 4: 实现 `FakeNewsDataSource.kt`**

```kotlin
package com.btg.mvvm.data.source

import com.btg.mvvm.data.model.NewsItem
import kotlinx.coroutines.delay

class FakeNewsDataSource : NewsDataSource {

    override suspend fun fetchNews(): List<NewsItem> {
        delay(600) // 模拟网络耗时
        return SAMPLE_NEWS
    }

    private companion object {
        val SAMPLE_NEWS = listOf(
            NewsItem(
                title = "示范新闻一：MVVM 架构落地",
                source = "示范来源",
                date = "2026-07-01",
                imageUrl = null,
                url = "https://example.com/news/1"
            ),
            NewsItem(
                title = "示范新闻二：协程与 Flow",
                source = "示范来源",
                date = "2026-07-01",
                imageUrl = null,
                url = "https://example.com/news/2"
            ),
            NewsItem(
                title = "示范新闻三：可替换数据源",
                source = "示范来源",
                date = "2026-07-01",
                imageUrl = null,
                url = "https://example.com/news/3"
            )
        )
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.btg.mvvm.data.source.FakeNewsDataSourceTest"`
Expected: PASS（`runTest` 会自动跳过 `delay`）。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/btg/mvvm/data/source/NewsDataSource.kt app/src/main/java/com/btg/mvvm/data/source/FakeNewsDataSource.kt app/src/test/java/com/btg/mvvm/data/source/FakeNewsDataSourceTest.kt
git commit -m "feat: add NewsDataSource interface and FakeNewsDataSource"
```

---

## Task 4: NewsRepository

**Files:**
- Create: `app/src/main/java/com/btg/mvvm/data/repository/NewsRepository.kt`
- Test: `app/src/test/java/com/btg/mvvm/data/repository/NewsRepositoryTest.kt`

**Interfaces:**
- Consumes: `NewsDataSource`（Task 3）、`NewsItem`（Task 2）、`ApiResult`（Task 2）
- Produces:
  - `class NewsRepository(dataSource: NewsDataSource, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`
  - `suspend fun getNews(): ApiResult<List<NewsItem>>`（成功 → `Success`，抛异常 → `Error`）

- [ ] **Step 1: 写失败测试 `NewsRepositoryTest.kt`**

```kotlin
package com.btg.mvvm.data.repository

import com.btg.common.result.ApiResult
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.data.source.NewsDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRepositoryTest {

    private val sample = listOf(
        NewsItem("t", "s", "d", null, "https://example.com/1")
    )

    @Test
    fun `getNews returns Success when data source succeeds`() = runTest {
        val repository = NewsRepository(
            dataSource = object : NewsDataSource {
                override suspend fun fetchNews(): List<NewsItem> = sample
            },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.getNews()

        assertEquals(ApiResult.Success(sample), result)
    }

    @Test
    fun `getNews returns Error when data source throws`() = runTest {
        val boom = RuntimeException("boom")
        val repository = NewsRepository(
            dataSource = object : NewsDataSource {
                override suspend fun fetchNews(): List<NewsItem> = throw boom
            },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.getNews()

        assertTrue(result is ApiResult.Error && result.throwable === boom)
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.btg.mvvm.data.repository.NewsRepositoryTest"`
Expected: 编译失败 —— `NewsRepository` 未定义。

- [ ] **Step 3: 实现 `NewsRepository.kt`**

```kotlin
package com.btg.mvvm.data.repository

import com.btg.common.result.ApiResult
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.data.source.NewsDataSource
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

- [ ] **Step 4: 运行测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.btg.mvvm.data.repository.NewsRepositoryTest"`
Expected: PASS（两个用例）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/btg/mvvm/data/repository/NewsRepository.kt app/src/test/java/com/btg/mvvm/data/repository/NewsRepositoryTest.kt
git commit -m "feat: add NewsRepository mapping data source to ApiResult"
```

---

## Task 5: 远程数据源骨架 + Retrofit suspend 示例

**Files:**
- Create: `app/src/main/java/com/btg/mvvm/data/source/NewsApi.kt`
- Create: `app/src/main/java/com/btg/mvvm/data/source/RemoteNewsDataSource.kt`

**Interfaces:**
- Consumes: `NewsDataSource`（Task 3）、`NewsItem`（Task 2）
- Produces:
  - `interface NewsApi { suspend fun getNews(type: String): NewsResponse }` + `data class NewsResponse`（占位）
  - `class RemoteNewsDataSource(api: NewsApi) : NewsDataSource`（`fetchNews()` 为 `TODO` 骨架）

> 落实 spec 第 4 节"`ApiService` 加 suspend 示例 + `RemoteNewsDataSource` 留骨架"。现有 `com.btg.common.http.api.ApiService` 是 Java 空标记接口，无法写 `suspend`，故新建 Kotlin 接口 `NewsApi`。本 task 只需编译通过（骨架不被调用）。

- [ ] **Step 1: 创建 `NewsApi.kt`**

```kotlin
package com.btg.mvvm.data.source

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 展示 Retrofit suspend 接口形态。有真实新闻接口后按其响应结构完善 [NewsResponse]。
 */
interface NewsApi {
    @GET("index")
    suspend fun getNews(@Query("type") type: String): NewsResponse
}

/** 占位响应体，待接入真实接口后按后端结构补全字段。 */
data class NewsResponse(
    val items: List<NewsItem> = emptyList()
)
```

- [ ] **Step 2: 创建 `RemoteNewsDataSource.kt`**

```kotlin
package com.btg.mvvm.data.source

import com.btg.mvvm.data.model.NewsItem

/**
 * 真实网络数据源骨架。
 *
 * TODO: 有真实 API key 后实现——调用 [NewsApi] 并把响应映射为 List<NewsItem>。
 * 届时只需在 MainActivity 的手动 DI 处，把 FakeNewsDataSource 换成
 * RemoteNewsDataSource(newsApi)，Repository / ViewModel / View 都不用改。
 */
class RemoteNewsDataSource(private val api: NewsApi) : NewsDataSource {
    override suspend fun fetchNews(): List<NewsItem> {
        TODO("接入真实新闻接口后实现：调用 api.getNews(...) 并映射为 List<NewsItem>")
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/btg/mvvm/data/source/NewsApi.kt app/src/main/java/com/btg/mvvm/data/source/RemoteNewsDataSource.kt
git commit -m "feat: add Retrofit suspend NewsApi and RemoteNewsDataSource skeleton"
```

---

## Task 6: ViewModel 层

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/base/BaseViewModel.kt`
- Create: `app/src/main/java/com/btg/mvvm/ui/news/NewsUiState.kt`
- Create: `app/src/main/java/com/btg/mvvm/ui/news/NewsEvent.kt`
- Create: `app/src/main/java/com/btg/mvvm/ui/news/NewsViewModel.kt`
- Create: `app/src/main/java/com/btg/mvvm/ui/news/NewsViewModelFactory.kt`
- Test: `app/src/test/java/com/btg/mvvm/util/MainDispatcherRule.kt`
- Test: `app/src/test/java/com/btg/mvvm/ui/news/NewsViewModelTest.kt`

**Interfaces:**
- Consumes: `NewsRepository`（Task 4）、`NewsItem`（Task 2）、`ApiResult`（Task 2）
- Produces:
  - `open class BaseViewModel : ViewModel()`（`com.btg.common.base`）
  - `data class NewsUiState(isLoading: Boolean = false, items: List<NewsItem> = emptyList(), errorMessage: String? = null)`
  - `sealed interface NewsEvent { data class OpenLink(val url: String) : NewsEvent }`
  - `class NewsViewModel(repo: NewsRepository) : BaseViewModel()`，暴露 `uiState: StateFlow<NewsUiState>`、`events: Flow<NewsEvent>`；方法 `loadNews()`、`onNewsClick(item: NewsItem)`
  - `class NewsViewModelFactory(repo: NewsRepository) : ViewModelProvider.Factory`

- [ ] **Step 1: 创建 `BaseViewModel.kt`**

```kotlin
package com.btg.common.base

import androidx.lifecycle.ViewModel

/** MVVM 分层锚点。暂不抽象通用逻辑（YAGNI），出现真实重复再上提。 */
open class BaseViewModel : ViewModel()
```

- [ ] **Step 2: 创建 `NewsUiState.kt` 与 `NewsEvent.kt`**

`NewsUiState.kt`:
```kotlin
package com.btg.mvvm.ui.news

import com.btg.mvvm.data.model.NewsItem

data class NewsUiState(
    val isLoading: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null
)
```

`NewsEvent.kt`:
```kotlin
package com.btg.mvvm.ui.news

sealed interface NewsEvent {
    data class OpenLink(val url: String) : NewsEvent
}
```

- [ ] **Step 3: 创建测试工具 `MainDispatcherRule.kt`**

```kotlin
package com.btg.mvvm.util

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

- [ ] **Step 4: 写失败测试 `NewsViewModelTest.kt`**

```kotlin
package com.btg.mvvm.ui.news

import com.btg.common.result.ApiResult
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.data.repository.NewsRepository
import com.btg.mvvm.data.source.NewsDataSource
import com.btg.mvvm.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sample = listOf(
        NewsItem("t", "s", "d", null, "https://example.com/1")
    )

    /** 用真实 NewsRepository + 受控 fake 数据源，通过数据源控制成功/失败。 */
    private fun repoReturning(result: ApiResult<List<NewsItem>>): NewsRepository {
        val dataSource = object : NewsDataSource {
            override suspend fun fetchNews(): List<NewsItem> = when (result) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> throw result.throwable
            }
        }
        return NewsRepository(dataSource, mainDispatcherRule.testDispatcher)
    }

    @Test
    fun `loadNews success updates uiState with items`() = runTest {
        val viewModel = NewsViewModel(repoReturning(ApiResult.Success(sample)))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(sample, state.items)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadNews error updates uiState with errorMessage`() = runTest {
        val viewModel = NewsViewModel(repoReturning(ApiResult.Error(RuntimeException("boom"))))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertEquals("boom", state.errorMessage)
    }

    @Test
    fun `onNewsClick emits OpenLink event`() = runTest {
        val viewModel = NewsViewModel(repoReturning(ApiResult.Success(sample)))
        val received = mutableListOf<NewsEvent>()
        val job = launch { viewModel.events.collect { received.add(it) } }

        viewModel.onNewsClick(sample.first())

        assertEquals(NewsEvent.OpenLink(sample.first().url), received.first())
        job.cancel()
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.btg.mvvm.ui.news.NewsViewModelTest"`
Expected: 编译失败 —— `NewsViewModel` / `NewsViewModelFactory` 未定义。

- [ ] **Step 6: 实现 `NewsViewModel.kt`**

```kotlin
package com.btg.mvvm.ui.news

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.data.repository.NewsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewsViewModel(private val repository: NewsRepository) : BaseViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _events = Channel<NewsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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

- [ ] **Step 7: 实现 `NewsViewModelFactory.kt`**

```kotlin
package com.btg.mvvm.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.btg.mvvm.data.repository.NewsRepository

class NewsViewModelFactory(
    private val repository: NewsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        NewsViewModel(repository) as T
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.btg.mvvm.ui.news.NewsViewModelTest"`
Expected: PASS（三个用例）。

- [ ] **Step 9: Commit**

```bash
git add lib_common/src/main/java/com/btg/common/base/BaseViewModel.kt app/src/main/java/com/btg/mvvm/ui/news/ app/src/test/java/com/btg/mvvm/util/MainDispatcherRule.kt app/src/test/java/com/btg/mvvm/ui/news/NewsViewModelTest.kt
git commit -m "feat: add NewsViewModel with StateFlow ui state and one-shot events"
```

---

## Task 7: View 层（BaseActivity + 布局 + Adapter + MainActivity）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/base/BaseActivity.kt`（替换现有空 `BaseActivity.java`）
- Delete: `lib_common/src/main/java/com/btg/common/base/BaseActivity.java`
- Create: `app/src/main/java/com/btg/mvvm/ui/news/NewsAdapter.kt`
- Create: `app/src/main/java/com/btg/mvvm/MainActivity.kt`
- Delete: `app/src/main/java/com/btg/mvvm/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/item_news.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `NewsViewModel` / `NewsViewModelFactory` / `NewsUiState` / `NewsEvent`（Task 6）、`NewsRepository`（Task 4）、`FakeNewsDataSource`（Task 3）、`NewsItem`（Task 2）
- Produces:
  - `abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity()`，抽象 `inflateBinding(inflater): VB`，`protected lateinit var binding: VB`
  - `class NewsAdapter(onClick: (NewsItem) -> Unit) : ListAdapter<NewsItem, NewsAdapter.NewsViewHolder>`
  - `class MainActivity : BaseActivity<ActivityMainBinding>`

> View 层无单元测试（UI）；验证 = `assembleDebug` 通过 + 手动运行观察。

- [ ] **Step 1: 用 Kotlin `BaseActivity` 替换空 stub**

先删旧文件：
```bash
git rm lib_common/src/main/java/com/btg/common/base/BaseActivity.java
```

创建 `lib_common/src/main/java/com/btg/common/base/BaseActivity.kt`:
```kotlin
package com.btg.common.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB

    protected abstract fun inflateBinding(inflater: LayoutInflater): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
    }
}
```

- [ ] **Step 2: 增补字符串资源**

在 `app/src/main/res/values/strings.xml` 的 `<resources>` 内加：
```xml
    <string name="news_load_error">加载失败，请稍后重试</string>
```
（现有 `app_name` 保留。）

- [ ] **Step 3: 创建列表项布局 `item_news.xml`**

`app/src/main/res/layout/item_news.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="?android:attr/selectableItemBackground">

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

- [ ] **Step 4: 改造 `activity_main.xml`**

整个文件替换为：
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <ProgressBar
        android:id="@+id/progressBar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <TextView
        android:id="@+id/errorText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/news_load_error"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 5: 创建 `NewsAdapter.kt`**

```kotlin
package com.btg.mvvm.ui.news

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btg.mvvm.data.model.NewsItem
import com.btg.mvvm.databinding.ItemNewsBinding

class NewsAdapter(
    private val onClick: (NewsItem) -> Unit
) : ListAdapter<NewsItem, NewsAdapter.NewsViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NewsViewHolder(
        private val binding: ItemNewsBinding,
        private val onClick: (NewsItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NewsItem) {
            binding.titleText.text = item.title
            binding.sourceText.text = item.source
            binding.dateText.text = item.date
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<NewsItem>() {
            override fun areItemsTheSame(oldItem: NewsItem, newItem: NewsItem) =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: NewsItem, newItem: NewsItem) =
                oldItem == newItem
        }
    }
}
```

- [ ] **Step 6: 用 Kotlin `MainActivity` 替换 Java 版**

先删旧文件：
```bash
git rm app/src/main/java/com/btg/mvvm/MainActivity.java
```

创建 `app/src/main/java/com/btg/mvvm/MainActivity.kt`:
```kotlin
package com.btg.mvvm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.btg.common.base.BaseActivity
import com.btg.mvvm.data.repository.NewsRepository
import com.btg.mvvm.data.source.FakeNewsDataSource
import com.btg.mvvm.databinding.ActivityMainBinding
import com.btg.mvvm.ui.news.NewsAdapter
import com.btg.mvvm.ui.news.NewsEvent
import com.btg.mvvm.ui.news.NewsUiState
import com.btg.mvvm.ui.news.NewsViewModel
import com.btg.mvvm.ui.news.NewsViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val viewModel: NewsViewModel by viewModels {
        // 手动 DI 唯一装配点：将来接真实接口时把 FakeNewsDataSource 换成 RemoteNewsDataSource。
        NewsViewModelFactory(NewsRepository(FakeNewsDataSource()))
    }

    private val newsAdapter = NewsAdapter { viewModel.onNewsClick(it) }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = newsAdapter
        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: NewsUiState) {
        binding.progressBar.isVisible = state.isLoading
        binding.errorText.isVisible = state.errorMessage != null
        newsAdapter.submitList(state.items)
    }

    private fun handleEvent(event: NewsEvent) {
        when (event) {
            is NewsEvent.OpenLink ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
        }
    }
}
```

- [ ] **Step 7: 全量编译**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`（ViewBinding 生成 `ActivityMainBinding`、`ItemNewsBinding`）。

- [ ] **Step 8: 跑全部单元测试**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 全部 PASS（`FakeNewsDataSourceTest` / `NewsRepositoryTest` / `NewsViewModelTest`）。

- [ ] **Step 9: 手动运行验证**

在模拟器/设备安装运行：`./gradlew :app:installDebug`（或 Android Studio Run）。
Expected：启动后短暂进度条 → 显示 3 条示范新闻列表；点击任一条打开浏览器到对应 `example.com` 链接。

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: add news list screen wiring View to ViewModel via ViewBinding"
```

---

## Self-Review 结果

**1. Spec 覆盖**（逐节核对）：
- §3 分层/模块归属 → Task 1（namespace/依赖）、贯穿 Task 2–7 ✅
- §4 数据层（NewsItem / ApiResult / DataSource / Fake / Repository / 网络改造 / RemoteNewsDataSource 骨架） → Task 2、3、4、5，ApiRetrofit 去 RxJava 在 Task 1 Step 13 ✅
- §5 ViewModel（UiState / Event / ViewModel / Factory / BaseViewModel） → Task 6 ✅
- §6 View（BaseActivity / MainActivity / ListAdapter+DiffUtil / repeatOnLifecycle / 字符串资源） → Task 7 ✅
- §7 基础设施（Kotlin DSL / catalog / JDK17-AGP8-Gradle8-Kotlin2.0 / compileSdk35-minSdk24 / ViewBinding / 移除 RxJava2 / 两阶段） → Task 1 ✅
- §8 测试（Repository + ViewModel 单测 / coroutines-test / MainDispatcherRule / 手写 fake、不引 mockk/Turbine） → Task 3、4、6 ✅
- §9 YAGNI（RemoteNewsDataSource 留骨架、无 Fragment、无 Espresso、无 DI 框架、BaseViewModel 极薄） → 遵守 ✅

**2. 占位符扫描**：无 TBD/TODO 式计划空洞。源码中 `RemoteNewsDataSource` 的 `TODO(...)` 是 spec 明确要求的运行时骨架，非计划占位。

**3. 类型一致性**：`ApiResult.Success/Error`、`NewsDataSource.fetchNews()`、`NewsRepository.getNews()`、`NewsViewModel.uiState/events/loadNews()/onNewsClick()`、`NewsEvent.OpenLink`、`BaseActivity.inflateBinding()` 在定义处（Task 2/3/4/6/7）与消费处签名一致；ViewBinding 生成类名 `ActivityMainBinding`/`ItemNewsBinding` 与布局文件名 `activity_main.xml`/`item_news.xml` 对应。

**已知不确定项**：版本号组合（AGP 8.6.1 / Gradle 8.9 / Kotlin 2.0.21 / compileSdk 35 等）以 Task 1 Step 14 的编译结果为准，不兼容则同版本族微调——已在 Global Constraints 注明。
