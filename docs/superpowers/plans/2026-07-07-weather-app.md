# module_weather 天气功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `module_weather` 从占位页落地为真实天气功能——聚合数据 query（实况+近5天）+ life（生活指数），首页背景随天气渐变 + 矢量插画，默认杭州、顶部可切换城市。

**Architecture:** 严格对齐 `module_news` 分层：`WeatherApi`(Retrofit) → `WeatherDataSource`(Remote/Fake 双实现) → `WeatherRepository`(唯一入口，`safeApiCall`) → `WeatherViewModel`(`@HiltViewModel`, `StateFlow`) → `WeatherFragment`(`StateLayout` 四态 + `SwipeRefreshLayout`)。Hilt(KSP) 装配，独立 `@WeatherJuheRetrofit`(baseUrl `apis.juhe.cn`)。

**Tech Stack:** Kotlin 2.0.21、Hilt 2.52、Retrofit 2.11、Gson、Coroutines/Flow、DataStore、ViewBinding、Navigation。测试：JUnit4 + kotlinx-coroutines-test + 手写 fake（不引 mockk/Robolectric/Turbine）。

## Global Constraints

- 全 Kotlin，遵循 kotlinlang 编码规范；公共 API 显式可见性；优先 `val`、空安全 `?./?:`，避免 `!!`。
- 不新增第三方依赖、不改 `libs.versions.toml` 版本、不改 SDK 版本、不动 AndroidManifest 权限。仅在 `module_weather/build.gradle.kts` 启用已有的 hilt/ksp/buildConfig。
- SDK：compileSdk 35 / minSdk 24；JDK 17。namespace `com.btg.weather`。
- API key 走 `local.properties` 的 `JUHE_API_KEY` → `BuildConfig`，不入库。
- 资源（字符串/颜色/尺寸）放 `res`，不硬编码进代码或布局。
- 日志用 `com.orhanobut.logger.Logger`，不用裸 `android.util.Log`。
- baseUrl：weather = `https://apis.juhe.cn/`（与 news 的 `v.juhe.cn` 不同，必须独立 Retrofit + Qualifier）。
- 聚合响应外壳 `JuheResponse` 在本模块内自持一份（方案 A，不跨模块、不上提 lib_common）。
- 提交用 Conventional Commits 祈使句；不 push、不改 CI。

---

### Task 1: 模块构建配置 + JuheResponse 外壳

**Files:**
- Modify: `module_weather/build.gradle.kts`（整体重写）
- Create: `module_weather/src/main/java/com/btg/weather/data/source/JuheResponse.kt`
- Test: `module_weather/src/test/java/com/btg/weather/data/source/JuheResponseTest.kt`

**Interfaces:**
- Consumes: `com.btg.common.network.AppException`（lib_common）
- Produces: `data class JuheResponse<T>(errorCode, reason, result)`；`fun <T> JuheResponse<T>.unwrap(): T`

- [ ] **Step 1: 写失败测试**

`module_weather/src/test/java/com/btg/weather/data/source/JuheResponseTest.kt`:

```kotlin
package com.btg.weather.data.source

import com.btg.common.network.AppException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JuheResponseTest {

    @Test
    fun `unwrap returns result on success`() {
        val resp = JuheResponse(errorCode = 0, reason = "查询成功", result = "ok")
        assertEquals("ok", resp.unwrap())
    }

    @Test
    fun `unwrap throws Business with code and reason on error_code non-zero`() {
        val resp = JuheResponse(errorCode = 207301, reason = "错误的查询城市名", result = null)
        val ex = assertThrows(AppException.Business::class.java) { resp.unwrap() }
        assertEquals(207301, ex.code)
        assertEquals("错误的查询城市名", ex.message)
    }

    @Test
    fun `unwrap throws Parse when result null but code success`() {
        val resp = JuheResponse<String>(errorCode = 0, reason = "查询成功", result = null)
        assertThrows(AppException.Parse::class.java) { resp.unwrap() }
    }
}
```

- [ ] **Step 2: 重写 build.gradle.kts（照搬 news 模块模式）**

`module_weather/build.gradle.kts`:

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
    namespace = "com.btg.weather"
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

dependencies {
    implementation(project(":lib_common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 3: 创建 JuheResponse.kt**

`module_weather/src/main/java/com/btg/weather/data/source/JuheResponse.kt`:

```kotlin
package com.btg.weather.data.source

import com.btg.common.network.AppException
import com.google.gson.annotations.SerializedName

/** 聚合数据统一响应外壳：{ error_code, reason, result }，error_code == 0 为成功。 */
data class JuheResponse<T>(
    @SerializedName("error_code") val errorCode: Int,
    val reason: String?,
    val result: T?,
)

/** 解包：成功返回 result；业务失败抛 Business（携带聚合错误码与 reason）；成功但 result 为空抛 Parse。 */
fun <T> JuheResponse<T>.unwrap(): T = when {
    errorCode != 0 -> throw AppException.Business(errorCode, reason ?: "业务处理失败")
    result == null -> throw AppException.Parse("响应数据为空")
    else -> result
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.data.source.JuheResponseTest"`
Expected: PASS（3 个用例）

- [ ] **Step 5: 提交**

```bash
git add module_weather/build.gradle.kts module_weather/src/main/java/com/btg/weather/data/source/JuheResponse.kt module_weather/src/test/java/com/btg/weather/data/source/JuheResponseTest.kt
git commit -m "feat(weather): enable hilt/buildConfig and add JuheResponse wrapper"
```

---

### Task 2: WeatherCategory 天气档位 + wid 映射

**Files:**
- Create: `module_weather/src/main/java/com/btg/weather/data/model/WeatherCategory.kt`
- Test: `module_weather/src/test/java/com/btg/weather/data/model/WeatherCategoryTest.kt`

**Interfaces:**
- Produces: `enum class WeatherCategory { CLEAR, CLOUDY, RAIN, SNOW, FOG }`；`fun WeatherCategory.Companion.fromWid(wid: String?): WeatherCategory`（纯函数，未知/空 → `CLOUDY`）。**枚举保持纯净不含 Android 资源 id**，档位→drawable 的映射放 UI 层（Task 9）。

- [ ] **Step 1: 写失败测试**

`module_weather/src/test/java/com/btg/weather/data/model/WeatherCategoryTest.kt`:

```kotlin
package com.btg.weather.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCategoryTest {

    @Test
    fun `clear wid maps to CLEAR`() {
        assertEquals(WeatherCategory.CLEAR, WeatherCategory.fromWid("00"))
    }

    @Test
    fun `cloudy and overcast map to CLOUDY`() {
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid("01"))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid("02"))
    }

    @Test
    fun `rain family maps to RAIN`() {
        listOf("03", "04", "05", "07", "10", "12", "19", "21", "25").forEach {
            assertEquals("wid=$it", WeatherCategory.RAIN, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `snow family maps to SNOW`() {
        listOf("06", "13", "16", "17", "26", "28").forEach {
            assertEquals("wid=$it", WeatherCategory.SNOW, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `fog dust haze map to FOG`() {
        listOf("18", "20", "29", "30", "31", "53").forEach {
            assertEquals("wid=$it", WeatherCategory.FOG, WeatherCategory.fromWid(it))
        }
    }

    @Test
    fun `null blank and unknown fall back to CLOUDY`() {
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid(null))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid(""))
        assertEquals(WeatherCategory.CLOUDY, WeatherCategory.fromWid("99"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.data.model.WeatherCategoryTest"`
Expected: FAIL（编译失败：`WeatherCategory` 未定义）

- [ ] **Step 3: 实现 WeatherCategory**

`module_weather/src/main/java/com/btg/weather/data/model/WeatherCategory.kt`:

```kotlin
package com.btg.weather.data.model

/**
 * 天气档位：把聚合接口 30+ 个 wid 归成 5 档，用于选背景渐变与矢量插画。
 * 保持纯净——不含 Android 资源 id，档位→资源映射在 UI 层。wid 表见天气种类接口文档。
 */
enum class WeatherCategory {
    CLEAR, CLOUDY, RAIN, SNOW, FOG;

    companion object {
        fun fromWid(wid: String?): WeatherCategory = when (wid?.trim()) {
            "00" -> CLEAR
            "01", "02" -> CLOUDY
            "03", "04", "05", "07", "08", "09", "10", "11", "12",
            "19", "21", "22", "23", "24", "25" -> RAIN
            "06", "13", "14", "15", "16", "17", "26", "27", "28" -> SNOW
            "18", "20", "29", "30", "31", "53" -> FOG
            else -> CLOUDY
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.data.model.WeatherCategoryTest"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add module_weather/src/main/java/com/btg/weather/data/model/WeatherCategory.kt module_weather/src/test/java/com/btg/weather/data/model/WeatherCategoryTest.kt
git commit -m "feat(weather): add WeatherCategory with wid mapping"
```

---

### Task 3: 领域模型 + DTO + 映射

**Files:**
- Create: `module_weather/src/main/java/com/btg/weather/data/model/Weather.kt`（领域模型）
- Create: `module_weather/src/main/java/com/btg/weather/data/source/WeatherDto.kt`（DTO + 映射）
- Test: `module_weather/src/test/java/com/btg/weather/data/source/WeatherDtoTest.kt`

**Interfaces:**
- Consumes: `WeatherCategory.fromWid`（Task 2）；`AppException`（lib_common）
- Produces:
  - `data class RealtimeWeather(info, category, temperature, humidity, direct, power, aqi)` 全 String 除 `category: WeatherCategory`
  - `data class ForecastDay(date, temperature, weather, category, direct)`（`category: WeatherCategory`，其余 String）
  - `data class LifeIndex(name: String, level: String, desc: String)`
  - `data class WeatherOverview(city, realtime, future, life)`：`realtime: RealtimeWeather`、`future: List<ForecastDay>`、`life: List<LifeIndex>`
  - `data class WeatherData(city: String, realtime: RealtimeWeather, future: List<ForecastDay>)`（数据源层载体）
  - `fun WeatherQueryResult.toWeatherData(): WeatherData`（realtime 为 null 抛 `AppException.Parse`）
  - `fun WeatherLifeResult.toLifeList(): List<LifeIndex>`

- [ ] **Step 1: 写失败测试**

`module_weather/src/test/java/com/btg/weather/data/source/WeatherDtoTest.kt`:

```kotlin
package com.btg.weather.data.source

import com.btg.common.network.AppException
import com.btg.weather.data.model.WeatherCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDtoTest {

    @Test
    fun `query result maps to domain with category from wid`() {
        val result = WeatherQueryResult(
            city = "苏州",
            realtime = RealtimeDto("阴", "02", "4", "82", "西北风", "3级", "80"),
            future = listOf(
                FutureDto("2019-02-22", "1/7℃", "小雨转多云", FutureWidDto("07", "01"), "北风转西北风"),
            ),
        )

        val data = result.toWeatherData()

        assertEquals("苏州", data.city)
        assertEquals("阴", data.realtime.info)
        assertEquals(WeatherCategory.CLOUDY, data.realtime.category)
        assertEquals("80", data.realtime.aqi)
        assertEquals(1, data.future.size)
        assertEquals("1/7℃", data.future[0].temperature)
        // future 档位取白天 wid=07 → RAIN
        assertEquals(WeatherCategory.RAIN, data.future[0].category)
    }

    @Test
    fun `query result with null realtime throws Parse`() {
        val result = WeatherQueryResult(city = "苏州", realtime = null, future = emptyList())
        assertThrows(AppException.Parse::class.java) { result.toWeatherData() }
    }

    @Test
    fun `null fields fall back to empty string`() {
        val result = WeatherQueryResult(
            city = null,
            realtime = RealtimeDto(null, null, null, null, null, null, null),
            future = null,
        )
        val data = result.toWeatherData()
        assertEquals("", data.city)
        assertEquals("", data.realtime.info)
        assertEquals(WeatherCategory.CLOUDY, data.realtime.category) // null wid 兜底
        assertTrue(data.future.isEmpty())
    }

    @Test
    fun `life result maps known keys in fixed order skipping blanks`() {
        val result = WeatherLifeResult(
            city = "北京",
            life = mapOf(
                "chuanyi" to LifeItemDto("冷", "天气冷，建议着棉服。"),
                "ziwaixian" to LifeItemDto("弱", "紫外线强度较弱。"),
                "diaoyu" to LifeItemDto(null, null), // v 空 → 跳过
                "unknownKey" to LifeItemDto("x", "y"), // 未知键 → 跳过
            ),
        )

        val list = result.toLifeList()

        assertEquals(2, list.size)
        // 固定展示顺序：穿衣在紫外线之前
        assertEquals("穿衣", list[0].name)
        assertEquals("冷", list[0].level)
        assertEquals("紫外线", list[1].name)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.data.source.WeatherDtoTest"`
Expected: FAIL（编译失败：类型未定义）

- [ ] **Step 3: 创建领域模型**

`module_weather/src/main/java/com/btg/weather/data/model/Weather.kt`:

```kotlin
package com.btg.weather.data.model

/** 实况天气。category 用于选背景渐变/插画；其余为展示文案（可能为空串）。 */
data class RealtimeWeather(
    val info: String,
    val category: WeatherCategory,
    val temperature: String,
    val humidity: String,
    val direct: String,
    val power: String,
    val aqi: String,
)

/** 近 5 天单日预报。category 取白天 wid。 */
data class ForecastDay(
    val date: String,
    val temperature: String,
    val weather: String,
    val category: WeatherCategory,
    val direct: String,
)

/** 生活指数单项：name 展示名（如「穿衣」），level 档位(v)，desc 详情(des)。 */
data class LifeIndex(
    val name: String,
    val level: String,
    val desc: String,
)

/** 天气总览：一次加载的完整领域模型。life 可能为空（生活指数接口失败时）。 */
data class WeatherOverview(
    val city: String,
    val realtime: RealtimeWeather,
    val future: List<ForecastDay>,
    val life: List<LifeIndex>,
)

/** 数据源层载体：query 接口解出的天气部分（不含 life）。city 取接口 result.city。 */
data class WeatherData(
    val city: String,
    val realtime: RealtimeWeather,
    val future: List<ForecastDay>,
)
```

- [ ] **Step 4: 创建 DTO + 映射**

`module_weather/src/main/java/com/btg/weather/data/source/WeatherDto.kt`:

```kotlin
package com.btg.weather.data.source

import com.btg.common.network.AppException
import com.btg.weather.data.model.ForecastDay
import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.RealtimeWeather
import com.btg.weather.data.model.WeatherCategory
import com.btg.weather.data.model.WeatherData

/** query 接口 result：{ city, realtime, future }。 */
data class WeatherQueryResult(
    val city: String?,
    val realtime: RealtimeDto?,
    val future: List<FutureDto>?,
)

data class RealtimeDto(
    val info: String?,
    val wid: String?,
    val temperature: String?,
    val humidity: String?,
    val direct: String?,
    val power: String?,
    val aqi: String?,
)

data class FutureDto(
    val date: String?,
    val temperature: String?,
    val weather: String?,
    /** future 的 wid 是对象 {day, night}，与 realtime 的 wid(字符串) 不同。 */
    val wid: FutureWidDto?,
    val direct: String?,
)

data class FutureWidDto(
    val day: String?,
    val night: String?,
)

/** life 接口 result：{ city, life: { chuanyi:{v,des}, ... } }。 */
data class WeatherLifeResult(
    val city: String?,
    val life: Map<String, LifeItemDto>?,
)

data class LifeItemDto(
    val v: String?,
    val des: String?,
)

/** 生活指数键 → 中文展示名，兼作固定展示顺序。 */
private val LIFE_NAMES: Map<String, String> = linkedMapOf(
    "chuanyi" to "穿衣",
    "ziwaixian" to "紫外线",
    "ganmao" to "感冒",
    "yundong" to "运动",
    "xiche" to "洗车",
    "daisan" to "带伞",
    "shushidu" to "舒适度",
    "guomin" to "过敏",
    "kongtiao" to "空调",
    "diaoyu" to "钓鱼",
)

fun WeatherQueryResult.toWeatherData(): WeatherData {
    val rt = realtime ?: throw AppException.Parse("天气实况数据为空")
    return WeatherData(
        city = city.orEmpty(),
        realtime = RealtimeWeather(
            info = rt.info.orEmpty(),
            category = WeatherCategory.fromWid(rt.wid),
            temperature = rt.temperature.orEmpty(),
            humidity = rt.humidity.orEmpty(),
            direct = rt.direct.orEmpty(),
            power = rt.power.orEmpty(),
            aqi = rt.aqi.orEmpty(),
        ),
        future = future.orEmpty().map { it.toModel() },
    )
}

private fun FutureDto.toModel(): ForecastDay = ForecastDay(
    date = date.orEmpty(),
    temperature = temperature.orEmpty(),
    weather = weather.orEmpty(),
    category = WeatherCategory.fromWid(wid?.day),
    direct = direct.orEmpty(),
)

/** 按 LIFE_NAMES 固定顺序输出；v 为空或键未知则跳过。 */
fun WeatherLifeResult.toLifeList(): List<LifeIndex> {
    val source = life ?: return emptyList()
    return LIFE_NAMES.mapNotNull { (key, name) ->
        val item = source[key] ?: return@mapNotNull null
        val level = item.v?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        LifeIndex(name = name, level = level, desc = item.des.orEmpty())
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.data.source.WeatherDtoTest"`
Expected: PASS（4 个用例）

- [ ] **Step 6: 提交**

```bash
git add module_weather/src/main/java/com/btg/weather/data/model/Weather.kt module_weather/src/main/java/com/btg/weather/data/source/WeatherDto.kt module_weather/src/test/java/com/btg/weather/data/source/WeatherDtoTest.kt
git commit -m "feat(weather): add domain models, DTOs and mappers"
```

---

### Task 4: WeatherApi + 数据源（接口 + Remote + Fake）

**Files:**
- Create: `module_weather/src/main/java/com/btg/weather/data/source/WeatherApi.kt`
- Create: `module_weather/src/main/java/com/btg/weather/data/source/WeatherDataSource.kt`
- Create: `module_weather/src/main/java/com/btg/weather/data/source/RemoteWeatherDataSource.kt`
- Create: `module_weather/src/main/java/com/btg/weather/data/source/FakeWeatherDataSource.kt`

**Interfaces:**
- Consumes: `JuheResponse.unwrap`（Task 1）、`toWeatherData`/`toLifeList`（Task 3）、`WeatherData`/`LifeIndex`/`RealtimeWeather`/`ForecastDay`/`WeatherCategory`
- Produces:
  - `interface WeatherApi { suspend fun query(key, city): JuheResponse<WeatherQueryResult>; suspend fun life(key, city): JuheResponse<WeatherLifeResult> }`
  - `interface WeatherDataSource { suspend fun fetchWeather(city: String): WeatherData; suspend fun fetchLife(city: String): List<LifeIndex> }`
  - `class RemoteWeatherDataSource(api: WeatherApi, apiKey: String) : WeatherDataSource`
  - `class FakeWeatherDataSource(failWeather: Boolean = false, failLife: Boolean = false) : WeatherDataSource`

> 本任务多为接口/装配代码，其测试价值由 Task 5（Repository）承载，这里不单独写测试。

- [ ] **Step 1: 创建 WeatherApi**

`module_weather/src/main/java/com/btg/weather/data/source/WeatherApi.kt`:

```kotlin
package com.btg.weather.data.source

import retrofit2.http.GET
import retrofit2.http.Query

/** 聚合数据「天气预报」接口。baseUrl = https://apis.juhe.cn/ */
interface WeatherApi {

    @GET("simpleWeather/query")
    suspend fun query(
        @Query("key") key: String,
        @Query("city") city: String,
    ): JuheResponse<WeatherQueryResult>

    @GET("simpleWeather/life")
    suspend fun life(
        @Query("key") key: String,
        @Query("city") city: String,
    ): JuheResponse<WeatherLifeResult>
}
```

- [ ] **Step 2: 创建 WeatherDataSource 接口**

`module_weather/src/main/java/com/btg/weather/data/source/WeatherDataSource.kt`:

```kotlin
package com.btg.weather.data.source

import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.WeatherData

/** 天气数据源统一入口。实现失败时抛异常，由 Repository 经 safeApiCall 捕获包装。 */
interface WeatherDataSource {
    suspend fun fetchWeather(city: String): WeatherData
    suspend fun fetchLife(city: String): List<LifeIndex>
}
```

- [ ] **Step 3: 创建 RemoteWeatherDataSource**

`module_weather/src/main/java/com/btg/weather/data/source/RemoteWeatherDataSource.kt`:

```kotlin
package com.btg.weather.data.source

import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.WeatherData

/** 真实网络数据源：调用聚合数据接口并映射为领域模型。 */
class RemoteWeatherDataSource(
    private val api: WeatherApi,
    private val apiKey: String,
) : WeatherDataSource {

    override suspend fun fetchWeather(city: String): WeatherData =
        api.query(apiKey, city).unwrap().toWeatherData()

    override suspend fun fetchLife(city: String): List<LifeIndex> =
        api.life(apiKey, city).unwrap().toLifeList()
}
```

- [ ] **Step 4: 创建 FakeWeatherDataSource**

`module_weather/src/main/java/com/btg/weather/data/source/FakeWeatherDataSource.kt`:

```kotlin
package com.btg.weather.data.source

import com.btg.common.network.AppException
import com.btg.weather.data.model.ForecastDay
import com.btg.weather.data.model.LifeIndex
import com.btg.weather.data.model.RealtimeWeather
import com.btg.weather.data.model.WeatherCategory
import com.btg.weather.data.model.WeatherData
import kotlinx.coroutines.delay

/**
 * 假数据源：无 key 时演示 / 单测用。
 * failWeather / failLife 用于测试异常分支（天气失败、生活指数失败）。
 */
class FakeWeatherDataSource(
    private val failWeather: Boolean = false,
    private val failLife: Boolean = false,
) : WeatherDataSource {

    override suspend fun fetchWeather(city: String): WeatherData {
        delay(300)
        if (failWeather) throw AppException.Business(207302, "查询不到该城市的相关信息")
        return WeatherData(
            city = city,
            realtime = RealtimeWeather(
                info = "多云", category = WeatherCategory.CLOUDY,
                temperature = "22", humidity = "60", direct = "东南风", power = "3级", aqi = "75",
            ),
            future = (0 until 5).map { i ->
                ForecastDay(
                    date = "2026-07-0${i + 7}",
                    temperature = "${20 + i}/${28 + i}℃",
                    weather = "多云",
                    category = WeatherCategory.CLOUDY,
                    direct = "东南风",
                )
            },
        )
    }

    override suspend fun fetchLife(city: String): List<LifeIndex> {
        delay(150)
        if (failLife) throw AppException.Network("网络错误，请重试")
        return listOf(
            LifeIndex("穿衣", "舒适", "建议着薄外套或牛仔裤等服装。"),
            LifeIndex("紫外线", "中等", "外出时建议涂擦防晒霜。"),
            LifeIndex("运动", "较适宜", "较适宜进行户外运动。"),
            LifeIndex("洗车", "适宜", "适宜洗车，未来一天无雨。"),
        )
    }
}
```

- [ ] **Step 5: 编译确认通过**

Run: `./gradlew :module_weather:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add module_weather/src/main/java/com/btg/weather/data/source/WeatherApi.kt module_weather/src/main/java/com/btg/weather/data/source/WeatherDataSource.kt module_weather/src/main/java/com/btg/weather/data/source/RemoteWeatherDataSource.kt module_weather/src/main/java/com/btg/weather/data/source/FakeWeatherDataSource.kt
git commit -m "feat(weather): add WeatherApi and remote/fake data sources"
```

---

### Task 5: WeatherRepository

**Files:**
- Create: `module_weather/src/main/java/com/btg/weather/data/repository/WeatherRepository.kt`
- Test: `module_weather/src/test/java/com/btg/weather/data/repository/WeatherRepositoryTest.kt`

**Interfaces:**
- Consumes: `WeatherDataSource`、`FakeWeatherDataSource`、`WeatherOverview`、`safeApiCall`、`ApiResult`
- Produces: `class WeatherRepository(dataSource: WeatherDataSource, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`；`suspend fun getWeatherOverview(city: String): ApiResult<WeatherOverview>`

- [ ] **Step 1: 写失败测试**

`module_weather/src/test/java/com/btg/weather/data/repository/WeatherRepositoryTest.kt`:

```kotlin
package com.btg.weather.data.repository

import com.btg.common.result.ApiResult
import com.btg.weather.data.source.FakeWeatherDataSource
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `getWeatherOverview returns Success with realtime future and life`() = runTest {
        val repo = WeatherRepository(FakeWeatherDataSource(), dispatcher)

        val result = repo.getWeatherOverview("杭州")

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("杭州", data.city)
        assertEquals("多云", data.realtime.info)
        assertEquals(5, data.future.size)
        assertEquals(4, data.life.size)
    }

    @Test
    fun `life failure still returns Success with empty life`() = runTest {
        val repo = WeatherRepository(FakeWeatherDataSource(failLife = true), dispatcher)

        val result = repo.getWeatherOverview("杭州")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.life.isEmpty())
    }

    @Test
    fun `weather failure returns Error`() = runTest {
        val repo = WeatherRepository(FakeWeatherDataSource(failWeather = true), dispatcher)

        val result = repo.getWeatherOverview("火星")

        assertTrue(result is ApiResult.Error)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.data.repository.WeatherRepositoryTest"`
Expected: FAIL（编译失败：`WeatherRepository` 未定义）

- [ ] **Step 3: 实现 WeatherRepository**

`module_weather/src/main/java/com/btg/weather/data/repository/WeatherRepository.kt`:

```kotlin
package com.btg.weather.data.repository

import com.btg.common.network.safeApiCall
import com.btg.common.result.ApiResult
import com.btg.weather.data.model.WeatherOverview
import com.btg.weather.data.source.WeatherDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 天气数据唯一入口。query（实况+预报）为必需，life（生活指数）为次要：
 * life 失败则吞成空列表，不阻塞主天气展示。
 */
class WeatherRepository(
    private val dataSource: WeatherDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getWeatherOverview(city: String): ApiResult<WeatherOverview> =
        withContext(ioDispatcher) {
            safeApiCall {
                val weather = dataSource.fetchWeather(city)
                val life = runCatching { dataSource.fetchLife(city) }.getOrDefault(emptyList())
                WeatherOverview(
                    city = weather.city,
                    realtime = weather.realtime,
                    future = weather.future,
                    life = life,
                )
            }
        }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.data.repository.WeatherRepositoryTest"`
Expected: PASS（3 个用例）

- [ ] **Step 5: 提交**

```bash
git add module_weather/src/main/java/com/btg/weather/data/repository/WeatherRepository.kt module_weather/src/test/java/com/btg/weather/data/repository/WeatherRepositoryTest.kt
git commit -m "feat(weather): add WeatherRepository combining weather and life"
```

---

### Task 6: CityStore（城市持久化）+ WeatherModule（DI）

**Files:**
- Create: `module_weather/src/main/java/com/btg/weather/data/CityStore.kt`
- Create: `module_weather/src/main/java/com/btg/weather/di/WeatherModule.kt`

**Interfaces:**
- Consumes: `PreferenceStore`（lib_common）、`OkHttpClient`/`Gson`（NetworkModule 提供）、`WeatherApi`/`WeatherDataSource`/`RemoteWeatherDataSource`/`WeatherRepository`、`BuildConfig.JUHE_API_KEY`
- Produces:
  - `interface CityStore { val currentCity: Flow<String>; suspend fun save(city: String) }`
  - `class DataStoreCityStore(store: PreferenceStore) : CityStore`
  - `@Qualifier annotation class WeatherJuheRetrofit`
  - Hilt `object WeatherModule` 提供 Retrofit / WeatherApi / WeatherDataSource / WeatherRepository / CityStore

> DataStore/Hilt 属 Android 装配，靠 Task 11 编译 + 手动验证；不写 JVM 单测。

- [ ] **Step 1: 创建 CityStore**

`module_weather/src/main/java/com/btg/weather/data/CityStore.kt`:

```kotlin
package com.btg.weather.data

import com.btg.common.storage.PreferenceStore
import kotlinx.coroutines.flow.Flow

/** 选定城市存取抽象：便于测试注入 fake。空串表示未选择过。 */
interface CityStore {
    val currentCity: Flow<String>
    suspend fun save(city: String)
}

/** DataStore 实现：选定城市持久化，冷启动自动恢复。 */
class DataStoreCityStore(private val store: PreferenceStore) : CityStore {

    override val currentCity: Flow<String> = store.getString(KEY_CITY, "")

    override suspend fun save(city: String) {
        store.putString(KEY_CITY, city)
    }

    private companion object {
        const val KEY_CITY = "selected_city"
    }
}
```

- [ ] **Step 2: 创建 WeatherModule**

`module_weather/src/main/java/com/btg/weather/di/WeatherModule.kt`:

```kotlin
package com.btg.weather.di

import android.content.Context
import com.btg.common.storage.PreferenceStore
import com.btg.weather.BuildConfig
import com.btg.weather.data.CityStore
import com.btg.weather.data.DataStoreCityStore
import com.btg.weather.data.repository.WeatherRepository
import com.btg.weather.data.source.RemoteWeatherDataSource
import com.btg.weather.data.source.WeatherApi
import com.btg.weather.data.source.WeatherDataSource
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 天气聚合数据专用 Retrofit 限定符（baseUrl apis.juhe.cn，与 news 的 v.juhe.cn 不同）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WeatherJuheRetrofit

/**
 * 天气数据装配。数据源唯一装配点：无 key 调试时把 RemoteWeatherDataSource
 * 换回 FakeWeatherDataSource()，上层不动。
 */
@Module
@InstallIn(SingletonComponent::class)
object WeatherModule {

    private const val WEATHER_BASE_URL = "https://apis.juhe.cn/"

    @Provides
    @Singleton
    @WeatherJuheRetrofit
    fun provideWeatherRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(WEATHER_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideWeatherApi(@WeatherJuheRetrofit retrofit: Retrofit): WeatherApi =
        retrofit.create(WeatherApi::class.java)

    @Provides
    @Singleton
    fun provideWeatherDataSource(api: WeatherApi): WeatherDataSource =
        RemoteWeatherDataSource(api, BuildConfig.JUHE_API_KEY)

    @Provides
    @Singleton
    fun provideWeatherRepository(dataSource: WeatherDataSource): WeatherRepository =
        WeatherRepository(dataSource)

    /** DataStore 同名文件只能有一个实例，必须单例提供。 */
    @Provides
    @Singleton
    fun provideCityStore(@ApplicationContext context: Context): CityStore =
        DataStoreCityStore(PreferenceStore(context, "weather_prefs"))
}
```

- [ ] **Step 3: 编译确认通过**

Run: `./gradlew :module_weather:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add module_weather/src/main/java/com/btg/weather/data/CityStore.kt module_weather/src/main/java/com/btg/weather/di/WeatherModule.kt
git commit -m "feat(weather): add CityStore and Hilt WeatherModule"
```

---

### Task 7: WeatherUiState + WeatherViewModel

**Files:**
- Create: `module_weather/src/main/java/com/btg/weather/ui/WeatherUiState.kt`
- Create: `module_weather/src/main/java/com/btg/weather/ui/WeatherViewModel.kt`
- Test: `module_weather/src/test/java/com/btg/weather/ui/WeatherViewModelTest.kt`
- Test util: `module_weather/src/test/java/com/btg/weather/util/MainDispatcherRule.kt`

**Interfaces:**
- Consumes: `WeatherRepository`、`CityStore`、`WeatherOverview`、`UiState`、`BaseViewModel`
- Produces:
  - `data class WeatherUiState(city: String = "", content: UiState<WeatherOverview> = UiState.Loading, isRefreshing: Boolean = false)`
  - `class WeatherViewModel(repository, cityStore) : BaseViewModel()`：`val uiState: StateFlow<WeatherUiState>`；`fun selectCity(name: String)`；`fun refresh()`；`companion object { const val DEFAULT_CITY = "杭州" }`

- [ ] **Step 1: 创建测试用 MainDispatcherRule（照搬 news 版）**

`module_weather/src/test/java/com/btg/weather/util/MainDispatcherRule.kt`:

```kotlin
package com.btg.weather.util

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

- [ ] **Step 2: 写失败测试**

`module_weather/src/test/java/com/btg/weather/ui/WeatherViewModelTest.kt`:

```kotlin
package com.btg.weather.ui

import com.btg.common.result.UiState
import com.btg.weather.data.CityStore
import com.btg.weather.data.repository.WeatherRepository
import com.btg.weather.data.source.FakeWeatherDataSource
import com.btg.weather.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeCityStore(initial: String = "") : CityStore {
        private val state = MutableStateFlow(initial)
        override val currentCity: Flow<String> = state.asStateFlow()
        override suspend fun save(city: String) { state.value = city }
    }

    private fun repo(failWeather: Boolean = false) =
        WeatherRepository(FakeWeatherDataSource(failWeather = failWeather), mainDispatcherRule.testDispatcher)

    @Test
    fun `init loads default city Hangzhou when store empty`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore(""))

        val state = vm.uiState.value
        assertEquals("杭州", state.city)
        assertTrue(state.content is UiState.Success)
    }

    @Test
    fun `init loads stored city`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore("上海"))

        assertEquals("上海", vm.uiState.value.city)
    }

    @Test
    fun `selectCity updates city and reloads`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore(""))

        vm.selectCity("北京")

        val state = vm.uiState.value
        assertEquals("北京", state.city)
        assertTrue(state.content is UiState.Success)
    }

    @Test
    fun `selectCity ignores blank input`() = runTest {
        val vm = WeatherViewModel(repo(), FakeCityStore("广州"))

        vm.selectCity("   ")

        assertEquals("广州", vm.uiState.value.city)
    }

    @Test
    fun `weather failure yields Error content`() = runTest {
        val vm = WeatherViewModel(repo(failWeather = true), FakeCityStore(""))

        assertTrue(vm.uiState.value.content is UiState.Error)
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.ui.WeatherViewModelTest"`
Expected: FAIL（编译失败：`WeatherViewModel`/`WeatherUiState` 未定义）

- [ ] **Step 4: 创建 WeatherUiState**

`module_weather/src/main/java/com/btg/weather/ui/WeatherUiState.kt`:

```kotlin
package com.btg.weather.ui

import com.btg.common.result.UiState
import com.btg.weather.data.model.WeatherOverview

/**
 * 天气页 UI 状态快照。
 * content 为四态；isRefreshing 表示"已有内容 + 下拉刷新中"，与首次 Loading 区分。
 */
data class WeatherUiState(
    val city: String = "",
    val content: UiState<WeatherOverview> = UiState.Loading,
    val isRefreshing: Boolean = false,
)
```

- [ ] **Step 5: 创建 WeatherViewModel**

`module_weather/src/main/java/com/btg/weather/ui/WeatherViewModel.kt`:

```kotlin
package com.btg.weather.ui

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.UiState
import com.btg.common.result.toUiState
import com.btg.weather.data.CityStore
import com.btg.weather.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val cityStore: CityStore,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val city = cityStore.currentCity.first().ifBlank { DEFAULT_CITY }
            _uiState.update { it.copy(city = city) }
            load(city)
        }
    }

    /** 切换城市：忽略空白与相同城市；持久化后重新加载。 */
    fun selectCity(name: String) {
        val city = name.trim()
        if (city.isEmpty() || city == _uiState.value.city) return
        viewModelScope.launch {
            cityStore.save(city)
            _uiState.update { it.copy(city = city) }
            load(city)
        }
    }

    fun refresh() = load(_uiState.value.city, refreshing = true)

    private fun load(city: String, refreshing: Boolean = false) {
        _uiState.update {
            it.copy(
                isRefreshing = refreshing,
                content = if (refreshing) it.content else UiState.Loading,
            )
        }
        viewModelScope.launch {
            val result = repository.getWeatherOverview(city).toUiState()
            // 防串台：结果返回时城市已切换则丢弃
            if (_uiState.value.city != city) return@launch
            _uiState.update { it.copy(isRefreshing = false, content = result) }
            if (result is UiState.Error) postError(result.message)
        }
    }

    companion object {
        const val DEFAULT_CITY = "杭州"
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `./gradlew :module_weather:testDebugUnitTest --tests "com.btg.weather.ui.WeatherViewModelTest"`
Expected: PASS（5 个用例）

- [ ] **Step 7: 提交**

```bash
git add module_weather/src/main/java/com/btg/weather/ui/WeatherUiState.kt module_weather/src/main/java/com/btg/weather/ui/WeatherViewModel.kt module_weather/src/test/java/com/btg/weather/ui/WeatherViewModelTest.kt module_weather/src/test/java/com/btg/weather/util/MainDispatcherRule.kt
git commit -m "feat(weather): add WeatherViewModel with city switch and refresh"
```

---

### Task 8: 背景/插画 drawable 资源 + 字符串

**Files:**
- Create: `module_weather/src/main/res/drawable/bg_weather_clear.xml` / `_cloudy` / `_rain` / `_snow` / `_fog`（5 个渐变）
- Create: `module_weather/src/main/res/drawable/ic_weather_clear.xml` / `_cloudy` / `_rain` / `_snow` / `_fog`（5 个矢量插画）
- Modify: `module_weather/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: 10 个 drawable 资源 id（供 Task 9 的档位映射引用）；新增字符串资源。

> 纯资源任务，无单测；由 Task 11 编译校验。渐变色值为占位设计，后续可调。

- [ ] **Step 1: 创建 5 个渐变背景**

`module_weather/src/main/res/drawable/bg_weather_clear.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="270" android:startColor="#4A90D9" android:endColor="#87CEEB" />
</shape>
```

`module_weather/src/main/res/drawable/bg_weather_cloudy.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="270" android:startColor="#6E7F8D" android:endColor="#A9B7C0" />
</shape>
```

`module_weather/src/main/res/drawable/bg_weather_rain.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="270" android:startColor="#3A4A5A" android:endColor="#5D7488" />
</shape>
```

`module_weather/src/main/res/drawable/bg_weather_snow.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="270" android:startColor="#7C8FA5" android:endColor="#CFDCE8" />
</shape>
```

`module_weather/src/main/res/drawable/bg_weather_fog.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="270" android:startColor="#8A8578" android:endColor="#BFB9A8" />
</shape>
```

- [ ] **Step 2: 创建 5 个矢量插画（白色描边，铺在深色渐变上）**

`module_weather/src/main/res/drawable/ic_weather_clear.xml`（太阳）:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp" android:height="96dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,7a5,5 0,1 0,0.001 10.001A5,5 0,0 0,12 7zM12,2l1.5,3h-3zM12,22l1.5,-3h-3zM2,12l3,1.5v-3zM22,12l-3,1.5v-3zM4.9,4.9l3.1,1.4 -1.7,1.7zM19.1,19.1l-3.1,-1.4 1.7,-1.7zM4.9,19.1l1.4,-3.1 1.7,1.7zM19.1,4.9l-1.4,3.1 -1.7,-1.7z" />
</vector>
```

`module_weather/src/main/res/drawable/ic_weather_cloudy.xml`（云）:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp" android:height="96dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/white"
        android:pathData="M19,18H6a4,4 0,0 1,-0.5 -7.97A5.5,5.5 0,0 1,16.5 9.5a3.5,3.5 0,0 1,2.5 8.5z" />
</vector>
```

`module_weather/src/main/res/drawable/ic_weather_rain.xml`（云+雨滴）:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp" android:height="96dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/white"
        android:pathData="M18,13H6.5A4.5,4.5 0,0 1,6 4.05,5.5 5.5,0 0,1 16.5,5.5 3,3 0,0 1,18 13z" />
    <path android:fillColor="@android:color/white"
        android:pathData="M8,16l-1.5,3.5h1.5l1.5,-3.5zM13,16l-1.5,3.5h1.5l1.5,-3.5zM16.5,16l-1.5,3.5h1.5l1.5,-3.5z" />
</vector>
```

`module_weather/src/main/res/drawable/ic_weather_snow.xml`（云+雪点）:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp" android:height="96dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/white"
        android:pathData="M18,13H6.5A4.5,4.5 0,0 1,6 4.05,5.5 5.5,0 0,1 16.5,5.5 3,3 0,0 1,18 13z" />
    <path android:fillColor="@android:color/white"
        android:pathData="M8,17m-1,0a1,1 0,1 0,2 0a1,1 0,1 0,-2 0zM12,19m-1,0a1,1 0,1 0,2 0a1,1 0,1 0,-2 0zM16,17m-1,0a1,1 0,1 0,2 0a1,1 0,1 0,-2 0z" />
</vector>
```

`module_weather/src/main/res/drawable/ic_weather_fog.xml`（横线雾）:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp" android:height="96dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/white"
        android:pathData="M3,8h18v2H3zM5,12h14v2H5zM3,16h18v2H3z" />
</vector>
```

- [ ] **Step 3: 追加字符串资源**

`module_weather/src/main/res/values/strings.xml`（整体替换）:

```xml
<resources>
    <string name="weather_title">天气</string>
    <string name="weather_change_city">切换城市</string>
    <string name="weather_city_hint">输入城市名，如 杭州</string>
    <string name="weather_refresh">刷新</string>
    <string name="weather_humidity">湿度 %1$s%%</string>
    <string name="weather_wind">%1$s %2$s</string>
    <string name="weather_aqi">空气质量 %1$s</string>
    <string name="weather_temperature_unit">%1$s°</string>
    <string name="weather_forecast_title">近 5 天</string>
    <string name="weather_life_title">生活指数</string>
    <string name="weather_empty">暂无天气数据</string>
    <string name="weather_confirm">确定</string>
    <string name="weather_cancel">取消</string>
</resources>
```

- [ ] **Step 4: 编译确认资源可用**

Run: `./gradlew :module_weather:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add module_weather/src/main/res/drawable/ module_weather/src/main/res/values/strings.xml
git commit -m "feat(weather): add gradient backgrounds, vector illustrations and strings"
```

---

### Task 9: 布局 + Adapter + 档位资源映射

**Files:**
- Create: `module_weather/src/main/res/layout/fragment_weather.xml`（整体重写占位布局）
- Create: `module_weather/src/main/res/layout/item_forecast.xml`
- Create: `module_weather/src/main/res/layout/item_life.xml`
- Create: `module_weather/src/main/java/com/btg/weather/ui/WeatherCategoryRes.kt`
- Create: `module_weather/src/main/java/com/btg/weather/ui/ForecastAdapter.kt`
- Create: `module_weather/src/main/java/com/btg/weather/ui/LifeAdapter.kt`

**Interfaces:**
- Consumes: `WeatherCategory`（Task 2）、`ForecastDay`/`LifeIndex`（Task 3）、Task 8 的 drawable id
- Produces:
  - `@DrawableRes fun WeatherCategory.backgroundRes(): Int`、`@DrawableRes fun WeatherCategory.illustrationRes(): Int`
  - `class ForecastAdapter : ListAdapter<ForecastDay, *>`（横向）
  - `class LifeAdapter(onClick: (LifeIndex) -> Unit) : ListAdapter<LifeIndex, *>`

- [ ] **Step 1: 创建档位→资源映射**

`module_weather/src/main/java/com/btg/weather/ui/WeatherCategoryRes.kt`:

```kotlin
package com.btg.weather.ui

import androidx.annotation.DrawableRes
import com.btg.weather.R
import com.btg.weather.data.model.WeatherCategory

/** 天气档位 → 背景渐变资源。 */
@DrawableRes
fun WeatherCategory.backgroundRes(): Int = when (this) {
    WeatherCategory.CLEAR -> R.drawable.bg_weather_clear
    WeatherCategory.CLOUDY -> R.drawable.bg_weather_cloudy
    WeatherCategory.RAIN -> R.drawable.bg_weather_rain
    WeatherCategory.SNOW -> R.drawable.bg_weather_snow
    WeatherCategory.FOG -> R.drawable.bg_weather_fog
}

/** 天气档位 → 矢量插画资源。 */
@DrawableRes
fun WeatherCategory.illustrationRes(): Int = when (this) {
    WeatherCategory.CLEAR -> R.drawable.ic_weather_clear
    WeatherCategory.CLOUDY -> R.drawable.ic_weather_cloudy
    WeatherCategory.RAIN -> R.drawable.ic_weather_rain
    WeatherCategory.SNOW -> R.drawable.ic_weather_snow
    WeatherCategory.FOG -> R.drawable.ic_weather_fog
}
```

- [ ] **Step 2: 创建 fragment_weather.xml**

`module_weather/src/main/res/layout/fragment_weather.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.btg.widget.StateLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/stateLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipeRefresh"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.core.widget.NestedScrollView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:fillViewport="true">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <!-- 顶部栏：城市 + 刷新 -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:padding="16dp">

                    <TextView
                        android:id="@+id/cityText"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:textSize="22sp"
                        android:textStyle="bold"
                        android:drawablePadding="6dp"
                        android:text="@string/weather_title" />

                    <TextView
                        android:id="@+id/changeCityText"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:padding="4dp"
                        android:text="@string/weather_change_city"
                        android:textColor="#1976D2"
                        android:textSize="14sp" />
                </LinearLayout>

                <!-- 当前天气卡：背景随天气档位变化 -->
                <LinearLayout
                    android:id="@+id/currentCard"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:background="@drawable/bg_weather_cloudy"
                    android:padding="24dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical">

                            <TextView
                                android:id="@+id/temperatureText"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:textColor="@android:color/white"
                                android:textSize="56sp"
                                android:textStyle="bold"
                                tools:ignore="RtlHardcoded" />

                            <TextView
                                android:id="@+id/infoText"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:textColor="@android:color/white"
                                android:textSize="18sp" />
                        </LinearLayout>

                        <ImageView
                            android:id="@+id/illustrationImage"
                            android:layout_width="96dp"
                            android:layout_height="96dp"
                            android:contentDescription="@null" />
                    </LinearLayout>

                    <TextView
                        android:id="@+id/detailText"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:textColor="@android:color/white"
                        android:textSize="14sp" />
                </LinearLayout>

                <!-- 近 5 天 -->
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:padding="16dp"
                    android:text="@string/weather_forecast_title"
                    android:textSize="16sp"
                    android:textStyle="bold" />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/forecastRecycler"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:clipToPadding="false"
                    android:paddingHorizontal="12dp"
                    android:orientation="horizontal"
                    app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

                <!-- 生活指数 -->
                <TextView
                    android:id="@+id/lifeTitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:padding="16dp"
                    android:text="@string/weather_life_title"
                    android:textSize="16sp"
                    android:textStyle="bold" />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/lifeRecycler"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingHorizontal="12dp"
                    android:paddingBottom="16dp"
                    app:layoutManager="androidx.recyclerview.widget.GridLayoutManager"
                    app:spanCount="2" />

            </LinearLayout>
        </androidx.core.widget.NestedScrollView>
    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

</com.btg.widget.StateLayout>
```

> 注：根标签需补 `xmlns:tools="http://schemas.android.com/tools"`。在根元素追加 `xmlns:tools="http://schemas.android.com/tools"` 属性。

- [ ] **Step 3: 创建 item_forecast.xml**

`module_weather/src/main/res/layout/item_forecast.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center_horizontal"
    android:padding="12dp"
    android:minWidth="84dp">

    <TextView
        android:id="@+id/dateText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="13sp" />

    <ImageView
        android:id="@+id/iconImage"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginVertical="6dp"
        android:contentDescription="@null" />

    <TextView
        android:id="@+id/weatherText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="13sp" />

    <TextView
        android:id="@+id/tempText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textSize="13sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/directText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textColor="#888888"
        android:textSize="11sp" />

</LinearLayout>
```

> item_forecast 的 icon 铺在浅色背景上，矢量插画为白色会看不清——在 Adapter 里对 forecast 图标用 `imageTintList` 染成深色（见 Step 5）。

- [ ] **Step 4: 创建 item_life.xml**

`module_weather/src/main/res/layout/item_life.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_margin="4dp"
    android:padding="14dp"
    android:background="?android:attr/selectableItemBackground"
    android:foreground="?android:attr/selectableItemBackground">

    <TextView
        android:id="@+id/lifeNameText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#888888"
        android:textSize="13sp" />

    <TextView
        android:id="@+id/lifeLevelText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textSize="16sp"
        android:textStyle="bold" />

</LinearLayout>
```

- [ ] **Step 5: 创建 ForecastAdapter**

`module_weather/src/main/java/com/btg/weather/ui/ForecastAdapter.kt`:

```kotlin
package com.btg.weather.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btg.weather.data.model.ForecastDay
import com.btg.weather.databinding.ItemForecastBinding

class ForecastAdapter : ListAdapter<ForecastDay, ForecastAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemForecastBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ForecastDay) {
            binding.dateText.text = item.date
            binding.weatherText.text = item.weather
            binding.tempText.text = item.temperature
            binding.directText.text = item.direct
            binding.iconImage.setImageResource(item.category.illustrationRes())
            // 插画本是白色，浅底列表里染成深灰
            binding.iconImage.imageTintList = ColorStateList.valueOf(Color.parseColor("#546E7A"))
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ForecastDay>() {
            override fun areItemsTheSame(oldItem: ForecastDay, newItem: ForecastDay) =
                oldItem.date == newItem.date

            override fun areContentsTheSame(oldItem: ForecastDay, newItem: ForecastDay) =
                oldItem == newItem
        }
    }
}
```

- [ ] **Step 6: 创建 LifeAdapter**

`module_weather/src/main/java/com/btg/weather/ui/LifeAdapter.kt`:

```kotlin
package com.btg.weather.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btg.weather.data.model.LifeIndex
import com.btg.weather.databinding.ItemLifeBinding

class LifeAdapter(
    private val onClick: (LifeIndex) -> Unit,
) : ListAdapter<LifeIndex, LifeAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLifeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val binding: ItemLifeBinding,
        private val onClick: (LifeIndex) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LifeIndex) {
            binding.lifeNameText.text = item.name
            binding.lifeLevelText.text = item.level
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<LifeIndex>() {
            override fun areItemsTheSame(oldItem: LifeIndex, newItem: LifeIndex) =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: LifeIndex, newItem: LifeIndex) =
                oldItem == newItem
        }
    }
}
```

- [ ] **Step 7: 编译确认通过**

Run: `./gradlew :module_weather:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 提交**

```bash
git add module_weather/src/main/res/layout/ module_weather/src/main/java/com/btg/weather/ui/WeatherCategoryRes.kt module_weather/src/main/java/com/btg/weather/ui/ForecastAdapter.kt module_weather/src/main/java/com/btg/weather/ui/LifeAdapter.kt
git commit -m "feat(weather): add layouts, adapters and category resource mapping"
```

---

### Task 10: WeatherFragment 组装

**Files:**
- Modify: `module_weather/src/main/java/com/btg/weather/ui/WeatherFragment.kt`（整体重写）

**Interfaces:**
- Consumes: `WeatherViewModel`/`WeatherUiState`（Task 7）、`ForecastAdapter`/`LifeAdapter`/`backgroundRes`/`illustrationRes`（Task 9）、lib_common 的 `collectOnStarted`/`onRefresh`/`toast`/`showAlertDialog`/`StateLayout` 四态、`UiState`

- [ ] **Step 1: 重写 WeatherFragment**

`module_weather/src/main/java/com/btg/weather/ui/WeatherFragment.kt`:

```kotlin
package com.btg.weather.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.result.UiState
import com.btg.common.ui.onRefresh
import com.btg.common.ui.showAlertDialog
import com.btg.common.ui.toast
import com.btg.weather.R
import com.btg.weather.data.model.WeatherOverview
import com.btg.weather.databinding.FragmentWeatherBinding
import dagger.hilt.android.AndroidEntryPoint

/** 天气首页：实况卡（背景随天气档位）+ 近 5 天 + 生活指数；顶部可切换城市。 */
@AndroidEntryPoint
class WeatherFragment : BaseFragment<FragmentWeatherBinding>() {

    private val viewModel: WeatherViewModel by viewModels()
    private val forecastAdapter = ForecastAdapter()
    private val lifeAdapter = LifeAdapter { index ->
        requireContext().showAlertDialog(title = index.name, message = index.desc)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentWeatherBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.forecastRecycler.adapter = forecastAdapter
        binding.lifeRecycler.adapter = lifeAdapter
        binding.swipeRefresh.onRefresh { viewModel.refresh() }
        binding.stateLayout.setOnRetryListener { viewModel.refresh() }

        val showCityDialog = { showCityDialog() }
        binding.changeCityText.setOnClickListener { showCityDialog() }
        binding.cityText.setOnClickListener { showCityDialog() }

        viewModel.uiState.collectOnStarted(viewLifecycleOwner) { render(it) }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }

    private fun render(state: WeatherUiState) {
        binding.cityText.text = state.city
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        when (val content = state.content) {
            is UiState.Loading -> binding.stateLayout.showLoading()
            is UiState.Empty -> binding.stateLayout.showEmpty()
            is UiState.Error -> {
                if (state.isRefreshing) binding.stateLayout.showContent()
                else binding.stateLayout.showError(content.message)
            }
            is UiState.Success -> {
                binding.stateLayout.showContent()
                bindOverview(content.data)
            }
        }
    }

    private fun bindOverview(data: WeatherOverview) {
        val rt = data.realtime
        binding.currentCard.setBackgroundResource(rt.category.backgroundRes())
        binding.illustrationImage.setImageResource(rt.category.illustrationRes())
        binding.temperatureText.text = getString(R.string.weather_temperature_unit, rt.temperature)
        binding.infoText.text = rt.info
        binding.detailText.text = buildString {
            append(getString(R.string.weather_humidity, rt.humidity))
            append("   ")
            append(getString(R.string.weather_wind, rt.direct, rt.power))
            if (rt.aqi.isNotBlank()) {
                append("   ")
                append(getString(R.string.weather_aqi, rt.aqi))
            }
        }
        forecastAdapter.submitList(data.future)
        binding.lifeTitle.visibility = if (data.life.isEmpty()) View.GONE else View.VISIBLE
        binding.lifeRecycler.visibility = if (data.life.isEmpty()) View.GONE else View.VISIBLE
        lifeAdapter.submitList(data.life)
    }

    private fun showCityDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.weather_city_hint)
            setText(viewModel.uiState.value.city)
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.weather_change_city)
            .setView(input)
            .setPositiveButton(R.string.weather_confirm) { _, _ ->
                viewModel.selectCity(input.text.toString())
            }
            .setNegativeButton(R.string.weather_cancel, null)
            .show()
    }
}
```

- [ ] **Step 2: 编译确认通过**

Run: `./gradlew :module_weather:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add module_weather/src/main/java/com/btg/weather/ui/WeatherFragment.kt
git commit -m "feat(weather): wire WeatherFragment with weather card, forecast, life"
```

---

### Task 11: 全量验证 + 文档

**Files:**
- Modify: `CLAUDE.md`（更新 module_weather 描述：占位 → 真实天气）

**Interfaces:** 无新增。

- [ ] **Step 1: 跑全部天气单测**

Run: `./gradlew :module_weather:testDebugUnitTest`
Expected: PASS（JuheResponse 3 + WeatherCategory 6 + WeatherDto 4 + WeatherRepository 3 + WeatherViewModel 5 = 21 用例）

- [ ] **Step 2: 全工程编译 + lint**

Run: `./gradlew assembleDebug lint`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 手动验证（需真机/模拟器 + local.properties 配好 JUHE_API_KEY）**

Run: `./gradlew installDebug`
手动核对：
- 底部切到「天气」Tab，默认显示杭州天气；实况卡背景随天气档位变化，插画正确。
- 近 5 天横向可滑；生活指数网格显示，点某项弹出详情文案。
- 点顶部城市 → 弹输入框 → 输入「北京」确定 → 刷新为北京天气；杀进程重进仍是北京（持久化生效）。
- 下拉刷新有转圈；输入错误城市名（如「火星」）显示错误态。
- 无 key 时：把 `WeatherModule.provideWeatherDataSource` 临时改为 `FakeWeatherDataSource()` 可看假数据（验证完改回）。

- [ ] **Step 4: 更新 CLAUDE.md**

在 `## 项目现状` 段把「天气（占位）」改为「天气（聚合数据：实况+近5天预报+生活指数，背景随天气渐变，默认杭州可切换）」；在「## 业务模块结构」的 **module_weather** 段替换「占位，无业务代码」为真实结构描述（data/source、data/repository、data/CityStore、di、ui）。在「待接入（YAGNI 留白）」里删除「module_weather 真实天气接口」。

- [ ] **Step 5: 提交**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for real weather module"
```

---

## Self-Review 记录

- **Spec coverage：** query+life（Task 3/4/5）、wids 内置（Task 2）、默认杭州+切城市（Task 7）、渐变+矢量插画背景（Task 8/9/10）、持久化（Task 6/7）、多 baseUrl 独立 Retrofit（Task 6）、JuheResponse 方案 A 复制（Task 1）、四态+下拉刷新（Task 10）、测试（Task 1/2/3/5/7/11）、导航不改 app 壳（spec 已述，Task 10 复用现有 destination）——均有对应任务。
- **类型一致性：** `WeatherData`/`WeatherOverview`/`RealtimeWeather`/`ForecastDay`/`LifeIndex`、`WeatherDataSource.fetchWeather/fetchLife`、`WeatherRepository.getWeatherOverview`、`CityStore.currentCity/save`、`WeatherViewModel.selectCity/refresh/DEFAULT_CITY`、`backgroundRes()/illustrationRes()` 跨任务签名一致。
- **占位扫描：** 无 TODO/TBD；每个代码步骤含完整代码。
- **注意点：** `fragment_weather.xml` 根元素需补 `xmlns:tools`（Step 2 已注明）；forecast 插画在浅底需 `imageTintList` 染色（Task 9 Step 5 已处理）。
