package com.example.woodsreader.domain.reader

import android.content.Context
import android.util.Log
import com.example.woodsreader.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.SupervisorJob

/**
 * Caso de uso responsable de la persistencia atómica y optimizada del progreso de lectura.
 *
 * Encapsula:
 * - Persistencia del progreso en Room (`progress`, `locatorJson`, `fallbackHref`, `fallbackPosition`).
 * - Debouncing de escrituras frecuentes para evitar contención de I/O en disco durante el scroll continuo.
 * - Guardado sincrónico inmediato (`flushImmediately`) en SharedPreferences (checkpoint) y Room para ON_PAUSE y onCleared.
 */
@Singleton
class SaveReadingProgressUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository
) {

    companion object {
        private const val TAG = "SaveReadingProgress"
        private const val CHECKPOINT_PREFS = "woods_last_reading_checkpoint"
        const val DEFAULT_DEBOUNCE_MS = 800L
        const val TTS_DEBOUNCE_MS = 10_000L
    }

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastSaveTimeMs: Long = 0L
    private var lastTtsSaveTimeMs: Long = 0L
    /**
     * Estado cacheado del último locator serializado. Asume una única sesión de lectura activa a la vez.
     */
    private var lastSavedLocatorJson: String? = null

    fun getLastSavedLocatorJson(): String? = lastSavedLocatorJson

    fun setLastSavedLocatorJson(json: String?) {
        lastSavedLocatorJson = json
    }

    /**
     * Serializa un [Locator] de Readium a formato JSON String.
     */
    fun serializeLocator(locator: Locator?): String {
        return locator?.toJSON()?.toString() ?: ""
    }

    /**
     * Guarda el progreso con verificación de debounce en milisegundos.
     * Retorna true si el guardado fue despachado a Room, o false si fue descartado por debounce.
     */
    fun saveProgressDebounced(
        scope: CoroutineScope,
        bookId: String,
        progress: Float,
        locator: Locator?,
        fallbackPosition: Int? = null,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS,
        force: Boolean = false
    ): Boolean {
        if (bookId.isBlank()) return false
        val now = System.currentTimeMillis()
        if (!force && (now - lastSaveTimeMs) <= debounceMs) {
            return false
        }
        lastSaveTimeMs = now

        val serialized = serializeLocator(locator)
        if (serialized.isNotEmpty()) {
            lastSavedLocatorJson = serialized
        }
        val locJson = serialized.ifEmpty { lastSavedLocatorJson.orEmpty() }
        val href = locator?.href?.toString() ?: ""
        val pos = fallbackPosition ?: locator?.locations?.position ?: 0

        scope.launch(Dispatchers.IO) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = bookId,
                progress = progress,
                locatorJson = locJson,
                fallbackHref = href,
                fallbackPosition = pos
            )
        }
        return true
    }

    /**
     * Guarda el progreso de lectura durante la reproducción TTS con debounce largo (10s).
     */
    fun saveTtsProgressDebounced(
        scope: CoroutineScope,
        bookId: String,
        locator: Locator,
        debounceMs: Long = TTS_DEBOUNCE_MS
    ): Boolean {
        if (bookId.isBlank()) return false
        val now = System.currentTimeMillis()
        if ((now - lastTtsSaveTimeMs) <= debounceMs) {
            return false
        }
        lastTtsSaveTimeMs = now

        val totalProg = (locator.locations.totalProgression ?: 0.0).toFloat()
        val locJson = serializeLocator(locator)
        lastSavedLocatorJson = locJson
        val fHref = locator.href.toString()
        val fPos = locator.locations.position ?: 0

        scope.launch(Dispatchers.IO) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = bookId,
                progress = totalProg,
                locatorJson = locJson,
                fallbackHref = fHref,
                fallbackPosition = fPos
            )
        }
        return true
    }

    /**
     * Guarda un checkpoint sincrónico en SharedPreferences vía commit() para supervivencia
     * ante cierre repentino del proceso por el sistema operativo.
     */
    fun saveCheckpointToPreferences(
        bookId: String,
        chapterIndex: Int,
        locatorJson: String,
        progress: Float
    ): Boolean {
        if (bookId.isBlank()) return false
        return runCatching {
            val prefs = context.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_book_id", bookId)
                .putInt("last_chapter_index_${bookId}", chapterIndex)
                .putString("last_locator_json_${bookId}", locatorJson)
                .putFloat("last_progress_${bookId}", progress)
                .putLong("last_saved_timestamp_${bookId}", System.currentTimeMillis())
                .commit()
        }.onFailure { Log.e(TAG, "No se pudo guardar el checkpoint de lectura para $bookId", it) }
            .getOrDefault(false)
    }

    /**
     * Checkpoint instantáneo de posición para eventos de ciclo de vida (ON_PAUSE, onCleared o cambio de app).
     * 1. Guarda de inmediato en SharedPreferences (sincrónico vía commit).
     * 2. Lanza persistencia inmediata en Room DB sin esperar ningún temporizador ni debounce.
     */
    fun flushImmediately(
        scope: CoroutineScope = saveScope,
        bookId: String,
        chapterIndex: Int,
        progress: Float,
        currentLocator: Locator?,
        fallbackLocatorJson: String? = null,
        fallbackPosition: Int? = null,
        fallbackHref: String? = null
    ) {
        if (bookId.isBlank()) return

        val locJson = if (currentLocator != null) {
            serializeLocator(currentLocator)
        } else {
            fallbackLocatorJson ?: lastSavedLocatorJson ?: ""
        }
        val href = currentLocator?.href?.toString() ?: fallbackHref ?: ""
        val pos = fallbackPosition ?: currentLocator?.locations?.position ?: 0

        // 1. SharedPreferences síncrono
        val saved = saveCheckpointToPreferences(
            bookId = bookId,
            chapterIndex = chapterIndex,
            locatorJson = locJson,
            progress = progress
        )
        if (!saved) {
            Log.e(TAG, "SharedPreferences rechazó el checkpoint inmediato para el libro $bookId")
        }

        // 2. Room DB inmediato
        scope.launch(Dispatchers.IO) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = bookId,
                progress = progress,
                locatorJson = locJson,
                fallbackHref = href,
                fallbackPosition = pos
            )
        }
    }
}
