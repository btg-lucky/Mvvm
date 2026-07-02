# 框架能力补齐 · 阶段 4（权限 + 存储）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐权限请求封装（含永久拒绝引导设置页）、DataStore KV 封装、EncryptedSharedPreferences 加密存储、Room 基建（`BaseDao`）+ app 收藏 demo。

**Architecture:** 权限与存储封装放 `lib_common`（`permission/`、`storage/`）；Room 演示 DB 放 `app`（`@Database` 所在模块接 room-compiler KSP）。可纯 JVM 测的只有权限结果判定（`PermissionResult`），其余依赖 Android framework 的只做编译验证 + 阶段末冒烟。

**Tech Stack:** Kotlin / AndroidX activity（ActivityResult）/ DataStore Preferences 1.1 / security-crypto 1.0 / Room 2.6（KSP）/ 协程 Flow。

## Global Constraints

- 语言全 Kotlin。公共 API 显式可见性。
- 加依赖只动 `libs.versions.toml` 既有别名（Room/DataStore/security 已在 Phase 0 声明并经 lib_opensource 暴露）；本阶段只在 app 增加 `ksp(libs.androidx.room.compiler)`。
- 不改 `minSdk`/`targetSdk`/`compileSdk`。
- 依赖 Android framework 的类只做编译验证（+ 冒烟）；纯逻辑（`PermissionResult`）走 TDD。
- 测试延续现状：不引 mockk/mockito/Turbine/Robolectric。
- 每任务末尾提交（Conventional Commits），message 末尾附：
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 工作分支：`feat/framework-buildout`。

---

### Task 1: 权限请求封装（PermissionResult TDD + PermissionRequester + 设置页）

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/permission/PermissionResult.kt`
- Create: `lib_common/src/test/java/com/btg/common/permission/PermissionResultTest.kt`
- Create: `lib_common/src/main/java/com/btg/common/permission/PermissionRequester.kt`
- Create: `lib_common/src/main/java/com/btg/common/permission/PermissionExt.kt`

**Interfaces:**
- Consumes: `androidx.activity.result`（`ActivityResultCaller`/`ActivityResultContracts.RequestMultiplePermissions`）、`Settings`/`Intent`。
- Produces:
  - `data class PermissionResult(val grants: Map<String, Boolean>)` + `allGranted`/`denied`（纯逻辑，TDD）
  - `class PermissionRequester(caller: ActivityResultCaller)`：`request(vararg permissions, onResult)`
  - `fun Context.openAppSettings()`

- [ ] **Step 1: 写 PermissionResult 失败测试**

创建 `lib_common/src/test/java/com/btg/common/permission/PermissionResultTest.kt`：

```kotlin
package com.btg.common.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionResultTest {

    @Test
    fun `allGranted is true when every permission granted`() {
        val result = PermissionResult(mapOf("A" to true, "B" to true))
        assertTrue(result.allGranted)
        assertTrue(result.denied.isEmpty())
    }

    @Test
    fun `allGranted is false when any permission denied`() {
        val result = PermissionResult(mapOf("A" to true, "B" to false))
        assertFalse(result.allGranted)
    }

    @Test
    fun `denied lists only ungranted permissions`() {
        val result = PermissionResult(mapOf("A" to true, "B" to false, "C" to false))
        assertEquals(listOf("B", "C"), result.denied)
    }

    @Test
    fun `empty result is treated as all granted`() {
        val result = PermissionResult(emptyMap())
        assertTrue(result.allGranted)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.permission.PermissionResultTest"`
Expected: 编译失败，`unresolved reference: PermissionResult`。

- [ ] **Step 3: 创建 PermissionResult**

创建 `lib_common/src/main/java/com/btg/common/permission/PermissionResult.kt`：

```kotlin
package com.btg.common.permission

/** 权限请求结果。 */
data class PermissionResult(val grants: Map<String, Boolean>) {
    /** 是否全部授予（空结果视为全部授予）。 */
    val allGranted: Boolean get() = grants.values.all { it }

    /** 被拒绝的权限列表。 */
    val denied: List<String> get() = grants.filterValues { !it }.keys.toList()
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :lib_common:testDebugUnitTest --tests "com.btg.common.permission.PermissionResultTest"`
Expected: `BUILD SUCCESSFUL`，4 个测试通过。

- [ ] **Step 5: 创建 PermissionRequester**

创建 `lib_common/src/main/java/com/btg/common/permission/PermissionRequester.kt`：

```kotlin
package com.btg.common.permission

import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 权限请求封装。必须在 Activity/Fragment 创建期（作为字段）实例化，
 * 以满足 registerForActivityResult 的时机要求。
 *
 * 用法：
 *   private val permission = PermissionRequester(this)
 *   permission.request(Manifest.permission.CAMERA) { result ->
 *       if (result.allGranted) { ... } else { openAppSettings() }
 *   }
 */
class PermissionRequester(caller: ActivityResultCaller) {

    private var callback: ((PermissionResult) -> Unit)? = null

    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        callback?.invoke(PermissionResult(grants))
    }

    fun request(vararg permissions: String, onResult: (PermissionResult) -> Unit) {
        callback = onResult
        launcher.launch(arrayOf(*permissions))
    }
}
```

- [ ] **Step 6: 创建 PermissionExt（跳设置页）**

创建 `lib_common/src/main/java/com/btg/common/permission/PermissionExt.kt`：

```kotlin
package com.btg.common.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 跳转到本应用的系统设置页（权限被永久拒绝后引导用户手动开启）。 */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 8: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/permission/ lib_common/src/test/java/com/btg/common/permission/
git commit -m "feat: add permission request wrapper with settings-page fallback

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: DataStore KV 封装

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/storage/PreferenceStore.kt`

**Interfaces:**
- Consumes: DataStore Preferences、协程 Flow。
- Produces: `class PreferenceStore(context, name)`——类型化 KV：`getString/putString`、`getInt/putInt`、`getBoolean/putBoolean`、`getLong/putLong`、`getFloat/putFloat`、`remove`、`clear`。读为 `Flow`，写为 `suspend`。

- [ ] **Step 1: 创建 PreferenceStore**

创建 `lib_common/src/main/java/com/btg/common/storage/PreferenceStore.kt`：

```kotlin
package com.btg.common.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore(Preferences) 封装：类型化 KV，读为 Flow、写为 suspend。
 * 每个 name 对应一个独立文件；同名请复用同一实例。
 */
class PreferenceStore(context: Context, name: String = "app_prefs") {

    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile(name) },
    )

    fun getString(key: String, default: String = ""): Flow<String> =
        dataStore.data.map { it[stringPreferencesKey(key)] ?: default }

    suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    fun getInt(key: String, default: Int = 0): Flow<Int> =
        dataStore.data.map { it[intPreferencesKey(key)] ?: default }

    suspend fun putInt(key: String, value: Int) {
        dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    fun getBoolean(key: String, default: Boolean = false): Flow<Boolean> =
        dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }

    suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    fun getLong(key: String, default: Long = 0L): Flow<Long> =
        dataStore.data.map { it[longPreferencesKey(key)] ?: default }

    suspend fun putLong(key: String, value: Long) {
        dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    fun getFloat(key: String, default: Float = 0f): Flow<Float> =
        dataStore.data.map { it[floatPreferencesKey(key)] ?: default }

    suspend fun putFloat(key: String, value: Float) {
        dataStore.edit { it[floatPreferencesKey(key)] = value }
    }

    suspend fun remove(key: String) {
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name == key }.forEach { prefs.remove(it) }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/storage/PreferenceStore.kt
git commit -m "feat: add DataStore-based typed PreferenceStore

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: EncryptedSharedPreferences 加密存储

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/storage/SecurePreferences.kt`

**Interfaces:**
- Consumes: `androidx.security.crypto`（security-crypto 1.0.0 API：`MasterKeys` + `EncryptedSharedPreferences.create`）。
- Produces: `class SecurePreferences(context, fileName)`——同步加密 KV：`putString/getString`、`putBoolean/getBoolean`、`remove`、`clear`。用于 token 等敏感数据。

- [ ] **Step 1: 创建 SecurePreferences**

创建 `lib_common/src/main/java/com/btg/common/storage/SecurePreferences.kt`：

```kotlin
package com.btg.common.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * EncryptedSharedPreferences 封装（AES256）。用于 token / 密码等敏感数据的加密存储。
 * 同步读写，接口与 SharedPreferences 类似。
 */
class SecurePreferences(context: Context, fileName: String = "secure_prefs") {

    private val prefs: SharedPreferences = run {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            fileName,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? = prefs.getString(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。若 security-crypto 1.0.0 的 `MasterKeys`/`create` 签名与此不符，按实际 API 调整（属版本对齐）。

- [ ] **Step 3: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/storage/SecurePreferences.kt
git commit -m "feat: add EncryptedSharedPreferences wrapper for sensitive data

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Room 基建 BaseDao

**Files:**
- Create: `lib_common/src/main/java/com/btg/common/storage/BaseDao.kt`

**Interfaces:**
- Consumes: `androidx.room`（room-runtime 注解，经 lib_opensource api）。
- Produces: `interface BaseDao<T>`——通用 `insert`/`insertAll`/`update`/`delete`。具体 `@Dao`（在 app）继承它复用这些方法。

- [ ] **Step 1: 创建 BaseDao**

创建 `lib_common/src/main/java/com/btg/common/storage/BaseDao.kt`：

```kotlin
package com.btg.common.storage

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/**
 * 通用 Room DAO 基接口。具体 @Dao 继承它即获得增删改的样板方法。
 * 注：本接口不加 @Dao，由继承它的具体 @Dao 触发 Room 处理（在 app 模块）。
 */
interface BaseDao<T> {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: T): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<T>)

    @Update
    suspend fun update(item: T)

    @Delete
    suspend fun delete(item: T)
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :lib_common:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add lib_common/src/main/java/com/btg/common/storage/BaseDao.kt
git commit -m "feat: add generic Room BaseDao

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: app Room 收藏 demo

**Files:**
- Modify: `app/build.gradle.kts`（加 `ksp(libs.androidx.room.compiler)`）
- Create: `app/src/main/java/com/btg/mvvm/data/local/NewsFavorite.kt`
- Create: `app/src/main/java/com/btg/mvvm/data/local/FavoriteDao.kt`
- Create: `app/src/main/java/com/btg/mvvm/data/local/AppDatabase.kt`

**Interfaces:**
- Consumes: `BaseDao`（Task 4）、Room。
- Produces: `@Entity NewsFavorite`、`@Dao FavoriteDao : BaseDao<NewsFavorite>` + `getAll(): Flow<List<NewsFavorite>>`、`@Database AppDatabase`（示范 Room 用法，收藏 NewsItem）。

- [ ] **Step 1: app 加 room-compiler（KSP）**

在 `app/build.gradle.kts` 的 `dependencies { }` 块内，`ksp(libs.hilt.compiler)` 之后追加：

```kotlin
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
```

- [ ] **Step 2: 创建实体 NewsFavorite**

创建 `app/src/main/java/com/btg/mvvm/data/local/NewsFavorite.kt`：

```kotlin
package com.btg.mvvm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 收藏的新闻（以 url 为主键，重复收藏覆盖）。 */
@Entity(tableName = "news_favorite")
data class NewsFavorite(
    @PrimaryKey val url: String,
    val title: String,
    val source: String,
    val date: String,
    val imageUrl: String?,
)
```

- [ ] **Step 3: 创建 FavoriteDao**

创建 `app/src/main/java/com/btg/mvvm/data/local/FavoriteDao.kt`：

```kotlin
package com.btg.mvvm.data.local

import androidx.room.Dao
import androidx.room.Query
import com.btg.common.storage.BaseDao
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao : BaseDao<NewsFavorite> {

    @Query("SELECT * FROM news_favorite ORDER BY date DESC")
    fun getAll(): Flow<List<NewsFavorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM news_favorite WHERE url = :url)")
    suspend fun exists(url: String): Boolean
}
```

- [ ] **Step 4: 创建 AppDatabase**

创建 `app/src/main/java/com/btg/mvvm/data/local/AppDatabase.kt`：

```kotlin
package com.btg.mvvm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NewsFavorite::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
```

- [ ] **Step 5: 编译验证（Room 代码生成）**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。Room 通过 KSP 生成 DAO/DB 实现。

- [ ] **Step 6: 提交**

```bash
git add app/build.gradle.kts app/src/main/java/com/btg/mvvm/data/local/
git commit -m "feat: add Room favorites demo (entity/dao/database) using BaseDao

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: 全量编译 + 单测 + 真机冒烟

**Files:** 无改动（纯验证）。

- [ ] **Step 1: 全量编译**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 全部单元测试**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`；新增 `PermissionResultTest`(4) 与既有测试全绿。

- [ ] **Step 3: 真机冒烟**

Run: `./gradlew installDebug`
手动：启动 app，确认示范页正常、无崩溃（本阶段未接入 UI 调用，主要验证依赖与代码生成不破坏运行）。
Expected: 正常启动；Logcat 无崩溃。（无设备时跳过并注明。）

- [ ] **Step 4: 收尾**

无代码改动则不提交，记录验证结果。

---

## Self-Review（对照 spec 第 7 节权限/存储）

- ✅ 权限封装 + 永久拒绝引导设置页（Task 1）；核心判定 `PermissionResult` 抽为纯函数并单测。
- ✅ DataStore 封装（Task 2）。
- ✅ EncryptedSharedPreferences（Task 3）。
- ✅ Room 基建 `BaseDao`（Task 4）+ app 收藏 demo（Task 5）。
- 依赖：Room/DataStore/security 均 Phase 0 已声明并经 lib_opensource api 暴露；仅 app 增加 room-compiler KSP + room runtime/ktx。
- 事件总线/前后台监听/崩溃捕获属阶段 1（已做）/阶段 5，不在本阶段。
- 阶段边界：只做权限 + 存储；不接 UI 调用（演示台留阶段 6）。
