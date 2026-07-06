package com.btg.mine.data.local

import androidx.room.Dao
import androidx.room.Query
import com.btg.common.storage.BaseDao

@Dao
interface UserDao : BaseDao<UserEntity> {

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?
}
