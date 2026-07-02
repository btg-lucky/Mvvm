package com.btg.mvvm.di

import com.btg.mvvm.data.repository.NewsRepository
import com.btg.mvvm.data.source.FakeNewsDataSource
import com.btg.mvvm.data.source.NewsDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 新闻数据装配。数据源的唯一装配点：换真实接口时把 FakeNewsDataSource
 * 换成 RemoteNewsDataSource(newsApi)，上层不动。
 */
@Module
@InstallIn(SingletonComponent::class)
object NewsModule {

    @Provides
    @Singleton
    fun provideNewsDataSource(): NewsDataSource = FakeNewsDataSource()

    @Provides
    @Singleton
    fun provideNewsRepository(dataSource: NewsDataSource): NewsRepository =
        NewsRepository(dataSource)
}
