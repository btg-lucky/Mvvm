# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目现状

Android 多模块工程，已落地一套 **Kotlin MVVM 架构**：单向数据流 `View → ViewModel → Repository → DataSource`，用"新闻列表"示范页（`MainActivity`）跑通端到端。示范数据走可切换的 `FakeNewsDataSource`（不依赖真实接口/key）。

技术栈：Kotlin 2.0.x（K2）+ 协程/Flow、AGP 8.6.x / Gradle 8.9 / JDK 17、Kotlin DSL 构建脚本 + Gradle Version Catalog、ViewBinding、AndroidX lifecycle。网络层保留（Retrofit 2.11 / OkHttp 4.12），已移除 RxJava2。

原有 `lib_common/http` 下的网络设施仍是 **Java**；MVVM 代码全是 **Kotlin**。按全局规范：新代码写 Kotlin，不要顺手把已有 Java 文件重写成 Kotlin。

## 构建命令

Gradle wrapper（Gradle 8.9，AGP 8.6.x，需 JDK 17）：

```bash
./gradlew assembleDebug                 # 编译 debug APK
./gradlew :app:assembleDebug            # 只编 app
./gradlew installDebug                  # 装到已连接设备
./gradlew testDebugUnitTest             # 跑单元测试（JVM）
./gradlew :app:testDebugUnitTest --tests "com.btg.mvvm.ui.news.NewsViewModelTest"  # 单类
./gradlew lint
./gradlew clean
```

单测目前都在 `app` 模块（`app/src/test/`）。无 instrumented 测试。

## 模块结构与依赖方向

四个模块，依赖链决定代码该放哪一层：

```
app  ──implementation──▶  lib_common  ──api──▶  lib_opensource   (第三方网络库出口)
                                       ──api──▶  lib_widget       (自定义控件，目前空)
```

- **app** (`com.btg.mvvm`)：application 模块，承载示范业务的完整 MVVM 代码（见下）。
- **lib_common** (`com.btg.common`)：核心库。放 MVVM 基类 `base/`、通用结果类型 `result/`、以及旧的 `http/` 网络层。用 `api` 暴露 `appcompat` 与 `lifecycle-viewmodel-ktx`，并 `api` 依赖下游两库，能力传递给 app。
- **lib_opensource** (`com.btg.opensource`)：**无业务代码，只做依赖聚合** —— 用 `api` 暴露 okhttp / retrofit / converter-gson / gson / logger。加/换网络相关依赖改这里。无 Kotlin 代码（未加 kotlin 插件）。
- **lib_widget** (`com.btg.widget`)：自定义 View 库，目前空。

## MVVM 分层（核心）

单向数据流，各层职责严格：View 只渲染 + 转发事件；ViewModel 持有 UI 状态、调 Repository，不持有 Context/View；Repository 是数据唯一入口。

- **通用结果类型**：`lib_common` `com.btg.common.result.ApiResult<out T>` —— `sealed interface`，`Success`/`Error`。Repository 用它包装成功/失败，ViewModel `when` 穷尽分支。
- **MVVM 基类**（`lib_common/base`）：
  - `BaseActivity<VB : ViewBinding>`：泛型承载 ViewBinding，子类实现 `inflateBinding()`。
  - `BaseViewModel`：极薄 `open class`（分层锚点，暂无通用逻辑）；预留 `BaseFragment`（尚未创建）。
- **示范业务**（`app`）：
  - `data/model/NewsItem`（领域模型 data class）
  - `data/source/`：`NewsDataSource`（接口）、`FakeNewsDataSource`（当前实现，返回假数据 + `delay`）、`NewsApi`（Retrofit suspend 接口示例 + 占位 `NewsResponse`）、`RemoteNewsDataSource`（真实实现骨架，`fetchNews()` 为 `TODO()`，待接真实 key）
  - `data/repository/NewsRepository`：`suspend getNews(): ApiResult<List<NewsItem>>`，`withContext(ioDispatcher)` + `runCatching`，`ioDispatcher` 可注入（测试用）
  - `ui/news/`：`NewsUiState`（data class 状态快照）、`NewsEvent`（sealed，一次性事件）、`NewsViewModel`（`StateFlow<NewsUiState>` + `Channel/receiveAsFlow` 事件）、`NewsViewModelFactory`（手动 DI）、`NewsAdapter`（`ListAdapter` + `DiffUtil`）
  - `MainActivity`：`by viewModels { NewsViewModelFactory(NewsRepository(FakeNewsDataSource())) }` —— **数据源的唯一装配点**；在 `repeatOnLifecycle(STARTED)` 里收集 `uiState`/`events`。

**依赖注入**：手动 DI（`ViewModelProvider.Factory`），无 Hilt/Koin。要换真实数据源，只改 `MainActivity` 装配点那一行（`FakeNewsDataSource` → `RemoteNewsDataSource`），上层不动。

## 依赖与版本集中管理

版本集中在 **`gradle/libs.versions.toml`**（Gradle Version Catalog），构建脚本是 **Kotlin DSL**（`*.gradle.kts`）。模块脚本用 `libs.xxx` 别名引用，不写死坐标。

- SDK 级别：`compileSdk 35` / `targetSdk 35` / `minSdk 24`，`applicationId com.btg.mvvm`（在 `app/build.gradle.kts`）。
- 加依赖/改版本先动 `libs.versions.toml`。改 minSdk/targetSdk/SDK 版本按全局规范需先确认。
- 各模块用 `namespace`（非 manifest `package`）：app=`com.btg.mvvm`，lib_common=`com.btg.common`，lib_opensource=`com.btg.opensource`，lib_widget=`com.btg.widget`。

## 网络层（旧设施，全在 lib_common/http，仍是 Java）

- **ApiRetrofit**（`http/api/`）：单例（`volatile` + 双重检查锁定），按 `baseUrl` 缓存 Retrofit 到 `mRetrofitMap`。装配 CookieJar、15s 超时、失败重连、`GsonConverterFactory`（RxJava2 CallAdapter 已移除；suspend 接口无需 CallAdapter）。
- **ApiService**（`http/api/`）：旧的 Java 空标记接口。新的 suspend 接口用 Kotlin 写（见 `app` 的 `NewsApi`）——Java 接口无法写 `suspend`。
- **ApiDns**（`http/api/`）：自定义 `Dns`，IPv4 排到 IPv6 前。**未接入 OkHttpClient**，需要时在 `ApiRetrofit` 里 `.dns(new ApiDns())`。
- **gson/**：四个 `TypeAdapter`（Integer/Double/Long/String）把 `null`/`"null"` 兜底成 0/0.0/0L/`""`，在 `ApiRetrofit.buildGson()` 注册。
- **convert/**：`MyGsonConverterFactory` 及其 converter。**写好了但 ApiRetrofit 用的是标准 `GsonConverterFactory`，这套还没接入。**
- **cookie/**：`CookieManger`(CookieJar) + `PersistentCookieStore` + `OkHttpCookies`，cookie 持久化到 SharedPreferences，已接入 OkHttpClient。
- **BaseContent**：常量接口，`BASE_URL` 指向聚合数据头条接口。

## 已知需要留意的点

- ⚠️ **`BaseApplication` 未在 `app/src/main/AndroidManifest.xml` 用 `android:name` 注册**，`getApplication()` 现返回 null。示范页走 `FakeNewsDataSource` 不碰 `ApiRetrofit`，暂不受影响；但一旦换成 `RemoteNewsDataSource`，`ApiRetrofit` 构造里的 `CookieManger(BaseApplication.getApplication())` 会 NPE。**接真实数据源前，先注册 `BaseApplication`。**
- 测试用 `kotlinx-coroutines-test`（`runTest` + 注入测试 dispatcher）+ 手写 fake，不引 mockk/mockito/Turbine。ViewModel 测试用 `MainDispatcherRule`（`UnconfinedTestDispatcher`）替换 Main。
- 日志用 `com.orhanobut.logger.Logger`（全局 tag `BTG_LOG`，仅 DEBUG 打印），不要用裸 `android.util.Log` / `System.out`。
- 设计文档与实现计划在 `docs/superpowers/`（spec + plan），记录了这套架构的决策依据。
