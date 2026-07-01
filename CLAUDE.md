# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目现状

早期阶段的 Android 多模块工程。项目名叫 "Mvvm"，但目前**还没有 MVVM 分层**（没有 ViewModel / Repository / 数据流），实质上是一个网络层脚手架 + 空的业务壳。现有代码全是 **Java**（AGP 3.5.4 时代），暂无单元测试、无 README。

按全局规范：新代码默认写 Kotlin，不要顺手把已有 Java 文件重写成 Kotlin。

## 构建命令

用 Gradle wrapper（Gradle 5.4.1，AGP 3.5.4）：

```bash
./gradlew assembleDebug          # 编译 debug APK
./gradlew :app:assembleDebug     # 只编 app 模块
./gradlew build                  # 全量构建（含 lint）
./gradlew clean
./gradlew installDebug           # 装到已连接设备
./gradlew lint                   # 运行 Android lint
```

当前没有测试代码。新增测试后：`./gradlew test`（单元测试）、`./gradlew connectedAndroidTest`（instrumented），单类用 `./gradlew :模块:test --tests "全限定类名"`。

## 模块结构与依赖方向

四个模块，依赖链是关键（决定了代码该放哪一层）：

```
app  ──implementation──▶  lib_common  ──api──▶  lib_opensource   (第三方网络库出口)
                                       ──api──▶  lib_widget       (自定义控件，目前空)
```

- **app** (`com.btg.mvvm`)：application 模块，只有 `MainActivity`，唯一带 launcher 的组件。
- **lib_common** (`com.btg.common`)：核心库，放 `base/` 基类和 `http/` 网络层。用 `api` 依赖下游两个库，所以它们的能力会传递给 app。
- **lib_opensource** (`com.btg.opensource`)：**没有业务代码，只做依赖聚合** —— 用 `api` 暴露 okhttp3 / retrofit2 / rxjava2 / converter-gson / logger。要给全项目加/换网络相关依赖，改这里。
- **lib_widget** (`com.btg.widget`)：自定义 View 库，目前空。

## 依赖与版本集中管理

**所有** SDK 版本和第三方库版本集中在根目录 `config.gradle` 的 `ext` 里：
- `ext.android` —— compileSdk 30 / minSdk 19 / targetSdk 30 / buildTools 30.0.2 / applicationId `com.btg.mvvm`。
- `ext.dependencies` —— 库坐标 map，各模块用 `rootProject.ext.dependencies["xxx"]` 引用。

加依赖或改版本先动 `config.gradle`，别在模块里写死坐标。改 minSdk/targetSdk/SDK 版本按全局规范需先确认。

## 网络层（核心，全在 lib_common/http）

- **ApiRetrofit**（`http/api/`）：单例，按 `baseUrl` 把 Retrofit 缓存进 `mRetrofitMap`，支持多 baseUrl。装配了 CookieJar、15s 超时、失败重连、`GsonConverterFactory` + `RxJava2CallAdapterFactory`。`initRetrofit(baseUrl)` 传空串则用默认 `BaseContent.BASE_URL`。
- **ApiService**（`http/api/`）：空接口，Retrofit 接口定义的落点，新增后端接口写这里（或其子接口）。
- **ApiDns**（`http/api/`）：自定义 `Dns`，把 IPv4 排到 IPv6 前面，规避部分机型解析慢。**注意：目前未接入 OkHttpClient**，需要时在 `ApiRetrofit` 里 `.dns(new ApiDns())`。
- **gson/**：四个 `TypeAdapter`（Integer/Double/Long/String）把后端返回的 `null`/`"null"` 兜底成 0 / 0.0 / 0L / `""`，在 `ApiRetrofit.buildGson()` 里注册。改空值兜底逻辑看这里。
- **convert/**：`MyGsonConverterFactory` 及其 request/response converter，用于处理返回体里 code 错误/多余字段。**注意：写好了但 ApiRetrofit 当前用的是标准 `GsonConverterFactory`，这套自定义 Factory 还没接入。**
- **cookie/**：`CookieManger`(CookieJar) + `PersistentCookieStore` + `OkHttpCookies`，把 cookie 持久化到 SharedPreferences，已接入 OkHttpClient。

## base 基类

- **BaseApplication**：持有静态 `Application`（`ApiRetrofit` 靠它拿 Context），并初始化 logger（全局 tag `BTG_LOG`，仅 DEBUG 打印）。**注意：目前没在 app 的 AndroidManifest 里用 `android:name` 注册**，`getApplication()` 现在会返回 null——真正用起来前得先在 manifest 注册。
- **BaseActivity**：空 stub（未继承 Activity）。
- **BaseContent**：常量接口，`BASE_URL` 目前指向聚合数据的头条接口。

## 已知需要留意的点

- 全局用 `com.orhanobut.logger.Logger` 打日志，不要用裸 `android.util.Log` 或 `System.out`。
