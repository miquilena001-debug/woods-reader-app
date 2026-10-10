package com.example.woodsreader.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReaderTheme {
    HIDDEN_FOREST,   // Oscuro con tintes verdes — predeterminado
    DAY,             // Blanco/crema para lectura diurna
    SEPIA,           // Tonos cálidos beige
    AMOLED,          // Negro puro para pantallas OLED
    SUNSET           // Tonos cálidos rojizos
}
