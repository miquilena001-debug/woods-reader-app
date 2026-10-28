package com.example.woodsreader.domain.reader

import android.content.Context
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.repository.BookRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import java.io.File

class LoadBookFastUseCaseTest {

    private val context: Context = mockk(relaxed = true)
    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val publicationOpener: PublicationOpener = mockk(relaxed = true)
    private val assetRetriever: AssetRetriever = mockk(relaxed = true)

    private lateinit var useCase: LoadBookFastUseCase

    @Before
    fun setUp() {
        useCase = LoadBookFastUseCase(
            context = context,
            bookRepository = bookRepository,
            assetRetriever = assetRetriever,
            publicationOpener = publicationOpener
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockLink(hrefStr: String, title: String? = null, children: List<Link> = emptyList()): Link {
        val link = mockk<Link>(relaxed = true)
        val url = mockk<Url>(relaxed = true)
        every { url.toString() } returns hrefStr
        every { link.url() } returns url
        val hrefObj = mockk<org.readium.r2.shared.publication.Href>(relaxed = true)
        every { hrefObj.toString() } returns hrefStr
        every { link.href } returns hrefObj
        every { link.title } returns title
        every { link.mediaType } returns MediaType.XHTML
        every { link.children } returns children
        return link
    }

    private fun createMockLocator(hrefStr: String): Locator {
        val locator = mockk<Locator>(relaxed = true)
        val url = mockk<Url>(relaxed = true)
        every { url.toString() } returns hrefStr
        every { locator.href } returns url
        return locator
    }

    // ── Cascada de Restauración de Posición (validateAndRestoreLocator) ───────

    @Test
    fun testValidateAndRestoreLocator_level1_validCfiInCacheReturnsCfiLocator() {
        val link0 = createMockLink("chapter1.xhtml", "Capítulo 1")
        val link1 = createMockLink("chapter2.xhtml", "Capítulo 2")
        val publication = mockk<Publication>(relaxed = true)
        every { publication.readingOrder } returns listOf(link0, link1)

        val cache = mapOf(
            "chapter1.xhtml" to 0,
            "chapter2.xhtml" to 1
        )

        mockkObject(Locator.Companion)
        val mockLocator = createMockLocator("chapter2.xhtml")
        every { Locator.fromJSON(any()) } returns mockLocator

        val restored = useCase.validateAndRestoreLocator(
            publication = publication,
            cache = cache,
            cfiJson = """{"href":"chapter2.xhtml"}""",
            fallbackHref = "chapter1.xhtml",
            fallbackPosition = 1
        )

        assertNotNull(restored)
        assertEquals("chapter2.xhtml", restored!!.href.toString())
    }

    @Test
    fun testValidateAndRestoreLocator_level1_cfiNotInCacheFallsThroughToLevel2() {
        val link0 = createMockLink("chapter1.xhtml", "Capítulo 1")
        val publication = mockk<Publication>(relaxed = true)
        every { publication.readingOrder } returns listOf(link0)

        val cache = mapOf("chapter1.xhtml" to 0)

        mockkObject(Locator.Companion)
        val orphanedLocator = createMockLocator("deleted_chapter.xhtml")
        every { Locator.fromJSON(any()) } returns orphanedLocator

        val restored = useCase.validateAndRestoreLocator(
            publication = publication,
            cache = cache,
            cfiJson = """{"href":"deleted_chapter.xhtml"}""",
            fallbackHref = "chapter1.xhtml",
            fallbackPosition = 1
        )

        assertNotNull(restored)
        assertEquals("chapter1.xhtml", restored!!.href.toString())
    }

    @Test
    fun testValidateAndRestoreLocator_level1_malformedCfiJsonFallsThroughToLevel2() {
        val link0 = createMockLink("chapter1.xhtml", "Capítulo 1")
        val publication = mockk<Publication>(relaxed = true)
        every { publication.readingOrder } returns listOf(link0)

        val cache = mapOf("chapter1.xhtml" to 0)

        val restored = useCase.validateAndRestoreLocator(
            publication = publication,
            cache = cache,
            cfiJson = "not a valid json {{{",
            fallbackHref = "chapter1.xhtml",
            fallbackPosition = 1
        )

        assertNotNull(restored)
        assertEquals("chapter1.xhtml", restored!!.href.toString())
    }

    @Test
    fun testValidateAndRestoreLocator_level2_validFallbackHrefReturnsConstructedLocator() {
        val link0 = createMockLink("intro.xhtml", "Introducción")
        val link1 = createMockLink("chapter1.xhtml", "Capítulo 1")
        val publication = mockk<Publication>(relaxed = true)
        every { publication.readingOrder } returns listOf(link0, link1)

        val cache = mapOf(
            "intro.xhtml" to 0,
            "chapter1.xhtml" to 1
        )

        val restored = useCase.validateAndRestoreLocator(
            publication = publication,
            cache = cache,
            cfiJson = null,
            fallbackHref = "chapter1.xhtml",
            fallbackPosition = 2
        )

        assertNotNull(restored)
        assertEquals("chapter1.xhtml", restored!!.href.toString())
        assertEquals(2, restored.locations.position)
        assertEquals(0.5, restored.locations.totalProgression!!, 0.001)
    }

    @Test
    fun testValidateAndRestoreLocator_level3_allFailedReturnsNull() {
        val link0 = createMockLink("intro.xhtml", "Introducción")
        val publication = mockk<Publication>(relaxed = true)
        every { publication.readingOrder } returns listOf(link0)

        val cache = mapOf("intro.xhtml" to 0)

        val restored = useCase.validateAndRestoreLocator(
            publication = publication,
            cache = cache,
            cfiJson = null,
            fallbackHref = "missing_chapter.xhtml",
            fallbackPosition = 99
        )

        assertNull(restored)
    }

    // ── Fast-path Posiciones Sintéticas para > 80 Capítulos ───────────────────

    @Test
    fun testFastPathForLargeBookOver80ChaptersGeneratesSyntheticLocators() = runTest {
        val tempFile = File.createTempFile("large_book", ".epub")
        io.mockk.mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { mockUri.scheme } returns "file"
        every { mockUri.path } returns tempFile.absolutePath
        every { android.net.Uri.parse(any()) } returns mockUri
        every { android.net.Uri.fromFile(any()) } returns mockUri

        try {
            val book = BookEntity(
                id = "large_book_1",
                title = "Libro Extenso 100 Capítulos",
                author = "Autor",
                filePath = tempFile.absolutePath,
                fileHash = "hash123",
                format = com.example.woodsreader.data.model.BookFormat.EPUB
            )

            // Crear 85 capítulos (> 80 LARGE_BOOK_CHAPTER_THRESHOLD)
            val links = (1..85).map { idx ->
                createMockLink("chap_$idx.xhtml", "Capítulo $idx")
            }

            val mockPub = mockk<Publication>(relaxed = true)
            every { mockPub.readingOrder } returns links
            every { mockPub.tableOfContents } returns emptyList()

            val mockAsset = mockk<Asset>(relaxed = true)
            val assetTry = mockk<Try<Asset, AssetRetriever.RetrieveError>>()
            every { assetTry.getOrNull() } returns mockAsset
            io.mockk.coEvery { assetRetriever.retrieve(any<File>()) } returns assetTry

            val openerTry = mockk<Try<Publication, PublicationOpener.OpenError>>()
            every { openerTry.getOrNull() } returns mockPub
            io.mockk.coEvery { publicationOpener.open(asset = any(), allowUserInteraction = any()) } returns openerTry

            val result = useCase(book)

            assertTrue(result is LoadBookResult.Success)
            val sessionData = (result as LoadBookResult.Success).data
            assertEquals(85, sessionData.positionsByResource.size)
            assertEquals(85, sessionData.totalPositions)

            // Verificar progresión sintética proporcional sin escanear ZIP
            val firstLocator = sessionData.positionsByResource[0][0]
            val lastLocator = sessionData.positionsByResource[84][0]

            assertEquals(0.0, firstLocator.locations.totalProgression!!, 0.001)
            assertEquals(84.0 / 85.0, lastLocator.locations.totalProgression!!, 0.001)
            assertEquals(1, firstLocator.locations.position)
            assertEquals(85, lastLocator.locations.position)
        } finally {
            tempFile.delete()
        }
    }

    // ── Aplanado de Tabla de Contenidos (flattenToc) ─────────────────────────

    @Test
    fun testFlattenTocRecursivelyAplansHierarchicalLinks() {
        val subChild1 = createMockLink("subchild1.xhtml", "Sub 1")
        val child1 = createMockLink("child1.xhtml", "Capítulo 1.1", listOf(subChild1))
        val child2 = createMockLink("child2.xhtml", "Capítulo 1.2")
        val parent1 = createMockLink("part1.xhtml", "Parte 1", listOf(child1, child2))
        val parent2 = createMockLink("part2.xhtml", "Parte 2")

        val flat = useCase.flattenToc(listOf(parent1, parent2))

        assertEquals(5, flat.size)
        assertEquals("part1.xhtml", flat[0].href.toString())
        assertEquals("child1.xhtml", flat[1].href.toString())
        assertEquals("subchild1.xhtml", flat[2].href.toString())
        assertEquals("child2.xhtml", flat[3].href.toString())
        assertEquals("part2.xhtml", flat[4].href.toString())
    }

    @Test
    fun testFlattenTocWithEmptyListReturnsEmpty() {
        val flat = useCase.flattenToc(emptyList())
        assertEquals(0, flat.size)
    }
}
