package com.example.woodsreader.data.database

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Tabla FTS4 para búsqueda full-text de libros.
 *
 * - FTS4 (no FTS5) porque soporta `matchinfo()` que Room utiliza internamente
 * - La tabla está vinculada a la tabla `books` mediante `content = "books"`
 * - Al hacer un match, se une con la tabla `books` por `rowid`
 *
 * Los campos expuestos aquí deben coincidir con los de BookEntity que
 * queremos que sean buscables: título, autor, descripción.
 */
@Fts4(contentEntity = BookEntity::class)
@Entity(tableName = "books_fts")
data class BookFtsEntity(
    val title: String,
    val author: String,
    val description: String
)
