package com.example.woodsreader.core.managers

import com.example.woodsreader.data.database.HighlightEntity

object HighlightExporter {
    fun exportToMarkdown(bookTitle: String, bookAuthor: String, highlights: List<HighlightEntity>): String {
        return buildString {
            appendLine("# Notas y Subrayados: $bookTitle")
            if (bookAuthor.isNotBlank()) {
                appendLine("por $bookAuthor")
            }
            appendLine()
            appendLine("---")
            appendLine()
            
            highlights.forEach { h ->
                if (h.chapterTitle.isNotBlank()) {
                    appendLine("## ${h.chapterTitle}")
                }
                appendLine("> ${h.highlightedText}")
                if (h.note.isNotBlank()) {
                    appendLine()
                    appendLine("**Nota:** ${h.note}")
                }
                appendLine()
            }
        }
    }

    fun exportToPlainText(bookTitle: String, bookAuthor: String, highlights: List<HighlightEntity>): String {
        return buildString {
            appendLine("Notas y Subrayados: $bookTitle")
            if (bookAuthor.isNotBlank()) {
                appendLine("por $bookAuthor")
            }
            appendLine()
            appendLine("=========================================")
            appendLine()
            
            highlights.forEach { h ->
                if (h.chapterTitle.isNotBlank()) {
                    appendLine("[Capítulo: ${h.chapterTitle}]")
                }
                appendLine("\"${h.highlightedText}\"")
                if (h.note.isNotBlank()) {
                    appendLine("Nota: ${h.note}")
                }
                appendLine()
                appendLine("-----------------------------------------")
                appendLine()
            }
        }
    }
}
