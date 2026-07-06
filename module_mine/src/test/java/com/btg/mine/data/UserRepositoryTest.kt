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
