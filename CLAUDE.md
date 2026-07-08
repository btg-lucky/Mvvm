# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目现状

Android 多模块工程：在可复用 Kotlin MVVM 脚手架之上落地了真实三模块 App——**新闻**（聚合数据接口：分类列表 + 分页 + 详情 WebView）、**天气**（聚合数据：实况+近5天预报+生活指数，背景随天气渐变+矢量插画，默认杭州可切换城市，DataStore 持久化）、**我的**（本地 Room 账号：注册/登录/退出，DataStore 持久登录态）。单 Activity + BottomNavigationView 三 Tab，每个业务是独立 feature module，自带 navigation graph，app 只做壳。

技术栈：Kotlin 2.0.21（K2）+ 协程/Flow、AGP 8.6.x / Gradle 8.9 / JDK 17、Kotlin DSL + Gradle Version Catalog、ViewBinding。**Hilt(KSP) 依赖注入**、Retrofit 2.11 / OkHttp 4.12、Coil、DataStore、Room、security-crypto、Navigation、SwipeRefreshLayout、Material。

**全工程零 Java，全部 Kotlin。** 新代码一律 Kotlin。

## 构建命令

Gradle wrapper（Gradle 8.9，AGP 8.6.x，需 JDK 17）：

```bash
./gradlew assembleDebug                 # 编译 debug APK
./gradlew :app:assembleDebug            # 只编 app
./gradlew installDebug                  # 装到已连接设备
./gradlew testDebugUnitTest             # 跑全部单元测试（JVM）
./gradlew :module_news:testDebugUnitTest  # 新闻模块单测
./gradlew :module_mine:testDebugUnitTest  # 我的模块单测
./gradlew :module_weather:testDebugUnitTest  # 天气模块单测
./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.ExceptionHandlerTest"  # 单类
./gradlew lint
./gradlew clean
```

单测在 `module_news/src/test/`、`module_mine/src/test/`、`module_weather/src/test/`、`lib_common/src/test/`。无 instrumented 测试（UI/存储/权限等依赖 framework 的能力靠手动验证）。

## 模块结构与依赖方向

七个模块，依赖链决定代码该放哪一层：

```
app ──▶ module_news / module_weather / module_mine ──▶ lib_common ──api──▶ lib_opensource / lib_widget
```

**App 壳与 feature 模块：**
- **app** (`com.btg.mvvm`)：壳。`App`(`@HiltAndroidApp`)、`MainActivity`(NavHost + BottomNavigationView，`nav_main.xml` include 三个子 graph，非顶级页面隐藏底部导航)。
- **module_news** (`com.btg.news`)：新闻。聚合数据接口（`JuheResponse.unwrap` → safeApiCall 体系，`@JuheRetrofit` 独立 baseUrl `https://v.juhe.cn/`）；API key 走 `local.properties` 的 `JUHE_API_KEY` → BuildConfig，不入库；TabLayout 10 分类 + 分页 + SwipeRefresh + StateLayout + 详情 WebView。
- **module_weather** (`com.btg.weather`)：天气。聚合数据接口（独立 `@WeatherJuheRetrofit`，baseUrl `https://apis.juhe.cn/`）；实况+近5天预报+生活指数，背景随档位渐变+矢量插画，默认杭州可切换城市，DataStore 持久化选定城市。
- **module_mine** (`com.btg.mine`)：本地账号。Room `users` 表 + `PasswordHasher`(SHA-256+盐，constant-time verify) + `SessionStore`(DataStore)；注册/登录/退出页。

**框架库（不含业务）：**
- **lib_common** (`com.btg.common`)：**框架核心**，绝大部分能力在这。应用 Hilt+KSP。用 `api` 暴露 appcompat / lifecycle / coroutines / core-ktx 等，并 `api` 依赖下游两库，能力传递给上层。
- **lib_opensource** (`com.btg.opensource`)：**无业务代码，只做依赖聚合** —— `api` 暴露 okhttp(+logging) / retrofit / gson / logger / coil / datastore / security-crypto / room / navigation / material / swiperefreshlayout / recyclerview / lifecycle-process。加/换第三方依赖改这里。无 Kotlin 代码。
- **lib_widget** (`com.btg.widget`)：纯自定义 View（不依赖框架基类）。当前有 `StateLayout`（多状态布局）、`TitleBar`。程序化构建、零布局资源。

设计文档见 `docs/superpowers/specs/2026-07-06-news-app-modular-design.md`。

## lib_common 框架能力总览

- **base/**：`BaseActivity<VB>`、`BaseFragment<VB>`（onDestroyView 自动释放 binding）、`BaseViewModel`（`launchWithState`/`launchListWithState` 帮手 + 统一 `errorEvent`）、`BaseApplication`（onCreate 初始化日志/崩溃捕获/前后台监听，`companion object instance`）、`BaseContent`（常量 object，`BASE_URL`）。
- **result/**：`ApiResult<out T>`（数据层，Success/Error）；`UiState<out T>`（UI 层四态 Loading/Success/Empty/Error）+ `toUiState`/`toListUiState` 映射扩展。
- **network/**：见下。
- **storage/**：`PreferenceStore`（DataStore 类型化 KV，读 Flow/写 suspend）、`SecurePreferences`（EncryptedSharedPreferences，敏感数据）、`BaseDao<T>`（通用 Room DAO 基接口）。
- **permission/**：`PermissionResult`（纯逻辑，allGranted/denied，有单测）、`PermissionRequester`（ActivityResult 封装）、`Context.openAppSettings()`（永久拒绝引导设置页）。
- **event/**：`FlowBus`（Flow-based 全局事件总线，按事件类型分流；replay=0）。
- **app/**：`CrashHandler`（全局崩溃捕获）、`AppForegroundObserver`（ProcessLifecycleOwner 前后台，`isForeground: StateFlow`）。
- **ui/**：`LoadingDialog`、`Context.showConfirmDialog/showAlertDialog`、`BaseBottomSheetFragment<VB>`、`Context.toast`（防抖）、沉浸式 `Activity.setStatusBar/transparentStatusBar`、`SwipeRefreshLayout.onRefresh` + `RecyclerView.onLoadMore` + `StateLayout.render(UiState)`。
- **ext/**：`View.visible/gone/invisible`、`View.setOnDebouncedClickListener`、`Int/Float.dp`、`Flow.collectOnStarted(owner)`、`ImageView.loadUrl`（Coil）。

## 网络层（`com.btg.common.network`，全 Kotlin）

装配走 **Hilt**，替代旧的单例 ApiRetrofit（已删除）。

- **NetworkModule**（Hilt `@Module`/`SingletonComponent`）：`@Provides` Gson / OkHttpClient / Retrofit / CookieJar / TokenProvider。15s 超时、失败重连、接入 `ApiDns`、`CookieManger`、Header/Token 拦截器、DEBUG 日志拦截器。要多 baseUrl 用 `@Qualifier`。
- **结果与异常**：`BaseResponse<T>`(code/message/data) + `unwrap(successCode)`；`sealed class AppException`(Network/Timeout/Server/Business/Parse/Unknown)；`ExceptionHandler.handle(throwable)` 映射为带友好文案的 AppException；`suspend safeApiCall { }` → `ApiResult<T>`（Repository 一行调用 `safeApiCall { api.xxx().unwrap() }`）。
- **拦截器**：`HeaderInterceptor`（公共头）、`TokenInterceptor` + `TokenProvider`（token 来源可插拔；默认实现返回 null，接真实来源替换 `NetworkModule.provideTokenProvider`）。
- **gson/**：`GsonFactory.create()` 注册四个空值兜底 `TypeAdapter`（`null`/`"null"` → 0/0.0/0L/`""`），供 NetworkModule 与测试共用。
- **cookie/**：`CookieManger`(CookieJar) + `PersistentCookieStore` + `OkHttpCookies`，持久化到 SharedPreferences，已接入 OkHttpClient。
- **ApiDns**：IPv4 优先，已接入 OkHttpClient。
- **download/**：`FileDownloader.download(url,dest): Flow<DownloadState>`（进度）、`ProgressRequestBody`（上传进度）。
- **ConnectivityObserver**：网络可用性 `Flow<Boolean>`（需 `ACCESS_NETWORK_STATE`，已在 lib_common manifest 声明；`INTERNET` 亦已声明）。

## 依赖注入（Hilt）

- `App : BaseApplication()` 加 `@HiltAndroidApp`，已在 manifest 用 `android:name` 注册。
- ViewModel 用 `@HiltViewModel` + `@Inject constructor`，Fragment 加 `@AndroidEntryPoint` 后 `by viewModels()` 即可。
- 数据装配放各模块的 Hilt `@Module`（如 `module_news/di/` 提供 `NewsDataSource`/`NewsRepository`，`module_mine/di/` 提供账号相关依赖）。**换真实数据源只改对应模块的 `@Provides` 一行**，上层不动。
- lib_common 里的 `NetworkModule` 提供网络单例。app / module_news / module_mine / module_weather / lib_common 应用 Hilt+KSP；lib_opensource/lib_widget 不接。

## 业务模块结构

**module_news**
- `data/source/`：`NewsDataSource`(接口)、`FakeNewsDataSource`(假数据+delay，供单测与无 key 演示)、`RemoteNewsDataSource`(真实接口，`JuheResponse.unwrap`)、`NewsApi`(Retrofit suspend 接口，`@JuheRetrofit`)。
- `data/repository/NewsRepository`：按分类分页拉取，`ioDispatcher` 可注入（测试用）。
- `ui/`：`NewsListViewModel`(`@HiltViewModel`)、`NewsAdapter`(ListAdapter+DiffUtil+Coil)、`NewsListFragment`(TabLayout + SwipeRefresh + StateLayout)、`NewsDetailFragment`(WebView + url fallback)。

**module_mine**
- `data/local/`：Room `users` 表（`UserEntity`、`UserDao : BaseDao`、`MineDatabase`）。
- `data/`：`PasswordHasher`(SHA-256+盐，constant-time compare)、`SessionStore`(DataStore `mine_prefs`，持久化登录态)。
- `ui/`：`LoginFragment`、`RegisterFragment`、`MineFragment`（已登录主页 + 退出）、对应 ViewModel。

**module_weather**
- `data/source/`：`WeatherDataSource`(接口)、`FakeWeatherDataSource`(假数据+delay)、`RemoteWeatherDataSource`(真实接口，`JuheResponse.unwrap`)、`WeatherApi`(Retrofit suspend，`@WeatherJuheRetrofit`)、`JuheResponse`(通用聚合响应壳)、`WeatherDto`(实况/预报/生活指数 DTO)。
- `data/repository/WeatherRepository`：`getWeatherOverview(city)`，query 必需+life 失败吞空，`ioDispatcher` 可注入（测试用）。
- `data/CityStore`：DataStore 持久化选定城市（默认"杭州"）。
- `data/model/WeatherCategory`：wid → 5 档映射（CLEAR/CLOUDY/RAIN/SNOW/FOG，未知兜底 CLOUDY）。
- `di/WeatherModule`：Hilt `@Module`，独立 `@WeatherJuheRetrofit` Retrofit，baseUrl `https://apis.juhe.cn/`。
- `ui/`：`WeatherViewModel`(`@HiltViewModel`)、`WeatherFragment`(SwipeRefresh + StateLayout，背景随 WeatherCategory 渐变+矢量插画)、`ForecastAdapter`(横向近5天)、`LifeAdapter`(网格生活指数，点击弹详情)。

## 依赖与版本集中管理

版本集中在 **`gradle/libs.versions.toml`**（Version Catalog），构建脚本 Kotlin DSL。模块脚本用 `libs.xxx` 别名，不写死坐标。

- SDK：`compileSdk 35` / `targetSdk 35` / `minSdk 24`，`applicationId com.btg.mvvm`。
- 加依赖/改版本先动 `libs.versions.toml`；改 SDK 版本按全局规范需先确认。
- `namespace`：app=`com.btg.mvvm`，module_news=`com.btg.news`，module_weather=`com.btg.weather`，module_mine=`com.btg.mine`，lib_common=`com.btg.common`，lib_opensource=`com.btg.opensource`，lib_widget=`com.btg.widget`。
- app 主题为 `Theme.MaterialComponents.Light.NoActionBar`（Material 组件前置要求）；targetSdk 35 强制 edge-to-edge，由 app 的 `activity_main.xml` 根布局统一 `android:fitsSystemWindows="true"` 避让系统栏（Fragment 根布局无需重复设置）。

## 约定与留意点

- 测试：`kotlinx-coroutines-test`（`runTest` + 注入 dispatcher）+ 手写 fake，**不引 mockk/mockito/Turbine/Robolectric**。ViewModel 测试用 `MainDispatcherRule`（`UnconfinedTestDispatcher`，module_news / module_mine / module_weather / lib_common 各有一份）。依赖 Android framework 的能力尽量把核心逻辑抽纯函数测（如 `PermissionResult`/`ExceptionHandler`/`GsonFactory`/`PasswordHasher`），framework 交互靠手动验证。
- 日志用 `com.orhanobut.logger.Logger`（全局 tag `BTG_LOG`，仅 DEBUG），不要用裸 `android.util.Log`/`System.out`。
- 下拉刷新用官方 `SwipeRefreshLayout`（SmartRefreshLayout 2.1 依赖旧 support 库、与 AGP 8 无 Jetifier 不兼容，已弃用）。
- 设计文档与分阶段实现计划在 `docs/superpowers/`（spec + plan），记录了这套架构逐阶段的决策依据；最新模块化设计文档见 `docs/superpowers/specs/2026-07-06-news-app-modular-design.md`。
- 待接入（YAGNI 留白）：`TokenProvider` 真实 token 来源、BottomSheet 具体实例（基类已备）。
