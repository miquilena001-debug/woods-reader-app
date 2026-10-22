package com.example.woodsreader.keymaker

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class KeyHistoryEntry(
    val id: String = System.currentTimeMillis().toString(),
    val installCode: String,
    val proKey: String,
    val buyerNote: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

class KeyHistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("woods_keymaker_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    companion object {
        private const val KEY_ENTRIES = "entries_json"
    }

    fun getHistory(): List<KeyHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<KeyHistoryEntry>>(raw)
                .sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addEntry(installCode: String, proKey: String, buyerNote: String = ""): List<KeyHistoryEntry> {
        val current = getHistory().toMutableList()
        // Eliminar duplicados previos para el mismo installCode si ya existía
        current.removeAll { it.installCode.equals(installCode.trim(), ignoreCase = true) }
        current.add(
            0,
            KeyHistoryEntry(
                installCode = installCode.trim().uppercase(),
                proKey = proKey.trim().uppercase(),
                buyerNote = buyerNote.trim()
            )
        )
        saveHistory(current)
        return current
    }

    fun removeEntry(id: String): List<KeyHistoryEntry> {
        val current = getHistory().toMutableList()
        current.removeAll { it.id == id }
        saveHistory(current)
        return current
    }

    fun clearAll() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun saveHistory(entries: List<KeyHistoryEntry>) {
        val encoded = json.encodeToString(entries)
        prefs.edit().putString(KEY_ENTRIES, encoded).apply()
    }
}
