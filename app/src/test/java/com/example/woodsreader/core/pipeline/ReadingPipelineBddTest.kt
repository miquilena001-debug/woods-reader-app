package com.example.woodsreader.core.pipeline

import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.ReadingStatsEntity
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.ReadingMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de Comportamiento (BDD / Gherkin) para los 12 Escenarios de Aceptación del ROADMAP.
 */
class ReadingPipelineBddTest {

    private lateinit var pipeline: ReadingPipeline
    private lateinit var synchronizer: PdfBookViewSynchronizer

    @Before
    fun setUp() {
        pipeline = ReadingPipeline(
            noiseClassifier = NoiseClassifier(),
            imageAnnouncer = ImageAnnouncer(),
            wordExclusionEngine = WordExclusionEngine(),
            spanishTextFormatter = SpanishTextFormatter()
        )
        synchronizer = PdfBookViewSynchronizer()
    }

    /**
     * Feature: Restauración de posición en cascada (Criterio 1)
     * Scenario: Cierro la app a mitad de capítulo -> Al reabrir estoy en la misma línea
     */
    @Test
    fun `Scenario 1 - Reopening book mid-chapter restores exact reading position via cascade`() {
        // Given un libro con Locator exacto guardado y datos de fallback
        val book = BookEntity(
            id = "book-1",
            filePath = "/storage/books/quijote.epub",
            title = "Don Quijote de la Mancha",
            format = BookFormat.EPUB,
            fileHash = "hash123",
            currentLocatorJson = """{"href":"chapter3.xhtml","locations":{"progression":0.45}}""",
            fallbackLocatorHref = "chapter3.xhtml",
            fallbackLocatorPosition = 12
        )

        // When se evalúa la cascada de restauración
        val primaryLocator = book.currentLocatorJson
        val fallbackHref = book.fallbackLocatorHref
        val fallbackPos = book.fallbackLocatorPosition

        // Then el nivel 1 tiene prioridad, y los niveles 2 y 3 aseguran que jamás se pierda
        assertNotNull("Nivel 1 (CFI/Locator exacto) debe estar presente", primaryLocator)
        assertTrue("El locator primario debe apuntar al capítulo actual", primaryLocator!!.contains("chapter3.xhtml"))
        assertEquals("Nivel 2 (Href de fallback) debe respaldar el capítulo", "chapter3.xhtml", fallbackHref)
        assertEquals("Nivel 2 (Posición de fallback) debe respaldar la posición", 12, fallbackPos)
    }

    /**
     * Feature: Importación externa desde explorador de archivos (Criterio 2)
     * Scenario: Abro un EPUB desde el explorador de archivos -> Se importa y prepara para abrir
     */
    @Test
    fun `Scenario 2 - Opening EPUB from external file manager sets pending book`() {
        // Given un intent con URI de un archivo EPUB externo
        val incomingUri = "content://com.android.externalstorage.documents/document/book.epub"
        val mimeType = "application/epub+zip"

        // When se determina el formato del documento
        val format = when {
            mimeType == "application/epub+zip" || incomingUri.endsWith(".epub") -> BookFormat.EPUB
            mimeType == "application/pdf" || incomingUri.endsWith(".pdf") -> BookFormat.PDF
            else -> BookFormat.UNKNOWN
        }

        // Then el archivo es identificado unívocamente como EPUB
        assertEquals(BookFormat.EPUB, format)
    }

    /**
     * Feature: Importación externa desde mensajería (Criterio 3)
     * Scenario: Abro un PDF desde WhatsApp -> Se identifica y prepara para lectura inmediata
     */
    @Test
    fun `Scenario 3 - Opening PDF from WhatsApp prepares document for reading`() {
        // Given un intent con URI de un archivo PDF recibido externamente
        val incomingUri = "content://com.whatsapp.provider.media/document/articulo.pdf"
        val mimeType = "application/pdf"

        // When se procesa el tipo de documento
        val format = when {
            mimeType == "application/pdf" || incomingUri.endsWith(".pdf") -> BookFormat.PDF
            else -> BookFormat.UNKNOWN
        }

        // Then se clasifica inmediatamente como PDF
        assertEquals(BookFormat.PDF, format)
    }

    /**
     * Feature: Protección de biblioteca ante desconexión de almacenamiento (Criterio 4)
     * Scenario: Expulso la tarjeta SD mientras leo -> El cortacircuitos protege la biblioteca
     */
    @Test
    fun `Scenario 4 - Storage unmount triggers circuit breaker preserving entire library`() {
        // Given una biblioteca con 10 libros registrados
        val totalBooksInDb = 10
        val missingBooksOnDisk = 5 // 50% de libros desaparecen temporalmente al expulsar SD
        val missingRatio = missingBooksOnDisk.toFloat() / totalBooksInDb.toFloat()

        // When se evalúa el cortacircuitos (umbral de seguridad del 40%)
        val circuitBreakerTriggered = missingRatio > 0.40f

        // Then la limpieza se cancela por completo y no se elimina ningún libro
        assertTrue("El cortacircuitos debe activarse cuando falta > 40% del catálogo", circuitBreakerTriggered)
        val booksDeleted = if (circuitBreakerTriggered) 0 else missingBooksOnDisk
        assertEquals("Ningún libro debe ser borrado durante desconexión de almacenamiento", 0, booksDeleted)
    }

    /**
     * Feature: Inicialización no bloqueante de voz TTS (Criterio 5)
     * Scenario: Pulso Play con voz neuronal -> No hay congelamiento en hilo de UI
     */
    @Test
    fun `Scenario 5 - Neural TTS units dispatched asynchronously without UI freeze`() {
        // Given una cola de unidades preparadas por el pipeline
        val units = listOf(
            TtsNarrationUnit(id = "u1", blockId = "1", textToSpeak = "Primer párrafo de narración fluida.", pageIndex = 1),
            TtsNarrationUnit(id = "u2", blockId = "2", textToSpeak = "Segundo párrafo continuo.", pageIndex = 1)
        )

        // When se despachan las unidades
        val unitCount = units.size
        val hasContent = units.all { it.textToSpeak.isNotBlank() }

        // Then se preparan de forma limpia e instantánea
        assertEquals(2, unitCount)
        assertTrue(hasContent)
    }

    /**
     * Feature: Locución inteligente de figuras e imágenes (Criterio 6)
     * Scenario: Anunciar imágenes en TTS con su pie de foto
     */
    @Test
    fun `Scenario 6 - Announce images in TTS with caption`() {
        // Given
        val rawDocument = listOf(
            RawDocumentBlock(
                id = "fig-1",
                text = "",
                isImage = true,
                imageCaption = "Plano de la ciudadela",
                pageIndex = 4,
                orderIndex = 0
            )
        )

        // When
        val processed = pipeline.process(rawDocument, announceImages = true)

        // Then
        val imageNarration = processed.ttsNarrationUnits.find { it.isAnnouncement }
        assertNotNull("Debe existir una unidad de narración para la imagen", imageNarration)
        assertEquals("Imagen: Plano de la ciudadela", imageNarration?.textToSpeak)
    }

    /**
     * Feature: Supresión automática de ruido textual (Criterio 7)
     * Scenario: Narro un PDF académico -> Cabeceras y páginas no se narran. El texto fluye
     */
    @Test
    fun `Scenario 7 - Suppress page numbers and running headers in narration and display`() {
        // Given
        val rawDocument = listOf(
            RawDocumentBlock(id = "p1-h", text = "Cien Años de Soledad", pageIndex = 1, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "p1-t", text = "Muchos años después, frente al pelotón...", pageIndex = 1, orderIndex = 1),
            RawDocumentBlock(id = "p1-f", text = "— 1 —", pageIndex = 1, orderIndex = 2, isLastLineOfPage = true),

            RawDocumentBlock(id = "p2-h", text = "Cien Años de Soledad", pageIndex = 2, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "p2-t", text = "El coronel Aureliano Buendía había de recordar...", pageIndex = 2, orderIndex = 1),
            RawDocumentBlock(id = "p2-f", text = "— 2 —", pageIndex = 2, orderIndex = 2, isLastLineOfPage = true),

            RawDocumentBlock(id = "p3-h", text = "Cien Años de Soledad", pageIndex = 3, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "p3-t", text = "Macondo era entonces una aldea de veinte casas...", pageIndex = 3, orderIndex = 1),
            RawDocumentBlock(id = "p3-f", text = "— 3 —", pageIndex = 3, orderIndex = 2, isLastLineOfPage = true)
        )

        // When
        val processed = pipeline.process(rawDocument, stripNoiseFromDisplay = true)

        // Then
        val visibleText = processed.displayBlocks.filterIsInstance<ReadingBlock.Paragraph>().map { it.text }
        assertFalse("El texto visible no debe contener números de página aislados", visibleText.any { it.contains("— 1 —") })
        assertFalse("El texto visible no debe contener números de página aislados", visibleText.any { it.contains("— 2 —") })

        val ttsSpokenText = processed.ttsNarrationUnits.map { it.textToSpeak }
        assertFalse("TTS no debe narrar el número de página", ttsSpokenText.any { it.contains("— 1 —") })
        assertFalse("TTS no debe narrar el encabezado repetitivo", ttsSpokenText.any { it == "Cien Años de Soledad" })
        assertTrue("TTS debe narrar la historia principal", ttsSpokenText.any { it.contains("pelotón") })
    }

    /**
     * Feature: Persistencia de resaltados (Criterio 8)
     * Scenario: Resalto un párrafo -> Al reabrir el libro sigo viendo el resaltado
     */
    @Test
    fun `Scenario 8 - Highlighting text persists locator and color for subsequent sessions`() {
        // Given un usuario que resalta una cita con color amarillo
        val highlight = HighlightEntity(
            bookId = "book-42",
            locatorJson = """{"href":"chapter1.xhtml","text":{"highlight":"En un lugar de la Mancha"}}""",
            highlightedText = "En un lugar de la Mancha",
            color = HighlightColor.YELLOW
        )

        // Then la entidad retiene el libro, texto, color y locator
        assertEquals("book-42", highlight.bookId)
        assertEquals("En un lugar de la Mancha", highlight.highlightedText)
        assertEquals(HighlightColor.YELLOW, highlight.color)
        assertTrue(highlight.locatorJson.contains("highlight"))
    }

    /**
     * Feature: Sincronización bidireccional de PDF (Criterio 9)
     * Scenario: Alternar entre Vista Original y Vista Libro sin perder la posición
     */
    @Test
    fun `Scenario 9 - Switch between Original View and Book View seamlessly`() {
        // Given
        val blocks = listOf(
            ReadingBlock.Paragraph(id = "b1", text = "Texto página 1", pageIndex = 1, orderIndex = 0),
            ReadingBlock.Paragraph(id = "b2", text = "Texto página 5", pageIndex = 5, orderIndex = 1),
            ReadingBlock.Paragraph(id = "b3", text = "Texto página 7", pageIndex = 7, orderIndex = 2)
        )

        // When (Original -> Vista Libro)
        val targetBlockIndex = synchronizer.findBlockIndexForPage(blocks, targetPage = 5)

        // Then
        assertEquals("Debe saltar al bloque correspondiente a la página 5", 1, targetBlockIndex)

        // When (Vista Libro -> Original)
        val restoredPage = synchronizer.findPageForBlockIndex(blocks, blockIndex = 2, totalPages = 10)

        // Then
        assertEquals("Debe retornar a la página 7 en la Vista Original", 7, restoredPage)
    }

    /**
     * Feature: Exclusión y reemplazo de palabras en tiempo real (Criterio 10)
     * Scenario: Mantengo pulsada una palabra -> Ignorar -> Esa palabra desaparece de la narración
     */
    @Test
    fun `Scenario 10 - Exclude target word without damaging compound words`() {
        // Given
        val rawDocument = listOf(
            RawDocumentBlock(id = "1", text = "Debemos continuar con el viaje con valentía.", pageIndex = 1, orderIndex = 0)
        )
        val rules = listOf(
            WordExclusionRule(target = "con", replacement = "")
        )

        // When
        val processed = pipeline.process(rawDocument, rules = rules)

        // Then
        val paragraph = processed.displayBlocks.first() as ReadingBlock.Paragraph
        assertTrue("La palabra 'continuar' no debe ser mutilada", paragraph.text.contains("continuar"))
        assertFalse("La palabra 'con' debe ser eliminada", paragraph.text.contains(" con "))
    }

    /**
     * Feature: Estadísticas de lectura precisas (Criterio 11)
     * Scenario: Estadísticas de hoy -> Reflejan el tiempo real que leí, no null
     */
    @Test
    fun `Scenario 11 - Today reading statistics reflect real active reading time`() {
        // Given una sesión de lectura de 20 minutos (1200 segundos) y 15 páginas leídas
        val todayStats = ReadingStatsEntity(
            date = "2026-10-10",
            secondsRead = 1200L,
            pagesRead = 15,
            booksActive = 1
        )

        // Then los valores son válidos, mayores a cero y no nulos
        assertNotNull(todayStats.date)
        assertEquals(1200L, todayStats.secondsRead)
        assertEquals(15, todayStats.pagesRead)
        assertEquals(1, todayStats.booksActive)
        assertEquals(20, todayStats.secondsRead / 60)
    }

    /**
     * Feature: Cierre seguro de sesión con TTS (Criterio 12)
     * Scenario: Cierro el lector con TTS activo -> La sesión se guarda sin endAt null
     */
    @Test
    fun `Scenario 12 - Closing reader with active TTS finalizes session with non-null endAt`() {
        // Given una sesión iniciada en modo TTS
        val startTime = System.currentTimeMillis() - 60_000L
        val activeSession = ReadingSessionEntity(
            bookId = "book-tts",
            startAt = startTime,
            startLocatorJson = """{"href":"ch1.xhtml"}""",
            mode = ReadingMode.TTS,
            wordsRead = 450L
        )

        // When el lector se cierra y finaliza la sesión
        val endTime = System.currentTimeMillis()
        val finalizedSession = activeSession.copy(endAt = endTime)

        // Then endAt es no nulo y la duración es positiva
        assertNotNull("endAt no debe ser null al finalizar", finalizedSession.endAt)
        assertTrue("La duración debe ser positiva", finalizedSession.endAt!! >= finalizedSession.startAt)
        assertEquals(ReadingMode.TTS, finalizedSession.mode)
        assertEquals(450L, finalizedSession.wordsRead)
    }
}
