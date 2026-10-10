package com.example.woodsreader.data.scanner

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/** Provides root storage directories to scan. Extracted for extreme testability. */
interface StorageRootProvider {
    fun getStorageRoots(): List<File>
}

@Singleton
class AndroidStorageRootProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageRootProvider {
    override fun getStorageRoots(): List<File> {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        val volumes = storageManager.storageVolumes
        
        val roots = volumes.mapNotNull { volume ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                volume.directory
            } else {
                try {
                    val getPath = volume.javaClass.getMethod("getPath")
                    val path = getPath.invoke(volume) as String
                    File(path)
                } catch (e: Exception) {
                    null
                }
            }
        }.filter { it.exists() && it.isDirectory }

        if (roots.isEmpty()) {
            val fallback = android.os.Environment.getExternalStorageDirectory()
            if (fallback != null && fallback.exists() && fallback.isDirectory) {
                return listOf(fallback)
            }
        }
        return roots
    }
}

interface FileSystemScanner {
    fun scanAllDevice(): Flow<File>
}

@Singleton
class DeepFileSystemScanner @Inject constructor(
    private val rootProvider: StorageRootProvider
) : FileSystemScanner {

    companion object {
        private const val MIN_FILE_SIZE_BYTES = 1024L

        private val IGNORED_FOLDERS = setOf(
            "system", "proc", "sys", "vendor", "data", "cache", "acct", "dev",
            "dcim", "movies", "music", "alarms", "notifications", "podcasts", "ringtones",
            ".thumbnails", "android"
        )

        private val SUPPORTED_EXTENSIONS = setOf("pdf", "epub")
    }

    override fun scanAllDevice(): Flow<File> = flow {
        val roots = rootProvider.getStorageRoots()
        val context = currentCoroutineContext()

        for (root in roots) {
            if (!context.isActive) break

            val stack = ArrayDeque<File>()
            stack.push(root)

            // Limit yield checks to avoid excessive context switching overhead
            var iterations = 0

            while (stack.isNotEmpty()) {
                if (++iterations % 100 == 0 && !context.isActive) {
                    break
                }

                val currentDir = stack.pop()
                val files = runCatching { currentDir.listFiles() }.getOrNull() ?: continue

                for (file in files) {
                    if (file.isDirectory) {
                        val nameLower = file.name.lowercase()
                        if (nameLower.startsWith(".") || IGNORED_FOLDERS.contains(nameLower)) {
                            continue
                        }
                        stack.push(file)
                    } else if (file.isFile && file.length() >= MIN_FILE_SIZE_BYTES) {
                        val ext = file.extension.lowercase()
                        if (SUPPORTED_EXTENSIONS.contains(ext)) {
                            emit(file)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
