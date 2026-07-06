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
