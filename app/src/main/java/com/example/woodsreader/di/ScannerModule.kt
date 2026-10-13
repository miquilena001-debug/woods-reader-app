package com.example.woodsreader.di

import com.example.woodsreader.data.scanner.AndroidStorageRootProvider
import com.example.woodsreader.data.scanner.DeepFileSystemScanner
import com.example.woodsreader.data.scanner.FileSystemScanner
import com.example.woodsreader.data.scanner.MediaStoreBookScanner
import com.example.woodsreader.data.scanner.MediaStoreScanner
import com.example.woodsreader.data.scanner.StorageRootProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindStorageRootProvider(
        provider: AndroidStorageRootProvider
    ): StorageRootProvider

    @Binds
    @Singleton
    abstract fun bindFileSystemScanner(
        scanner: DeepFileSystemScanner
    ): FileSystemScanner

    @Binds
    @Singleton
    abstract fun bindMediaStoreScanner(
        scanner: MediaStoreBookScanner
    ): MediaStoreScanner
}
