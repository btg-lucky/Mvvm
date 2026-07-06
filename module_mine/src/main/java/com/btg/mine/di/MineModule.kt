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
