package com.example.woodsreader.service

import android.os.FileObserver
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Android's FileObserver is not recursive by default.
 * This class recursively creates FileObservers for every sub-directory 
 * to detect newly downloaded or transferred books.
 */
class RecursiveFileObserver(
    private val rootPath: String,
    private val onFileEvent: (File) -> Unit
) {
    // CopyOnWriteArrayList: seguro para acceso concurrente desde hilos de FileObserver
    private val observers = CopyOnWriteArrayList<SingleFileObserver>()
    private val mask = FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.DELETE_SELF or FileObserver.MOVED_FROM
    
    private val blacklist = setOf(
        "android/data", "android/media", "android/obb",
        ".thumbnails", "dcim", "pictures", "movies", "music",
        "alarms", "notifications", "podcasts", "ringtones"
    )

    fun startWatching() {
        stopWatching()
        val rootDir = File(rootPath)
        if (rootDir.exists()) {
            watchDirectoryRecursively(rootDir, 0)
        }
    }

    fun stopWatching() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }

    private fun watchDirectoryRecursively(dir: File, depth: Int) {
        // Limit depth to avoid resource exhaustion
        if (depth > 10) return

        // Watch this directory
        val observer = SingleFileObserver(dir.absolutePath, mask, depth)
        observer.startWatching()
        observers.add(observer)

        // Then watch all existing subdirectories
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && file.shouldWatch(depth)) {
                watchDirectoryRecursively(file, depth + 1)
            }
        }
    }

    private fun File.shouldWatch(depth: Int): Boolean {
        if (!isDirectory || isHidden || name.startsWith(".")) return false
        val pathLower = absolutePath.lowercase()
        return blacklist.none { pathLower.contains("/$it") || pathLower.endsWith("/$it") }
    }

    private inner class SingleFileObserver(
        val path: String,
        mask: Int,
        val depth: Int
    ) : FileObserver(path, mask) {
        override fun onEvent(event: Int, relativePath: String?) {
            // 1. Detectar si el directorio vigilado fue borrado o movido (M-8)
            if ((event and FileObserver.DELETE_SELF) != 0 || (event and FileObserver.MOVED_FROM) != 0) {
                this.stopWatching()
                observers.remove(this)
                return
            }

            if (relativePath == null) return
            val file = File(path, relativePath)
            
            // 2. Si se crea un nuevo directorio, vigilarlo también
            if ((event and FileObserver.CREATE) != 0 && file.shouldWatch(depth)) {
                watchDirectoryRecursively(file, depth + 1)
            } 
            // 3. Disparar el listener para otros eventos
            else {
                onFileEvent(file)
            }
        }
    }
}
