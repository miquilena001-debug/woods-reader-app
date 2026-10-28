package com.example.woodsreader.core.tts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object M4aMetadataEmbedder {

    private data class Mp4Box(
        val offset: Long,
        val size: Long,
        val type: String,
        val headerSize: Int
    )

    /**
     * Incrusta Título, Autor, Álbum (Novela), Subtítulos sincronizados (.LRC) y Portada (JPEG)
     * directamente dentro del archivo .m4a sin generar archivos externos.
     */
    fun embedAllIntoM4a(
        m4aFile: File,
        title: String,
        author: String,
        bookTitle: String,
        synchronizedLyricsLrc: String,
        coverJpegBytes: ByteArray?
    ): Boolean {
        if (!m4aFile.exists() || m4aFile.length() < 32L) return false

        return runCatching {
            RandomAccessFile(m4aFile, "rw").use { raf ->
                val fileLen = raf.length()
                val topBoxes = readBoxes(raf, 0L, fileLen)
                val moovBox = topBoxes.firstOrNull { it.type == "moov" } ?: return false
                val mdatBox = topBoxes.firstOrNull { it.type == "mdat" } ?: return false

                // Leer el contenido interno de 'moov'
                val moovPayloadOffset = moovBox.offset + moovBox.headerSize
                val moovPayloadEnd = moovBox.offset + moovBox.size
                val moovChildren = readBoxes(raf, moovPayloadOffset, moovPayloadEnd)

                // Conservar todos los átomos originales de 'moov' excepto 'udta' previo si existiera
                val preservedMoovContent = ByteArrayOutputStream()
                for (child in moovChildren) {
                    if (child.type != "udta") {
                        val bytes = ByteArray(child.size.toInt())
                        raf.seek(child.offset)
                        raf.readFully(bytes)
                        preservedMoovContent.write(bytes)
                    }
                }

                // Construir el nuevo átomo 'udta' -> 'meta' -> 'ilst' con portada y subtítulos adentro
                val udtaBytes = buildUdtaAtom(
                    title = title,
                    artist = author,
                    album = bookTitle,
                    lyricsLrc = synchronizedLyricsLrc,
                    coverJpeg = coverJpegBytes
                )
                preservedMoovContent.write(udtaBytes)

                val newMoovPayload = preservedMoovContent.toByteArray()
                val newMoovAtom = wrapAtom("moov", newMoovPayload)

                if (moovBox.offset > mdatBox.offset) {
                    // 'moov' ya está al final del archivo: reemplazar directamente en el sitio
                    raf.seek(moovBox.offset)
                    raf.write(newMoovAtom)
                    raf.setLength(moovBox.offset + newMoovAtom.size)
                } else {
                    // 'moov' está antes de 'mdat': convertir el viejo 'moov' en un átomo 'free' del
                    // mismo tamaño exacto para NO desplazar 'mdat' ni invalidar punteros 'stco',
                    // y escribir el nuevo 'moov' completo al final del archivo.
                    val oldSize = moovBox.size.toInt()
                    if (oldSize >= 8) {
                        raf.seek(moovBox.offset)
                        val freeHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).apply {
                            putInt(oldSize)
                            put("free".toByteArray(Charsets.US_ASCII))
                        }.array()
                        raf.write(freeHeader)
                        val zeroBuf = ByteArray(minOf(4096, oldSize - 8))
                        var remaining = oldSize - 8
                        while (remaining > 0) {
                            val toWrite = minOf(remaining, zeroBuf.size)
                            raf.write(zeroBuf, 0, toWrite)
                            remaining -= toWrite
                        }
                        raf.seek(fileLen)
                        raf.write(newMoovAtom)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Sobrecarga conveniente que procesa directamente un archivo de imagen de portada (JPG, PNG, WebP)
     * optimizándolo a bytes JPEG listos para incrustar en el átomo 'covr'.
     */
    fun embedAllIntoM4a(
        m4aFile: File,
        title: String,
        author: String,
        bookTitle: String,
        synchronizedLyricsLrc: String,
        coverFile: File?
    ): Boolean {
        val coverBytes = prepareCoverBytes(coverFile)
        return embedAllIntoM4a(
            m4aFile = m4aFile,
            title = title,
            author = author,
            bookTitle = bookTitle,
            synchronizedLyricsLrc = synchronizedLyricsLrc,
            coverJpegBytes = coverBytes
        )
    }

    private fun prepareCoverBytes(coverFile: File?): ByteArray? {
        if (coverFile == null || !coverFile.exists() || coverFile.length() == 0L) return null
        return runCatching {
            val isJpeg = coverFile.name.endsWith(".jpg", ignoreCase = true) || coverFile.name.endsWith(".jpeg", ignoreCase = true)
            if (isJpeg && coverFile.length() <= 800 * 1024L) {
                coverFile.readBytes()
            } else {
                val bmp = BitmapFactory.decodeFile(coverFile.absolutePath) ?: return@runCatching coverFile.readBytes()
                val maxDim = 800
                val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                    val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
                    val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
                    val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(bmp, w, h, true)
                } else {
                    bmp
                }
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                baos.toByteArray()
            }
        }.getOrNull()
    }

    private fun readBoxes(raf: RandomAccessFile, start: Long, end: Long): List<Mp4Box> {
        val list = ArrayList<Mp4Box>()
        var pos = start
        val headerBuf = ByteArray(8)
        while (pos + 8 <= end) {
            raf.seek(pos)
            raf.readFully(headerBuf)
            val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.BIG_ENDIAN)
            val size32 = bb.int.toLong() and 0xFFFFFFFFL
            val type = String(headerBuf, 4, 4, Charsets.ISO_8859_1)

            var actualSize = size32
            var headerSize = 8
            if (size32 == 1L) {
                if (pos + 16 > end) break
                val extBuf = ByteArray(8)
                raf.readFully(extBuf)
                actualSize = ByteBuffer.wrap(extBuf).order(ByteOrder.BIG_ENDIAN).long
                headerSize = 16
            } else if (size32 == 0L) {
                actualSize = end - pos
            }

            if (actualSize < headerSize || pos + actualSize > end) break
            list.add(Mp4Box(pos, actualSize, type, headerSize))
            pos += actualSize
        }
        return list
    }

    private fun buildUdtaAtom(
        title: String,
        artist: String,
        album: String,
        lyricsLrc: String,
        coverJpeg: ByteArray?
    ): ByteArray {
        val ilstChildren = ByteArrayOutputStream()

        if (title.isNotBlank()) {
            ilstChildren.write(buildTextMetaItem(byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()), title))
        }
        if (artist.isNotBlank()) {
            ilstChildren.write(buildTextMetaItem(byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte()), artist))
        }
        if (album.isNotBlank()) {
            ilstChildren.write(buildTextMetaItem(byteArrayOf(0xA9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte()), album))
        }
        if (lyricsLrc.isNotBlank()) {
            // Átomo ©lyr con las letras sincronizadas en formato LRC
            ilstChildren.write(buildTextMetaItem(byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()), lyricsLrc))
        }
        if (coverJpeg != null && coverJpeg.isNotEmpty()) {
            ilstChildren.write(buildCoverMetaItem(coverJpeg))
        }

        val ilstAtom = wrapAtom("ilst", ilstChildren.toByteArray())

        // Átomo obligatorio 'hdlr' para metadatos iTunes/MP4 ('mdir' / 'appl')
        val hdlrPayload = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0) // version & flags
            putInt(0) // pre_defined
            put("mdir".toByteArray(Charsets.US_ASCII))
            put("appl".toByteArray(Charsets.US_ASCII))
            putInt(0)
            putInt(0)
            put(0.toByte()) // name (null-terminated)
        }.array()
        val hdlrAtom = wrapAtom("hdlr", hdlrPayload)

        val metaPayload = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0)) // FullBox version + flags
            write(hdlrAtom)
            write(ilstAtom)
        }.toByteArray()
        val metaAtom = wrapAtom("meta", metaPayload)

        return wrapAtom("udta", metaAtom)
    }

    private fun buildTextMetaItem(fourCc: ByteArray, text: String): ByteArray {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        val dataPayload = ByteBuffer.allocate(8 + utf8.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(1) // Tipo 1 = UTF-8 text
            putInt(0) // Locale = 0
            put(utf8)
        }.array()
        val dataAtom = wrapAtom("data", dataPayload)
        return wrapAtomBytes(fourCc, dataAtom)
    }

    private fun buildCoverMetaItem(jpegBytes: ByteArray): ByteArray {
        val dataPayload = ByteBuffer.allocate(8 + jpegBytes.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(13) // Tipo 13 = JPEG (0x0D)
            putInt(0)  // Locale = 0
            put(jpegBytes)
        }.array()
        val dataAtom = wrapAtom("data", dataPayload)
        return wrapAtom("covr", dataAtom)
    }

    private fun wrapAtom(typeAscii: String, payload: ByteArray): ByteArray {
        return wrapAtomBytes(typeAscii.toByteArray(Charsets.ISO_8859_1), payload)
    }

    private fun wrapAtomBytes(fourCc: ByteArray, payload: ByteArray): ByteArray {
        val totalSize = 8 + payload.size
        return ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(totalSize)
            put(fourCc, 0, 4)
            put(payload)
        }.array()
    }
}
