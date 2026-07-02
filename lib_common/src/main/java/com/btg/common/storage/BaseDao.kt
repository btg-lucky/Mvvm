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
