package com.example.woodsreader.core.web2epub.novel

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
enum class JobStatus {
    IN_PROGRESS,
    PAUSED_CHALLENGE,
    COMPLETED,
    FAILED
}

@Serializable
data class StoredChapterInfo(
    val index: Int,
    val title: String,
    val url: String,
    val fileName: String,
    val wordCount: Int,
    val status: String // mapeo de ChapterStatus
)

@Serializable
data class ImportJobState(
    val jobId: String,
    val novelUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String?,
    val coverLocalPath: String?,
    val totalChapters: Int,
    val status: JobStatus = JobStatus.IN_PROGRESS,
    val chapters: List<StoredChapterInfo> = emptyList()
)

/**
 * Almacenamiento local puro para reanudación de descargas e importaciones.
 * Estructura en disco:
 * baseDir/
 *   jobs/
 *     <jobId>/
 *       state.json
 *       state.tmp
 *       cover.<ext>
 *       chapters/
 *         chapter_0001.xhtml
 *       images/
 *         <hash>.<ext>
 */
class ImportStore(
    private val baseDir: File
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    companion object {
        fun computeJobId(url: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
            val hash = digest.digest(url.trim().lowercase().toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    private fun getJobDir(jobId: String): File {
        val dir = File(baseDir, "jobs/$jobId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveState(state: ImportJobState) {
        val jobDir = getJobDir(state.jobId)
        val stateFile = File(jobDir, "state.json")
        val tmpFile = File(jobDir, "state.json.tmp")

        val serialized = json.encodeToString(state)
        tmpFile.writeText(serialized, Charsets.UTF_8)
        if (stateFile.exists()) {
            stateFile.delete()
        }
        tmpFile.renameTo(stateFile)
    }

    fun loadState(jobId: String): ImportJobState? {
        val jobDir = File(baseDir, "jobs/$jobId")
        val stateFile = File(jobDir, "state.json")
        if (!stateFile.exists()) return null

        return try {
            val content = stateFile.readText(Charsets.UTF_8)
            json.decodeFromString<ImportJobState>(content)
        } catch (_: Exception) {
            null
        }
    }

    fun saveChapterContent(jobId: String, fileName: String, xhtmlContent: String): File {
        val chaptersDir = File(getJobDir(jobId), "chapters")
        if (!chaptersDir.exists()) chaptersDir.mkdirs()

        val file = File(chaptersDir, fileName)
        file.writeText(xhtmlContent, Charsets.UTF_8)
        return file
    }

    fun loadChapterContent(jobId: String, fileName: String): String? {
        val file = File(baseDir, "jobs/$jobId/chapters/$fileName")
        if (!file.exists()) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveImage(jobId: String, relativeZipPath: String, bytes: ByteArray): File {
        // relativeZipPath es "OEBPS/images/<hash>.<ext>"
        val cleanName = relativeZipPath.substringAfterLast("/")
        val imagesDir = File(getJobDir(jobId), "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        val file = File(imagesDir, cleanName)
        file.writeBytes(bytes)
        return file
    }

    fun loadImage(jobId: String, fileName: String): ByteArray? {
        val file = File(baseDir, "jobs/$jobId/images/$fileName")
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    fun saveCover(jobId: String, extension: String, bytes: ByteArray): String {
        val jobDir = getJobDir(jobId)
        val coverFile = File(jobDir, "cover.$extension")
        coverFile.writeBytes(bytes)
        return coverFile.name
    }

    fun loadCover(jobId: String, coverFileName: String): ByteArray? {
        val file = File(baseDir, "jobs/$jobId/$coverFileName")
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    fun deleteJob(jobId: String): Boolean {
        val jobDir = File(baseDir, "jobs/$jobId")
        if (!jobDir.exists()) return true
        return jobDir.deleteRecursively()
    }
}
