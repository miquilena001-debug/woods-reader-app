package com.example.woodsreader.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FakeStorageRootResolver(
    private val publicRoot: File,
    private val privateRoot: File,
    var externalGranted: Boolean = true
) : StorageRootResolver {
    override fun getPublicWoodsReaderRoot(): File = publicRoot
    override fun getFallbackPrivateRoot(): File = privateRoot
    override fun isExternalAccessGranted(): Boolean = externalGranted
}

class WoodsFolderStructureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var publicRoot: File
    private lateinit var privateRoot: File
    private lateinit var fakeResolver: FakeStorageRootResolver
    private lateinit var folderStructure: WoodsFolderStructure

    @Before
    fun setUp() {
        publicRoot = File(tempFolder.newFolder("Documents"), "WoodsReader")
        privateRoot = File(tempFolder.newFolder("Android_data"), "WoodsReader")
        fakeResolver = FakeStorageRootResolver(publicRoot, privateRoot, externalGranted = true)
        folderStructure = WoodsFolderStructure(fakeResolver)
    }

    @Test
    fun `creates canonical subfolders under public root when permission granted`() {
        assertTrue(folderStructure.backupsDir.exists())
        assertTrue(folderStructure.epubsDir.exists())
        assertTrue(folderStructure.audiobooksDir.exists())
        assertTrue(folderStructure.videosDir.exists())
        assertTrue(folderStructure.bookmarksDir.exists())
        assertTrue(folderStructure.sharedDir.exists())
        assertTrue(folderStructure.siteProfilesDir.exists())

        assertEquals(File(publicRoot, "Backups").absolutePath, folderStructure.backupsDir.absolutePath)
        assertEquals(File(publicRoot, "Epubs").absolutePath, folderStructure.epubsDir.absolutePath)
    }

    @Test
    fun `switches to fallback private root when external permission is denied`() {
        fakeResolver.externalGranted = false

        assertEquals(File(privateRoot, "Backups").absolutePath, folderStructure.backupsDir.absolutePath)
        assertEquals(File(privateRoot, "Epubs").absolutePath, folderStructure.epubsDir.absolutePath)
        assertTrue(folderStructure.backupsDir.exists())
    }

    @Test
    fun `sanitizeFileName cleans forbidden characters and limits length`() {
        val dirty = "Novela: El Secreto/Mágico? *Destino* <Parte 1> | Vol. 2"
        val clean = folderStructure.sanitizeFileName(dirty, maxLength = 40)

        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("|"))
        assertTrue(clean.length <= 40)
    }

    @Test
    fun `resolveUniqueFile resolves collisions with numeric increment`() {
        val parent = folderStructure.epubsDir
        val baseName = "mi_libro"
        val ext = "epub"

        val f1 = folderStructure.resolveUniqueFile(parent, baseName, ext)
        assertEquals("mi_libro.epub", f1.name)
        f1.createNewFile()

        val f2 = folderStructure.resolveUniqueFile(parent, baseName, ext)
        assertEquals("mi_libro (1).epub", f2.name)
        f2.createNewFile()

        val f3 = folderStructure.resolveUniqueFile(parent, baseName, ext)
        assertEquals("mi_libro (2).epub", f3.name)
    }
}
