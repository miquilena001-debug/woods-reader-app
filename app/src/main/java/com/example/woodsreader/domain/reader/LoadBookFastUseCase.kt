package com.example.woodsreader.domain.reader

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.woodsreader.core.pipeline.EpubPackageSanitizer
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import javax.inject.Inject

/**
 * Datos estructurados devueltos tras la apertura ultra rápida y preparación de índices del libro.
 */
data class LoadedBookSessionData(
    val book: BookEntity,
    val publication: Publication,
    val uri: Uri,
    val resourceIndexCache: Map<String, Int>,
    val positionsByResource: List<List<Locator>>,
    val totalPositions: Int,
    val initialLocator: Locator?,
    val initialPdfPage: Int? = null,
    val flatTableOfContents: List<Link> = emptyList()
)

sealed interface LoadBookResult {
    data class Success(val data: LoadedBookSessionData) : LoadBookResult
    data class Error(val message: String, val throwable: Throwable? = null) : LoadBookResult
}

/**
 * Caso de uso responsable de la apertura acelerada de publicaciones (EPUB / PDF),
 * generación de índices O(1), bypass de posiciones para libros masivos y restauración
 * resiliente de posición sin bloqueo del hilo principal.
 */
class LoadBookFastUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener
) {

    companion object {
        private const val TAG = "LoadBookFastUseCase"
        private const val CHECKPOINT_PREFS = "woods_last_reading_checkpoint"
        const val LARGE_BOOK_CHAPTER_THRESHOLD = 80
    }

    suspend fun getBookEntity(bookId: String): BookEntity? = withContext(Dispatchers.IO) {
        bookRepository.getBook(bookId)
    }

    suspend operator fun invoke(book: BookEntity): LoadBookResult {
        return withContext(Dispatchers.IO) {
            try {
                val uri = resolveBookUri(book)
                    ?: return@withContext LoadBookResult.Error("Archivo no accesible: ${book.title}")

                val pub = openPublication(uri, book)
                    ?: return@withContext LoadBookResult.Error("No se pudo abrir el formato de ${book.title}")

                // 1. Construcción O(1) de resourceIndexCache con pre-asignación en Dispatchers.Default
                val fastIndexCache = withContext(Dispatchers.Default) {
                    val order = pub.readingOrder
                    val map = HashMap<String, Int>(order.size * 4)
                    for (i in order.indices) {
                        val rawHref = order[i].href.toString()
                        val cleanHref = rawHref.substringBefore('#').removePrefix("/")
                        val filename = rawHref.substringAfterLast('/')
                        val cleanFilename = filename.substringBefore('#')
                        map[rawHref] = i
                        map[cleanHref] = i
                        map[filename] = i
                        map[cleanFilename] = i
                    }
                    map
                }

                // 2. Posiciones sintéticas inmediatas O(N) sin escanear ZIP en libros extensos (> 80 capítulos)
                val totalChapters = pub.readingOrder.size.coerceAtLeast(1)
                val computedPositions: List<List<Locator>> = if (totalChapters > LARGE_BOOK_CHAPTER_THRESHOLD) {
                    withContext(Dispatchers.Default) {
                        val invTotal = 1.0 / totalChapters.toDouble()
                        pub.readingOrder.mapIndexed { index, link ->
                            listOf(
                                Locator(
                                    href = link.url(),
                                    mediaType = link.mediaType ?: MediaType.XHTML,
                                    title = link.title,
                                    locations = Locator.Locations(
                                        progression = 0.0,
                                        position = index + 1,
                                        totalProgression = index.toDouble() * invTotal
                                    )
                                )
                            )
                        }
                    }
                } else {
                    val rawPos = runCatching { pub.positions() }.getOrDefault(emptyList())
                    if (rawPos.isNotEmpty()) {
                        rawPos.groupBy { it.href.toString().substringBefore('#') }.values.toList()
                    } else {
                        val invTotal = 1.0 / totalChapters.toDouble()
                        pub.readingOrder.mapIndexed { index, link ->
                            listOf(
                                Locator(
                                    href = link.url(),
                                    mediaType = link.mediaType ?: MediaType.XHTML,
                                    title = link.title,
                                    locations = Locator.Locations(
                                        progression = 0.0,
                                        position = index + 1,
                                        totalProgression = index.toDouble() * invTotal
                                    )
                                )
                            )
                        }
                    }
                }

                val totalPositions = computedPositions.sumOf { it.size }

                // 3. Resolución resiliente del Locator inicial:
                // Compara el checkpoint ultra-rápido de SharedPreferences con los datos persistidos en Room
                val isPdf = book.format.name == "PDF"
                val initialPdfPage: Int?
                val initialLocator: Locator?

                if (isPdf) {
                    val prefs = context.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
                    val lastBookId = prefs.getString("last_book_id", null)
                    val checkpointChapter = if (lastBookId == book.id) {
                        prefs.getInt("last_chapter_index_${book.id}", -1).takeIf { it > 0 }
                    } else null

                    initialPdfPage = checkpointChapter
                        ?: book.fallbackLocatorPosition?.takeIf { it > 0 }
                        ?: 1
                    initialLocator = null
                } else {
                    initialPdfPage = null
                    val prefs = context.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
                    val lastBookId = prefs.getString("last_book_id", null)
                    val checkpointLocatorJson = if (lastBookId == book.id) {
                        prefs.getString("last_locator_json_${book.id}", null)?.takeIf { it.isNotBlank() }
                    } else null

                    // Intentar primero con checkpoint reciente de SharedPreferences, luego con Room
                    initialLocator = validateAndRestoreLocator(
                        publication = pub,
                        cache = fastIndexCache,
                        cfiJson = checkpointLocatorJson ?: book.currentLocatorJson,
                        fallbackHref = book.fallbackLocatorHref,
                        fallbackPosition = book.fallbackLocatorPosition
                    )
                }

                // 4. Aplanar Tabla de Contenidos (TOC) y generar TOC virtual para PDFs sin índice
                val finalPub = if (isPdf && pub.tableOfContents.isEmpty()) {
                    generateVirtualPdfPublication(pub, uri, book)
                } else {
                    pub
                }

                val flatToc = flattenToc(finalPub.tableOfContents.ifEmpty { finalPub.readingOrder })

                LoadBookResult.Success(
                    LoadedBookSessionData(
                        book = book,
                        publication = finalPub,
                        uri = uri,
                        resourceIndexCache = fastIndexCache,
                        positionsByResource = computedPositions,
                        totalPositions = totalPositions,
                        initialLocator = initialLocator,
                        initialPdfPage = initialPdfPage,
                        flatTableOfContents = flatToc
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando libro ${book.id}: ${e.message}", e)
                LoadBookResult.Error("Error al cargar libro: ${e.message}", e)
            }
        }
    }

    suspend operator fun invoke(bookId: String): LoadBookResult {
        val book = getBookEntity(bookId)
            ?: return LoadBookResult.Error("Libro no encontrado en la base de datos")
        return invoke(book)
    }

    private suspend fun openPublication(uri: Uri, book: BookEntity): Publication? {
        val assetResult = when (uri.scheme) {
            "file" -> {
                val filePath = uri.path ?: book.filePath
                assetRetriever.retrieve(File(filePath))
            }
            else -> {
                val url = AbsoluteUrl(uri.toString())
                if (url != null) assetRetriever.retrieve(url) else null
            }
        }
        val asset = assetResult?.getOrNull() ?: return null
        return publicationOpener.open(
            asset = EpubPackageSanitizer.transformAsset(asset),
            allowUserInteraction = false
        ).getOrNull()
    }

    private fun generateVirtualPdfPublication(pub: Publication, uri: Uri, book: BookEntity): Publication {
        return try {
            val file = when (uri.scheme) {
                "file" -> uri.path?.let { File(it) } ?: File(book.filePath)
                else -> File(book.filePath)
            }
            if (!file.exists()) return pub

            val chapters = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                com.example.woodsreader.core.pdf.PdfParagraphHealer.scanChapters(doc)
            }
            if (chapters.isEmpty()) return pub

            val virtualLinks = com.example.woodsreader.core.pdf.PdfParagraphHealer.chaptersToTocLinks(chapters)
            if (virtualLinks.isEmpty()) return pub

            val containerField = Publication::class.java.getDeclaredField("container").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val container = containerField.get(pub) as org.readium.r2.shared.util.data.Container<org.readium.r2.shared.util.resource.Resource>
            val servicesBuilderField = Publication::class.java.getDeclaredField("servicesBuilder").apply { isAccessible = true }
            val servicesBuilder = servicesBuilderField.get(pub) as? Publication.ServicesBuilder
            Publication(
                manifest = pub.manifest.copy(tableOfContents = virtualLinks),
                container = container,
                servicesBuilder = servicesBuilder ?: Publication.ServicesBuilder()
            )
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo generar TOC virtual para PDF: ${e.message}")
            pub
        }
    }

    /**
     * Resuelve y valida la ruta o URI del libro.
     */
    fun resolveBookUri(book: BookEntity): Uri? {
        val path = book.filePath
        if (path.isBlank()) return null
        return try {
            if (path.startsWith("/")) {
                val file = File(path)
                return if (file.exists()) Uri.fromFile(file) else null
            }

            val uri = Uri.parse(path)
            when (uri.scheme) {
                "content" -> {
                    if (uri.authority?.startsWith("media") == true) {
                        val physicalPath = try {
                            context.contentResolver.query(
                                uri,
                                arrayOf(MediaStore.Files.FileColumns.DATA),
                                null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val colIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                                    if (colIdx >= 0) cursor.getString(colIdx) else null
                                } else null
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not query DATA column for MediaStore URI: $uri", e)
                            null
                        }

                        if (physicalPath != null) {
                            val file = File(physicalPath)
                            if (file.exists()) {
                                Log.d(TAG, "Resolved MediaStore URI to file path: $physicalPath")
                                return Uri.fromFile(file)
                            }
                        }

                        return try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                                Log.d(TAG, "MediaStore URI accessible via FileDescriptor: $uri")
                                uri
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "MediaStore URI no longer accessible: $path")
                            null
                        }
                    }

                    return try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { uri }
                    } catch (e: Exception) {
                        Log.w(TAG, "SAF URI no longer accessible: $path")
                        null
                    }
                }
                "file" -> {
                    val file = File(uri.path ?: return null)
                    if (file.exists()) uri else null
                }
                else -> uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving book URI: $path", e)
            null
        }
    }

    /**
     * Valida y restaura el Locator con la cascada de 3 niveles:
     * 1. CFI exacto (Locator.fromJSON) con verificación O(1)
     * 2. Fallback por href de capítulo con resolución O(1)
     * 3. Null (Readium usa la primera posición del libro)
     */
    fun validateAndRestoreLocator(
        publication: Publication,
        cache: Map<String, Int>?,
        cfiJson: String?,
        fallbackHref: String?,
        fallbackPosition: Int?
    ): Locator? {
        // Nivel 1: CFI exacto con verificación O(1)
        if (cfiJson != null) {
            try {
                val locator = Locator.fromJSON(JSONObject(cfiJson))
                if (locator != null) {
                    val hrefStr = locator.href.toString()
                    val fastIndex = cache?.get(hrefStr)
                        ?: cache?.get(hrefStr.substringBefore('#'))
                        ?: cache?.get(hrefStr.substringAfterLast('/'))
                        ?: cache?.get(hrefStr.substringAfterLast('/').substringBefore('#'))

                    if (fastIndex != null && fastIndex in publication.readingOrder.indices) {
                        Log.d(TAG, "Locator restored via CFI (Level 1)")
                        return locator
                    }
                    Log.w(TAG, "CFI href not found in publication, falling back to Level 2")
                }
            } catch (e: Exception) {
                Log.w(TAG, "CFI parse failed, falling back to Level 2: ${e.message}")
            }
        }

        // Nivel 2: Fallback por href de capítulo con resolución O(1)
        if (fallbackHref != null) {
            try {
                val fastIndex = cache?.get(fallbackHref)
                    ?: cache?.get(fallbackHref.substringBefore('#'))
                    ?: cache?.get(fallbackHref.substringAfterLast('/'))
                    ?: cache?.get(fallbackHref.substringAfterLast('/').substringBefore('#'))

                if (fastIndex != null && fastIndex in publication.readingOrder.indices) {
                    val link = publication.readingOrder[fastIndex]
                    val total = publication.readingOrder.size.coerceAtLeast(1)
                    val locator = Locator(
                        href = link.url(),
                        mediaType = link.mediaType ?: MediaType.XHTML,
                        title = link.title,
                        locations = Locator.Locations(
                            progression = 0.0,
                            position = fastIndex + 1,
                            totalProgression = fastIndex.toDouble() / total.toDouble()
                        )
                    )
                    Log.d(TAG, "Locator restored via fallback href (Level 2): $fallbackHref (index $fastIndex)")
                    return locator
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback href restoration failed: ${e.message}")
            }
        }

        // Nivel 3: Primera posición del libro
        Log.w(TAG, "All locator restoration failed. Starting from beginning (Level 3).")
        return null
    }

    /**
     * Aplana recursivamente una lista jerárquica de Links de la Tabla de Contenidos.
     */
    fun flattenToc(links: List<Link>): List<Link> {
        val result = mutableListOf<Link>()
        fun recurse(items: List<Link>) {
            for (item in items) {
                result.add(item)
                if (item.children.isNotEmpty()) {
                    recurse(item.children)
                }
            }
        }
        recurse(links)
        return result
    }

    /**
     * Extrae el número de página de un [Link] con destino de página (ej. #page=15, /page_15, /15).
     */
    fun extractPageNumberFromLink(link: Link): Int? {
        val linkHref = link.href.toString()
        Regex("""(?i)[#?&]page=(\d+)""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""(?i)/page[_-]?(\d+)""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""#(\d+)$""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""^/?(\d+)$""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    /**
     * Resuelve el título de un capítulo a partir de su href o número de página buscando en TOC y readingOrder.
     */
    fun resolveChapterTitle(pub: Publication, resourceHref: String, pageNumber: Int? = null): String? {
        if (pageNumber != null && pageNumber > 0) {
            val tocLinks = pub.tableOfContents.ifEmpty { pub.readingOrder }
            val matchingLink = tocLinks
                .mapNotNull { link ->
                    val page = extractPageNumberFromLink(link)
                    if (page != null) page to link else null
                }
                .filter { it.first <= pageNumber }
                .maxByOrNull { it.first }
                ?.second
            if (matchingLink?.title?.isNotBlank() == true) {
                return matchingLink.title?.trim()
            }
        }

        val normalized = EpubPackageSanitizer.normalizeResourcePath(resourceHref)
        val fileName = normalized.substringAfterLast('/')

        fun findInLinks(links: List<Link>): String? {
            for (link in links) {
                val linkNorm = EpubPackageSanitizer.normalizeResourcePath(link.href.toString())
                if (linkNorm == normalized ||
                    linkNorm.endsWith("/$normalized") ||
                    normalized.endsWith("/$linkNorm") ||
                    fileName.equals(linkNorm.substringAfterLast('/'), ignoreCase = true)
                ) {
                    val title = link.title?.trim()?.takeIf { it.isNotEmpty() }
                    if (title != null) return title
                }
                val child = findInLinks(link.children)
                if (child != null) return child
            }
            return null
        }

        return findInLinks(pub.tableOfContents)
            ?: findInLinks(pub.readingOrder)
            ?: pub.readingOrder.indexOfFirst { it.href.toString() == resourceHref }
                .takeIf { it >= 0 }
                ?.let { idx -> pub.tableOfContents.getOrNull(idx)?.title?.trim()?.takeIf { it.isNotEmpty() } }
    }
}
