package com.example.woodsreader.ui.reader

import android.net.Uri
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.service.CoverExtractor
import org.readium.r2.shared.publication.Publication

class ReaderBookHydrator(
    private val bookRepository: BookRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val coverExtractor: CoverExtractor,
    private val logWarn: (String, Throwable) -> Unit
) {
    suspend fun hydrate(book: BookEntity, uri: Uri, publication: Publication) {
        try {
            bookRepository.markOpened(book.id)
            userPreferencesRepository.updateLastReadBookId(book.id)
            if (ReaderLoadResolvers.isCoverMissing(book.coverCachePath)) {
                val newCover = coverExtractor.extractCover(
                    source = uri,
                    format = book.format,
                    fileHash = book.fileHash,
                    title = book.title,
                    author = book.author,
                    publication = publication
                )
                if (newCover != null) {
                    bookRepository.updateCoverPath(book.id, newCover)
                }
            }
        } catch (e: Exception) {
            logWarn("Error en hidratación diferida (Fase 2) de ${book.title}", e)
        }
    }
}
