package com.example.woodsreader.di

import android.content.Context
import androidx.room.Room
import com.example.woodsreader.data.database.AppDatabase
import com.example.woodsreader.data.database.AppTypeConverters
import com.example.woodsreader.data.database.BookDao
import com.example.woodsreader.data.database.BookmarkDao
import com.example.woodsreader.data.database.CollectionDao
import com.example.woodsreader.data.database.HighlightDao
import com.example.woodsreader.data.database.ReadingSessionDao
import com.example.woodsreader.data.database.ReadingStatsDao
import com.example.woodsreader.data.database.TtsRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppTypeConverters(): AppTypeConverters = AppTypeConverters()

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        converters: AppTypeConverters
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "lector_epub.db"
    )
        .addTypeConverter(converters)
        // << CHANGE: Registro de migraciones formales usando los objetos pre-instanciados
        .addMigrations(
            com.example.woodsreader.data.database.migration.MIGRATION_1_2,
            com.example.woodsreader.data.database.migration.MIGRATION_2_3,
            com.example.woodsreader.data.database.migration.MIGRATION_3_4,
            com.example.woodsreader.data.database.migration.MIGRATION_4_5,
            com.example.woodsreader.data.database.migration.MIGRATION_5_6,
            com.example.woodsreader.data.database.migration.MIGRATION_6_7,
            com.example.woodsreader.data.database.migration.MIGRATION_7_8,
            com.example.woodsreader.data.database.migration.MIGRATION_8_9
        )
        // .fallbackToDestructiveMigration() // REMOVIDO: Protección estricta de datos (Parte 4)
        .build()

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun provideHighlightDao(db: AppDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideReadingSessionDao(db: AppDatabase): ReadingSessionDao = db.readingSessionDao()

    @Provides
    fun provideCollectionDao(db: AppDatabase): CollectionDao = db.collectionDao()

    @Provides
    fun provideReadingStatsDao(db: AppDatabase): ReadingStatsDao = db.readingStatsDao()

    @Provides
    fun provideTtsRuleDao(db: AppDatabase): TtsRuleDao = db.ttsRuleDao()
}
