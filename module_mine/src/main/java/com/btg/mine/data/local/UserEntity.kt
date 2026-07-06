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
