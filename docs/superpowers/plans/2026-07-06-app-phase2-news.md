# 阶段 2:新闻模块——聚合数据真实接口 + 分类 + 分页 + 详情

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** module_news 接入聚合数据「新闻头条」真实接口:列表页顶部 TabLayout 十分类 + 下拉刷新 + 上拉分页,点击进详情页(原生头部 + WebView 渲染 content HTML,失败降级加载原文 url)。

**Architecture:** `NewsListFragment → NewsListViewModel → NewsRepository → NewsDataSource(Remote/Fake) → NewsApi(Retrofit)`。聚合响应外壳 `JuheResponse<T>`(error_code/reason/result)经 `unwrap()` 接入 lib_common 的 `safeApiCall → ApiResult` 体系。API key 从 `local.properties` 注入 BuildConfig。

**Tech Stack:** Retrofit 2.11 / Gson / Hilt / Coil / TabLayout / WebView / kotlinx-coroutines-test

## Global Constraints

- 全 Kotlin;不新增第三方依赖、不改版本号
- API key 不进 git:`local.properties` 里 `JUHE_API_KEY=你的key`;代码只读 `BuildConfig.JUHE_API_KEY`
- 接口地址:列表 `GET https://v.juhe.cn/toutiao/index`,详情 `GET https://v.juhe.cn/toutiao/content`;成功码 `error_code == 0`
- 分类 type:top/guonei/guoji/yule/tiyu/keji/caijing/youxi/qiche/jiankang;page ≤ 50;page_size ≤ 30;固定 `is_filter=1`
- 测试只用 `runTest` + 手写 fake + `MainDispatcherRule`,不引 mock 框架
- 网络在 IO dispatcher,ViewModel 不持有 Context
- 每任务一 commit

**前置:** 阶段 1 已完成(module_news 存在且走假数据)。

---

### Task 1: BuildConfig 注入 JUHE_API_KEY

**Files:**
- Modify: `module_news/build.gradle.kts`

**Interfaces:**
- Produces: `com.btg.news.BuildConfig.JUHE_API_KEY: String`(未配置时为空串)

- [ ] **Step 1: 修改 module_news/build.gradle.kts**

文件顶部(plugins 块之后)加读取逻辑,android 块加 buildConfig:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

/** 从 local.properties 读聚合数据 key（不进 git）；未配置时为空串，请求会得到 key 错误提示。 */
val juheApiKey: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    props.getProperty("JUHE_API_KEY", "")
}

android {
    namespace = "com.btg.news"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "JUHE_API_KEY", "\"$juheApiKey\"")
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
        buildConfig = true
    }
}
```

dependencies 块保持不变。

- [ ] **Step 2: local.properties 加 key(本地操作,不提交)**

在 `local.properties` 末尾加一行(由使用者自行填真实 key):

```properties
JUHE_API_KEY=在这里填聚合数据申请的key
```

- [ ] **Step 3: 验证生成**

Run: `./gradlew :module_news:assembleDebug`
Expected: BUILD SUCCESSFUL;`module_news/build/generated/source/buildConfig/debug/com/btg/news/BuildConfig.java` 含 `JUHE_API_KEY` 字段

- [ ] **Step 4: Commit**

```bash
git add module_news/build.gradle.kts
git commit -m "build: inject JUHE_API_KEY from local.properties into module_news BuildConfig"
```

---

### Task 2: 聚合响应外壳 JuheResponse + DTO 与映射(TDD)

**Files:**
- Create: `module_news/src/main/java/com/btg/news/data/source/JuheResponse.kt`
- Create: `module_news/src/main/java/com/btg/news/data/source/NewsDto.kt`
- Modify: `module_news/src/main/java/com/btg/news/data/model/NewsItem.kt`
- Create: `module_news/src/main/java/com/btg/news/data/model/NewsDetail.kt`
- Test: `module_news/src/test/java/com/btg/news/data/source/JuheResponseTest.kt`
- Test: `module_news/src/test/java/com/btg/news/data/source/NewsDtoMappingTest.kt`

**Interfaces:**
- Consumes: lib_common 的 `AppException.Business(code, message)`、`AppException.Parse(message)`
- Produces:
  - `NewsItem(uniquekey: String, title: String, source: String, date: String, category: String, imageUrl: String?, url: String)`
  - `NewsDetail(title: String, source: String, date: String, category: String, contentHtml: String, url: String)`
  - `JuheResponse<T>(errorCode: Int, reason: String?, result: T?)` + `fun <T> JuheResponse<T>.unwrap(): T`
  - `NewsListResult(data: List<NewsListItemDto>?)`、`NewsDetailResult(uniquekey, content, detail)` + `toModel()` 扩展

- [ ] **Step 1: 写失败测试 JuheResponseTest**

```kotlin
package com.btg.news.data.source

import com.btg.common.network.AppException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JuheResponseTest {

    @Test
    fun `unwrap returns result when error_code is 0`() {
        val response = JuheResponse(errorCode = 0, reason = "success", result = "data")
        assertEquals("data", response.unwrap())
    }

    @Test
    fun `unwrap throws Business when error_code is not 0`() {
        val response = JuheResponse<String>(errorCode = 10012, reason = "请求超过次数限制", result = null)
        val e = assertThrows(AppException.Business::class.java) { response.unwrap() }
        assertEquals(10012, e.code)
        assertEquals("请求超过次数限制", e.message)
    }

    @Test
    fun `unwrap throws Parse when result is null on success code`() {
        val response = JuheResponse<String>(errorCode = 0, reason = "success", result = null)
        assertThrows(AppException.Parse::class.java) { response.unwrap() }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :module_news:testDebugUnitTest --tests "com.btg.news.data.source.JuheResponseTest"`
Expected: 编译失败(JuheResponse 未定义)

- [ ] **Step 3: 实现 JuheResponse.kt**

```kotlin
package com.btg.news.data.source

import com.btg.common.network.AppException
import com.google.gson.annotations.SerializedName

/** 聚合数据统一响应外壳：{ error_code, reason, result }，error_code == 0 为成功。 */
data class JuheResponse<T>(
    @SerializedName("error_code") val errorCode: Int,
    val reason: String?,
    val result: T?,
)

/** 解包：成功返回 result；业务失败抛 Business（携带聚合错误码与 reason 文案）；成功但 result 为空抛 Parse。 */
fun <T> JuheResponse<T>.unwrap(): T = when {
    errorCode != 0 -> throw AppException.Business(errorCode, reason ?: "业务处理失败")
    result == null -> throw AppException.Parse("响应数据为空")
    else -> result
}
```

- [ ] **Step 4: 扩展模型 NewsItem 与新建 NewsDetail**

`NewsItem.kt` 整体替换:

```kotlin
package com.btg.news.data.model

data class NewsItem(
    val uniquekey: String,
    val title: String,
    val source: String,
    val date: String,
    val category: String,
    val imageUrl: String?,
    val url: String
)
```

`NewsDetail.kt`:

```kotlin
package com.btg.news.data.model

data class NewsDetail(
    val title: String,
    val source: String,
    val date: String,
    val category: String,
    /** 接口返回的正文 HTML 片段。 */
    val contentHtml: String,
    /** 原文链接，详情缺失时的降级入口。 */
    val url: String
)
```

NewsItem 扩字段会让既有代码编译不过,同步做最小修补保证全模块可编译:

1. `FakeNewsDataSource` 的 SAMPLE_NEWS 三条构造改为新签名(补 uniquekey/category),第一条如下,fake-2/fake-3 同理:

```kotlin
NewsItem(
    uniquekey = "fake-1",
    title = "示范新闻一：MVVM 架构落地",
    source = "示范来源",
    date = "2026-07-01",
    category = "top",
    imageUrl = null,
    url = "https://example.com/news/1"
)
```

2. `NewsAdapter` 的 DIFF `areItemsTheSame` 改为 `oldItem.uniquekey == newItem.uniquekey`
3. `NewsRepositoryTest` 与 `NewsListViewModelTest` 中的 `NewsItem` 构造改为新签名:`NewsItem("k1", "t", "s", "d", "top", null, "https://example.com/1")`

- [ ] **Step 5: 写失败测试 NewsDtoMappingTest**

```kotlin
package com.btg.news.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsDtoMappingTest {

    @Test
    fun `list item dto maps all fields`() {
        val dto = NewsListItemDto(
            uniquekey = "k1", title = "标题", date = "2026-07-06 10:00",
            category = "头条", authorName = "来源A",
            url = "https://example.com/1", thumbnail = "https://img/1.png"
        )
        val model = dto.toModel()
        assertEquals("k1", model.uniquekey)
        assertEquals("标题", model.title)
        assertEquals("来源A", model.source)
        assertEquals("2026-07-06 10:00", model.date)
        assertEquals("头条", model.category)
        assertEquals("https://img/1.png", model.imageUrl)
        assertEquals("https://example.com/1", model.url)
    }

    @Test
    fun `list item dto null fields fall back to empty`() {
        val dto = NewsListItemDto(null, null, null, null, null, null, null)
        val model = dto.toModel()
        assertEquals("", model.uniquekey)
        assertEquals("", model.title)
        assertNull(model.imageUrl)
    }

    @Test
    fun `detail result maps detail and content`() {
        val result = NewsDetailResult(
            uniquekey = "k1",
            content = "<p>正文</p>",
            detail = NewsDetailDto(
                title = "标题", date = "2026-07-06 10:00", category = "娱乐",
                authorName = "来源B", url = "https://example.com/1"
            )
        )
        val model = result.toModel()
        assertEquals("标题", model.title)
        assertEquals("来源B", model.source)
        assertEquals("娱乐", model.category)
        assertEquals("<p>正文</p>", model.contentHtml)
        assertEquals("https://example.com/1", model.url)
    }
}
```

- [ ] **Step 6: 实现 NewsDto.kt 后运行两个测试类**

```kotlin
package com.btg.news.data.source

import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.google.gson.annotations.SerializedName

/** 列表接口 result：{ stat, data: [...] }，无数据时 data 为 null。 */
data class NewsListResult(
    val data: List<NewsListItemDto>?,
)

data class NewsListItemDto(
    val uniquekey: String?,
    val title: String?,
    val date: String?,
    val category: String?,
    @SerializedName("author_name") val authorName: String?,
    val url: String?,
    @SerializedName("thumbnail_pic_s") val thumbnail: String?,
)

fun NewsListItemDto.toModel(): NewsItem = NewsItem(
    uniquekey = uniquekey.orEmpty(),
    title = title.orEmpty(),
    source = authorName.orEmpty(),
    date = date.orEmpty(),
    category = category.orEmpty(),
    imageUrl = thumbnail?.takeIf { it.isNotBlank() },
    url = url.orEmpty(),
)

/** 详情接口 result：{ uniquekey, content, detail: {...} }。 */
data class NewsDetailResult(
    val uniquekey: String?,
    val content: String?,
    val detail: NewsDetailDto?,
)

data class NewsDetailDto(
    val title: String?,
    val date: String?,
    val category: String?,
    @SerializedName("author_name") val authorName: String?,
    val url: String?,
)

fun NewsDetailResult.toModel(): NewsDetail = NewsDetail(
    title = detail?.title.orEmpty(),
    source = detail?.authorName.orEmpty(),
    date = detail?.date.orEmpty(),
    category = detail?.category.orEmpty(),
    contentHtml = content.orEmpty(),
    url = detail?.url.orEmpty(),
)
```

Run: `./gradlew :module_news:testDebugUnitTest`
Expected: BUILD SUCCESSFUL,6 个新测试 PASS,既有测试(已按 Step 4 修补)全部通过

- [ ] **Step 7: Commit**

```bash
git add module_news/src
git commit -m "feat: add juhe response envelope, dtos and model mapping for news"
```

---

### Task 3: 数据源与仓库升级(接口签名 + Remote 实现 + Fake 重写)

**Files:**
- Create: `module_news/src/main/java/com/btg/news/data/model/NewsCategory.kt`
- Modify: `module_news/src/main/java/com/btg/news/data/source/NewsApi.kt`(整体重写)
- Modify: `module_news/src/main/java/com/btg/news/data/source/NewsDataSource.kt`(整体重写)
- Modify: `module_news/src/main/java/com/btg/news/data/source/RemoteNewsDataSource.kt`(整体重写)
- Modify: `module_news/src/main/java/com/btg/news/data/source/FakeNewsDataSource.kt`(整体重写)
- Modify: `module_news/src/main/java/com/btg/news/data/repository/NewsRepository.kt`(整体重写)
- Test: `module_news/src/test/java/com/btg/news/data/source/RemoteNewsDataSourceTest.kt`(新建)
- Modify: `module_news/src/test/java/com/btg/news/data/source/FakeNewsDataSourceTest.kt`(整体重写)
- Modify: `module_news/src/test/java/com/btg/news/data/repository/NewsRepositoryTest.kt`(整体重写)

**Interfaces:**
- Consumes: `JuheResponse.unwrap()`、DTO `toModel()`、lib_common `safeApiCall`
- Produces:
  - `NewsCategory(val type: String, val label: String)` 枚举,10 项,首项 TOP
  - `NewsDataSource { suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem>; suspend fun fetchNewsDetail(uniquekey: String): NewsDetail }`
  - `RemoteNewsDataSource(api: NewsApi, apiKey: String)`
  - `NewsRepository { suspend fun getNews(type: String, page: Int, pageSize: Int = 30): ApiResult<List<NewsItem>>; suspend fun getNewsDetail(uniquekey: String): ApiResult<NewsDetail> }`
  - `NewsApi.getNewsList(key, type, page, pageSize, isFilter)` / `getNewsDetail(key, uniquekey)`

- [ ] **Step 1: NewsCategory.kt**

```kotlin
package com.btg.news.data.model

/** 聚合数据新闻分类。type 为接口参数值，label 为 Tab 展示文案。 */
enum class NewsCategory(val type: String, val label: String) {
    TOP("top", "推荐"),
    GUONEI("guonei", "国内"),
    GUOJI("guoji", "国际"),
    YULE("yule", "娱乐"),
    TIYU("tiyu", "体育"),
    KEJI("keji", "科技"),
    CAIJING("caijing", "财经"),
    YOUXI("youxi", "游戏"),
    QICHE("qiche", "汽车"),
    JIANKANG("jiankang", "健康"),
}
```

- [ ] **Step 2: 重写 NewsApi.kt(删除占位 NewsResponse)**

```kotlin
package com.btg.news.data.source

import retrofit2.http.GET
import retrofit2.http.Query

/** 聚合数据「新闻头条」接口。baseUrl = https://v.juhe.cn/ */
interface NewsApi {

    @GET("toutiao/index")
    suspend fun getNewsList(
        @Query("key") key: String,
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("is_filter") isFilter: Int = 1,
    ): JuheResponse<NewsListResult>

    @GET("toutiao/content")
    suspend fun getNewsDetail(
        @Query("key") key: String,
        @Query("uniquekey") uniquekey: String,
    ): JuheResponse<NewsDetailResult>
}
```

- [ ] **Step 3: 重写 NewsDataSource.kt**

```kotlin
package com.btg.news.data.source

import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem

/** 新闻数据源统一入口。实现：失败时抛异常，由 Repository 经 safeApiCall 捕获包装。 */
interface NewsDataSource {
    suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem>
    suspend fun fetchNewsDetail(uniquekey: String): NewsDetail
}
```

- [ ] **Step 4: 写失败测试 RemoteNewsDataSourceTest(fake NewsApi 验证映射与传参)**

```kotlin
package com.btg.news.data.source

import com.btg.common.network.AppException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteNewsDataSourceTest {

    private class FakeApi : NewsApi {
        var lastListParams: List<Any>? = null
        var listResponse = JuheResponse(
            errorCode = 0, reason = "success",
            result = NewsListResult(
                data = listOf(
                    NewsListItemDto("k1", "标题1", "2026-07-06", "头条", "来源", "https://e.com/1", "https://img/1"),
                )
            )
        )
        var detailResponse = JuheResponse(
            errorCode = 0, reason = "success",
            result = NewsDetailResult(
                uniquekey = "k1", content = "<p>正文</p>",
                detail = NewsDetailDto("标题1", "2026-07-06", "头条", "来源", "https://e.com/1")
            )
        )

        override suspend fun getNewsList(key: String, type: String, page: Int, pageSize: Int, isFilter: Int): JuheResponse<NewsListResult> {
            lastListParams = listOf(key, type, page, pageSize, isFilter)
            return listResponse
        }

        override suspend fun getNewsDetail(key: String, uniquekey: String): JuheResponse<NewsDetailResult> =
            detailResponse
    }

    @Test
    fun `fetchNews passes key and params and maps items`() = runTest {
        val api = FakeApi()
        val source = RemoteNewsDataSource(api, apiKey = "test-key")

        val items = source.fetchNews("yule", page = 2, pageSize = 30)

        assertEquals(listOf<Any>("test-key", "yule", 2, 30, 1), api.lastListParams)
        assertEquals(1, items.size)
        assertEquals("k1", items.first().uniquekey)
    }

    @Test
    fun `fetchNews returns empty list when data is null`() = runTest {
        val api = FakeApi().apply {
            listResponse = JuheResponse(0, "success", NewsListResult(data = null))
        }
        val source = RemoteNewsDataSource(api, "test-key")

        assertEquals(emptyList<Any>(), source.fetchNews("top", 1, 30))
    }

    @Test
    fun `fetchNewsDetail maps content and detail`() = runTest {
        val source = RemoteNewsDataSource(FakeApi(), "test-key")

        val detail = source.fetchNewsDetail("k1")

        assertEquals("标题1", detail.title)
        assertEquals("<p>正文</p>", detail.contentHtml)
    }

    @Test
    fun `fetchNewsDetail throws Parse when content is blank`() = runTest {
        val api = FakeApi().apply {
            detailResponse = JuheResponse(
                0, "success",
                NewsDetailResult("k1", content = "", detail = NewsDetailDto("t", "d", "c", "a", "u"))
            )
        }
        val source = RemoteNewsDataSource(api, "test-key")

        assertThrows(AppException.Parse::class.java) {
            kotlinx.coroutines.runBlocking { source.fetchNewsDetail("k1") }
        }
    }
}
```

- [ ] **Step 5: 运行确认失败**

Run: `./gradlew :module_news:testDebugUnitTest --tests "com.btg.news.data.source.RemoteNewsDataSourceTest"`
Expected: 编译失败(RemoteNewsDataSource 构造与方法不匹配)

- [ ] **Step 6: 重写 RemoteNewsDataSource.kt**

```kotlin
package com.btg.news.data.source

import com.btg.common.network.AppException
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem

/** 真实网络数据源：调用聚合数据接口并映射为领域模型。 */
class RemoteNewsDataSource(
    private val api: NewsApi,
    private val apiKey: String,
) : NewsDataSource {

    override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> =
        api.getNewsList(apiKey, type, page, pageSize).unwrap()
            .data.orEmpty()
            .map { it.toModel() }

    override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail {
        val detail = api.getNewsDetail(apiKey, uniquekey).unwrap().toModel()
        if (detail.contentHtml.isBlank()) throw AppException.Parse("新闻详情内容为空")
        return detail
    }
}
```

- [ ] **Step 7: 重写 FakeNewsDataSource.kt(分页假数据,第 3 页起为空模拟到底)**

```kotlin
package com.btg.news.data.source

import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import kotlinx.coroutines.delay

/** 假数据源：无 key 时演示/测试用。前 2 页有数据，之后为空模拟"没有更多"。 */
class FakeNewsDataSource : NewsDataSource {

    override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> {
        delay(400)
        if (page > 2) return emptyList()
        return (1..pageSize).map { i ->
            NewsItem(
                uniquekey = "$type-$page-$i",
                title = "[$type] 示范新闻 第${page}页 第${i}条",
                source = "示范来源",
                date = "2026-07-06 10:00",
                category = type,
                imageUrl = null,
                url = "https://example.com/$type/$page/$i"
            )
        }
    }

    override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail {
        delay(200)
        return NewsDetail(
            title = "示范新闻 $uniquekey",
            source = "示范来源",
            date = "2026-07-06 10:00",
            category = "top",
            contentHtml = "<p>这是 $uniquekey 的示范正文。</p>",
            url = ""
        )
    }
}
```

- [ ] **Step 8: 重写 NewsRepository.kt(safeApiCall 接管异常映射)**

```kotlin
package com.btg.news.data.repository

import com.btg.common.network.safeApiCall
import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.source.NewsDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository(
    private val dataSource: NewsDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getNews(type: String, page: Int, pageSize: Int = 30): ApiResult<List<NewsItem>> =
        withContext(ioDispatcher) {
            safeApiCall { dataSource.fetchNews(type, page, pageSize) }
        }

    suspend fun getNewsDetail(uniquekey: String): ApiResult<NewsDetail> =
        withContext(ioDispatcher) {
            safeApiCall { dataSource.fetchNewsDetail(uniquekey) }
        }
}
```

- [ ] **Step 9: 重写两个旧测试**

`FakeNewsDataSourceTest.kt`:

```kotlin
package com.btg.news.data.source

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNewsDataSourceTest {

    private val source = FakeNewsDataSource()

    @Test
    fun `fetchNews returns pageSize items for first page`() = runTest {
        val items = source.fetchNews("top", page = 1, pageSize = 30)
        assertEquals(30, items.size)
        assertTrue(items.all { it.category == "top" })
    }

    @Test
    fun `fetchNews returns empty beyond page 2`() = runTest {
        assertTrue(source.fetchNews("top", page = 3, pageSize = 30).isEmpty())
    }

    @Test
    fun `fetchNewsDetail returns detail with content`() = runTest {
        val detail = source.fetchNewsDetail("k1")
        assertTrue(detail.contentHtml.isNotBlank())
    }
}
```

`NewsRepositoryTest.kt`:

```kotlin
package com.btg.news.data.repository

import com.btg.common.network.AppException
import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.source.NewsDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRepositoryTest {

    private val sampleItem = NewsItem("k1", "t", "s", "d", "top", null, "https://e.com/1")
    private val sampleDetail = NewsDetail("t", "s", "d", "top", "<p>x</p>", "https://e.com/1")

    private class ThrowingSource(private val throwable: Throwable) : NewsDataSource {
        override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> = throw throwable
        override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail = throw throwable
    }

    private inner class SuccessSource : NewsDataSource {
        override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> = listOf(sampleItem)
        override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail = sampleDetail
    }

    @Test
    fun `getNews wraps success`() = runTest {
        val repo = NewsRepository(SuccessSource(), StandardTestDispatcher(testScheduler))
        val result = repo.getNews("top", 1)
        assertEquals(ApiResult.Success(listOf(sampleItem)), result)
    }

    @Test
    fun `getNews maps exception to AppException`() = runTest {
        val repo = NewsRepository(ThrowingSource(RuntimeException("boom")), StandardTestDispatcher(testScheduler))
        val result = repo.getNews("top", 1)
        assertTrue(result is ApiResult.Error && (result as ApiResult.Error).throwable is AppException)
    }

    @Test
    fun `getNewsDetail wraps success`() = runTest {
        val repo = NewsRepository(SuccessSource(), StandardTestDispatcher(testScheduler))
        assertEquals(ApiResult.Success(sampleDetail), repo.getNewsDetail("k1"))
    }
}
```

注:此步后 `NewsListViewModel`/`NewsAdapter`/`NewsListViewModelTest` 仍引用旧签名而编译不过——Task 5/6 重写它们。为保持本任务可验证,Step 10 先做**最小适配**。

- [ ] **Step 10: 最小适配让全模块编译通过**

`NewsListViewModel.loadNews()` 中 `repository.getNews()` 临时改为 `repository.getNews(NewsCategory.TOP.type, 1)`(加 import `com.btg.news.data.model.NewsCategory`;Adapter DIFF 已在 Task 2 改完);`NewsListViewModelTest` 中 fake 数据源改为新接口签名:

```kotlin
private val sample = listOf(
    NewsItem("k1", "t", "s", "d", "top", null, "https://example.com/1")
)

private fun repoReturning(result: ApiResult<List<NewsItem>>): NewsRepository {
    val dataSource = object : NewsDataSource {
        override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> = when (result) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> throw result.throwable
        }
        override suspend fun fetchNewsDetail(uniquekey: String) = throw UnsupportedOperationException()
    }
    return NewsRepository(dataSource, mainDispatcherRule.testDispatcher)
}
```

同时该测试类中 `loadNews error` 用例的期望文案改为断言非空(safeApiCall 会把 RuntimeException 映射为 AppException.Unknown,message 仍是 "boom"):保持 `assertEquals("boom", state.errorMessage)` 即可(ExceptionHandler 对 Unknown 保留原 message)。

- [ ] **Step 11: 运行全模块测试**

Run: `./gradlew :module_news:testDebugUnitTest`
Expected: BUILD SUCCESSFUL,全部通过

- [ ] **Step 12: Commit**

```bash
git add module_news/src
git commit -m "feat: real juhe news api datasource with paging-capable repository"
```

---

### Task 4: DI 装配——独立 Retrofit(@JuheRetrofit)+ 切换 Remote

**Files:**
- Modify: `module_news/src/main/java/com/btg/news/di/NewsModule.kt`(整体重写)

**Interfaces:**
- Consumes: lib_common `NetworkModule` 提供的 `OkHttpClient`、`Gson`;`BuildConfig.JUHE_API_KEY`
- Produces: Hilt 单例 `NewsApi`、`NewsDataSource`(Remote)、`NewsRepository`;`@JuheRetrofit` 限定符

- [ ] **Step 1: 重写 NewsModule.kt**

```kotlin
package com.btg.news.di

import com.btg.news.BuildConfig
import com.btg.news.data.repository.NewsRepository
import com.btg.news.data.source.NewsApi
import com.btg.news.data.source.NewsDataSource
import com.btg.news.data.source.RemoteNewsDataSource
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 聚合数据专用 Retrofit 限定符（baseUrl 与框架默认不同，演示多 baseUrl 模式）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JuheRetrofit

/**
 * 新闻数据装配。数据源唯一装配点：无 key 调试时把 RemoteNewsDataSource
 * 换回 FakeNewsDataSource()，上层不动。
 */
@Module
@InstallIn(SingletonComponent::class)
object NewsModule {

    private const val JUHE_BASE_URL = "https://v.juhe.cn/"

    @Provides
    @Singleton
    @JuheRetrofit
    fun provideJuheRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(JUHE_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideNewsApi(@JuheRetrofit retrofit: Retrofit): NewsApi =
        retrofit.create(NewsApi::class.java)

    @Provides
    @Singleton
    fun provideNewsDataSource(api: NewsApi): NewsDataSource =
        RemoteNewsDataSource(api, BuildConfig.JUHE_API_KEY)

    @Provides
    @Singleton
    fun provideNewsRepository(dataSource: NewsDataSource): NewsRepository =
        NewsRepository(dataSource)
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add module_news/src/main/java/com/btg/news/di/NewsModule.kt
git commit -m "feat: wire juhe retrofit qualifier and switch news to remote datasource"
```

---

### Task 5: 列表 ViewModel——分类切换 / 刷新 / 分页(TDD)

**Files:**
- Modify: `module_news/src/main/java/com/btg/news/ui/list/NewsListUiState.kt`(整体重写)
- Modify: `module_news/src/main/java/com/btg/news/ui/list/NewsListViewModel.kt`(整体重写)
- Delete: `module_news/src/main/java/com/btg/news/ui/list/NewsEvent.kt`(点击改为 Fragment 直接导航)
- Modify: `module_news/src/test/java/com/btg/news/ui/list/NewsListViewModelTest.kt`(整体重写)

**Interfaces:**
- Consumes: `NewsRepository.getNews(type, page)`、`NewsCategory`、`BaseViewModel.postError/errorEvent`
- Produces:
  - `NewsListUiState(category: NewsCategory = TOP, isRefreshing: Boolean = false, isLoadingMore: Boolean = false, items: List<NewsItem> = emptyList(), errorMessage: String? = null, noMoreData: Boolean = false)`
  - `NewsListViewModel { uiState: StateFlow<NewsListUiState>; fun selectCategory(NewsCategory); fun refresh(); fun loadMore() }`(errorEvent 继承自 BaseViewModel,供加载更多失败弹 toast)

- [ ] **Step 1: 重写失败测试 NewsListViewModelTest**

```kotlin
package com.btg.news.ui.list

import com.btg.news.data.model.NewsCategory
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.repository.NewsRepository
import com.btg.news.data.source.NewsDataSource
import com.btg.news.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 可控分页 fake：每页 pageSize 条，maxPage 之后返回空；failOnFetch 时抛异常。 */
    private class PagingSource(
        private val maxPage: Int = 2,
        var failOnFetch: Boolean = false,
    ) : NewsDataSource {
        override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> {
            if (failOnFetch) throw RuntimeException("boom")
            if (page > maxPage) return emptyList()
            return (1..pageSize).map { i ->
                NewsItem("$type-$page-$i", "t$i", "s", "d", type, null, "https://e.com/$page/$i")
            }
        }
        override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail =
            throw UnsupportedOperationException()
    }

    private fun viewModel(source: PagingSource = PagingSource()) =
        NewsListViewModel(NewsRepository(source, mainDispatcherRule.testDispatcher))

    @Test
    fun `init loads first page of TOP`() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(NewsCategory.TOP, state.category)
        assertEquals(30, state.items.size)
    }

    @Test
    fun `loadMore appends next page`() = runTest {
        val vm = viewModel()
        vm.loadMore()
        assertEquals(60, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.noMoreData)
    }

    @Test
    fun `loadMore beyond last page sets noMoreData`() = runTest {
        val vm = viewModel()
        vm.loadMore() // page 2
        vm.loadMore() // page 3 -> empty
        val state = vm.uiState.value
        assertEquals(60, state.items.size)
        assertTrue(state.noMoreData)
    }

    @Test
    fun `selectCategory resets list and reloads`() = runTest {
        val vm = viewModel()
        vm.loadMore()
        vm.selectCategory(NewsCategory.YULE)
        val state = vm.uiState.value
        assertEquals(NewsCategory.YULE, state.category)
        assertEquals(30, state.items.size)
        assertTrue(state.items.all { it.category == "yule" })
    }

    @Test
    fun `selectCategory with same category is no-op`() = runTest {
        val vm = viewModel()
        vm.loadMore()
        vm.selectCategory(NewsCategory.TOP)
        assertEquals(60, vm.uiState.value.items.size)
    }

    @Test
    fun `refresh error sets errorMessage`() = runTest {
        val source = PagingSource(failOnFetch = true)
        val vm = viewModel(source)
        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertTrue(state.items.isEmpty())
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `refresh after error recovers`() = runTest {
        val source = PagingSource(failOnFetch = true)
        val vm = viewModel(source)
        source.failOnFetch = false
        vm.refresh()
        assertEquals(30, vm.uiState.value.items.size)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :module_news:testDebugUnitTest --tests "com.btg.news.ui.list.NewsListViewModelTest"`
Expected: 编译失败(方法不存在)

- [ ] **Step 3: 重写 NewsListUiState.kt**

```kotlin
package com.btg.news.ui.list

import com.btg.news.data.model.NewsCategory
import com.btg.news.data.model.NewsItem

data class NewsListUiState(
    val category: NewsCategory = NewsCategory.TOP,
    /** 首次加载 / 下拉刷新 / 切分类中。 */
    val isRefreshing: Boolean = false,
    /** 上拉加载下一页中。 */
    val isLoadingMore: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null,
    val noMoreData: Boolean = false,
)
```

- [ ] **Step 4: 重写 NewsListViewModel.kt,删除 NewsEvent.kt**

```kotlin
package com.btg.news.ui.list

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.news.data.model.NewsCategory
import com.btg.news.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NewsListViewModel @Inject constructor(
    private val repository: NewsRepository,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(NewsListUiState())
    val uiState: StateFlow<NewsListUiState> = _uiState.asStateFlow()

    /** 当前已加载到的页码（1 起）。 */
    private var page = 1

    init {
        refresh()
    }

    fun selectCategory(category: NewsCategory) {
        if (category == _uiState.value.category) return
        _uiState.update {
            it.copy(category = category, items = emptyList(), noMoreData = false, errorMessage = null)
        }
        refresh()
    }

    fun refresh() {
        val category = _uiState.value.category
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.getNews(category.type, 1)
            // 防串台：结果返回时若分类已切换，丢弃过期结果
            if (_uiState.value.category != category) return@launch
            when (result) {
                is ApiResult.Success -> {
                    page = 1
                    _uiState.update {
                        it.copy(isRefreshing = false, items = result.data, noMoreData = result.data.isEmpty())
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isRefreshing = false, errorMessage = result.throwable.message ?: "加载失败")
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoadingMore || state.noMoreData || page >= MAX_PAGE) return
        val category = state.category
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result = repository.getNews(category.type, page + 1)
            if (_uiState.value.category != category) return@launch
            when (result) {
                is ApiResult.Success -> {
                    page += 1
                    _uiState.update {
                        it.copy(isLoadingMore = false, items = it.items + result.data, noMoreData = result.data.isEmpty())
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    postError(result.throwable.message ?: "加载失败")
                }
            }
        }
    }

    private companion object {
        /** 聚合接口 page 上限。 */
        const val MAX_PAGE = 50
    }
}
```

注:删除 `NewsEvent.kt` 后 `NewsListFragment` 编译不过,先做最小适配——删掉 `handleEvent`/events 收集与 `viewModel.onNewsClick` 调用(Adapter 点击回调临时置空 `NewsAdapter { }`),render 里 `state.isLoading` 改 `state.isRefreshing`;Task 6 整体重写该 Fragment。

- [ ] **Step 5: 运行测试**

Run: `./gradlew :module_news:testDebugUnitTest`
Expected: 全部 PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: news list viewmodel with category switch, refresh and paging"
```

---

### Task 6: 列表 UI——TabLayout + 上拉加载 + 点击跳详情

**Files:**
- Modify: `module_news/src/main/res/layout/fragment_news_list.xml`(整体重写)
- Modify: `module_news/src/main/java/com/btg/news/ui/list/NewsListFragment.kt`(整体重写)
- Modify: `module_news/src/main/java/com/btg/news/ui/list/NewsAdapter.kt`(bind 加分类展示)
- Create: `module_news/src/main/java/com/btg/news/ui/detail/NewsDetailArgs.kt`
- Modify: `module_news/src/main/res/navigation/nav_news.xml`(加 action 与详情占位声明放 Task 7;本任务只加 action 所需内容,见 Step 4)

**Interfaces:**
- Consumes: `NewsListViewModel`、`RecyclerView.onLoadMore`(lib_common)、`Context.toast`
- Produces: `NewsDetailArgs { const UNIQUEKEY/TITLE/URL; fun of(item: NewsItem): Bundle }`;nav action `action_newsList_to_newsDetail`

- [ ] **Step 1: 重写 fragment_news_list.xml(顶部 TabLayout)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:tabMode="scrollable" />

    <com.btg.widget.StateLayout
        android:id="@+id/stateLayout"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

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

</LinearLayout>
```

- [ ] **Step 2: NewsDetailArgs.kt(参数常量与 Bundle 构造,列表/详情共用)**

```kotlin
package com.btg.news.ui.detail

import android.os.Bundle
import androidx.core.os.bundleOf
import com.btg.news.data.model.NewsItem

/** 详情页导航参数。ViewModel 通过 SavedStateHandle 用同名 key 读取。 */
object NewsDetailArgs {
    const val UNIQUEKEY = "uniquekey"
    const val TITLE = "title"
    const val URL = "url"

    fun of(item: NewsItem): Bundle = bundleOf(
        UNIQUEKEY to item.uniquekey,
        TITLE to item.title,
        URL to item.url,
    )
}
```

- [ ] **Step 3: 重写 NewsListFragment.kt**

```kotlin
package com.btg.news.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ui.onLoadMore
import com.btg.common.ui.onRefresh
import com.btg.common.ui.toast
import com.btg.news.R
import com.btg.news.data.model.NewsCategory
import com.btg.news.databinding.FragmentNewsListBinding
import com.btg.news.ui.detail.NewsDetailArgs
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

/** 新闻列表：分类 Tab + 下拉刷新 + 上拉分页 + StateLayout 四态。 */
@AndroidEntryPoint
class NewsListFragment : BaseFragment<FragmentNewsListBinding>() {

    private val viewModel: NewsListViewModel by viewModels()
    private val newsAdapter = NewsAdapter { item ->
        findNavController().navigate(R.id.action_newsList_to_newsDetail, NewsDetailArgs.of(item))
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsListBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupList()

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }

    private fun setupTabs() {
        NewsCategory.entries.forEach { category ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(category.label))
        }
        binding.tabLayout.getTabAt(viewModel.uiState.value.category.ordinal)?.select()
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectCategory(NewsCategory.entries[tab.position])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupList() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = newsAdapter
        binding.stateLayout.setOnRetryListener { viewModel.refresh() }
        binding.swipeRefresh.onRefresh { viewModel.refresh() }
        binding.recyclerView.onLoadMore(
            canLoadMore = {
                val s = viewModel.uiState.value
                !s.isRefreshing && !s.isLoadingMore && !s.noMoreData
            },
            onLoadMore = { viewModel.loadMore() },
        )
    }

    private fun render(state: NewsListUiState) {
        binding.swipeRefresh.isRefreshing = state.isRefreshing && state.items.isNotEmpty()
        when {
            state.isRefreshing && state.items.isEmpty() -> binding.stateLayout.showLoading()
            state.errorMessage != null && state.items.isEmpty() ->
                binding.stateLayout.showError(state.errorMessage)
            state.items.isEmpty() && !state.isRefreshing -> binding.stateLayout.showEmpty()
            else -> binding.stateLayout.showContent()
        }
        newsAdapter.submitList(state.items)
    }
}
```

- [ ] **Step 4: nav_news.xml 加 action(详情 destination 本步一并声明,Fragment 类 Task 7 创建——先建空壳类保证编译,见 Step 5)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_news"
    app:startDestination="@id/newsListFragment">

    <fragment
        android:id="@+id/newsListFragment"
        android:name="com.btg.news.ui.list.NewsListFragment"
        android:label="@string/news_title">
        <action
            android:id="@+id/action_newsList_to_newsDetail"
            app:destination="@id/newsDetailFragment" />
    </fragment>

    <fragment
        android:id="@+id/newsDetailFragment"
        android:name="com.btg.news.ui.detail.NewsDetailFragment"
        android:label="@string/news_detail_title" />

</navigation>
```

`module_news/src/main/res/values/strings.xml` 加:

```xml
<string name="news_detail_title">新闻详情</string>
```

- [ ] **Step 5: NewsAdapter 更新 + 详情 Fragment 空壳**

`NewsAdapter.kt` 的 `bind` 中来源展示改为「分类 · 来源」:

```kotlin
binding.sourceText.text = if (item.category.isNotBlank()) "${item.category} · ${item.source}" else item.source
```

新建空壳 `module_news/src/main/java/com/btg/news/ui/detail/NewsDetailFragment.kt`(Task 7 重写):

```kotlin
package com.btg.news.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.news.databinding.FragmentNewsDetailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewsDetailFragment : BaseFragment<FragmentNewsDetailBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsDetailBinding.inflate(inflater, container, false)
}
```

空壳布局 `module_news/src/main/res/layout/fragment_news_detail.xml`(Task 7 重写):

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

- [ ] **Step 6: 编译与全测**

Run: `./gradlew assembleDebug :module_news:testDebugUnitTest`
Expected: BUILD SUCCESSFUL,测试全过

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: news list ui with category tabs, load-more and detail navigation"
```

---

### Task 7: 详情页——ViewModel(TDD)+ WebView 渲染与降级

**Files:**
- Create: `module_news/src/main/java/com/btg/news/ui/detail/NewsDetailViewModel.kt`
- Modify: `module_news/src/main/java/com/btg/news/ui/detail/NewsDetailFragment.kt`(整体重写)
- Modify: `module_news/src/main/res/layout/fragment_news_detail.xml`(整体重写)
- Test: `module_news/src/test/java/com/btg/news/ui/detail/NewsDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `NewsRepository.getNewsDetail(uniquekey)`、`BaseViewModel.launchWithState`、`UiState<NewsDetail>`、`NewsDetailArgs`
- Produces: `NewsDetailViewModel(savedStateHandle, repository) { detailState: StateFlow<UiState<NewsDetail>>; fun load() }`

- [ ] **Step 1: 写失败测试 NewsDetailViewModelTest**

```kotlin
package com.btg.news.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.btg.common.result.UiState
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.model.NewsItem
import com.btg.news.data.repository.NewsRepository
import com.btg.news.data.source.NewsDataSource
import com.btg.news.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val detail = NewsDetail("标题", "来源", "2026-07-06", "top", "<p>正文</p>", "https://e.com/1")

    private fun repo(fail: Boolean): NewsRepository {
        val source = object : NewsDataSource {
            override suspend fun fetchNews(type: String, page: Int, pageSize: Int): List<NewsItem> =
                throw UnsupportedOperationException()
            override suspend fun fetchNewsDetail(uniquekey: String): NewsDetail =
                if (fail) throw RuntimeException("boom") else detail
        }
        return NewsRepository(source, mainDispatcherRule.testDispatcher)
    }

    private fun handle() = SavedStateHandle(mapOf(NewsDetailArgs.UNIQUEKEY to "k1"))

    @Test
    fun `init loads detail into Success state`() = runTest {
        val vm = NewsDetailViewModel(handle(), repo(fail = false))
        assertEquals(UiState.Success(detail), vm.detailState.value)
    }

    @Test
    fun `load failure lands in Error state`() = runTest {
        val vm = NewsDetailViewModel(handle(), repo(fail = true))
        assertTrue(vm.detailState.value is UiState.Error)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :module_news:testDebugUnitTest --tests "com.btg.news.ui.detail.NewsDetailViewModelTest"`
Expected: 编译失败(NewsDetailViewModel 未定义)

- [ ] **Step 3: 实现 NewsDetailViewModel.kt**

```kotlin
package com.btg.news.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.btg.common.base.BaseViewModel
import com.btg.common.result.UiState
import com.btg.news.data.model.NewsDetail
import com.btg.news.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NewsRepository,
) : BaseViewModel() {

    private val uniquekey: String = savedStateHandle[NewsDetailArgs.UNIQUEKEY] ?: ""

    private val _detailState = MutableStateFlow<UiState<NewsDetail>>(UiState.Loading)
    val detailState: StateFlow<UiState<NewsDetail>> = _detailState.asStateFlow()

    init {
        load()
    }

    fun load() = launchWithState(_detailState) { repository.getNewsDetail(uniquekey) }
}
```

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :module_news:testDebugUnitTest --tests "com.btg.news.ui.detail.NewsDetailViewModelTest"`
Expected: 2 个测试 PASS

- [ ] **Step 5: 重写 fragment_news_detail.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.btg.widget.StateLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/stateLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <TextView
            android:id="@+id/titleText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingHorizontal="16dp"
            android:paddingTop="16dp"
            android:textSize="20sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/metaText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingHorizontal="16dp"
            android:paddingTop="8dp"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/dateText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingHorizontal="16dp"
            android:paddingTop="2dp"
            android:paddingBottom="12dp"
            android:textSize="12sp" />

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#EEEEEE" />

        <WebView
            android:id="@+id/webView"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

    </LinearLayout>

</com.btg.widget.StateLayout>
```

- [ ] **Step 6: 重写 NewsDetailFragment.kt(渲染 + 降级 + WebView 生命周期)**

```kotlin
package com.btg.news.ui.detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.fragment.app.viewModels
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.result.UiState
import com.btg.news.data.model.NewsDetail
import com.btg.news.databinding.FragmentNewsDetailBinding
import dagger.hilt.android.AndroidEntryPoint

/** 新闻详情：原生头部 + WebView 渲染 content HTML；详情缺失时降级加载原文 url。 */
@AndroidEntryPoint
class NewsDetailFragment : BaseFragment<FragmentNewsDetailBinding>() {

    private val viewModel: NewsDetailViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsDetailBinding.inflate(inflater, container, false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 降级加载原文网页需要 JS；只加载可信新闻源链接
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = WebViewClient()

        viewModel.detailState.collectOnStarted(viewLifecycleOwner) { render(it) }
    }

    private fun render(state: UiState<NewsDetail>) {
        when (state) {
            is UiState.Loading -> binding.stateLayout.showLoading()
            is UiState.Success -> showDetail(state.data)
            is UiState.Error, is UiState.Empty -> fallbackToUrl()
        }
    }

    private fun showDetail(detail: NewsDetail) {
        binding.stateLayout.showContent()
        binding.titleText.text = detail.title
        binding.metaText.text = listOf(detail.category, detail.source)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        binding.dateText.text = detail.date
        // content 中图片多为 // 协议相对地址，baseUrl 用 https: 使其可加载
        binding.webView.loadDataWithBaseURL("https:", wrapHtml(detail.contentHtml), "text/html", "utf-8", null)
    }

    /** 详情接口失败（如 223502 查不到详情）时，降级用 WebView 直接加载原文。 */
    private fun fallbackToUrl() {
        val url = arguments?.getString(NewsDetailArgs.URL).orEmpty()
        if (url.isBlank()) {
            binding.stateLayout.showError("详情加载失败")
            binding.stateLayout.setOnRetryListener { viewModel.load() }
            return
        }
        binding.stateLayout.showContent()
        binding.titleText.text = arguments?.getString(NewsDetailArgs.TITLE).orEmpty()
        binding.metaText.text = ""
        binding.dateText.text = ""
        binding.webView.loadUrl(url)
    }

    private fun wrapHtml(content: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { margin: 12px; font-size: 16px; line-height: 1.6; word-wrap: break-word; }
                img { max-width: 100%; height: auto; }
            </style>
        </head>
        <body>$content</body>
        </html>
    """.trimIndent()

    override fun onDestroyView() {
        // WebView 需在 binding 释放前手动销毁，避免泄漏
        binding.webView.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroyView()
    }
}
```

- [ ] **Step 7: 全量验证**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL,全部测试通过

- [ ] **Step 8: 手动验证(装机,需已配置真实 key)**

Run: `./gradlew installDebug`
检查:列表真实数据展示;切分类刷新;下拉刷新;滚到底自动加载第 2 页;点击进详情(原生标题 + 图文正文);无 key 或频次超限时错误态文案可见且可重试。

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: news detail page with webview content rendering and url fallback"
```
