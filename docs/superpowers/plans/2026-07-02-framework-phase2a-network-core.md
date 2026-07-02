# 框架能力补齐 · 阶段 2a（网络层核心逻辑）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在新包 `com.btg.common.network` 铺好网络层的纯逻辑核心——gson 空值兜底（Kotlin）、`BaseResponse`/`unwrap`、`AppException` 异常体系、`ExceptionHandler` 映射、`safeApiCall`；全部用单测覆盖。旧 `com.btg.common.http` 本阶段原样保留、保持编译（到 2b 再删）。

**Architecture:** 纯 Kotlin、可纯 JVM 测。新代码全部落在新包 `network`，不改动旧 `http`，因此本阶段是纯增量、零破坏。Gson 复用现有 4 个空值兜底适配器的逻辑（原样转写为 Kotlin），集中到 `GsonFactory` 供后续 `NetworkModule`（2b）与测试共用。

**Tech Stack:** Kotlin 2.0.21 / Gson 2.11 / Retrofit 2.11（`HttpException`）/ 协程 / JUnit4。

## Global Constraints

- 语言全 Kotlin；本阶段不删除、不修改任何现有 Java 文件（旧 http 到 2b 才删）。
- 新代码统一放包 `com.btg.common.network`（及子包 `network.gson`）。
- 不改 `minSdk`/`targetSdk`/`compileSdk`；不新增 catalog 条目（gson/retrofit 已在依赖，`logging-interceptor` 留到 2b）。
- 公共 API 显式可见性；`sealed` 表达有限状态，`when` 穷尽不写 else。
- 测试延续现状：`runTest` + 手写 fake，不引 mockk/mockito/Turbine/Robolectric。
- 每任务末尾提交（Conventional Commits），message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 工作分支：`feat/framework-buildout`。

---

### Task 1: gson 空值兜底适配器转 Kotlin + GsonFactory（TDD）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/gson/IntegerDefaultAdapter.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/gson/DoubleDefaultAdapter.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/gson/LongDefaultAdapter.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/gson/StringNullAdapter.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/gson/GsonFactory.kt`
- Create: `lib_common/src/test/java/com/btg/common/network/gson/GsonFactoryTest.kt`

**Interfaces:**
- Consumes: Gson（已由 lib_opensource api 暴露）。
- Produces: `object GsonFactory { fun create(): Gson }`——注册好 4 个兜底适配器的 Gson，供 2b 的 `NetworkModule` 复用；4 个适配器类（新包，与旧 http.gson 的 Java 版并存、不冲突）。

- [ ] **Step 1: 写失败测试**

创建 `lib_common/src/test/java/com/btg/common/network/gson/GsonFactoryTest.kt`：

```kotlin
package com.btg.common.network.gson

import org.junit.Assert.assertEquals
import org.junit.Test

class GsonFactoryTest {

    private data class Foo(val n: Int, val d: Double, val l: Long, val s: String)

    private val gson = GsonFactory.create()

    @Test
    fun `empty and null-string numbers default to zero`() {
        val foo = gson.fromJson("""{"n":"","d":"null","l":"","s":"x"}""", Foo::class.java)
        assertEquals(0, foo.n)
        assertEquals(0.0, foo.d, 0.0)
        assertEquals(0L, foo.l)
    }

    @Test
    fun `normal numbers parse correctly`() {
        val foo = gson.fromJson("""{"n":5,"d":1.5,"l":9,"s":"x"}""", Foo::class.java)
        assertEquals(5, foo.n)
        assertEquals(1.5, foo.d, 0.0)
        assertEquals(9L, foo.l)
    }

    @Test
    fun `string null literal becomes empty`() {
        val foo = gson.fromJson("""{"n":0,"d":0,"l":0,"s":null}""", Foo::class.java)
        assertEquals("", foo.s)
    }

    @Test
    fun `string "null" text becomes empty`() {
        val foo = gson.fromJson("""{"n":0,"d":0,"l":0,"s":"null"}""", Foo::class.java)
        assertEquals("", foo.s)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.gson.GsonFactoryTest"`
Expected: 编译失败，`unresolved reference: GsonFactory`。

- [ ] **Step 3: 创建 4 个适配器**

创建 `lib_common/src/main/java/com/btg/common/network/gson/IntegerDefaultAdapter.kt`：

```kotlin
package com.btg.common.network.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

/** 后台返回 "" 或 "null" 时兜底为 0。 */
class IntegerDefaultAdapter : JsonSerializer<Int>, JsonDeserializer<Int> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Int {
        try {
            val s = json.asString
            if (s == "" || s == "null") return 0
        } catch (ignore: Exception) {
        }
        return try {
            json.asInt
        } catch (e: NumberFormatException) {
            throw JsonSyntaxException(e)
        }
    }

    override fun serialize(src: Int, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src)
}
```

创建 `lib_common/src/main/java/com/btg/common/network/gson/DoubleDefaultAdapter.kt`：

```kotlin
package com.btg.common.network.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

/** 后台返回 "" 或 "null" 时兜底为 0.0。 */
class DoubleDefaultAdapter : JsonSerializer<Double>, JsonDeserializer<Double> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Double {
        try {
            val s = json.asString
            if (s == "" || s == "null") return 0.0
        } catch (ignore: Exception) {
        }
        return try {
            json.asDouble
        } catch (e: NumberFormatException) {
            throw JsonSyntaxException(e)
        }
    }

    override fun serialize(src: Double, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src)
}
```

创建 `lib_common/src/main/java/com/btg/common/network/gson/LongDefaultAdapter.kt`：

```kotlin
package com.btg.common.network.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

/** 后台返回 "" 或 "null" 时兜底为 0L。 */
class LongDefaultAdapter : JsonSerializer<Long>, JsonDeserializer<Long> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Long {
        try {
            val s = json.asString
            if (s == "" || s == "null") return 0L
        } catch (ignore: Exception) {
        }
        return try {
            json.asLong
        } catch (e: NumberFormatException) {
            throw JsonSyntaxException(e)
        }
    }

    override fun serialize(src: Long, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src)
}
```

创建 `lib_common/src/main/java/com/btg/common/network/gson/StringNullAdapter.kt`：

```kotlin
package com.btg.common.network.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/** 后台返回 null 或 "null" 字符串时兜底为空字符串。 */
class StringNullAdapter : TypeAdapter<String>() {
    override fun read(reader: JsonReader): String {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return ""
        }
        val jsonStr = reader.nextString()
        return if (jsonStr == "null") "" else jsonStr
    }

    override fun write(writer: JsonWriter, value: String?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.value(value)
    }
}
```

- [ ] **Step 4: 创建 GsonFactory**

创建 `lib_common/src/main/java/com/btg/common/network/gson/GsonFactory.kt`：

```kotlin
package com.btg.common.network.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * 统一构建注册了空值兜底适配器的 Gson。
 * 兜底规则：后台返回 "" / "null" / null 时，int→0、double→0.0、long→0L、String→""，
 * 避免单个脏字段导致整条解析失败。NetworkModule 与测试共用此工厂。
 */
object GsonFactory {
    fun create(): Gson = GsonBuilder()
        .registerTypeAdapter(Int::class.javaObjectType, IntegerDefaultAdapter())
        .registerTypeAdapter(Int::class.javaPrimitiveType, IntegerDefaultAdapter())
        .registerTypeAdapter(Double::class.javaObjectType, DoubleDefaultAdapter())
        .registerTypeAdapter(Double::class.javaPrimitiveType, DoubleDefaultAdapter())
        .registerTypeAdapter(Long::class.javaObjectType, LongDefaultAdapter())
        .registerTypeAdapter(Long::class.javaPrimitiveType, LongDefaultAdapter())
        .registerTypeAdapter(String::class.java, StringNullAdapter())
        .create()
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.gson.GsonFactoryTest"`
Expected: `BUILD SUCCESSFUL`，4 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/gson/ lib_common/src/test/java/com/btg/common/network/gson/
git commit -m "feat: port gson null-safety adapters to Kotlin with GsonFactory

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: BaseResponse + unwrap（TDD）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/BaseResponse.kt`（含 `AppException` 的前置引用；`AppException` 在 Task 3 创建）
- Create: `lib_common/src/test/java/com/btg/common/network/BaseResponseTest.kt`

> 注：`unwrap()` 抛出的是 `AppException.Business` / `AppException.Parse`，这两个类型在 Task 3 定义。因此**先做 Task 3 再做 Task 2 亦可**；本计划按"先建 AppException"的顺序执行——**实际执行时先做 Task 3，再回到 Task 2**。为避免顺序困惑，Task 2 与 Task 3 合并在同一提交周期：先建 AppException（Task 3 Step 3），再写 BaseResponse 与其测试。

- [ ] **Step 1: （见 Task 3）先完成 AppException 的创建**

先执行 Task 3 的 Step 1–3（写 ExceptionHandler 失败测试前，`AppException.kt` 已建）。确保 `com.btg.common.network.AppException` 存在后再继续本任务。

- [ ] **Step 2: 写 BaseResponse 失败测试**

创建 `lib_common/src/test/java/com/btg/common/network/BaseResponseTest.kt`：

```kotlin
package com.btg.common.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseResponseTest {

    @Test
    fun `unwrap returns data on success code`() {
        val resp = BaseResponse(code = 0, message = "ok", data = "payload")
        assertEquals("payload", resp.unwrap())
    }

    @Test
    fun `unwrap throws Business on non-success code`() {
        val resp = BaseResponse(code = 401, message = "未登录", data = null)
        val ex = assertThrows(AppException.Business::class.java) { resp.unwrap() }
        assertEquals(401, ex.code)
        assertEquals("未登录", ex.message)
    }

    @Test
    fun `unwrap throws Parse when success but data is null`() {
        val resp = BaseResponse<String>(code = 0, message = "ok", data = null)
        assertThrows(AppException.Parse::class.java) { resp.unwrap() }
    }

    @Test
    fun `unwrap honors custom success code`() {
        val resp = BaseResponse(code = 200, message = "ok", data = 42)
        assertEquals(42, resp.unwrap(successCode = 200))
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.BaseResponseTest"`
Expected: 编译失败，`unresolved reference: BaseResponse`。

- [ ] **Step 4: 实现 BaseResponse**

创建 `lib_common/src/main/java/com/btg/common/network/BaseResponse.kt`：

```kotlin
package com.btg.common.network

/** 业务成功码默认值。不同后端可在 unwrap 处传入实际成功码。 */
const val CODE_SUCCESS: Int = 0

/**
 * 统一响应包装。真实后端字段名不同（如 error_code/reason/result）时，
 * 在具体 data class 上用 @SerializedName 映射到 code/message/data。
 */
data class BaseResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?,
)

/**
 * 解包：业务码成功且 data 非空返回 data；业务码失败抛 [AppException.Business]；
 * 成功但 data 为空抛 [AppException.Parse]。
 */
fun <T> BaseResponse<T>.unwrap(successCode: Int = CODE_SUCCESS): T = when {
    code != successCode -> throw AppException.Business(code, message ?: "业务处理失败")
    data == null -> throw AppException.Parse("响应数据为空")
    else -> data
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.BaseResponseTest"`
Expected: `BUILD SUCCESSFUL`，4 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/BaseResponse.kt lib_common/src/test/java/com/btg/common/network/BaseResponseTest.kt
git commit -m "feat: add BaseResponse<T> and unwrap with business/parse errors

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: AppException 异常体系 + ExceptionHandler（TDD）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/AppException.kt`
- Create: `lib_common/src/main/java/com/btg/common/network/ExceptionHandler.kt`
- Create: `lib_common/src/test/java/com/btg/common/network/ExceptionHandlerTest.kt`

**Interfaces:**
- Consumes: `retrofit2.HttpException`、`okhttp3`（构造测试用 Response）、Gson `JsonSyntaxException`。
- Produces:
  - `sealed class AppException(message, cause)`：`Network`、`Timeout`、`Server(httpCode)`、`Business(code)`、`Parse`、`Unknown`。
  - `object ExceptionHandler { fun handle(throwable: Throwable): AppException }`——已是 `AppException` 原样返回，其余按类型映射为带友好文案的 `AppException`。
  - 说明：文案为框架默认（Kotlin 常量）。项目需本地化可再包一层；此处优先保证 ExceptionHandler 可纯 JVM 单测（spec 测试计划要求）。

- [ ] **Step 1: 创建 AppException**

创建 `lib_common/src/main/java/com/btg/common/network/AppException.kt`：

```kotlin
package com.btg.common.network

/**
 * 应用统一异常。数据层把各种底层异常映射到这里，UI 层拿到的都是带友好文案的 AppException。
 */
sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 无网络 / 连接失败。 */
    class Network(message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 请求超时。 */
    class Timeout(message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 服务器返回 HTTP 错误码（4xx/5xx）。 */
    class Server(val httpCode: Int, message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 业务码非成功（HTTP 200 但 body.code 表示失败）。 */
    class Business(val code: Int, message: String) : AppException(message)

    /** 解析失败 / 数据格式异常。 */
    class Parse(message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 其他未归类异常。 */
    class Unknown(message: String, cause: Throwable? = null) : AppException(message, cause)
}
```

- [ ] **Step 2: 写 ExceptionHandler 失败测试**

创建 `lib_common/src/test/java/com/btg/common/network/ExceptionHandlerTest.kt`：

```kotlin
package com.btg.common.network

import com.google.gson.JsonSyntaxException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ExceptionHandlerTest {

    @Test
    fun `unknown host maps to Network`() {
        val result = ExceptionHandler.handle(UnknownHostException("no dns"))
        assertTrue(result is AppException.Network)
    }

    @Test
    fun `socket timeout maps to Timeout`() {
        val result = ExceptionHandler.handle(SocketTimeoutException())
        assertTrue(result is AppException.Timeout)
    }

    @Test
    fun `http exception maps to Server with code`() {
        val body = "err".toResponseBody(null)
        val httpEx = HttpException(Response.error<Any>(503, body))
        val result = ExceptionHandler.handle(httpEx)
        assertTrue(result is AppException.Server)
        assertEquals(503, (result as AppException.Server).httpCode)
    }

    @Test
    fun `json syntax maps to Parse`() {
        val result = ExceptionHandler.handle(JsonSyntaxException("bad json"))
        assertTrue(result is AppException.Parse)
    }

    @Test
    fun `existing AppException is returned as-is`() {
        val original = AppException.Business(401, "未登录")
        assertSame(original, ExceptionHandler.handle(original))
    }

    @Test
    fun `other throwable maps to Unknown`() {
        val result = ExceptionHandler.handle(IllegalStateException("boom"))
        assertTrue(result is AppException.Unknown)
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.ExceptionHandlerTest"`
Expected: 编译失败，`unresolved reference: ExceptionHandler`。

- [ ] **Step 4: 实现 ExceptionHandler**

创建 `lib_common/src/main/java/com/btg/common/network/ExceptionHandler.kt`：

```kotlin
package com.btg.common.network

import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把各种底层 Throwable 映射为带友好文案的 [AppException]。
 * 已是 AppException（如 unwrap 抛出的 Business/Parse）则原样返回。
 * 文案为框架默认值；需本地化的项目可在此基础上再包一层。
 */
object ExceptionHandler {

    fun handle(throwable: Throwable): AppException = when (throwable) {
        is AppException -> throwable
        is SocketTimeoutException -> AppException.Timeout("网络请求超时，请稍后重试", throwable)
        is UnknownHostException, is ConnectException ->
            AppException.Network("网络连接失败，请检查网络", throwable)
        is HttpException -> AppException.Server(throwable.code(), "服务器异常(${throwable.code()})", throwable)
        is JsonSyntaxException, is JsonParseException, is JsonIOException ->
            AppException.Parse("数据解析失败", throwable)
        is IOException -> AppException.Network("网络异常，请检查网络", throwable)
        else -> AppException.Unknown(throwable.message ?: "未知错误", throwable)
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.ExceptionHandlerTest"`
Expected: `BUILD SUCCESSFUL`，6 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/AppException.kt lib_common/src/main/java/com/btg/common/network/ExceptionHandler.kt lib_common/src/test/java/com/btg/common/network/ExceptionHandlerTest.kt
git commit -m "feat: add AppException hierarchy and ExceptionHandler mapping

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

> 执行顺序提醒：先做 Task 3（建 AppException + ExceptionHandler），再做 Task 2（BaseResponse 依赖 AppException）。

---

### Task 4: safeApiCall（TDD）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/network/SafeApiCall.kt`
- Create: `lib_common/src/test/java/com/btg/common/network/SafeApiCallTest.kt`

**Interfaces:**
- Consumes: `ApiResult`（现有）、`ExceptionHandler`（Task 3）。
- Produces: `suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T>`——成功 → `ApiResult.Success`；抛异常 → `ApiResult.Error(ExceptionHandler.handle(e))`。

- [ ] **Step 1: 写失败测试**

创建 `lib_common/src/test/java/com/btg/common/network/SafeApiCallTest.kt`：

```kotlin
package com.btg.common.network

import com.btg.common.result.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class SafeApiCallTest {

    @Test
    fun `returns Success when block succeeds`() = runTest {
        val result = safeApiCall { "ok" }
        assertEquals(ApiResult.Success("ok"), result)
    }

    @Test
    fun `returns Error with mapped AppException when block throws`() = runTest {
        val result = safeApiCall { throw SocketTimeoutException() }
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).throwable is AppException.Timeout)
    }

    @Test
    fun `keeps business exception from unwrap`() = runTest {
        val result = safeApiCall { BaseResponse(code = 1, message = "失败", data = null).unwrap() }
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).throwable is AppException.Business)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.SafeApiCallTest"`
Expected: 编译失败，`unresolved reference: safeApiCall`。

- [ ] **Step 3: 实现 safeApiCall**

创建 `lib_common/src/main/java/com/btg/common/network/SafeApiCall.kt`：

```kotlin
package com.btg.common.network

import com.btg.common.result.ApiResult

/**
 * 统一安全调用：执行挂起 [block]，成功包成 [ApiResult.Success]，
 * 抛出的任何异常经 [ExceptionHandler] 映射为 [AppException] 后包成 [ApiResult.Error]。
 * Repository 一行调用：safeApiCall { api.xxx().unwrap() }。
 */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> =
    runCatching { block() }
        .fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = { ApiResult.Error(ExceptionHandler.handle(it)) },
        )
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.network.SafeApiCallTest"`
Expected: `BUILD SUCCESSFUL`，3 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/network/SafeApiCall.kt lib_common/src/test/java/com/btg/common/network/SafeApiCallTest.kt
git commit -m "feat: add safeApiCall wrapping results and mapping exceptions

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: 全量编译 + 全部单测回归

**Files:** 无改动（纯验证）。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`（旧 http 未动，新 network 增量编译）。

- [ ] **Step 2: 全部单元测试**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`；`GsonFactoryTest`(4) + `BaseResponseTest`(4) + `ExceptionHandlerTest`(6) + `SafeApiCallTest`(3) 与既有测试全部通过。

- [ ] **Step 3: 收尾**

无代码改动则不提交；记录验证结果。

---

## Self-Review（对照 spec 第 4 节网络层核心逻辑）

- ✅ gson 兜底适配器保留并转 Kotlin（Task 1，逻辑与旧 Java 一致）。
- ✅ `BaseResponse<T>` + `unwrap`（Task 2）。
- ✅ `AppException` 体系 + `ExceptionHandler`（Task 3）。
- ✅ `safeApiCall`（Task 4）。
- 依赖顺序：Task 3 先于 Task 2（BaseResponse.unwrap 依赖 AppException），计划已在 Task 2 顶部与 Task 3 尾部标注执行顺序。
- 类型一致性：`AppException.Business(code)`/`Parse` 在 BaseResponse.unwrap、ExceptionHandler、测试中签名一致；`ApiResult.Success/Error(throwable)` 与现有源一致；`GsonFactory.create()` 供 2b NetworkModule 复用。
- 阶段边界：纯增量，新包 `network`，不动旧 `http`、不删 Java、不引新依赖；拦截器/NetworkModule/cookie/dns/下载上传/网络监听/删除旧 http 全部留 2b。
