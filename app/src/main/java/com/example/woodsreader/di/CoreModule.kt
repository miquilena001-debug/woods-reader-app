package com.example.woodsreader.di

import com.example.woodsreader.core.managers.SystemTimeProvider
import com.example.woodsreader.core.managers.TimeProvider
import com.example.woodsreader.core.managers.SleepTimer
import com.example.woodsreader.core.managers.SleepTimerManager
import com.example.woodsreader.service.Summarizer
import com.example.woodsreader.service.TextRankSummarizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(
        systemTimeProvider: SystemTimeProvider
    ): TimeProvider

    @Binds
    @Singleton
    abstract fun bindSummarizer(
        textRankSummarizer: TextRankSummarizer
    ): Summarizer

    @Binds
    @Singleton
    abstract fun bindSleepTimer(
        sleepTimerManager: SleepTimerManager
    ): SleepTimer
}
