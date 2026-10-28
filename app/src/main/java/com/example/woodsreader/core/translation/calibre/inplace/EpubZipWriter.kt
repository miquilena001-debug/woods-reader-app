package com.example.woodsreader.core.translation.calibre.inplace

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object EpubZipWriter {

    /**
     * Copia todas las entradas de [sourceZipFile] a [outputFile], sustituyendo únicamente
     * las entradas presentes en [modifiedEntries] (ruta relativa -> contenido en bytes).
     */
    fun writeInPlace(
        sourceZipFile: ZipFile,
        outputFile: File,
        modifiedEntries: Map<String, ByteArray>
    ) {
        val tempFiles = mutableMapOf<String, File>()
        val tempDir = File(outputFile.parentFile ?: File("."), ".tmp_entries_${System.currentTimeMillis()}")
        try {
            if (modifiedEntries.isNotEmpty()) {
                tempDir.mkdirs()
                for ((path, bytes) in modifiedEntries) {
                    val f = File(tempDir, "entry_${tempFiles.size}.dat")
                    f.writeBytes(bytes)
                    tempFiles[path] = f
                }
            }
            writeInPlaceFiles(sourceZipFile, outputFile, tempFiles)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Copia todas las entradas de [sourceZipFile] a [outputFile], sustituyendo únicamente
     * las entradas presentes en [modifiedFiles] (ruta relativa -> archivo con contenido).
     *
     * Streaming directo desde disco: memoria constante O(1) independientemente del tamaño del libro.
     * Garantiza el cumplimiento estricto del estándar EPUB:
     * 1. 'mimetype' es la primera entrada, método STORED (sin compresión), sin extra fields.
     * 2. Todas las demás entradas usan DEFLATED.
     * 3. Escritura atómica mediante archivo temporal '.tmp'.
     */
    fun writeInPlaceFiles(
        sourceZipFile: ZipFile,
        outputFile: File,
        modifiedFiles: Map<String, File>
    ) {
        val tempFile = File(outputFile.parentFile ?: File("."), "${outputFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zos ->
                // 1. Escribir mimetype primero (STORED)
                writeMimetypeEntry(zos, sourceZipFile, modifiedFiles["mimetype"])

                // 2. Escribir el resto de entradas
                val writtenNames = mutableSetOf("mimetype")

                val entries = sourceZipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val normalizedName = entry.name.replace('\\', '/')
                    if (normalizedName == "mimetype") continue

                    val modifiedFile = modifiedFiles[normalizedName]
                    if (modifiedFile != null && modifiedFile.exists()) {
                        writeDeflatedFileEntry(zos, normalizedName, modifiedFile)
                    } else {
                        copyEntryDirectly(zos, sourceZipFile, entry, normalizedName)
                    }
                    writtenNames.add(normalizedName)
                }

                // Escribir cualquier entrada nueva que estuviese en modifiedFiles y no en el zip original
                for ((name, file) in modifiedFiles) {
                    val normalized = name.replace('\\', '/')
                    if (normalized !in writtenNames && file.exists()) {
                        writeDeflatedFileEntry(zos, normalized, file)
                        writtenNames.add(normalized)
                    }
                }
            }

            // Verificar que el ZIP resultante es válido antes de renombrar
            ZipFile(tempFile).use { verifyZip ->
                if (verifyZip.getEntry("mimetype") == null) {
                    throw IllegalStateException("El archivo EPUB generado no contiene entrada mimetype válida.")
                }
            }

            // Reemplazo atómico
            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }
    }

    private fun writeMimetypeEntry(
        zos: ZipOutputStream,
        sourceZip: ZipFile,
        customFile: File?
    ) {
        val bytes = if (customFile != null && customFile.exists()) {
            customFile.readBytes()
        } else {
            val originalEntry = sourceZip.getEntry("mimetype")
            if (originalEntry != null) {
                sourceZip.getInputStream(originalEntry).use { it.readBytes() }
            } else {
                "application/epub+zip".toByteArray(Charsets.US_ASCII)
            }
        }

        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
            extra = null
        }
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun writeDeflatedFileEntry(
        zos: ZipOutputStream,
        name: String,
        file: File
    ) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(entry)
        file.inputStream().use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }

    private fun copyEntryDirectly(
        zos: ZipOutputStream,
        sourceZip: ZipFile,
        sourceEntry: ZipEntry,
        name: String
    ) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(entry)
        sourceZip.getInputStream(sourceEntry).use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }
}
