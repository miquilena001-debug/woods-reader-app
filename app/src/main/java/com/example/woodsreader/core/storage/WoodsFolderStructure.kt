package com.example.woodsreader.core.storage

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor canónico de la estructura de carpetas de Woods Reader (Regla R-TEST).
 * Todas las rutas y nombres de archivo se resuelven aquí sin dependencias de android.*.
 */
@Singleton
class WoodsFolderStructure @Inject constructor(
    private val rootResolver: StorageRootResolver
) {
    val rootDir: File get() = rootResolver.getEffectiveRoot()

    val backupsDir: File get() = File(rootDir, "Backups").apply { if (!exists()) mkdirs() }
    val audiobooksDir: File get() = File(rootDir, "Audiolibros").apply { if (!exists()) mkdirs() }
    val videosDir: File get() = File(rootDir, "Videos").apply { if (!exists()) mkdirs() }
    val bookmarksDir: File get() = File(rootDir, "Marcadores").apply { if (!exists()) mkdirs() }
    val epubsDir: File get() = File(rootDir, "Epubs").apply { if (!exists()) mkdirs() }
    val sharedDir: File get() = File(rootDir, "Compartidos").apply { if (!exists()) mkdirs() }
    val siteProfilesDir: File get() = File(rootDir, "site_profiles").apply { if (!exists()) mkdirs() }

    /**
     * Sanea nombres de archivo para compatibilidad total con FAT32/exFAT/NTFS.
     * Elimina caracteres no permitidos: \ / : * ? " < > | y colapsa espacios.
     */
    fun sanitizeFileName(name: String, maxLength: Int = 60): String {
        val sanitized = name
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "archivo" }
        return sanitized.take(maxLength)
    }

    /**
     * Genera un archivo único dentro del directorio padre resolviendo colisiones numéricas:
     * baseName.ext -> baseName (1).ext -> baseName (2).ext
     */
    fun resolveUniqueFile(parentDir: File, baseName: String, extension: String): File {
        val extClean = extension.removePrefix(".")
        val sanitizedBase = sanitizeFileName(baseName)
        var target = File(parentDir, "$sanitizedBase.$extClean")
        var counter = 1
        while (target.exists()) {
            target = File(parentDir, "$sanitizedBase ($counter).$extClean")
            counter++
        }
        return target
    }
}
