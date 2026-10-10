package com.example.woodsreader.data.model

import android.net.Uri

data class BookScanResult(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val dateModified: Long,
    val source: Source
) {
    enum class Source { MEDIASTORE, SAF, LEGACY_PATH }
}
