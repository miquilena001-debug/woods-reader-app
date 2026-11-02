package com.example.woodsreader.di

import com.example.woodsreader.core.storage.StorageRootResolver
import com.example.woodsreader.data.storage.AndroidStorageRootResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindStorageRootResolver(
        resolver: AndroidStorageRootResolver
    ): StorageRootResolver
}
