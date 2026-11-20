package com.example.woodsreader.di

import androidx.datastore.core.DataStoreFactory
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.datastore.UserPreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreModuleCorruptionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `corrupt preferences are backed up and replaced by defaults`() = runTest {
        val source = temporaryFolder.newFile("user_prefs.json")
        val corruptContents = """{"theme":"""
        source.writeText(corruptContents)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val store = DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            corruptionHandler = DataStoreModule.userPreferencesCorruptionHandler { source },
            scope = scope,
            produceFile = { source }
        )

        try {
            assertEquals(UserPreferences(), store.data.first())
            assertEquals(
                corruptContents,
                temporaryFolder.root.resolve("user_prefs.json.corrupt").readText()
            )

            store.updateData { it.copy(fontSizeSp = 22f) }
            assertEquals(22f, store.data.first().fontSizeSp, 0f)
        } finally {
            scope.cancel()
        }
    }
}
