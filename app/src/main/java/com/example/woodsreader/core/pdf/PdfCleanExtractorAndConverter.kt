package com.example.woodsreader.core.pdf

import com.example.woodsreader.core.web2epub.LocalEpubBuilder
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.security.MessageDigest

/**
 * Modelo de progreso para la conversión limpia de PDF a EPUB.
 */
data class PdfConversionProgress(
    val currentPage: Int,
    val totalPages: Int,
    val chaptersCreated: Int,
    val illustrationsSaved: Int,
    val adsBlocked: Int,
    val isFinished: Boolean = false,
    val statusMessage: String = ""
)

/**
 * Extractor y convertidor inteligente de PDFs a EPUB en Woods Reader:
 *
 * Filtro Anti-Publicidad de 3 Capas:
 * 1. Deduplicación SHA-256: Si un gráfico (logo, QR, banner) se repite >= 2 veces en el libro,
 *    se clasifica como spam/marca de agua y se elimina de todas las páginas (excepto la portada de pág 1).
 * 2. Filtro de Proporción y Tamaño: Descarta micro-iconos (< 140x140 px) y tiras achatadas
 *    (aspect ratio > 3.6 o < 0.22).
 * 3. Limpieza de Texto y Line-Unwrapping: Elimina encabezados y pies de página repetidos (> 3 páginas)
 *    o con enlaces a redes (Discord, Patreon, Telegram), uniendo los renglones cortados para lectura continua.
 *
 * Conservación de Ilustraciones Reales:
 * - Toda ilustración artística de la historia que supere los filtros se conserva en alta calidad
 *   y se incrusta dentro de `OEBPS/images/` del archivo EPUB con su etiqueta `<div class="illust-box">`.
 */
object PdfCleanExtractorAndConverter {

    private val SPAM_TEXT_REGEX = Regex(
        """(?i)\b(?:https?://|www\.|t\.me/|discord(?:\.gg|app\.com)/|patreon\.com/|wattpad\.com/|paypal\.me/|traducid[oa]\s+por|le[ea]\s+gratis\s+en|scan(?:lation)?|novelupdates)\b"""
    )

    /**
     * Calcula la huella digital SHA-256 de un arreglo de bytes en milisegundos.
     */
    fun calculateSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Determina si una línea contiene publicidad evidente o URLs de grupos de traducción.
     */
    fun isSpamTextLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        return SPAM_TEXT_REGEX.containsMatchIn(trimmed)
    }

    /**
     * Normaliza una línea para comparación de encabezados/pies repetidos,
     * ignorando números variables de página y puntuación accesoria.
     */
    fun normalizeForHeaderComparison(line: String): String {
        return line.trim()
            .lowercase()
            .replace(Regex("""\d+"""), "#")
            .replace(Regex("""[—–\-_|•*~]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Convierte un PDF a un libro EPUB limpio con capítulos estructurados e ilustraciones reales.
     */
    suspend fun convertPdfToCleanEpub(
        pdfFile: File,
        outputEpubFile: File,
        bookTitle: String,
        bookAuthor: String = "Web Novel",
        languageCode: String = "es",
        imageCompressor: (ByteArray) -> ByteArray = { raw -> compressToJpegFallback(raw) },
        pageTextProvider: ((doc: PDDocument, pageIdx: Int) -> String)? = null,
        onProgress: (PdfConversionProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                throw IllegalArgumentException("El archivo PDF no existe o está vacío: ${pdfFile.path}")
            }

            PDDocument.load(pdfFile).use { doc ->
                val totalPages = doc.numberOfPages
                if (totalPages == 0) {
                    throw IllegalStateException("El documento PDF no contiene páginas legibles.")
                }

                onProgress(
                    PdfConversionProgress(
                        currentPage = 0,
                        totalPages = totalPages,
                        chaptersCreated = 0,
                        illustrationsSaved = 0,
                        adsBlocked = 0,
                        statusMessage = "Fase 1/2: Analizando marcas de agua y estructura..."
                    )
                )

                // ── FASE 1: Análisis Ligero de Huellas Digitales y Encabezados Repetidos ──
                val imageHashCounts = HashMap<String, Int>(64)
                val imageFirstPage = HashMap<String, Int>(64)
                val rawImageMap = HashMap<String, ByteArray>(64)
                val headerOccurrences = HashMap<String, Int>(64)
                val footerOccurrences = HashMap<String, Int>(64)

                var coverCandidateBytes: ByteArray? = null

                val stripper = if (pageTextProvider == null) {
                    runCatching { PDFTextStripper().apply { sortByPosition = true } }.getOrNull()
                } else null

                for (pageIdx in 0 until totalPages) {
                    currentCoroutineContext().ensureActive()

                    // Extraer texto preliminar para detectar encabezados/pies repetidos
                    val rawPageText = if (pageTextProvider != null) {
                        pageTextProvider(doc, pageIdx + 1)
                    } else if (stripper != null) {
                        stripper.startPage = pageIdx + 1
                        stripper.endPage = pageIdx + 1
                        val sw = StringWriter()
                        stripper.writeText(doc, sw)
                        sw.toString()
                    } else {
                        ""
                    }

                    val pageLines = rawPageText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (pageLines.isNotEmpty()) {
                        val firstNorm = normalizeForHeaderComparison(pageLines.first())
                        if (firstNorm.length in 4..80) {
                            headerOccurrences[firstNorm] = (headerOccurrences[firstNorm] ?: 0) + 1
                        }
                        val lastNorm = normalizeForHeaderComparison(pageLines.last())
                        if (lastNorm.length in 4..80) {
                            footerOccurrences[lastNorm] = (footerOccurrences[lastNorm] ?: 0) + 1
                        }
                    }

                    // Extraer imágenes y calcular SHA-256
                    try {
                        val page = doc.getPage(pageIdx)
                        val resources = page.resources
                        resources?.xObjectNames?.forEach { xName ->
                            val xObject = try { resources.getXObject(xName) } catch (_: Throwable) { null }
                            if (xObject is PDImageXObject) {
                                val bytes = try {
                                    xObject.createInputStream().use { it.readBytes() }
                                } catch (_: Throwable) {
                                    null
                                }
                                if (bytes != null && bytes.size > 256) {
                                    val hash = calculateSha256(bytes)
                                    imageHashCounts[hash] = (imageHashCounts[hash] ?: 0) + 1
                                    if (!imageFirstPage.containsKey(hash)) {
                                        imageFirstPage[hash] = pageIdx
                                        rawImageMap[hash] = bytes
                                    }

                                    // Si estamos en la primera página y la imagen tiene buen tamaño, es candidata a portada
                                    if (pageIdx == 0 && coverCandidateBytes == null) {
                                        val w = xObject.width
                                        val h = xObject.height
                                        if (w >= 280 && h >= 380 && (w.toFloat() / h.toFloat()) in 0.45f..1.15f) {
                                            coverCandidateBytes = bytes
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }

                // Identificar lista negra de spam (imágenes repetidas >= 2 veces a lo largo del libro)
                val spamImageHashes = imageHashCounts.filter { it.value >= 2 }.keys.toSet()

                // Encabezados o pies de página que se repiten en 3 o más páginas
                val spamHeaders = headerOccurrences.filter { it.value >= 3 }.keys.toSet()
                val spamFooters = footerOccurrences.filter { it.value >= 3 }.keys.toSet()

                // ── FASE 2: Extracción Limpia y Construcción de Capítulos con LocalEpubBuilder ──
                val epubBuilder = LocalEpubBuilder(
                    outputFile = outputEpubFile,
                    bookTitle = bookTitle,
                    bookAuthor = bookAuthor,
                    languageCode = languageCode
                )

                // Incrustar portada oficial si se detectó
                val finalCover = coverCandidateBytes
                if (finalCover != null) {
                    epubBuilder.setCoverImage(imageCompressor(finalCover))
                }

                var chaptersCount = 0
                var illustrationsCount = 0
                var adsBlockedCount = 0

                var currentChapterTitle = "Capítulo 1"
                var currentChapterParagraphs = ArrayList<String>(128)
                var chapterSequence = 1

                fun flushChapter() {
                    if (currentChapterParagraphs.isNotEmpty()) {
                        epubBuilder.appendChapter(
                            rawTitle = currentChapterTitle,
                            paragraphs = currentChapterParagraphs,
                            sourceUrl = "section_$chapterSequence"
                        )
                        chaptersCount++
                        chapterSequence++
                        currentChapterParagraphs = ArrayList(128)
                    }
                }

                for (pageIdx in 0 until totalPages) {
                    currentCoroutineContext().ensureActive()

                    val rawPageText = if (pageTextProvider != null) {
                        pageTextProvider(doc, pageIdx + 1)
                    } else if (stripper != null) {
                        stripper.startPage = pageIdx + 1
                        stripper.endPage = pageIdx + 1
                        val sw = StringWriter()
                        stripper.writeText(doc, sw)
                        sw.toString()
                    } else {
                        ""
                    }

                    // 1. Filtrar texto descartando spam textual, números de página y encabezados/pies repetidos
                    val rawLines = rawPageText.lines()
                    val filteredLines = mutableListOf<String>()

                    for (i in rawLines.indices) {
                        val line = rawLines[i].trim()
                        if (line.isEmpty()) continue

                        // Descartar números de página
                        if (PdfParagraphHealer.isPageNumberLine(line)) continue

                        // Descartar URLs o menciones a grupos de traducción / donaciones
                        if (isSpamTextLine(line)) {
                            adsBlockedCount++
                            continue
                        }

                        // Descartar si coincide con encabezado repetido en primera línea
                        if (i == 0 && spamHeaders.contains(normalizeForHeaderComparison(line))) {
                            adsBlockedCount++
                            continue
                        }

                        // Descartar si coincide con pie repetido en última línea
                        if (i == rawLines.lastIndex && spamFooters.contains(normalizeForHeaderComparison(line))) {
                            adsBlockedCount++
                            continue
                        }

                        filteredLines.add(line)
                    }

                    // 2. Procesar imágenes de la página aplicando los 3 filtros anti-publicidad
                    val pageImagesToInsert = mutableListOf<String>()
                    try {
                        val page = doc.getPage(pageIdx)
                        val resources = page.resources
                        resources?.xObjectNames?.forEach { xName ->
                            val xObject = try { resources.getXObject(xName) } catch (_: Throwable) { null }
                            if (xObject is PDImageXObject) {
                                val w = xObject.width
                                val h = xObject.height

                                // Filtro C: Descartar si es micro-icono o tira achatada extrema
                                if (w < 140 || h < 140) {
                                    adsBlockedCount++
                                    return@forEach
                                }
                                val ratio = w.toFloat() / h.toFloat()
                                if (ratio > 3.6f || ratio < 0.22f) {
                                    adsBlockedCount++
                                    return@forEach
                                }

                                val bytes = try {
                                    xObject.createInputStream().use { it.readBytes() }
                                } catch (_: Throwable) {
                                    null
                                } ?: return@forEach

                                val hash = calculateSha256(bytes)

                                // Filtro A: Descartar si es un banner o logo repetido en múltiples páginas
                                if (hash in spamImageHashes) {
                                    adsBlockedCount++
                                    return@forEach
                                }

                                // Es una ilustración real única: registrarla en el EPUB
                                val compressed = imageCompressor(bytes)
                                val imgMarker = epubBuilder.registerChapterImage(compressed)
                                if (imgMarker.isNotEmpty()) {
                                    pageImagesToInsert.add(imgMarker)
                                    illustrationsCount++
                                }
                            }
                        }
                    } catch (_: Throwable) {}

                    // 3. Estructuración en capítulos
                    // Si encontramos un encabezado de capítulo al inicio de la página, creamos nuevo capítulo
                    val firstHeading = filteredLines.firstOrNull { PdfParagraphHealer.isChapterHeading(it) }
                    if (firstHeading != null && currentChapterParagraphs.isNotEmpty()) {
                        flushChapter()
                        currentChapterTitle = PdfParagraphHealer.extractCleanChapterTitle(firstHeading, chapterSequence)
                    }

                    // Incrustar ilustraciones encontradas en esta página
                    pageImagesToInsert.forEach { currentChapterParagraphs.add(it) }

                    // Curar y unir renglones cortados (Line-Unwrapping)
                    val healedParagraphs = PdfParagraphHealer.healParagraphs(filteredLines)
                    for (p in healedParagraphs) {
                        // Evitar repetir el título del capítulo dentro del cuerpo si ya se usó como encabezado
                        if (p != firstHeading) {
                            currentChapterParagraphs.add(p)
                        }
                    }

                    if ((pageIdx + 1) % 10 == 0 || pageIdx == totalPages - 1) {
                        onProgress(
                            PdfConversionProgress(
                                currentPage = pageIdx + 1,
                                totalPages = totalPages,
                                chaptersCreated = chaptersCount,
                                illustrationsSaved = illustrationsCount,
                                adsBlocked = adsBlockedCount,
                                statusMessage = "Página ${pageIdx + 1} de $totalPages — ${illustrationsCount} ilustraciones, ${adsBlockedCount} anuncios bloqueados"
                            )
                        )
                    }
                }

                // Volcar último capítulo
                flushChapter()

                // Si no se detectó ningún capítulo (novela sin palabras clave de capítulo), volcar contenido global
                if (chaptersCount == 0 && currentChapterParagraphs.isNotEmpty()) {
                    epubBuilder.appendChapter(
                        rawTitle = bookTitle,
                        paragraphs = currentChapterParagraphs,
                        sourceUrl = "full_book"
                    )
                    chaptersCount = 1
                }

                val finished = epubBuilder.finishEpub()
                if (!finished || !outputEpubFile.exists() || outputEpubFile.length() == 0L) {
                    throw IllegalStateException("No se pudo generar el archivo EPUB de salida.")
                }

                onProgress(
                    PdfConversionProgress(
                        currentPage = totalPages,
                        totalPages = totalPages,
                        chaptersCreated = chaptersCount,
                        illustrationsSaved = illustrationsCount,
                        adsBlocked = adsBlockedCount,
                        isFinished = true,
                        statusMessage = "¡Conversión completada con éxito!"
                    )
                )

                outputEpubFile
            }
        }
    }

    private fun compressToJpegFallback(rawBytes: ByteArray): ByteArray {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (bitmap != null) {
                val bos = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bos)
                bitmap.recycle()
                bos.toByteArray()
            } else {
                rawBytes
            }
        } catch (_: Throwable) {
            rawBytes
        }
    }
}
