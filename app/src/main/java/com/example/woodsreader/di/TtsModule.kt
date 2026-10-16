package com.example.woodsreader.di

import com.example.woodsreader.core.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {
    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext context: android.content.Context,
        ttsRuleDao: com.example.woodsreader.data.database.TtsRuleDao,
        userPreferencesRepository: com.example.woodsreader.data.datastore.UserPreferencesRepository,
        sherpaModelManager: com.example.woodsreader.core.tts.SherpaModelManager,
        edgeNeuralTtsClient: com.example.woodsreader.core.tts.EdgeNeuralTtsClient
    ): TtsManager = TtsManager(context, ttsRuleDao, userPreferencesRepository, sherpaModelManager, edgeNeuralTtsClient)

}