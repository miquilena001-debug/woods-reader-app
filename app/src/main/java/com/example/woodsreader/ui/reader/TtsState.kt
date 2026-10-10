package com.example.woodsreader.ui.reader

enum class TtsState {
    IDLE,
    LOADING,   // Servicio enlazándose o navigator inicializándose
    PLAYING,
    PAUSED,
    STOPPED
}
