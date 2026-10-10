package com.example.woodsreader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "reading_stats")
data class ReadingStatsEntity(
    @PrimaryKey
    val date: String, // ISO-8601 (yyyy-MM-dd)
    val secondsRead: Long = 0,
    val pagesRead: Int = 0,
    val booksActive: Int = 0
)
