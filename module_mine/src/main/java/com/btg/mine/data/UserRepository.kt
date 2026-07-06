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
