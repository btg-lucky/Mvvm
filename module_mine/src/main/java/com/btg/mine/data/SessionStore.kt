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
