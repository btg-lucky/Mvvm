# MVVM 架构补齐 · 设计文档

- 日期：2026-07-01
- 状态：已确认（待转实现计划）
- 范围：把 `Mvvm` 工程从"纯 Java 网络脚手架"补齐为一套可复用的 Kotlin MVVM 架构，用一个"新闻列表"示范页跑通端到端。

## 1. 背景与目标

项目名为 Mvvm，但当前没有任何 MVVM 分层（无 ViewModel / Repository / 单向数据流），实质是网络层脚手架 + 空的 `MainActivity`，全 Java、无测试。

本次目标：搭一套**可复用的 Kotlin MVVM 骨架**，并用"新闻列表"示范页把 `View → ViewModel → Repository → DataSource` 端到端跑通。数据源用**可切换的 Fake 实现**，不被外部 API key 阻塞，同时示范"数据源唯一入口且可替换"这一 MVVM 关键点。

按用户要求：**当作新建项目、放开手脚、用较新的库与工具链**。

## 2. 关键决策记录

| 决策点 | 选择 | 理由 |
|---|---|---|
| 产出形态 | 可复用骨架 + 一个示范页跑通端到端 | 光有骨架不验证等于没验证；做完整功能又会撑大范围 |
| 语言 | 新代码全 Kotlin | 全局规范；现有 Java 网络类保留、互操作 |
| 异步范式 | 协程 + Flow，移除 RxJava2 | 避免长期维护两套异步范式 |
| 网络 | 升 Retrofit，`suspend` 函数 + 纯协程 | Retrofit 2.4 不支持 suspend |
| 示范数据 | 可切换 Fake 数据源 | 不被 API key 阻塞；顺带示范数据源可替换、好测试 |
| 依赖注入 | 手动 DI（`ViewModelProvider.Factory`） | 单页示范引 DI 框架属过度工程；手动即符合可单测要求 |
| 依赖/版本管理 | Gradle Version Catalog (`libs.versions.toml`) | 现代新项目默认，类型安全 |
| 构建脚本 | Kotlin DSL (`.gradle.kts`) | 与项目语言一致、类型安全 |
| minSdk | 24（原 19） | 现代下限，可用更多新 API，减少兼容分支 |

## 3. 架构总览

### 单向数据流

```
View (Activity)  ──事件──▶  ViewModel  ──调用──▶  Repository  ──▶  DataSource(Fake/Remote)
     ▲                          │
     └──── StateFlow<UiState> ──┘   （状态反向回流，View 只渲染）
```

### 分层与模块归属

| 层 | 模块 | 内容 |
|---|---|---|
| MVVM 基类 | `lib_common/base` | `BaseActivity<VB : ViewBinding>`（替换现有空 stub）、`open class BaseViewModel`（极薄，仅分层锚点）；预留 `BaseFragment` |
| 通用结果封装 | `lib_common` | `ApiResult<out T>` sealed |
| 网络设施 | `lib_common/http` | `ApiRetrofit` 改造为协程 + suspend（去 RxJava） |
| 示范业务 | `app` | `MainActivity`、`NewsViewModel`、`NewsViewModelFactory`、`NewsRepository`、`NewsDataSource`(接口) + `FakeNewsDataSource`、领域模型 `NewsItem`、UI 状态 `NewsUiState`、事件 `NewsEvent`、`NewsAdapter` |

UI 承载：示范页用 `MainActivity` 直接承载（`RecyclerView` + `ListAdapter`），不引 Fragment；`BaseFragment` 只留基类不实例化。

## 4. 数据层

### 领域模型（`app`）

```kotlin
data class NewsItem(
    val title: String,
    val source: String,
    val date: String,
    val imageUrl: String?,   // 可空，用空安全渲染
    val url: String
)
```

### 通用结果封装（`lib_common`，全项目复用）

```kotlin
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val throwable: Throwable) : ApiResult<Nothing>
}
```

用 `sealed` 表达"成功/失败"有限状态，ViewModel 用 `when` 穷尽分支（不写 `else`，让编译器检查）。

### 数据源接口 + Fake 实现（`app`）

```kotlin
interface NewsDataSource {
    suspend fun fetchNews(): List<NewsItem>   // 失败抛异常
}

class FakeNewsDataSource : NewsDataSource {
    override suspend fun fetchNews(): List<NewsItem> {
        delay(600)                 // 模拟网络耗时
        return listOf(/* 一批硬编码假新闻 */)
    }
}
```

### Repository（`app`，数据唯一入口）

```kotlin
class NewsRepository(
    private val dataSource: NewsDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getNews(): ApiResult<List<NewsItem>> = withContext(ioDispatcher) {
        runCatching { dataSource.fetchNews() }
            .fold({ ApiResult.Success(it) }, { ApiResult.Error(it) })
    }
}
```

IO 切换在 Repository 完成（挂起函数可取消）；`ioDispatcher` 可注入，单测传测试调度器。

### 网络层改造（配合协程；示范走 Fake、不被 key 阻塞）

- `ApiRetrofit`：移除 `RxJava2CallAdapterFactory`（suspend 无需 call adapter），升 Retrofit 到较新稳定版（2.11.x）。
- `ApiService`：加一个 `suspend fun` 示例方法签名，展示真实接口形态。
- `RemoteNewsDataSource`（调 `ApiService` 的真实实现）本次**只留骨架/TODO**，不接入；将来有 key 时补，手动 DI 换一行即可。

## 5. ViewModel 层

### UI 状态（`data class` 快照，单一 `StateFlow` 暴露）

```kotlin
data class NewsUiState(
    val isLoading: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null
)
```

选 `data class` 而非 sealed：列表页需要表达"已有数据 + 正在刷新"这种组合态。

### 一次性事件（`Channel` + `receiveAsFlow()`，不重放）

```kotlin
sealed interface NewsEvent {
    data class OpenLink(val url: String) : NewsEvent
}
```

### ViewModel（不持有 View/Context，可单测）

```kotlin
class NewsViewModel(private val repo: NewsRepository) : BaseViewModel() {
    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _events = Channel<NewsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { loadNews() }

    fun loadNews() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = repo.getNews()) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, items = r.data) }
                is ApiResult.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.throwable.message) }
            }
        }
    }

    fun onNewsClick(item: NewsItem) {
        viewModelScope.launch { _events.send(NewsEvent.OpenLink(item.url)) }
    }
}
```

### 手动 Factory

```kotlin
class NewsViewModelFactory(private val repo: NewsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NewsViewModel(repo) as T
}
```

### BaseViewModel（`lib_common`）

保持极薄，本次仅作分层锚点：`open class BaseViewModel : ViewModel()`。出现真实重复再上提（YAGNI）。

## 6. View 层

### BaseActivity（`lib_common`，泛型承载 ViewBinding，替换现有空 stub）

```kotlin
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

Activity 的 binding 随 Activity 销毁即可，无需 Fragment 式 `onDestroyView` 释放；`BaseFragment` 版留到将来做时再处理释放。

### MainActivity（只渲染 + 转发事件）

```kotlin
class MainActivity : BaseActivity<ActivityMainBinding>() {
    private val viewModel: NewsViewModel by viewModels {
        NewsViewModelFactory(NewsRepository(FakeNewsDataSource()))   // 手动 DI 唯一装配点
    }
    private val newsAdapter = NewsAdapter { viewModel.onNewsClick(it) }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun render(state: NewsUiState) { /* 进度条 / 错误提示 / submitList */ }
    private fun handleEvent(e: NewsEvent) { /* OpenLink → Intent.ACTION_VIEW */ }
}
```

### 列表（`RecyclerView` + `ListAdapter` + `DiffUtil`，不用 `notifyDataSetChanged`）

```kotlin
class NewsAdapter(private val onClick: (NewsItem) -> Unit) :
    ListAdapter<NewsItem, NewsAdapter.VH>(DIFF) { /* item ViewBinding + DiffUtil.ItemCallback */ }
```

其余按规范：字符串/尺寸/颜色进 resources；`by viewModels` 用 `activity-ktx`。

## 7. 基础设施改造（当作新项目，全面现代化）

### 改动清单

1. 所有构建脚本迁移到 **Kotlin DSL**（`build.gradle` → `build.gradle.kts`，`settings.gradle` → `settings.gradle.kts`）。
2. 引入 **Gradle Version Catalog**（`gradle/libs.versions.toml`），删除 `config.gradle`；`settings.gradle.kts` 配 `dependencyResolutionManagement`（`google()` / `mavenCentral()`，替换废弃的 `jcenter()`）。
3. 工具链：**JDK 17**、**AGP 8.x**、**Gradle 8.x**、**Kotlin 2.0.x（K2）**。
4. `compileSdk` & `targetSdk` = **35**，`minSdk` = **24**。
5. 各模块启用 ViewBinding：`android { buildFeatures { viewBinding = true } }`。
6. 状态收集用 `repeatOnLifecycle`（lifecycle 2.8.x 支持）。
7. 移除 RxJava2 相关依赖（`adapter-rxjava2`、`rxandroid`）。

### 版本方向（较新稳定版；实现时按当下最新稳定版核对，以编译通过为准）

| 用途 | 库 | 版本方向 |
|---|---|---|
| 构建 | AGP / Gradle | 8.x / 8.x |
| 语言 | Kotlin | 2.0.x |
| 协程 | kotlinx-coroutines-android / -test | 1.8+ |
| 生命周期 | lifecycle-viewmodel-ktx / lifecycle-runtime-ktx | 2.8.x |
| Activity | androidx.activity:activity-ktx | 1.9.x |
| 网络 | retrofit / converter-gson | 2.11.x |
| 网络 | okhttp | 4.12.x |
| UI | appcompat / recyclerview / constraintlayout | 较新稳定 |
| 测试 | junit4 / kotlinx-coroutines-test | 较新稳定 |

> 版本号为方向性建议，不硬记某个精确号；实现阶段核对当下最新稳定版并以编译通过为准。

### 执行策略：分两阶段，各自验证

1. **基础设施阶段**：只做构建迁移（Kotlin DSL + Version Catalog + 工具链/SDK 升级 + 依赖增删 + 启用 ViewBinding），目标是**空壳编译通过**（`./gradlew assembleDebug`）。先确认地基稳。
2. **MVVM 代码阶段**：地基通过后，再铺第 4–6 节的 Kotlin 代码 + 第 8 节测试。

一旦版本组合出问题，在阶段 1 即暴露，不与业务代码搅在一起。

## 8. 测试策略

JVM 单元测试，覆盖两层：

**NewsRepository 测试**（注入 fake `NewsDataSource` + 测试调度器）：
- 数据源正常 → `ApiResult.Success` 且数据透传
- 数据源抛异常 → `ApiResult.Error` 且异常被捕获包装

**NewsViewModel 测试**（注入 fake `NewsRepository`）：
- `loadNews()` 先置 `isLoading = true`
- 成功 → `isLoading = false` + `items` 填充
- 失败 → `isLoading = false` + `errorMessage` 有值
- `onNewsClick` → `events` 发出 `OpenLink`

**测试设施**：
- `kotlinx-coroutines-test`（`runTest` + `StandardTestDispatcher`）；Repository / ViewModel 的 dispatcher 均可注入。
- 一个 `MainDispatcherRule`（`Dispatchers.setMain`）解决 `viewModelScope` 默认跑在 Main 的问题。
- 手写 fake 替身，**不引 mockk/mockito**。
- **不引 Turbine**，用 `runTest` 收集验证，保持依赖精简。

## 9. 不纳入范围（YAGNI）

- `RemoteNewsDataSource` 真实接口实现（留骨架，等 key）。
- `BaseFragment` 实例化 / Fragment 承载。
- Espresso / instrumented UI 测试。
- DI 框架（Hilt / Koin）。
- 详情页 / 多页面导航。
- `BaseViewModel` 的通用逻辑抽象。
