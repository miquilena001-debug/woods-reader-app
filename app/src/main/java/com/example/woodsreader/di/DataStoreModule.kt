package com.example.woodsreader.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.datastore.UserPreferencesSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private const val TAG = "UserPrefsDataStore"

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DataStore<UserPreferences> {
        val produceFile = { context.dataStoreFile(USER_PREFS_FILE_NAME) }
        return DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            corruptionHandler = userPreferencesCorruptionHandler(produceFile),
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = produceFile
        )
    }

    internal fun userPreferencesCorruptionHandler(
        produceFile: () -> File
    ): ReplaceFileCorruptionHandler<UserPreferences> =
        ReplaceFileCorruptionHandler { corruption ->
            val source = produceFile()
            val backup = File("${source.absolutePath}.corrupt")
            try {
                if (source.exists()) source.copyTo(backup, overwrite = true)
            } catch (e: IOException) {
                Log.w(TAG, "Could not preserve corrupt preferences (${e.javaClass.simpleName})")
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not preserve corrupt preferences (${e.javaClass.simpleName})")
            }
            Log.e(
                TAG,
                "Corrupt preferences replaced (${corruption.cause?.javaClass?.simpleName ?: corruption.javaClass.simpleName})"
            )
            UserPreferencesSerializer.defaultValue
        }

    private const val USER_PREFS_FILE_NAME = "user_prefs.json"
}
