# 框架能力补齐 · 阶段 2b（网络层装配与 IO）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成网络层重构的装配与 IO 部分：删除整个旧 `com.btg.common.http`（死代码）、`BaseContent` 转 Kotlin、cookie/dns 转 Kotlin、拦截器体系、Hilt `NetworkModule`、下载/上传进度、网络状态监听。收官后工程零 Java（除 `BaseApplication`，留阶段 5）。

**Architecture:** 新代码全部落在 `com.btg.common.network`。旧 http 是死代码（已核实无外部引用），先整包删除再新建，避免新旧并存。网络装配走 Hilt `NetworkModule`（`@Provides` OkHttp/Retrofit/Gson/CookieJar），替代旧的双重检查锁单例 `ApiRetrofit`。

**Tech Stack:** Kotlin / OkHttp 4.12 + logging-interceptor / Retrofit 2.11 / Gson / Hilt / 协程 Flow / okio。

## Global Constraints

- 语言全 Kotlin。本阶段结束后除 `lib_common/.../base/BaseApplication.java`（留阶段 5 转换）外无其它业务 Java。
- 新代码统一放包 `com.btg.common.network`（子包 `cookie`/`interceptor`/`download`/`gson`）。
- 加依赖只动 `gradle/libs.versions.toml`：新增 `logging-interceptor`（复用 okhttp 版本），在 `lib_opensource` 用 `api` 暴露。
- 不改 `minSdk`/`targetSdk`/`compileSdk`。
- 依赖 Android framework 的类只做编译验证（+ 阶段末真机冒烟），不写自动化测试（延续 spec 务实取向）。
- 公共 API 显式可见性。
- 每任务末尾提交（Conventional Commits），message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 工作分支：`feat/framework-buildout`。

---

### Task 1: 删除旧 http 包 + BaseContent 转 Kotlin

**Files:**
- Delete: `lib_common/src/main/java/com/btg/common/http/`（整个目录：`api/ApiRetrofit.java`、`api/ApiService.java`、`api/ApiDns.java`、`convert/*.java`×3、`cookie/*.java`×3、`gson/*.java`×4）
- Delete: `lib_common/src/main/java/com/btg/common/base/BaseContent.java`
- Create: `lib_common/src/main/java/com/btg/common/base/BaseContent.kt`

**Interfaces:**
- Consumes: 无。
- Produces: `object BaseContent { const val BASE_URL: String }`——供 `NetworkModule`（Task 4）取 baseUrl。
- 已核实：旧 http 包与 BaseContent 无任何外部引用（`grep` 仅 http 内部及 ApiRetrofit→BaseContent），删除安全。

- [ ] **Step 1: 删除旧 http 目录与 BaseContent.java**

```bash
git rm -r lib_common/src/main/java/com/btg/common/http
git rm lib_common/src/main/java/com/btg/common/base/BaseContent.java
```

- [ ] **Step 2: 新建 Kotlin 版 BaseContent**

创建 `lib_common/src/main/java/com/btg/common/base/BaseContent.kt`：

```kotlin
package com.btg.common.base

/** 全局常量。 */
object BaseContent {
    /** 网络请求默认 baseUrl（示范用聚合数据头条接口）。 */
    const val BASE_URL: String = "http://v.juhe.cn/toutiao"
}
```

- [ ] **Step 3: 编译验证（旧 http 删除后仍编译）**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`（旧 http 为死代码，删除不影响；新 network 核心逻辑已在 2a 落地）。

- [ ] **Step 4: 提交**

```bash
git add -A lib_common/src/main/java/com/btg/common/
git commit -m "refactor: remove dead legacy http package, convert BaseContent to Kotlin

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ApiDns + cookie 持久化转 Kotlin

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/ApiDns.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/cookie/OkHttpCookies.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/cookie/PersistentCookieStore.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/cookie/CookieManger.kt`

**Interfaces:**
- Consumes: OkHttp（`Dns`/`CookieJar`/`Cookie`/`HttpUrl`）、`Logger`、Android `Context`/`SharedPreferences`。
- Produces: `class ApiDns : Dns`；`class CookieManger(context) : CookieJar`（供 `NetworkModule` 注入）；`PersistentCookieStore`、`OkHttpCookies`（cookie 持久化到 SharedPreferences）。
- 说明：faithful 移植旧 Java 逻辑；修复旧 `add()` 缺失的 `apply()`（原代码写入未提交，cookie 实际未持久化）。

- [ ] **Step 1: 创建 ApiDns**

创建 `lib_common/src/main/java/com/btg/common/network/ApiDns.kt`：

```kotlin
package com.btg.common.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/** IPv4 排到 IPv6 前，缓解部分网络下请求慢的问题。 */
class ApiDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val ordered = ArrayList<InetAddress>()
            for (address in InetAddress.getAllByName(hostname)) {
                if (address is Inet4Address) ordered.add(0, address) else ordered.add(address)
            }
            ordered
        } catch (e: NullPointerException) {
            throw UnknownHostException("Broken system behaviour").apply { initCause(e) }
        }
    }
}
```

- [ ] **Step 2: 创建 OkHttpCookies**

创建 `lib_common/src/main/java/com/btg/common/network/cookie/OkHttpCookies.kt`：

```kotlin
package com.btg.common.network.cookie

import okhttp3.Cookie
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/** Cookie 的可序列化包装（手动读写字段，兼容 Java 序列化流）。 */
class OkHttpCookies(@Transient private val cookies: Cookie) : Serializable {

    @Transient
    private var clientCookies: Cookie? = null

    fun getCookies(): Cookie = clientCookies ?: cookies

    @Throws(IOException::class)
    private fun writeObject(out: ObjectOutputStream) {
        out.writeObject(cookies.name)
        out.writeObject(cookies.value)
        out.writeLong(cookies.expiresAt)
        out.writeObject(cookies.domain)
        out.writeObject(cookies.path)
        out.writeBoolean(cookies.secure)
        out.writeBoolean(cookies.httpOnly)
        out.writeBoolean(cookies.hostOnly)
        out.writeBoolean(cookies.persistent)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(input: ObjectInputStream) {
        val name = input.readObject() as String
        val value = input.readObject() as String
        val expiresAt = input.readLong()
        val domain = input.readObject() as String
        val path = input.readObject() as String
        val secure = input.readBoolean()
        val httpOnly = input.readBoolean()
        val hostOnly = input.readBoolean()
        input.readBoolean() // persistent：读出以对齐字节流，Cookie.Builder 无对应 setter

        var builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
        builder = if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        builder = builder.path(path)
        if (secure) builder = builder.secure()
        if (httpOnly) builder = builder.httpOnly()
        clientCookies = builder.build()
    }
}
```

- [ ] **Step 3: 创建 PersistentCookieStore**

创建 `lib_common/src/main/java/com/btg/common/network/cookie/PersistentCookieStore.kt`：

```kotlin
package com.btg.common.network.cookie

import android.content.Context
import android.text.TextUtils
import com.orhanobut.logger.Logger
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** cookie 持久化存储：内存缓存 + SharedPreferences 序列化。 */
class PersistentCookieStore(context: Context) {

    private val cookies = HashMap<String, ConcurrentHashMap<String, Cookie>>()
    private val cookiePrefs = context.getSharedPreferences(COOKIE_PREFS, Context.MODE_PRIVATE)

    init {
        for ((host, value) in cookiePrefs.all) {
            val names = TextUtils.split(value as String, ",")
            for (name in names) {
                val encoded = cookiePrefs.getString(name, null) ?: continue
                val decoded = decodeCookie(encoded) ?: continue
                cookies.getOrPut(host) { ConcurrentHashMap() }[name] = decoded
            }
        }
    }

    fun add(url: HttpUrl, cookie: Cookie) {
        val name = getCookieToken(cookie)
        if (!cookie.persistent) {
            cookies.getOrPut(url.host) { ConcurrentHashMap() }[name] = cookie
        } else {
            cookies[url.host]?.remove(name)
        }
        val keys = cookies[url.host]?.keys ?: emptySet<String>()
        cookiePrefs.edit()
            .putString(url.host, TextUtils.join(",", keys))
            .putString(name, encodeCookie(OkHttpCookies(cookie)))
            .apply()
    }

    fun get(url: HttpUrl): List<Cookie> = cookies[url.host]?.values?.toList() ?: emptyList()

    fun removeAll(): Boolean {
        cookiePrefs.edit().clear().apply()
        cookies.clear()
        return true
    }

    fun remove(url: HttpUrl, cookie: Cookie): Boolean {
        val name = getCookieToken(cookie)
        val hostCookies = cookies[url.host] ?: return false
        if (!hostCookies.containsKey(name)) return false
        hostCookies.remove(name)
        val editor = cookiePrefs.edit()
        if (cookiePrefs.contains(name)) editor.remove(name)
        editor.putString(url.host, TextUtils.join(",", hostCookies.keys)).apply()
        return true
    }

    private fun getCookieToken(cookie: Cookie): String = "${cookie.name}@${cookie.domain}"

    private fun encodeCookie(cookie: OkHttpCookies?): String? {
        if (cookie == null) return null
        val os = ByteArrayOutputStream()
        return try {
            ObjectOutputStream(os).use { it.writeObject(cookie) }
            byteArrayToHexString(os.toByteArray())
        } catch (e: IOException) {
            Logger.e(e, "IOException in encodeCookie")
            null
        }
    }

    private fun decodeCookie(cookieString: String): Cookie? {
        val bytes = hexStringToByteArray(cookieString)
        return try {
            ObjectInputStream(ByteArrayInputStream(bytes)).use {
                (it.readObject() as OkHttpCookies).getCookies()
            }
        } catch (e: IOException) {
            Logger.e(e, "IOException in decodeCookie")
            null
        } catch (e: ClassNotFoundException) {
            Logger.e(e, "ClassNotFoundException in decodeCookie")
            null
        }
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString().uppercase(Locale.US)
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private companion object {
        const val COOKIE_PREFS = "Cookies_prefs"
    }
}
```

- [ ] **Step 4: 创建 CookieManger**

创建 `lib_common/src/main/java/com/btg/common/network/cookie/CookieManger.kt`：

```kotlin
package com.btg.common.network.cookie

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** OkHttp CookieJar 实现，委托 [PersistentCookieStore] 做持久化。 */
class CookieManger(context: Context) : CookieJar {

    private val cookieStore = PersistentCookieStore(context)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            cookieStore.add(url, cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore.get(url)
}
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/ApiDns.kt lib_common/src/main/java/com/btg/common/network/cookie/
git commit -m "feat: port ApiDns and cookie persistence to Kotlin

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 拦截器体系（Header / Token）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/interceptor/HeaderInterceptor.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/interceptor/TokenInterceptor.kt`

**Interfaces:**
- Consumes: OkHttp `Interceptor`。
- Produces: `class HeaderInterceptor(headers: Map<String,String>)`；`fun interface TokenProvider { fun token(): String? }`；`class TokenInterceptor(tokenProvider, headerName, scheme)`——供 `NetworkModule` 装配。

- [ ] **Step 1: 创建 HeaderInterceptor**

创建 `lib_common/src/main/java/com/btg/common/network/interceptor/HeaderInterceptor.kt`：

```kotlin
package com.btg.common.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** 统一附加公共请求头。 */
class HeaderInterceptor(private val headers: Map<String, String> = emptyMap()) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}
```

- [ ] **Step 2: 创建 TokenInterceptor 与 TokenProvider**

创建 `lib_common/src/main/java/com/btg/common/network/interceptor/TokenInterceptor.kt`：

```kotlin
package com.btg.common.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Token 来源。项目接入真实 token（如从 DataStore 读）时提供实现。 */
fun interface TokenProvider {
    fun token(): String?
}

/** 有 token 时附加认证头（默认 Authorization: Bearer <token>）。 */
class TokenInterceptor(
    private val tokenProvider: TokenProvider,
    private val headerName: String = "Authorization",
    private val scheme: String = "Bearer",
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.token()
        val request = if (token.isNullOrEmpty()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header(headerName, "$scheme $token")
                .build()
        }
        return chain.proceed(request)
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/interceptor/
git commit -m "feat: add header and token interceptors with pluggable TokenProvider

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: logging-interceptor 依赖 + Hilt NetworkModule

**Files:**
- Modify: `gradle/libs.versions.toml`（`[libraries]` 加 `okhttp-logging`）
- Modify: `lib_opensource/build.gradle.kts`（`api(libs.okhttp.logging)`）
- Create: `lib_common/src/main/java/com/btg/common/network/NetworkModule.kt`

**Interfaces:**
- Consumes: `GsonFactory`（2a）、`CookieManger`/`ApiDns`（Task 2）、`HeaderInterceptor`/`TokenInterceptor`/`TokenProvider`（Task 3）、`BaseContent`（Task 1）、`com.btg.common.BuildConfig`、Hilt。
- Produces: Hilt `@Module NetworkModule`（`SingletonComponent`）`@Provides`：`Gson`、`TokenProvider`（默认返回 null 的骨架）、`CookieJar`、`OkHttpClient`、`Retrofit`。替代旧 `ApiRetrofit` 单例。

- [ ] **Step 1: catalog 加 logging-interceptor**

在 `gradle/libs.versions.toml` 的 `[libraries]` 块内，`okhttp = ...` 之后追加：

```toml
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
```

- [ ] **Step 2: lib_opensource 暴露 logging-interceptor**

在 `lib_opensource/build.gradle.kts` 的 `dependencies { }` 网络分组里，`api(libs.okhttp)` 之后追加：

```kotlin
    api(libs.okhttp.logging)
```

- [ ] **Step 3: 创建 NetworkModule**

创建 `lib_common/src/main/java/com/btg/common/network/NetworkModule.kt`：

```kotlin
package com.btg.common.network

import android.content.Context
import com.btg.common.BuildConfig
import com.btg.common.base.BaseContent
import com.btg.common.network.cookie.CookieManger
import com.btg.common.network.gson.GsonFactory
import com.btg.common.network.interceptor.HeaderInterceptor
import com.btg.common.network.interceptor.TokenInterceptor
import com.btg.common.network.interceptor.TokenProvider
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络依赖装配。替代旧的双重检查锁单例 ApiRetrofit。
 * 单 baseUrl 默认；如需多 baseUrl，用 @Qualifier 区分再各自 @Provides 一套 Retrofit。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonFactory.create()

    /** Token 来源骨架。TODO: 接真实来源（如从 DataStore 读），替换此默认实现。 */
    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = TokenProvider { null }

    @Provides
    @Singleton
    fun provideCookieJar(@ApplicationContext context: Context): CookieJar = CookieManger(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        tokenProvider: TokenProvider,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .dns(ApiDns())
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(HeaderInterceptor())
        .addInterceptor(TokenInterceptor(tokenProvider))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(BaseContent.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
```

- [ ] **Step 4: 编译验证（含 Hilt 处理）**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。Hilt 在 app 聚合处理 NetworkModule；未被注入的 `@Provides` 不报错。

- [ ] **Step 5: 提交**

```bash
git add gradle/libs.versions.toml lib_opensource/build.gradle.kts lib_common/src/main/java/com/btg/common/network/NetworkModule.kt
git commit -m "feat: add Hilt NetworkModule wiring okhttp/retrofit with interceptors, cookie, dns, logging

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: 下载 / 上传进度

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/download/DownloadState.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/download/FileDownloader.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/download/ProgressRequestBody.kt`

**Interfaces:**
- Consumes: `OkHttpClient`、`ExceptionHandler`、okio、协程 Flow。
- Produces:
  - `sealed interface DownloadState { Progress / Success / Failed }`
  - `class FileDownloader(client) { fun download(url, dest): Flow<DownloadState> }`
  - `class ProgressRequestBody(delegate, onProgress)`（上传进度包装）

- [ ] **Step 1: 创建 DownloadState**

创建 `lib_common/src/main/java/com/btg/common/network/download/DownloadState.kt`：

```kotlin
package com.btg.common.network.download

import com.btg.common.network.AppException
import java.io.File

/** 下载状态。percent 为 -1 表示总长度未知。 */
sealed interface DownloadState {
    data class Progress(val bytesRead: Long, val total: Long, val percent: Int) : DownloadState
    data class Success(val file: File) : DownloadState
    data class Failed(val error: AppException) : DownloadState
}
```

- [ ] **Step 2: 创建 FileDownloader**

创建 `lib_common/src/main/java/com/btg/common/network/download/FileDownloader.kt`：

```kotlin
package com.btg.common.network.download

import com.btg.common.network.ExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** 带进度的文件下载器。download 返回冷 Flow，收集时才真正下载，可随协程取消。 */
class FileDownloader(private val client: OkHttpClient) {

    fun download(url: String, dest: File): Flow<DownloadState> = flow {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw IOException("HTTP ${response.code}")
            }
            val total = body.contentLength()
            var bytesRead = 0L
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        val percent = if (total > 0) ((bytesRead * 100) / total).toInt() else -1
                        emit(DownloadState.Progress(bytesRead, total, percent))
                    }
                }
            }
            emit(DownloadState.Success(dest))
        }
    }.catch { e ->
        emit(DownloadState.Failed(ExceptionHandler.handle(e)))
    }.flowOn(Dispatchers.IO)
}
```

- [ ] **Step 3: 创建 ProgressRequestBody**

创建 `lib_common/src/main/java/com/btg/common/network/download/ProgressRequestBody.kt`：

```kotlin
package com.btg.common.network.download

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** 上传进度包装：包住原 RequestBody，写出时回调已写字节数。 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, total: Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val countingSink = object : ForwardingSink(sink) {
            private var written = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                onProgress(written, total)
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/download/
git commit -m "feat: add file download and upload with progress (Flow + okio)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: 网络状态监听

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/ConnectivityObserver.kt`

**Interfaces:**
- Consumes: Android `ConnectivityManager`、协程 `callbackFlow`。
- Produces: `class ConnectivityObserver(context) { fun observe(): Flow<Boolean>; fun isCurrentlyAvailable(): Boolean }`——true 表示有可用网络。

- [ ] **Step 1: 创建 ConnectivityObserver**

创建 `lib_common/src/main/java/com/btg/common/network/ConnectivityObserver.kt`：

```kotlin
package com.btg.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** 网络可用性监听，包成 Flow<Boolean>（true=有可用网络）。 */
class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        trySend(isCurrentlyAvailable())
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun isCurrentlyAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/ConnectivityObserver.kt
git commit -m "feat: add ConnectivityObserver exposing network availability as Flow

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: 全量编译 + 全部单测 + 真机冒烟

**Files:** 无改动（纯验证）。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 全部单元测试**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`；2a 网络核心测试 + 阶段 1 测试 + app 现有测试全部通过。

- [ ] **Step 3: 真机冒烟**

Run: `./gradlew installDebug`（需连接设备）
手动：启动 app，确认示范页正常、无崩溃（NetworkModule 已装配但示范仍走 Fake，不实际发网络请求）。
Expected: 正常启动、列表展示；Logcat 无 Hilt/NPE 崩溃。
（无设备时跳过并注明。）

- [ ] **Step 4: 收尾**

无代码改动则不提交，记录验证结果。

---

## Self-Review（对照 spec 第 4 节装配与 4.6 追加能力）

- ✅ 移除未接入的 `MyGsonConverterFactory`、空 `ApiService`、旧单例 `ApiRetrofit`（Task 1 整包删）。
- ✅ 保留并 Kotlin 化 cookie 三件套、`ApiDns`（并真正接进 OkHttp，Task 2 + Task 4）、gson 兜底（2a）、`BaseContent`（Task 1）。
- ✅ 拦截器：日志（NetworkModule 中 DEBUG 装配）、公共 Header、Token 注入骨架（Task 3 + Task 4）。
- ✅ 装配走 Hilt `NetworkModule`（Task 4）。
- ✅ 下载/上传进度（Task 5）、网络状态监听（Task 6）。
- 类型一致性：`GsonFactory.create()`（2a）、`CookieManger(context)`/`ApiDns()`（Task 2）、`HeaderInterceptor`/`TokenInterceptor`/`TokenProvider`（Task 3）、`BaseContent.BASE_URL`（Task 1）在 NetworkModule（Task 4）签名一致；`ExceptionHandler.handle`（2a）在 FileDownloader（Task 5）复用。
- 依赖：仅新增 `logging-interceptor`（复用 okhttp 版本），经 lib_opensource api 下发。
- 阶段边界：收官后除 `BaseApplication.java`（留阶段 5）外无业务 Java。
