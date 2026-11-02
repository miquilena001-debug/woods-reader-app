package com.example.woodsreader.core.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class WoodsBackupVaultV3Test {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun vaultV3_serializesAndDeserializesHeaderAndCollections() {
        val header = WoodsBackupHeader(
            formatVersion = 3,
            app = "Woods Reader",
            appVersionCode = 11,
            appVersionName = "1.0.10",
            roomSchemaVersion = 11,
            exportedAt = 1700000000000L,
            exportDateFormatted = "2026-11-02 12:00:00",
            checksumSha256 = "dummy-sha256",
            booksCount = 1,
            highlightsCount = 2,
            bookmarksCount = 0,
            collectionsCount = 1,
            sessionsCount = 0,
            readingStatsCount = 0
        )

        val collection = CollectionBackup(
            id = "col-1",
            name = "Fantasía Épica",
            description = "Novelas favoritas",
            createdAt = 1700000000000L
        )

        val book = BookProgressBackup(
            id = "book-1",
            title = "Test Novel",
            fileHash = "hash-123",
            filePath = "/storage/emulated/0/Documents/WoodsReader/Epubs/Test.epub",
            readingProgressPercent = 45.5f
        )

        val vault = WoodsReaderBackupVault(
            formatVersion = 3,
            header = header,
            books = listOf(book),
            collections = listOf(collection)
        )

        val encoded = json.encodeToString(vault)
        assertTrue(encoded.contains("\"formatVersion\": 3"))
        assertTrue(encoded.contains("Fantasía Épica"))
        assertTrue(encoded.contains("dummy-sha256"))

        val decoded = json.decodeFromString<WoodsReaderBackupVault>(encoded)
        assertEquals(3, decoded.formatVersion)
        assertNotNull(decoded.header)
        assertEquals(1, decoded.header?.collectionsCount)
        assertEquals("Fantasía Épica", decoded.collections.first().name)
        assertEquals(1, decoded.books.size)
        assertEquals("Test Novel", decoded.books.first().title)
    }

    @Test
    fun vaultV3_backwardCompatibleWithV2Json() {
        val v2Json = """
            {
                "formatVersion": 2,
                "app": "Woods Reader",
                "exportedAt": 1699999999000,
                "exportDateFormatted": "2026-11-01 10:00:00",
                "books": [
                    {
                        "id": "book-v2",
                        "title": "Legacy Book",
                        "fileHash": "legacy-hash",
                        "filePath": "/cache/legacy.epub",
                        "readingProgressPercent": 10.0
                    }
                ],
                "highlights": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString<WoodsReaderBackupVault>(v2Json)
        assertEquals(2, decoded.formatVersion)
        assertEquals("Woods Reader", decoded.app)
        assertEquals(1, decoded.books.size)
        assertEquals("Legacy Book", decoded.books.first().title)
        assertTrue(decoded.collections.isEmpty())
        assertEquals(null, decoded.header)
    }

    @Test
    fun calculateSha256_producesCorrectDigest() {
        val input = "Test string for SHA-256 verification"
        val md = MessageDigest.getInstance("SHA-256")
        val expected = md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        val actual = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertEquals(expected, actual)
    }
}
