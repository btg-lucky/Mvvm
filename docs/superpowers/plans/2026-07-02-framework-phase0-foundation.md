# 框架能力补齐 · 阶段 0（地基）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把整套框架要用到的依赖全部接进工程、Hilt + KSP 接入 `app`/`lib_common`、修复 `Application` 未注册（`getApplication()` 返回 null）的坑，让工程带着完整依赖图**编译通过、现有单测全绿**。

**Architecture:** 只动构建配置与装配点，不写业务逻辑。新依赖集中进 Gradle Version Catalog；纯运行时库在 `lib_opensource` 用 `api` 聚合下发；Hilt 插件 + KSP 处理器只在有注入类/注解的 `app` 与 `lib_common` 应用；`app` 新建 `App : BaseApplication()` 加 `@HiltAndroidApp` 并在 manifest 注册。故意一次性引入全部依赖，让版本冲突在本阶段暴露，不与后续业务代码搅在一起。

**Tech Stack:** Kotlin 2.0.21 / AGP 8.6.1 / Gradle 8.9 / JDK 17；Gradle Version Catalog + Kotlin DSL；Hilt 2.52（KSP）；KSP 2.0.21-1.0.28。

## Global Constraints

- 语言：新代码一律 Kotlin；本阶段**不转换任何现有 Java 文件**（`http/`、`BaseApplication`、`BaseContent` 的 Kotlin 化留到阶段 2/5）。
- 不改 `minSdk = 24` / `targetSdk = 35` / `compileSdk = 35` / `applicationId = com.btg.mvvm`。
- 加依赖/版本只动 `gradle/libs.versions.toml`，模块脚本用 `libs.xxx` 别名，不写死坐标。
- 依赖方向不变：`app → lib_common → lib_opensource / lib_widget`；`lib_common` 用 `api` 传递能力。
- 各模块 `namespace`：app=`com.btg.mvvm`，lib_common=`com.btg.common`，lib_opensource=`com.btg.opensource`，lib_widget=`com.btg.widget`。
- 每个任务末尾提交一次（Conventional Commits，祈使句），commit message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 当前工作分支：`feat/framework-buildout`（已创建）。
- 版本号为核对后的确切值；若某库解析失败，就近调到相邻可用版本并在 commit message 注明，不要停在报错上。

---

### Task 1: 扩展 Version Catalog 并在根脚本声明 Hilt / KSP 插件

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`（根）

**Interfaces:**
- Consumes: 无（本阶段第一个任务）。
- Produces: 供后续任务引用的 catalog 别名——
  - 插件别名：`libs.plugins.hilt`、`libs.plugins.ksp`
  - 库别名：`libs.hilt.android`、`libs.hilt.compiler`、`libs.androidx.hilt.navigation.fragment`、`libs.coil`、`libs.androidx.datastore.preferences`、`libs.androidx.security.crypto`、`libs.androidx.room.runtime`、`libs.androidx.room.ktx`、`libs.androidx.room.compiler`、`libs.androidx.navigation.fragment.ktx`、`libs.androidx.navigation.ui.ktx`、`libs.androidx.lifecycle.process`、`libs.material`、`libs.smartrefresh.kernel`、`libs.smartrefresh.header.classics`、`libs.smartrefresh.footer.classics`

- [ ] **Step 1: 在 `[versions]` 追加新版本号**

在 `gradle/libs.versions.toml` 的 `[versions]` 块末尾（`junit = "4.13.2"` 之后）追加：

```toml
ksp = "2.0.21-1.0.28"
hilt = "2.52"
hiltNavigation = "1.2.0"
coil = "2.7.0"
datastore = "1.1.1"
securityCrypto = "1.0.0"
room = "2.6.1"
navigation = "2.8.4"
material = "1.12.0"
smartRefresh = "2.1.0"
```

（`lifecycle-process` 复用已有的 `lifecycle = "2.8.7"`，不新增版本。）

- [ ] **Step 2: 在 `[libraries]` 追加新库别名**

在 `[libraries]` 块末尾（`junit = ...` 之后）追加：

```toml
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-fragment = { module = "androidx.hilt:hilt-navigation-fragment", version.ref = "hiltNavigation" }
coil = { module = "io.coil-kt:coil", version.ref = "coil" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "securityCrypto" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-navigation-fragment-ktx = { module = "androidx.navigation:navigation-fragment-ktx", version.ref = "navigation" }
androidx-navigation-ui-ktx = { module = "androidx.navigation:navigation-ui-ktx", version.ref = "navigation" }
material = { module = "com.google.android.material:material", version.ref = "material" }
smartrefresh-kernel = { module = "io.github.scwang90:refresh-layout-kernel", version.ref = "smartRefresh" }
smartrefresh-header-classics = { module = "io.github.scwang90:refresh-header-classics", version.ref = "smartRefresh" }
smartrefresh-footer-classics = { module = "io.github.scwang90:refresh-footer-classics", version.ref = "smartRefresh" }
```

- [ ] **Step 3: 在 `[plugins]` 追加 Hilt / KSP**

在 `[plugins]` 块末尾（`kotlin-android = ...` 之后）追加：

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 4: 根 `build.gradle.kts` 声明两个新插件（apply false）**

把根 `build.gradle.kts` 整体替换为：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 5: 验证 catalog 与插件声明可解析**

Run: `./gradlew help -q`
Expected: 构建成功（`BUILD SUCCESSFUL`），无 "Could not resolve" / catalog 解析错误。此命令只校验脚本与 catalog 语法，不下载全部依赖。

- [ ] **Step 6: 提交**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "build: add hilt/ksp/coil/datastore/room/navigation/material/smartrefresh to version catalog

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 在 lib_opensource 聚合纯运行时依赖

**Files:**
- Modify: `lib_opensource/build.gradle.kts:27-33`（`dependencies { }` 块）

**Interfaces:**
- Consumes: Task 1 的库别名（`libs.coil`、`libs.androidx.datastore.preferences` 等）。
- Produces: 通过 `lib_common` 的 `api(project(":lib_opensource"))` 把 Coil / DataStore / Room-runtime / Navigation / Material / SmartRefresh / security-crypto / lifecycle-process 传递给全工程。Room 注解处理器（`room-compiler`）**不在此**，留到阶段 4 在 `app` 用 `ksp` 接入。

- [ ] **Step 1: 扩充 `lib_opensource` 的 dependencies 块**

把 `lib_opensource/build.gradle.kts` 的 `dependencies { }` 块替换为：

```kotlin
dependencies {
    // 网络（原有）
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.retrofit.converter.gson)
    api(libs.gson)
    api(libs.logger)

    // 存储
    api(libs.androidx.datastore.preferences)
    api(libs.androidx.security.crypto)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)

    // 导航
    api(libs.androidx.navigation.fragment.ktx)
    api(libs.androidx.navigation.ui.ktx)

    // 生命周期（前后台监听）
    api(libs.androidx.lifecycle.process)

    // 图片
    api(libs.coil)

    // UI
    api(libs.material)
    api(libs.smartrefresh.kernel)
    api(libs.smartrefresh.header.classics)
    api(libs.smartrefresh.footer.classics)
}
```

- [ ] **Step 2: 验证 lib_opensource 能解析并编译**

Run: `./gradlew :lib_opensource:assembleDebug`
Expected: `BUILD SUCCESSFUL`。首次会下载新依赖；若出现某坐标 "Could not find"，按 Global Constraints 就近调版本后重试。

- [ ] **Step 3: 提交**

```bash
git add lib_opensource/build.gradle.kts
git commit -m "build: aggregate coil/datastore/room/navigation/material/smartrefresh via lib_opensource api

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 在 lib_common 接入 Hilt + KSP

**Files:**
- Modify: `lib_common/build.gradle.kts:1-4`（`plugins { }`）与 `:35-40`（`dependencies { }`）

**Interfaces:**
- Consumes: Task 1 的 `libs.plugins.hilt`、`libs.plugins.ksp`、`libs.hilt.android`、`libs.hilt.compiler`。
- Produces: `lib_common` 具备定义 Hilt `@Module`（阶段 2 的 `NetworkModule` 等）的能力。

- [ ] **Step 1: 在 plugins 块加 hilt 与 ksp**

把 `lib_common/build.gradle.kts` 的 `plugins { }` 块替换为：

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
```

- [ ] **Step 2: 在 dependencies 块加 hilt 运行时与编译器**

把 `lib_common/build.gradle.kts` 的 `dependencies { }` 块替换为：

```kotlin
dependencies {
    api(project(":lib_opensource"))
    api(project(":lib_widget"))
    api(libs.androidx.appcompat)
    api(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

- [ ] **Step 3: 验证 lib_common 编译（KSP + Hilt 生效）**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。此时 `lib_common` 无 Hilt 注解类，KSP 无产物属正常。

- [ ] **Step 4: 提交**

```bash
git add lib_common/build.gradle.kts
git commit -m "build: apply hilt and ksp plugins to lib_common

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: 在 app 接入 Hilt + KSP，新建 Application 并在 manifest 注册

**Files:**
- Modify: `app/build.gradle.kts:1-4`（`plugins { }`）与 `:37-50`（`dependencies { }`）
- Create: `app/src/main/java/com/btg/mvvm/App.kt`
- Modify: `app/src/main/AndroidManifest.xml:4`（`<application>` 加 `android:name`）

**Interfaces:**
- Consumes: Task 1 的 `libs.plugins.hilt`/`libs.plugins.ksp`/`libs.hilt.android`/`libs.hilt.compiler`；`lib_common` 里现有的 Java `com.btg.common.base.BaseApplication`。
- Produces: 已注册的 `com.btg.mvvm.App`（`@HiltAndroidApp`），修复 `BaseApplication.getApplication()` 返回 null 的问题；为后续阶段的 `@HiltViewModel`/`@AndroidEntryPoint` 提供根组件。

- [ ] **Step 1: 在 app 的 plugins 块加 hilt 与 ksp**

把 `app/build.gradle.kts` 的 `plugins { }` 块替换为：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
```

- [ ] **Step 2: 在 app 的 dependencies 块加 hilt 运行时与编译器**

把 `app/build.gradle.kts` 的 `dependencies { }` 块替换为：

```kotlin
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

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 3: 新建 `App.kt`**

创建 `app/src/main/java/com/btg/mvvm/App.kt`：

```kotlin
package com.btg.mvvm

import com.btg.common.base.BaseApplication
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口 Application。
 *
 * 继承 lib_common 的 [BaseApplication]（日志初始化、后续挂前后台监听/崩溃捕获），
 * 并以 @HiltAndroidApp 生成 Hilt 根组件。必须在 AndroidManifest 里用 android:name 注册，
 * 否则运行时用的是默认 Application，BaseApplication.getApplication() 会返回 null。
 */
@HiltAndroidApp
class App : BaseApplication()
```

- [ ] **Step 4: 在 manifest 注册 App**

把 `app/src/main/AndroidManifest.xml` 的 `<application` 开标签（第 4 行）改为在首行加入 `android:name`：

```xml
    <application
        android:name=".App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
```

（其余 `<activity>` 等内容不动。）

- [ ] **Step 5: 验证 app 编译（Hilt 根组件生成）**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。Hilt 会为 `App` 生成 `Hilt_App` 等类；`MainActivity` 仍用现有手动 `NewsViewModelFactory`，不受影响。

- [ ] **Step 6: 提交**

```bash
git add app/build.gradle.kts app/src/main/java/com/btg/mvvm/App.kt app/src/main/AndroidManifest.xml
git commit -m "feat: register Hilt Application (App : BaseApplication @HiltAndroidApp) and fix null getApplication

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: 全量编译 + 现有单测回归 + 手动冒烟

**Files:**
- 无改动（纯验证任务；如发现问题在对应任务修）。

**Interfaces:**
- Consumes: Task 2–4 的产物（完整依赖图 + Hilt 装配）。
- Produces: 阶段 0 完成标志——完整工程编译通过、现有单测全绿、示范页可正常启动。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`。这是"依赖组合无冲突"的关键验证；若失败，多为版本冲突，按 Global Constraints 微调后回到对应任务提交修复。

- [ ] **Step 2: 现有单元测试回归**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`，`NewsRepositoryTest` / `NewsViewModelTest` / `FakeNewsDataSourceTest` 全通过（本阶段未动业务代码，应保持全绿）。

- [ ] **Step 3: 手动冒烟（确认 Application 注册生效、示范页正常）**

Run: `./gradlew installDebug`（需连接设备/模拟器）
手动：启动 app，确认新闻列表正常加载（走 `FakeNewsDataSource`），无启动崩溃。
Expected: 应用正常启动、列表展示；Logcat 无 `NullPointerException` / Hilt 相关崩溃。
（无设备时跳过此步并在收尾说明中注明"未做设备冒烟"。）

- [ ] **Step 4: 收尾（无代码改动则无需提交）**

若 Step 1–3 均通过且无修复改动，本任务不产生 commit；在阶段小结里记录验证结果。若产生了修复改动，按所属任务的语义提交。

---

## Self-Review（对照 spec 第 12 节"阶段 0"）

- ✅ `lib_opensource` 加聚合依赖 → Task 2。
- ✅ Hilt + KSP 接入 `app`/`lib_common` → Task 3、Task 4。
- ✅ 修 `BaseApplication` 注册（`App : BaseApplication` + `@HiltAndroidApp` + manifest）→ Task 4。
- ✅ 空壳/现有 demo 编译通过 + 现有单测绿 → Task 5。
- 版本一致性：catalog 别名（Task 1 定义）与各模块引用（Task 2–4）逐一对应，命名遵循 catalog 的 `-`→`.` 映射（如 `androidx-datastore-preferences` → `libs.androidx.datastore.preferences`）。
- 不含占位符：所有版本号、坐标、代码、命令均为确切值。
- 阶段边界：本阶段只碰构建配置 + 一个 `App.kt`；不转换 Java、不写业务逻辑（符合 spec 阶段划分）。
