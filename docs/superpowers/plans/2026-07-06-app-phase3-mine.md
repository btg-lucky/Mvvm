# 阶段 3:我的模块——本地 Room 账号体系(注册 / 登录 / 退出)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** module_mine 实现完整本地账号体系:注册(用户名唯一 + 密码加盐哈希入 Room)、登录(哈希校验)、登录态 DataStore 持久化(冷启动保持)、我的页依登录态切换 UI、退出登录。

**Architecture:** `MineFragment/LoginFragment/RegisterFragment → 各自 ViewModel → UserRepository → UserDao(Room) + SessionStore(DataStore)`。密码哈希抽纯函数 `PasswordHasher`(SHA-256 + 随机盐);登录态抽 `SessionStore` 接口便于测试注入 fake。

**Tech Stack:** Room(KSP) / DataStore(经 lib_common PreferenceStore) / Hilt / Material TextInputLayout / kotlinx-coroutines-test

## Global Constraints

- 全 Kotlin;不新增第三方依赖、不改版本号
- 密码绝不明文存储:存 `SHA-256(salt + password)` 十六进制 + 随机 16 字节盐
- 校验规则:用户名非空;密码 ≥ 6 位;注册时两次密码一致;用户名唯一
- 测试:`runTest` + 手写 fake(不引 mock 框架);ViewModel 测试用 `MainDispatcherRule`
- Room 数据库 `exportSchema = false`(不导 schema 文件)
- 每任务一 commit

**前置:** 阶段 1 已完成(module_mine 占位存在,nav_mine.xml 有 mineFragment)。与阶段 2 无依赖,可并行。

---

### Task 1: PasswordHasher 纯函数(TDD)

**Files:**
- Create: `module_mine/src/main/java/com/btg/mine/data/PasswordHasher.kt`
- Test: `module_mine/src/test/java/com/btg/mine/data/PasswordHasherTest.kt`

**Interfaces:**
- Produces: `object PasswordHasher { fun generateSalt(): String; fun hash(password: String, salt: String): String; fun verify(password: String, salt: String, expectedHash: String): Boolean }`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.btg.mine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `same password and salt produce same hash`() {
        assertEquals(
            PasswordHasher.hash("secret123", "abcd"),
            PasswordHasher.hash("secret123", "abcd")
        )
    }

    @Test
    fun `different salt produces different hash`() {
        assertNotEquals(
            PasswordHasher.hash("secret123", "salt1"),
            PasswordHasher.hash("secret123", "salt2")
        )
    }

    @Test
    fun `hash is 64-char lowercase hex`() {
        val hash = PasswordHasher.hash("secret123", "abcd")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `generateSalt returns 32-char hex and differs each call`() {
        val s1 = PasswordHasher.generateSalt()
        val s2 = PasswordHasher.generateSalt()
        assertEquals(32, s1.length)
        assertNotEquals(s1, s2)
    }

    @Test
    fun `verify matches correct password and rejects wrong one`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("secret123", salt)
        assertTrue(PasswordHasher.verify("secret123", salt, hash))
        assertFalse(PasswordHasher.verify("wrong", salt, hash))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :module_mine:testDebugUnitTest --tests "com.btg.mine.data.PasswordHasherTest"`
Expected: 编译失败(PasswordHasher 未定义)

- [ ] **Step 3: 实现 PasswordHasher.kt**

```kotlin
package com.btg.mine.data

import java.security.MessageDigest
import java.security.SecureRandom

/** 密码加盐哈希（纯函数，可单测）。存储格式：SHA-256(salt + password) 的十六进制。 */
object PasswordHasher {

    /** 生成 16 字节随机盐（32 位十六进制字符串）。 */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + password).toByteArray(Charsets.UTF_8))
            .toHex()

    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        hash(password, salt) == expectedHash

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :module_mine:testDebugUnitTest --tests "com.btg.mine.data.PasswordHasherTest"`
Expected: 5 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add module_mine/src
git commit -m "feat: add salted sha-256 password hasher for mine module"
```

---

### Task 2: Room 用户库(Entity / Dao / Database)

**Files:**
- Modify: `module_mine/build.gradle.kts`(加 Room KSP 编译器)
- Create: `module_mine/src/main/java/com/btg/mine/data/local/UserEntity.kt`
- Create: `module_mine/src/main/java/com/btg/mine/data/local/UserDao.kt`
- Create: `module_mine/src/main/java/com/btg/mine/data/local/MineDatabase.kt`

**Interfaces:**
- Consumes: lib_common `BaseDao<T>`(insert/insertAll/update/delete)
- Produces:
  - `UserEntity(id: Long = 0, username: String, passwordHash: String, salt: String, createdAt: Long)`,表名 `users`,username 唯一索引
  - `UserDao : BaseDao<UserEntity> { suspend fun findByUsername(username: String): UserEntity? }`
  - `MineDatabase : RoomDatabase { fun userDao(): UserDao }`

- [ ] **Step 1: module_mine/build.gradle.kts 的 dependencies 加 Room 编译器**

```kotlin
ksp(libs.androidx.room.compiler)
```

(Room runtime/ktx 已通过 lib_common → lib_opensource 的 api 传递,无需再声明。)

- [ ] **Step 2: UserEntity.kt**

```kotlin
package com.btg.mine.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    /** SHA-256(salt + password) 十六进制，绝不存明文。 */
    val passwordHash: String,
    val salt: String,
    val createdAt: Long,
)
```

- [ ] **Step 3: UserDao.kt**

```kotlin
package com.btg.mine.data.local

import androidx.room.Dao
import androidx.room.Query
import com.btg.common.storage.BaseDao

@Dao
interface UserDao : BaseDao<UserEntity> {

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?
}
```

- [ ] **Step 4: MineDatabase.kt**

```kotlin
package com.btg.mine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UserEntity::class], version = 1, exportSchema = false)
abstract class MineDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
```

- [ ] **Step 5: 编译验证(触发 Room KSP 校验 SQL 与实体)**

Run: `./gradlew :module_mine:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add module_mine
git commit -m "feat: add room user database for mine module"
```

---

### Task 3: SessionStore + UserRepository(TDD)

**Files:**
- Create: `module_mine/src/main/java/com/btg/mine/data/SessionStore.kt`
- Create: `module_mine/src/main/java/com/btg/mine/data/UserRepository.kt`
- Create: `module_mine/src/test/java/com/btg/mine/util/MainDispatcherRule.kt`
- Test: `module_mine/src/test/java/com/btg/mine/data/UserRepositoryTest.kt`

**Interfaces:**
- Consumes: `UserDao`、`PasswordHasher`、lib_common `PreferenceStore`、`safeApiCall`、`ApiResult`、`AppException.Business`
- Produces:
  - `interface SessionStore { val currentUser: Flow<String?>; suspend fun save(username: String); suspend fun clear() }`
  - `DataStoreSessionStore(store: PreferenceStore) : SessionStore`
  - `UserRepository(dao: UserDao, session: SessionStore, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`:
    - `val currentUser: Flow<String?>`
    - `suspend fun register(username: String, password: String): ApiResult<Unit>`(重名抛 Business,成功自动登录)
    - `suspend fun login(username: String, password: String): ApiResult<Unit>`
    - `suspend fun logout()`
  - 错误码常量:`CODE_USER_EXISTS = 1`、`CODE_USER_NOT_FOUND = 2`、`CODE_WRONG_PASSWORD = 3`(UserRepository companion)

- [ ] **Step 1: SessionStore.kt(接口 + DataStore 实现)**

```kotlin
package com.btg.mine.data

import com.btg.common.storage.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 登录态存取抽象：便于测试注入 fake。 */
interface SessionStore {
    /** 当前登录用户名，未登录为 null。 */
    val currentUser: Flow<String?>
    suspend fun save(username: String)
    suspend fun clear()
}

/** DataStore 实现：登录用户名持久化，冷启动自动恢复。 */
class DataStoreSessionStore(private val store: PreferenceStore) : SessionStore {

    override val currentUser: Flow<String?> =
        store.getString(KEY_USERNAME).map { it.ifEmpty { null } }

    override suspend fun save(username: String) {
        store.putString(KEY_USERNAME, username)
    }

    override suspend fun clear() {
        store.remove(KEY_USERNAME)
    }

    private companion object {
        const val KEY_USERNAME = "current_username"
    }
}
```

- [ ] **Step 2: MainDispatcherRule(复制既有模板,包名 com.btg.mine.util)**

```kotlin
package com.btg.mine.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

- [ ] **Step 3: 写失败测试 UserRepositoryTest**

```kotlin
package com.btg.mine.data

import com.btg.common.network.AppException
import com.btg.common.result.ApiResult
import com.btg.mine.data.local.UserDao
import com.btg.mine.data.local.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryTest {

    private class FakeUserDao : UserDao {
        val users = mutableListOf<UserEntity>()
        override suspend fun findByUsername(username: String): UserEntity? =
            users.firstOrNull { it.username == username }
        override suspend fun insert(item: UserEntity): Long {
            users.add(item); return users.size.toLong()
        }
        override suspend fun insertAll(items: List<UserEntity>) { users.addAll(items) }
        override suspend fun update(item: UserEntity) = Unit
        override suspend fun delete(item: UserEntity) = Unit
    }

    private class FakeSessionStore : SessionStore {
        private val state = MutableStateFlow<String?>(null)
        override val currentUser: Flow<String?> = state
        val value: String? get() = state.value
        override suspend fun save(username: String) { state.value = username }
        override suspend fun clear() { state.value = null }
    }

    private fun runCase(block: suspend (UserRepository, FakeUserDao, FakeSessionStore) -> Unit) = runTest {
        val dao = FakeUserDao()
        val session = FakeSessionStore()
        val repo = UserRepository(dao, session, StandardTestDispatcher(testScheduler))
        block(repo, dao, session)
    }

    @Test
    fun `register stores hashed password and logs in`() = runCase { repo, dao, session ->
        val result = repo.register("alice", "secret123")

        assertTrue(result is ApiResult.Success)
        val user = dao.users.single()
        assertEquals("alice", user.username)
        assertTrue(user.passwordHash != "secret123")
        assertTrue(PasswordHasher.verify("secret123", user.salt, user.passwordHash))
        assertEquals("alice", session.value)
    }

    @Test
    fun `register with existing username fails`() = runCase { repo, _, session ->
        repo.register("alice", "secret123")
        session.clear()

        val result = repo.register("alice", "another66")

        assertTrue(result is ApiResult.Error)
        val error = (result as ApiResult.Error).throwable as AppException.Business
        assertEquals(UserRepository.CODE_USER_EXISTS, error.code)
        assertNull(session.value)
    }

    @Test
    fun `login succeeds with correct password`() = runCase { repo, _, session ->
        repo.register("alice", "secret123")
        session.clear()

        val result = repo.login("alice", "secret123")

        assertTrue(result is ApiResult.Success)
        assertEquals("alice", session.value)
    }

    @Test
    fun `login fails with wrong password`() = runCase { repo, _, session ->
        repo.register("alice", "secret123")
        session.clear()

        val result = repo.login("alice", "wrong-pass")

        assertTrue(result is ApiResult.Error)
        assertEquals(
            UserRepository.CODE_WRONG_PASSWORD,
            ((result as ApiResult.Error).throwable as AppException.Business).code
        )
        assertNull(session.value)
    }

    @Test
    fun `login fails for unknown user`() = runCase { repo, _, _ ->
        val result = repo.login("nobody", "secret123")

        assertTrue(result is ApiResult.Error)
        assertEquals(
            UserRepository.CODE_USER_NOT_FOUND,
            ((result as ApiResult.Error).throwable as AppException.Business).code
        )
    }

    @Test
    fun `logout clears session`() = runCase { repo, _, session ->
        repo.register("alice", "secret123")
        repo.logout()
        assertNull(session.value)
    }
}
```

- [ ] **Step 4: 运行确认失败**

Run: `./gradlew :module_mine:testDebugUnitTest --tests "com.btg.mine.data.UserRepositoryTest"`
Expected: 编译失败(UserRepository 未定义)

- [ ] **Step 5: 实现 UserRepository.kt**

```kotlin
package com.btg.mine.data

import com.btg.common.network.AppException
import com.btg.common.network.safeApiCall
import com.btg.common.result.ApiResult
import com.btg.mine.data.local.UserDao
import com.btg.mine.data.local.UserEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 本地账号仓库：注册 / 登录 / 登出，登录态经 SessionStore 持久化。 */
class UserRepository(
    private val dao: UserDao,
    private val session: SessionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** 当前登录用户名，未登录为 null。 */
    val currentUser: Flow<String?> = session.currentUser

    /** 注册：用户名唯一校验 → 加盐哈希入库 → 自动登录。 */
    suspend fun register(username: String, password: String): ApiResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                if (dao.findByUsername(username) != null) {
                    throw AppException.Business(CODE_USER_EXISTS, "用户名已存在")
                }
                val salt = PasswordHasher.generateSalt()
                dao.insert(
                    UserEntity(
                        username = username,
                        passwordHash = PasswordHasher.hash(password, salt),
                        salt = salt,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                session.save(username)
            }
        }

    suspend fun login(username: String, password: String): ApiResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val user = dao.findByUsername(username)
                    ?: throw AppException.Business(CODE_USER_NOT_FOUND, "用户不存在")
                if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                    throw AppException.Business(CODE_WRONG_PASSWORD, "密码错误")
                }
                session.save(username)
            }
        }

    suspend fun logout() = session.clear()

    companion object {
        const val CODE_USER_EXISTS = 1
        const val CODE_USER_NOT_FOUND = 2
        const val CODE_WRONG_PASSWORD = 3
    }
}
```

- [ ] **Step 6: 运行测试通过**

Run: `./gradlew :module_mine:testDebugUnitTest`
Expected: PasswordHasherTest + UserRepositoryTest 全部 PASS

- [ ] **Step 7: Commit**

```bash
git add module_mine/src
git commit -m "feat: user repository with session store and local auth logic"
```

---

### Task 4: Hilt 装配 MineModule

**Files:**
- Create: `module_mine/src/main/java/com/btg/mine/di/MineModule.kt`

**Interfaces:**
- Consumes: `MineDatabase`、`UserDao`、`DataStoreSessionStore`、`UserRepository`、lib_common `PreferenceStore`
- Produces: Hilt 单例 `MineDatabase`、`UserDao`、`SessionStore`、`UserRepository`

- [ ] **Step 1: MineModule.kt**

```kotlin
package com.btg.mine.di

import android.content.Context
import androidx.room.Room
import com.btg.common.storage.PreferenceStore
import com.btg.mine.data.DataStoreSessionStore
import com.btg.mine.data.SessionStore
import com.btg.mine.data.UserRepository
import com.btg.mine.data.local.MineDatabase
import com.btg.mine.data.local.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MineModule {

    @Provides
    @Singleton
    fun provideMineDatabase(@ApplicationContext context: Context): MineDatabase =
        Room.databaseBuilder(context, MineDatabase::class.java, "mine.db").build()

    @Provides
    @Singleton
    fun provideUserDao(db: MineDatabase): UserDao = db.userDao()

    /** DataStore 同名文件只能有一个实例，必须单例提供。 */
    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        DataStoreSessionStore(PreferenceStore(context, "mine_prefs"))

    @Provides
    @Singleton
    fun provideUserRepository(dao: UserDao, session: SessionStore): UserRepository =
        UserRepository(dao, session)
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add module_mine/src/main/java/com/btg/mine/di/MineModule.kt
git commit -m "feat: wire mine module dependencies with hilt"
```

---

### Task 5: 三个页面 ViewModel(注册校验 TDD)

**Files:**
- Create: `module_mine/src/main/java/com/btg/mine/ui/MineViewModel.kt`
- Create: `module_mine/src/main/java/com/btg/mine/ui/LoginViewModel.kt`
- Create: `module_mine/src/main/java/com/btg/mine/ui/RegisterViewModel.kt`
- Test: `module_mine/src/test/java/com/btg/mine/ui/RegisterViewModelTest.kt`

**Interfaces:**
- Consumes: `UserRepository`、`BaseViewModel.postError/errorEvent`
- Produces:
  - `MineViewModel { currentUser: StateFlow<String?>; fun logout() }`
  - `LoginViewModel { isSubmitting: StateFlow<Boolean>; loginSuccess: Flow<Unit>; fun login(username, password) }`
  - `RegisterViewModel { isSubmitting: StateFlow<Boolean>; registerSuccess: Flow<Unit>; fun register(username, password, confirm) }`

- [ ] **Step 1: 写失败测试 RegisterViewModelTest**

```kotlin
package com.btg.mine.ui

import com.btg.mine.data.SessionStore
import com.btg.mine.data.UserRepository
import com.btg.mine.data.local.UserDao
import com.btg.mine.data.local.UserEntity
import com.btg.mine.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeUserDao : UserDao {
        val users = mutableListOf<UserEntity>()
        override suspend fun findByUsername(username: String): UserEntity? =
            users.firstOrNull { it.username == username }
        override suspend fun insert(item: UserEntity): Long { users.add(item); return 1 }
        override suspend fun insertAll(items: List<UserEntity>) { users.addAll(items) }
        override suspend fun update(item: UserEntity) = Unit
        override suspend fun delete(item: UserEntity) = Unit
    }

    private class FakeSessionStore : SessionStore {
        private val state = MutableStateFlow<String?>(null)
        override val currentUser: Flow<String?> = state
        override suspend fun save(username: String) { state.value = username }
        override suspend fun clear() { state.value = null }
    }

    private val dao = FakeUserDao()

    private fun viewModel() = RegisterViewModel(
        UserRepository(dao, FakeSessionStore(), mainDispatcherRule.testDispatcher)
    )

    @Test
    fun `register success emits registerSuccess`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        var succeeded = false
        val job = launch { vm.registerSuccess.collect { succeeded = true } }

        vm.register("alice", "secret123", "secret123")

        assertTrue(succeeded)
        assertEquals(1, dao.users.size)
        job.cancel()
    }

    @Test
    fun `blank username posts error and does not register`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        val errors = mutableListOf<String>()
        val job = launch { vm.errorEvent.collect { errors.add(it) } }

        vm.register("  ", "secret123", "secret123")

        assertTrue(errors.isNotEmpty())
        assertTrue(dao.users.isEmpty())
        job.cancel()
    }

    @Test
    fun `short password posts error`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        val errors = mutableListOf<String>()
        val job = launch { vm.errorEvent.collect { errors.add(it) } }

        vm.register("alice", "12345", "12345")

        assertTrue(errors.isNotEmpty())
        assertTrue(dao.users.isEmpty())
        job.cancel()
    }

    @Test
    fun `mismatched confirm posts error`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        val errors = mutableListOf<String>()
        val job = launch { vm.errorEvent.collect { errors.add(it) } }

        vm.register("alice", "secret123", "secret124")

        assertTrue(errors.isNotEmpty())
        assertTrue(dao.users.isEmpty())
        job.cancel()
    }

    @Test
    fun `duplicate username posts business error message`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.register("alice", "secret123", "secret123")
        val errors = mutableListOf<String>()
        val job = launch { vm.errorEvent.collect { errors.add(it) } }

        vm.register("alice", "secret456", "secret456")

        assertEquals("用户名已存在", errors.first())
        job.cancel()
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :module_mine:testDebugUnitTest --tests "com.btg.mine.ui.RegisterViewModelTest"`
Expected: 编译失败(RegisterViewModel 未定义)

- [ ] **Step 3: 实现三个 ViewModel**

`RegisterViewModel.kt`:

```kotlin
package com.btg.mine.ui

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.mine.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: UserRepository,
) : BaseViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _registerSuccess = Channel<Unit>(Channel.BUFFERED)
    val registerSuccess: Flow<Unit> = _registerSuccess.receiveAsFlow()

    fun register(username: String, password: String, confirm: String) {
        val name = username.trim()
        when {
            name.isBlank() -> { postError("用户名不能为空"); return }
            password.length < 6 -> { postError("密码至少 6 位"); return }
            password != confirm -> { postError("两次输入的密码不一致"); return }
        }
        _isSubmitting.value = true
        viewModelScope.launch {
            when (val result = repository.register(name, password)) {
                is ApiResult.Success -> _registerSuccess.send(Unit)
                is ApiResult.Error -> postError(result.throwable.message ?: "注册失败")
            }
            _isSubmitting.value = false
        }
    }
}
```

`LoginViewModel.kt`:

```kotlin
package com.btg.mine.ui

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.mine.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: UserRepository,
) : BaseViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _loginSuccess = Channel<Unit>(Channel.BUFFERED)
    val loginSuccess: Flow<Unit> = _loginSuccess.receiveAsFlow()

    fun login(username: String, password: String) {
        val name = username.trim()
        if (name.isBlank() || password.isBlank()) {
            postError("用户名和密码不能为空")
            return
        }
        _isSubmitting.value = true
        viewModelScope.launch {
            when (val result = repository.login(name, password)) {
                is ApiResult.Success -> _loginSuccess.send(Unit)
                is ApiResult.Error -> postError(result.throwable.message ?: "登录失败")
            }
            _isSubmitting.value = false
        }
    }
}
```

`MineViewModel.kt`:

```kotlin
package com.btg.mine.ui

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.mine.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MineViewModel @Inject constructor(
    private val repository: UserRepository,
) : BaseViewModel() {

    /** 当前登录用户名，未登录为 null。 */
    val currentUser: StateFlow<String?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
```

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :module_mine:testDebugUnitTest`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add module_mine/src
git commit -m "feat: mine/login/register viewmodels with input validation"
```

---

### Task 6: 三个页面 UI + 导航

**Files:**
- Modify: `module_mine/src/main/java/com/btg/mine/ui/MineFragment.kt`(整体重写)
- Create: `module_mine/src/main/java/com/btg/mine/ui/LoginFragment.kt`
- Create: `module_mine/src/main/java/com/btg/mine/ui/RegisterFragment.kt`
- Modify: `module_mine/src/main/res/layout/fragment_mine.xml`(整体重写)
- Create: `module_mine/src/main/res/layout/fragment_login.xml`
- Create: `module_mine/src/main/res/layout/fragment_register.xml`
- Modify: `module_mine/src/main/res/navigation/nav_mine.xml`(整体重写)
- Modify: `module_mine/src/main/res/values/strings.xml`(整体重写)

**Interfaces:**
- Consumes: 三个 ViewModel、`collectOnStarted`、`Context.toast`、`View.setOnDebouncedClickListener`(lib_common ViewExt)
- Produces: nav_mine 完整图(mine → login → register);登录/注册页不显示底部导航(id 不在 app 的 topLevelDestinations 中,阶段 1 已实现)

- [ ] **Step 1: 重写 strings.xml**

```xml
<resources>
    <string name="mine_title">我的</string>
    <string name="login_title">登录</string>
    <string name="register_title">注册</string>

    <string name="mine_not_logged_in">未登录</string>
    <string name="mine_go_login">登录 / 注册</string>
    <string name="mine_logout">退出登录</string>

    <string name="hint_username">用户名</string>
    <string name="hint_password">密码</string>
    <string name="hint_confirm_password">确认密码</string>
    <string name="btn_login">登录</string>
    <string name="btn_register">注册</string>
    <string name="go_register">没有账号？去注册</string>
    <string name="login_success">登录成功</string>
    <string name="register_success">注册成功</string>
</resources>
```

- [ ] **Step 2: 重写 fragment_mine.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:id="@+id/guestGroup"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="32dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/mine_not_logged_in"
            android:textSize="18sp" />

        <Button
            android:id="@+id/btnGoLogin"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/mine_go_login" />

    </LinearLayout>

    <LinearLayout
        android:id="@+id/userGroup"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="32dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/usernameText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="20sp"
            android:textStyle="bold" />

        <Button
            android:id="@+id/btnLogout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/mine_logout" />

    </LinearLayout>

</FrameLayout>
```

- [ ] **Step 3: fragment_login.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/usernameLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/hint_username">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/usernameEdit"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="text"
            android:maxLines="1" />

    </com.google.android.material.textfield.TextInputLayout>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/passwordLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:hint="@string/hint_password"
        app:endIconMode="password_toggle">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/passwordEdit"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textPassword"
            android:maxLines="1" />

    </com.google.android.material.textfield.TextInputLayout>

    <Button
        android:id="@+id/btnLogin"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="@string/btn_login" />

    <TextView
        android:id="@+id/btnGoRegister"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="16dp"
        android:padding="8dp"
        android:text="@string/go_register"
        android:textColor="@color/colorPrimary" />

</LinearLayout>
```

注:`@color/colorPrimary` 定义在 app 模块,library 模块引用不到——在 `module_mine/src/main/res/values/colors.xml` 新建:

```xml
<resources>
    <color name="colorPrimary">#008577</color>
</resources>
```

- [ ] **Step 4: fragment_register.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/usernameLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/hint_username">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/usernameEdit"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="text"
            android:maxLines="1" />

    </com.google.android.material.textfield.TextInputLayout>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/passwordLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:hint="@string/hint_password"
        app:endIconMode="password_toggle">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/passwordEdit"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textPassword"
            android:maxLines="1" />

    </com.google.android.material.textfield.TextInputLayout>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/confirmLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:hint="@string/hint_confirm_password"
        app:endIconMode="password_toggle">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/confirmEdit"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textPassword"
            android:maxLines="1" />

    </com.google.android.material.textfield.TextInputLayout>

    <Button
        android:id="@+id/btnRegister"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="@string/btn_register" />

</LinearLayout>
```

- [ ] **Step 5: 重写 nav_mine.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_mine"
    app:startDestination="@id/mineFragment">

    <fragment
        android:id="@+id/mineFragment"
        android:name="com.btg.mine.ui.MineFragment"
        android:label="@string/mine_title">
        <action
            android:id="@+id/action_mine_to_login"
            app:destination="@id/loginFragment" />
    </fragment>

    <fragment
        android:id="@+id/loginFragment"
        android:name="com.btg.mine.ui.LoginFragment"
        android:label="@string/login_title">
        <action
            android:id="@+id/action_login_to_register"
            app:destination="@id/registerFragment" />
    </fragment>

    <fragment
        android:id="@+id/registerFragment"
        android:name="com.btg.mine.ui.RegisterFragment"
        android:label="@string/register_title" />

</navigation>
```

- [ ] **Step 6: 重写 MineFragment.kt**

```kotlin
package com.btg.mine.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ext.setOnDebouncedClickListener
import com.btg.mine.R
import com.btg.mine.databinding.FragmentMineBinding
import dagger.hilt.android.AndroidEntryPoint

/** 我的页：依登录态切换 未登录入口 / 用户信息 + 退出登录。 */
@AndroidEntryPoint
class MineFragment : BaseFragment<FragmentMineBinding>() {

    private val viewModel: MineViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMineBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnGoLogin.setOnDebouncedClickListener {
            findNavController().navigate(R.id.action_mine_to_login)
        }
        binding.btnLogout.setOnDebouncedClickListener { viewModel.logout() }

        viewModel.currentUser.collectOnStarted(viewLifecycleOwner) { user ->
            binding.guestGroup.isVisible = user == null
            binding.userGroup.isVisible = user != null
            binding.usernameText.text = user.orEmpty()
        }
    }
}
```

- [ ] **Step 7: LoginFragment.kt**

```kotlin
package com.btg.mine.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ext.setOnDebouncedClickListener
import com.btg.common.ui.toast
import com.btg.mine.R
import com.btg.mine.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    private val viewModel: LoginViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLogin.setOnDebouncedClickListener {
            viewModel.login(
                binding.usernameEdit.text.toString(),
                binding.passwordEdit.text.toString(),
            )
        }
        binding.btnGoRegister.setOnDebouncedClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        viewModel.isSubmitting.collectOnStarted(viewLifecycleOwner) {
            binding.btnLogin.isEnabled = !it
        }
        viewModel.loginSuccess.collectOnStarted(viewLifecycleOwner) {
            requireContext().toast(getString(R.string.login_success))
            findNavController().popBackStack()
        }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }
}
```

- [ ] **Step 8: RegisterFragment.kt**

```kotlin
package com.btg.mine.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ext.setOnDebouncedClickListener
import com.btg.common.ui.toast
import com.btg.mine.R
import com.btg.mine.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {

    private val viewModel: RegisterViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRegisterBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRegister.setOnDebouncedClickListener {
            viewModel.register(
                binding.usernameEdit.text.toString(),
                binding.passwordEdit.text.toString(),
                binding.confirmEdit.text.toString(),
            )
        }

        viewModel.isSubmitting.collectOnStarted(viewLifecycleOwner) {
            binding.btnRegister.isEnabled = !it
        }
        viewModel.registerSuccess.collectOnStarted(viewLifecycleOwner) {
            requireContext().toast(getString(R.string.register_success))
            // 注册即自动登录，直接回到我的页
            findNavController().popBackStack(R.id.mineFragment, false)
        }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }
}
```

- [ ] **Step 9: 全量验证**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL,全部测试通过

- [ ] **Step 10: 手动验证(装机)**

Run: `./gradlew installDebug`
检查:我的页未登录 → 点「登录 / 注册」进登录页(底部导航隐藏)→ 去注册 → 注册(重名/短密码/不一致各报对应 toast)→ 注册成功回我的页显示用户名 → 退出登录回未登录态 → 再登录(错密码报错)→ 杀进程重启,登录态保持。

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: mine module ui with local login, register and logout"
```

---

### Task 7: 更新 CLAUDE.md 反映新工程形态

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Produces: 文档与代码一致,后续会话不被过时信息误导

- [ ] **Step 1: 更新「项目现状」段**

把开头「项目现状」段中"能力演示台(Home → 新闻 / 组件 / 存储 三个演示页)"的表述替换为:

> Android 多模块工程:在可复用 Kotlin MVVM 脚手架之上落地了真实三模块 App——**新闻**(聚合数据接口:分类列表 + 分页 + 详情 WebView)、**天气**(占位)、**我的**(本地 Room 账号:注册/登录/退出,DataStore 持久登录态)。单 Activity + BottomNavigationView 三 Tab,每个业务是独立 feature module,自带 navigation graph,app 只做壳。

- [ ] **Step 2: 更新「模块结构与依赖方向」段**

依赖图替换为:

```
app ──▶ module_news / module_weather / module_mine ──▶ lib_common ──api──▶ lib_opensource / lib_widget
```

模块说明补充:
- **app** (`com.btg.mvvm`):壳。`App`(@HiltAndroidApp)、`MainActivity`(NavHost + BottomNavigationView,`nav_main.xml` include 三个子 graph,非顶级页面隐藏底部导航)。
- **module_news** (`com.btg.news`):新闻。聚合数据接口(`JuheResponse.unwrap` → safeApiCall 体系,`@JuheRetrofit` 独立 baseUrl);API key 走 `local.properties` 的 `JUHE_API_KEY` → BuildConfig,不入库。
- **module_weather** (`com.btg.weather`):占位。
- **module_mine** (`com.btg.mine`):本地账号。Room `users` 表 + `PasswordHasher`(SHA-256+盐)+ `SessionStore`(DataStore)。

并注明:原 app 内的演示页(Home/组件/存储)与「示范业务(app)」一节所述结构已被本次改造替代;设计文档见 `docs/superpowers/specs/2026-07-06-news-app-modular-design.md`。构建命令一节的单测示例路径改为 `:module_news:testDebugUnitTest` / `:module_mine:testDebugUnitTest`。

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for modular news/weather/mine app structure"
```
