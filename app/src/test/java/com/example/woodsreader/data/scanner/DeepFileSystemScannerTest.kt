package com.example.woodsreader.data.scanner

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DeepFileSystemScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scanner: DeepFileSystemScanner
    private lateinit var rootProvider: StorageRootProvider
    private lateinit var rootDir: File

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("root")
        
        rootProvider = object : StorageRootProvider {
            override fun getStorageRoots(): List<File> = listOf(rootDir)
        }
        
        scanner = DeepFileSystemScanner(rootProvider)
    }

    @Test
    fun testIgnoresBlacklistedFoldersAndHiddenFolders() = runTest {
        // Blacklisted
        val androidDir = File(rootDir, "Android")
        androidDir.mkdirs()
        File(androidDir, "test.epub").writeText("dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content")

        // Hidden
        val hiddenDir = File(rootDir, ".hidden")
        hiddenDir.mkdirs()
        File(hiddenDir, "test2.epub").writeText("dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content dummy content")

        val results = scanner.scanAllDevice().toList()
        assertTrue("Results should be empty since folders are ignored", results.isEmpty())
    }

    @Test
    fun testIgnoresUnsupportedExtensionsAndSmallFiles() = runTest {
        val validDir = File(rootDir, "Books")
        validDir.mkdirs()

        // Unsupported extension
        val unsupportedFile = File(validDir, "image.jpg")
        unsupportedFile.writeText("a".repeat(2000))

        // Supported extension but too small (< 1024 bytes)
        val smallFile = File(validDir, "small.epub")
        smallFile.writeText("tiny")

        val results = scanner.scanAllDevice().toList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun testFindsSupportedValidFilesDeepInHierarchy() = runTest {
        val l1 = File(rootDir, "Level1")
        val l2 = File(l1, "Level2")
        val l3 = File(l2, "Level3")
        l3.mkdirs()

        val validFile1 = File(l3, "book1.epub")
        validFile1.writeText("a".repeat(1500))

        val validFile2 = File(l2, "book2.pdf")
        validFile2.writeText("b".repeat(1500))

        val results = scanner.scanAllDevice().toList()
        assertEquals(2, results.size)
        assertTrue(results.contains(validFile1))
        assertTrue(results.contains(validFile2))
    }
}
