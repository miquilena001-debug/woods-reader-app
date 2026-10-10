package com.example.woodsreader.data.database

import androidx.room.TypeConverter
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.model.ReadingStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * TypeConverters for Room.
 * @ProvidedTypeConverter allows Hilt to inject dependencies if needed.
 *
 * Without these, Room cannot serialize enums or lists and the build fails with:
 * "Cannot figure out how to save this field into database."
 */
@androidx.room.ProvidedTypeConverter
class AppTypeConverters @Inject constructor() {

    // ── Enums ↔ String (using .name for refactoring-safety) ──────────────────

    @TypeConverter fun bookFormatToString(v: BookFormat): String = v.name
    @TypeConverter fun stringToBookFormat(v: String): BookFormat =
        BookFormat.entries.firstOrNull { it.name == v } ?: BookFormat.UNKNOWN

    @TypeConverter fun readingStatusToString(v: ReadingStatus): String = v.name
    @TypeConverter fun stringToReadingStatus(v: String): ReadingStatus =
        ReadingStatus.entries.firstOrNull { it.name == v } ?: ReadingStatus.NOT_STARTED

    @TypeConverter fun highlightColorToString(v: HighlightColor): String = v.name
    @TypeConverter fun stringToHighlightColor(v: String): HighlightColor =
        HighlightColor.entries.firstOrNull { it.name == v } ?: HighlightColor.YELLOW

    @TypeConverter fun readingModeToString(v: ReadingMode): String = v.name
    @TypeConverter fun stringToReadingMode(v: String): ReadingMode =
        ReadingMode.entries.firstOrNull { it.name == v } ?: ReadingMode.MANUAL

    private val json = Json { ignoreUnknownKeys = true }

    // ── List<String> ↔ JSON ───────────────────────────────────────────────────

    @TypeConverter fun stringListToJson(v: List<String>): String = json.encodeToString(v)
    @TypeConverter fun jsonToStringList(v: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(v) }.getOrDefault(emptyList())
}
