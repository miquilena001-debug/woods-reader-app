package com.example.woodsreader.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.woodsreader.data.model.BookScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface MediaStoreScanner {
    fun scanDevice(): Flow<BookScanResult>
}

@Singleton
class MediaStoreBookScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaStoreScanner {

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("pdf", "epub")
        val SUPPORTED_MIMES = setOf("application/pdf", "application/epub+zip")
        
        const val MIN_FILE_SIZE_BYTES = 1024L
        
        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val SELECTION = """
            (${MediaStore.Files.FileColumns.SIZE} > ?) AND
            (
                ${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?)
                OR ${MediaStore.Files.FileColumns.DATA} LIKE ?
                OR ${MediaStore.Files.FileColumns.DATA} LIKE ?
            )
        """.trimIndent()

        val SELECTION_ARGS = arrayOf(
            MIN_FILE_SIZE_BYTES.toString(),
            "application/pdf",
            "application/epub+zip",
            "%.pdf",
            "%.epub"
        )
    }

    override fun scanDevice(): Flow<BookScanResult> = flow {
        val contentUri = MediaStore.Files.getContentUri("external")
        val resolver: ContentResolver = context.contentResolver
        val coroutineContext = currentCoroutineContext()

        resolver.query(
            contentUri,
            PROJECTION,
            SELECTION,
            SELECTION_ARGS,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            var iterations = 0

            while (cursor.moveToNext()) {
                // Yield/cancel check to prevent runaway background threads if job is cancelled
                if (++iterations % 100 == 0 && !coroutineContext.isActive) {
                    break
                }

                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val dataPath = cursor.getString(dataCol)
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol)
                val modified = cursor.getLong(dateCol)

                val extension = (dataPath?.substringAfterLast('.', "") ?: name.substringAfterLast('.', "")).lowercase()
                
                if (!SUPPORTED_EXTENSIONS.contains(extension) && !SUPPORTED_MIMES.contains(mime)) {
                    continue
                }

                val resolvedUri: Uri = if (dataPath != null) {
                    val file = File(dataPath)
                    if (file.exists()) {
                        Uri.fromFile(file)
                    } else {
                        ContentUris.withAppendedId(contentUri, id)
                    }
                } else {
                    ContentUris.withAppendedId(contentUri, id)
                }

                emit(
                    BookScanResult(
                        uri = resolvedUri,
                        displayName = name,
                        sizeBytes = size,
                        mimeType = mime,
                        dateModified = modified * 1000L, // Convert to ms
                        source = BookScanResult.Source.MEDIASTORE
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)
}
