# 框架能力补齐 · 设计文档

- 日期：2026-07-02
- 状态：已确认（待转实现计划）
- 前置：本设计建立在 `2026-07-01-mvvm-architecture-design.md`（MVVM 骨架）之上，把工程从"MVVM 骨架 + 新闻示范页"升级为一套**开箱即用的业务脚手架**，供未来新项目直接复用快速开发。

## 1. 背景与目标

现状：已有一套可跑通的 Kotlin MVVM 单向数据流骨架（`View → ViewModel → Repository → DataSource`），但只有网络+列表这一条链路。旧 `lib_common/http` 是早期 Java 网络设施，部分未接入（`MyGsonConverterFactory`、`ApiDns`）、部分是空壳（`ApiService`）。

目标：补齐 Android 项目**高频通用能力**，做成可复用框架。用户已逐条勾选范围（核心 + 按需，全收；不做项见第 13 节）。关键选型已定：**Hilt(KSP) + DataStore + Coil + SmartRefreshLayout + 保留 Gson**。所有 Java 文件转 Kotlin，转完项目零 Java。

按用户要求：**当作新项目，放手做**。

## 2. 关键决策记录

| 决策点 | 选择 | 理由 |
|---|---|---|
| DI | Hilt + KSP，替换手动 Factory | 框架价值在于提前铺好 DI；手动 DI 仅适合单页 demo，多依赖必换。KSP 比 kapt 快 |
| KV 存储 | DataStore（Preferences 版） | 官方方向、纯 Kotlin 无 native、天然 Flow 契合本架构 |
| 图片 | Coil | 纯 Kotlin、复用已有 OkHttp/协程、API 最简 |
| 下拉刷新 | SmartRefreshLayout | 内置"下拉刷新 + 上拉加载更多"，列表标配，开箱即用 |
| 序列化 | 保留 Gson | 复用已有的空值兜底 TypeAdapter（应对后端脏数据）；换库是纯横向替换无架构收益 |
| 导航 | 单 Activity + Navigation | 演示台多页面导航；也是现代推荐形态 |
| 测试 | 手写 fake + coroutines-test，不引 mockk/Turbine/Robolectric | 延续现状轻测试取向；framework 交互靠演示台手动验证 |
| Java 处理 | 全部转 Kotlin | 用户明确要求；转完零 Java |

## 3. 模块职责重构

沿用现有四模块与依赖方向（`app → lib_common → lib_opensource / lib_widget`），明确各自定位：

```
app (com.btg.mvvm)              演示/showcase + Application(@HiltAndroidApp)
                                单 Activity + Navigation 串起各能力示范页
lib_common (com.btg.common)     框架核心（绝大部分能力）
  - base/        BaseActivity / BaseFragment / BaseViewModel / BaseApplication
  - result/      ApiResult<T>(数据层结果) + UiState<T>(UI 层四态)
  - network/     Kotlin 重写 http：Retrofit+OkHttp+协程、BaseResponse、safeApiCall、
                 异常体系、拦截器、cookie、dns、gson 兜底、下载上传进度、网络状态监听、NetworkModule(Hilt)
  - storage/     DataStore 封装 + EncryptedSharedPreferences 封装 + Room 基建
  - permission/  权限请求封装 + 拒绝引导设置页
  - event/       Flow-based 事件总线
  - app/         App 前后台监听 + 全局崩溃捕获
  - ext/         扩展函数集（dp/px、View 显隐、防抖点击、Flow 收集、Coil 图片扩展）
  - ui/          Toast 封装、Loading/确认 Dialog、BottomSheet 基类、沉浸式状态栏、TitleBar 逻辑
lib_opensource (com.btg.opensource)  纯依赖聚合：api 暴露
                                okhttp/retrofit/converter-gson/gson/logger
                                + coil / datastore / room / navigation / smartrefresh / material
lib_widget (com.btg.widget)     纯自定义 View：StateLayout(多状态布局)、TitleBar 视图
```

**放置原则**：
- `lib_widget` / `lib_opensource` 在 `lib_common` 下游 → 只放"不依赖框架基类"的纯 View 和依赖聚合。`StateLayout`/`TitleBar` 是纯 View，放 `lib_widget`。
- 带 `Context`/协程/基类的能力（Dialog、Toast、DataStore、权限、Coil 扩展等）放 `lib_common`。

**Hilt 接入范围**：`app` 与 `lib_common`（有注入类/Hilt module）应用 Hilt + KSP 插件；`lib_opensource`/`lib_widget`（纯依赖/纯 View）不接。`@HiltAndroidApp` 注解在 `app` 的具体 `Application` 上。

## 4. 网络层重构（改动最大，替换旧 http）

旧 http 全部 Kotlin 化并重做成协程原生。

### 4.1 保留 / 丢弃

- **保留并 Kotlin 化**：4 个 gson 空值兜底 `TypeAdapter`（`null`/`"null"` → 0/0.0/0L/`""`，逻辑不变）；`CookieManger` + `PersistentCookieStore` + `OkHttpCookies`（cookie 持久化）；`ApiDns`（IPv4 优先，**这次真正接进 OkHttpClient**）；`BaseContent`（常量 → Kotlin `object`/`const`）。
- **丢弃**：未接入的 `MyGsonConverterFactory`（与标准 `GsonConverterFactory` 重复的死代码）、空标记接口 `ApiService`（新 suspend 接口用 Kotlin 写）、旧单例 `ApiRetrofit`（双重检查锁 → 换成 Hilt `@Provides`）。

### 4.2 统一响应与解包

```kotlin
data class BaseResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
)

// 解包：业务码成功取 data，否则抛 BusinessException
fun <T> BaseResponse<T>.unwrap(): T { ... }
```

### 4.3 异常体系

```kotlin
sealed class AppException(message: String) : Exception(message) {
    class Network(msg: String) : AppException(msg)   // 无网络/连接失败
    class Timeout(msg: String) : AppException(msg)
    class Server(val httpCode: Int, msg: String) : AppException(msg)   // 4xx/5xx
    class Business(val code: Int, msg: String) : AppException(msg)     // 业务码非成功
    class Parse(msg: String) : AppException(msg)
    class Unknown(msg: String) : AppException(msg)
}

object ExceptionHandler {
    fun handle(throwable: Throwable): AppException { ... }  // 各类 Throwable → AppException + 友好文案
}
```

- 友好文案放 resources（`strings.xml`），不硬编码。
- ViewModel 不再各自 try-catch，统一由此映射。

### 4.4 安全调用封装

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> =
    runCatching { block() }
        .fold({ ApiResult.Success(it) }, { ApiResult.Error(ExceptionHandler.handle(it)) })
```

Repository 一行调用：`safeApiCall { api.getNews().unwrap() }`。

### 4.5 拦截器体系 + 装配（Hilt）

- 拦截器：`HttpLoggingInterceptor`（仅 DEBUG）、公共 Header 拦截器、Token 注入拦截器（Token 来源做成可注入骨架，无真后端不硬接，留 TODO）。
- `ApiDns` 接进 OkHttpClient；`CookieManger` 接进（沿用现有）。
- `NetworkModule`（Hilt `@Module`）`@Provides` OkHttpClient / Retrofit / Service。单 baseUrl 默认；多 baseUrl 用 `@Qualifier` 区分（留注释示范）。
- `baseUrl` 从 `BaseContent` 常量取。

### 4.6 追加能力

- **下载 / 上传 + 进度**：`suspend fun download(...): Flow<DownloadState>`（Emitting 进度百分比 + 完成/失败）；上传同理（`RequestBody` 包装发进度）。
- **网络状态监听**：`ConnectivityManager` 的 callback 包成 `Flow<Boolean>`（是否有可用网络），供断网提示。

## 5. 结果与状态、基类

### 5.1 双层结果类型

- `ApiResult<out T>`（保留，`Success`/`Error`）——**数据层**用。
- `UiState<out T>`（新增）——**UI 层**用：

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}
```

- 提供映射扩展 `ApiResult<List<T>>.toUiState()`：`Success` 非空 → `Success`；`Success` 空列表 → `Empty`；`Error` → `Error(message)`。

> 说明：简单单次内容页用 `UiState<T>` 四态；需要"已有数据 + 正在刷新"组合态的复杂列表页，仍可用 data class 快照（沿用 news 现状），文档注明两者取舍。

### 5.2 基类增强

- **`BaseViewModel`**：提供 `launchWithState { }` 帮手（自动发 Loading → Success/Empty/Error 到指定 `MutableStateFlow`）+ 统一错误事件通道（`Channel`/`receiveAsFlow`）。
- **`BaseFragment<VB : ViewBinding>`**（新增）：泛型承载 ViewBinding，`onDestroyView` 自动置空 binding 防泄漏；观察用 `viewLifecycleOwner`。
- **`BaseActivity<VB>`**：保留现状。
- **`BaseApplication`**（Java → Kotlin）：挂 App 前后台监听 + 全局崩溃捕获初始化。

### 5.3 修复 Application 注册坑

`app` 定义 `class App : BaseApplication()` 加 `@HiltAndroidApp`，并在 `AndroidManifest.xml` 用 `android:name` 注册。一并修掉现在 `getApplication()` 返回 null 的问题。

## 6. UI 组件 / 交互

- **Toast 封装**：统一样式、防重复刷屏（同内容短时间只弹一次）。
- **Loading Dialog**：请求中全局遮罩。
- **通用确认 / 提示 Dialog**：标题/内容/按钮回调，DSL 或 builder 风格。
- **BottomSheet 基类**：底部菜单/选择。
- **StateLayout（`lib_widget`）**：一个容器控件切换 加载/空/错误/内容 四态，配合 `UiState<T>`。
- **TitleBar**：统一标题栏（视图在 `lib_widget`，交互逻辑按需在 `lib_common/ui`）。
- **沉浸式状态栏**：Activity/Fragment 帮手（`WindowCompat` + 状态栏透明/图标明暗）。
- **SmartRefreshLayout 封装**：下拉刷新 + 上拉加载更多的通用接线帮手。

## 7. 权限 / 存储 / 事件 / 可靠性

- **权限封装（`permission/`）**：基于 `ActivityResult` API，支持单/多权限、回调式；永久拒绝 → 引导跳应用设置页。核心判定逻辑（哪些未授权）抽成纯函数便于测试。
- **DataStore 封装（`storage/`）**：Preferences 版，key + 默认值声明式；读为 `Flow`，写为 `suspend`。
- **EncryptedSharedPreferences 封装**：敏感数据（token 等）加密存储。
- **Room 基建**：提供基础 DAO/TypeConverter；演示 DB（收藏表）放 `app`（`@Database` 所在模块应用 Room KSP）。
- **事件总线（`event/`）**：Flow-based（`SharedFlow`）跨页面通信，替代 EventBus。
- **全局崩溃捕获（`app/`）**：`Thread.setDefaultUncaughtExceptionHandler`，记录日志（`Logger`）后兜底。
- **App 前后台监听（`app/`）**：`ProcessLifecycleOwner` 或 `ActivityLifecycleCallbacks`，暴露前后台状态。

## 8. 扩展函数 / 图片

- **`ext/`**：`dp`/`px` 转换、View 显隐（`visible()/gone()`）、防抖点击、`Flow` 在 `repeatOnLifecycle` 的收集扩展、`Context` 常用等。
- **Coil 图片扩展**：`ImageView.load(url, placeholder, error)`，占位/错误/圆角等常用参数封装。

## 9. 演示 app（活文档）

`app` 从"单页新闻"改成**单 Activity + Navigation 的能力展示台**：首页列表点进各示范页——

1. 网络 + 列表 + 下拉刷新 + 上拉加载更多 + Coil 图片（扩展现有 news demo，仍走可切换 `FakeNewsDataSource`）
2. 权限申请（含永久拒绝引导设置页）
3. 弹窗 / Toast / BottomSheet
4. 多状态布局 StateLayout 切换
5. 存储（DataStore + 加密 SP + Room 收藏 demo）

既验证框架，又当范例供未来项目参考。

## 10. 依赖与版本（新增，集中进 `libs.versions.toml`）

在现有 catalog 基础上新增（版本方向性，实现时核对当下最新稳定版，以编译通过为准）：

| 用途 | 库 | 备注 |
|---|---|---|
| DI | dagger-hilt（plugin + runtime + compiler）、hilt-navigation-fragment | 用 KSP |
| KSP | com.google.devtools.ksp（plugin） | 版本需与 Kotlin 匹配 |
| 存储 | androidx.datastore:datastore-preferences、androidx.security:security-crypto、androidx.room（runtime/ktx + compiler） | Room 用 KSP |
| 图片 | io.coil-kt:coil | |
| 导航 | androidx.navigation:navigation-fragment-ktx / navigation-ui-ktx | |
| 刷新 | io.github.scwang90:refresh-layout-kernel + 刷新头/脚 | SmartRefreshLayout |
| UI | com.google.android.material:material | BottomSheet 等 |
| 生命周期 | androidx.lifecycle:lifecycle-process | 前后台监听 |

> 加依赖只动 `libs.versions.toml`；不改 minSdk/targetSdk/compileSdk。

## 11. 测试策略

延续 `runTest` + 手写 fake + 注入 dispatcher；**不引** mockk/mockito/Turbine/Robolectric。

**① 纯 JVM 单测（重点全写）**：
- `ExceptionHandler`：各类 `Throwable` → 对应 `AppException` + 文案
- `BaseResponse.unwrap()`：成功取 data；失败抛 `Business`；data null 处理
- `safeApiCall`：正常 → Success；异常 → Error 且已映射
- `ApiResult → UiState` 映射：非空 Success / 空列表 Empty / Error
- gson 兜底 `TypeAdapter`：`null`/`"null"` → 默认值；正常透传
- `NewsRepository` / `NewsViewModel`：沿用现有断言
- 事件总线：发送→订阅收到；无订阅者不崩

**② 依赖 Android framework 的封装**：核心逻辑抽成纯函数纳入①；framework 交互（DataStore/权限/Dialog/Room 实际读写）**不写自动化测，靠演示台 app 手动验证**（务实做法，符合轻测试取向）。

**③ 不做**：Espresso / instrumented UI 测试。

## 12. 分阶段实施顺序（每阶段独立编译 + 验证）

- **阶段 0 · 地基**：`lib_opensource` 加聚合依赖；Hilt + KSP 接入 `app`/`lib_common`；修 `BaseApplication` 注册（`App : BaseApplication` + `@HiltAndroidApp` + manifest）→ 空壳/现有 demo 编译通过。
- **阶段 1 · 核心基类 + 结果/状态 + 扩展 + 事件总线**：`BaseFragment`、`BaseViewModel` 增强、`UiState<T>` + 映射、`ext/`、`event/`。
- **阶段 2 · 网络层重构**：Kotlin 化 http、`BaseResponse`/`unwrap`、`AppException`/`ExceptionHandler`、`safeApiCall`、拦截器、cookie/dns 接入、gson adapter 转 Kotlin、`NetworkModule`、下载上传进度、网络状态监听。
- **阶段 3 · UI 组件**：`StateLayout`、Loading/确认 Dialog、BottomSheet 基类、`TitleBar`、沉浸式、SmartRefresh 封装。
- **阶段 4 · 权限 + 存储**：权限封装 + 设置页引导、DataStore、加密 SP、Room 基建 + 收藏 demo。
- **阶段 5 · 可靠性**：崩溃捕获、前后台监听。
- **阶段 6 · 演示台 + Navigation + Coil + 测试**：单 Activity + Navigation、各示范页、Coil 图片、补齐第 11 节测试。

每阶段末跑 `./gradlew assembleDebug`（+ 相关 `testDebugUnitTest`）确认。

## 13. 不纳入范围（YAGNI）

- 屏幕适配（AndroidAutoSize，侵入大）
- WebView 封装
- 多模块路由（ARouter，现规模不需要）
- kotlinx.serialization / Moshi 迁移（保留 Gson）
- Robolectric / instrumented 测试
- `RemoteNewsDataSource` 真实接口实现（延续留骨架，等 key）
- Token 刷新真实后端对接（留可注入骨架）
