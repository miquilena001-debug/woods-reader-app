package com.example.woodsreader.core.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class M4aMetadataEmbedderTest {

    private data class ParsedBox(
        val type: String,
        val offset: Long,
        val size: Long,
        val payload: ByteArray
    )

    private fun wrapAtom(type: String, payload: ByteArray): ByteArray {
        val totalSize = 8 + payload.size
        return ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(totalSize)
            put(type.toByteArray(Charsets.ISO_8859_1), 0, 4)
            put(payload)
        }.array()
    }

    private fun createSyntheticFtypBox(): ByteArray {
        // 32 bytes ftyp: "ftyp", major "M4A ", minor 0, compatible "M4A ", "mp42", "isom"
        val payload = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN).apply {
            put("M4A ".toByteArray(Charsets.US_ASCII))
            putInt(0)
            put("M4A ".toByteArray(Charsets.US_ASCII))
            put("mp42".toByteArray(Charsets.US_ASCII))
            put("isom".toByteArray(Charsets.US_ASCII))
        }.array()
        return wrapAtom("ftyp", payload)
    }

    private fun createSyntheticMoovBox(): ByteArray {
        // moov con un átomo hijo mínimo 'mvhd'
        val mvhdPayload = ByteArray(16) { 0x01.toByte() }
        val mvhdAtom = wrapAtom("mvhd", mvhdPayload)
        return wrapAtom("moov", mvhdAtom)
    }

    private fun createSyntheticMdatBox(dataBytes: ByteArray): ByteArray {
        return wrapAtom("mdat", dataBytes)
    }

    private fun readTopLevelBoxes(file: File): List<ParsedBox> {
        val list = mutableListOf<ParsedBox>()
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            var pos = 0L
            val header = ByteArray(8)
            while (pos + 8 <= fileLen) {
                raf.seek(pos)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                val size = bb.int.toLong() and 0xFFFFFFFFL
                val type = String(header, 4, 4, Charsets.ISO_8859_1)

                val payloadSize = (size - 8).toInt().coerceAtLeast(0)
                val payload = ByteArray(payloadSize)
                raf.readFully(payload)

                list.add(ParsedBox(type, pos, size, payload))
                pos += size
            }
        }
        return list
    }

    private fun parseChildBoxes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): List<ParsedBox> {
        val list = mutableListOf<ParsedBox>()
        var pos = offset
        val end = offset + length
        while (pos + 8 <= end) {
            val bb = ByteBuffer.wrap(bytes, pos, 8).order(ByteOrder.BIG_ENDIAN)
            val size = bb.int.toLong() and 0xFFFFFFFFL
            val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)

            if (size < 8 || pos + size > end) break
            val payloadSize = (size - 8).toInt()
            val payload = ByteArray(payloadSize)
            System.arraycopy(bytes, pos + 8, payload, 0, payloadSize)

            list.add(ParsedBox(type, pos.toLong(), size, payload))
            pos += size.toInt()
        }
        return list
    }

    @Test
    fun testEmbedAllIntoM4a_moovBeforeMdat_preservesMdatBytesAndConvertsOldMoovToFree() {
        val tempFile = File.createTempFile("synth_moov_before", ".m4a")
        try {
            val mdatData = ByteArray(64) { (it + 10).toByte() }
            val ftyp = createSyntheticFtypBox()
            val moov = createSyntheticMoovBox()
            val mdat = createSyntheticMdatBox(mdatData)

            tempFile.outputStream().use { out ->
                out.write(ftyp)
                out.write(moov)
                out.write(mdat)
            }

            val originalMoovOffset = ftyp.size.toLong()
            val originalMdatOffset = (ftyp.size + moov.size).toLong()

            val success = M4aMetadataEmbedder.embedAllIntoM4a(
                m4aFile = tempFile,
                title = "Capítulo 1",
                author = "Autor de Prueba",
                bookTitle = "Libro de Prueba",
                synchronizedLyricsLrc = "[00:01.00]Letra de prueba",
                coverJpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
            )
            assertTrue(success)

            val boxes = readTopLevelBoxes(tempFile)
            // Debe contener: ftyp, free (el viejo moov), mdat, moov (el nuevo moov al final)
            assertEquals(4, boxes.size)
            assertEquals("ftyp", boxes[0].type)
            assertEquals("free", boxes[1].type)
            assertEquals(originalMoovOffset, boxes[1].offset)
            assertEquals(moov.size.toLong(), boxes[1].size)

            assertEquals("mdat", boxes[2].type)
            assertEquals(originalMdatOffset, boxes[2].offset)
            assertArrayEquals(mdatData, boxes[2].payload)

            assertEquals("moov", boxes[3].type)
            assertTrue(boxes[3].offset >= originalMdatOffset + mdat.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testEmbedAllIntoM4a_moovAfterMdat_preservesMdatBytesAndRewritesMoovInPlace() {
        val tempFile = File.createTempFile("synth_moov_after", ".m4a")
        try {
            val mdatData = ByteArray(64) { (it * 2).toByte() }
            val ftyp = createSyntheticFtypBox()
            val mdat = createSyntheticMdatBox(mdatData)
            val moov = createSyntheticMoovBox()

            tempFile.outputStream().use { out ->
                out.write(ftyp)
                out.write(mdat)
                out.write(moov)
            }

            val originalMdatOffset = ftyp.size.toLong()
            val originalMoovOffset = (ftyp.size + mdat.size).toLong()

            val success = M4aMetadataEmbedder.embedAllIntoM4a(
                m4aFile = tempFile,
                title = "Capítulo Final",
                author = "Brandon Sanderson",
                bookTitle = "Palabras Radiantes",
                synchronizedLyricsLrc = "[00:02.00]Vida antes que muerte",
                coverJpegBytes = byteArrayOf(1, 2, 3, 4)
            )
            assertTrue(success)

            val boxes = readTopLevelBoxes(tempFile)
            // Debe contener: ftyp, mdat, moov reescrito en el sitio
            assertEquals(3, boxes.size)
            assertEquals("ftyp", boxes[0].type)

            assertEquals("mdat", boxes[1].type)
            assertEquals(originalMdatOffset, boxes[1].offset)
            assertArrayEquals(mdatData, boxes[1].payload)

            assertEquals("moov", boxes[2].type)
            assertEquals(originalMoovOffset, boxes[2].offset)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testEmbedAllIntoM4a_parsesBackAtoms_covr_lyr_nam_art_alb() {
        val tempFile = File.createTempFile("synth_parse_back", ".m4a")
        try {
            val ftyp = createSyntheticFtypBox()
            val mdat = createSyntheticMdatBox(ByteArray(32) { 0x55.toByte() })
            val moov = createSyntheticMoovBox()

            tempFile.outputStream().use { out ->
                out.write(ftyp)
                out.write(mdat)
                out.write(moov)
            }

            val testTitle = "El Camino de los Reyes - Cap 1"
            val testAuthor = "Brandon Sanderson"
            val testAlbum = "El Archivo de las Tormentas"
            val testLrc = "[00:01.00]El viento susurraba\n[00:04.50]entre las colinas de las Llanuras Quebradas"
            val testCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x10, 0x20, 0x30)

            val success = M4aMetadataEmbedder.embedAllIntoM4a(
                m4aFile = tempFile,
                title = testTitle,
                author = testAuthor,
                bookTitle = testAlbum,
                synchronizedLyricsLrc = testLrc,
                coverJpegBytes = testCover
            )
            assertTrue(success)

            // Parsear hacia abajo: moov -> udta -> meta -> ilst
            val topBoxes = readTopLevelBoxes(tempFile)
            val moovBox = topBoxes.first { it.type == "moov" }
            val moovChildren = parseChildBoxes(moovBox.payload)

            val udtaBox = moovChildren.firstOrNull { it.type == "udta" }
            assertNotNull(udtaBox)

            val udtaChildren = parseChildBoxes(udtaBox!!.payload)
            val metaBox = udtaChildren.firstOrNull { it.type == "meta" }
            assertNotNull(metaBox)

            // meta es un FullBox (4 bytes version/flags al inicio antes de sus hijos)
            val metaChildren = parseChildBoxes(metaBox!!.payload, offset = 4)
            val ilstBox = metaChildren.firstOrNull { it.type == "ilst" }
            assertNotNull(ilstBox)

            val ilstChildren = parseChildBoxes(ilstBox!!.payload)

            // 1. Átomo ©nam (0xA9, 'n', 'a', 'm')
            val namType = String(byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()), Charsets.ISO_8859_1)
            val namBox = ilstChildren.firstOrNull { it.type == namType }
            assertNotNull(namBox)
            val namData = parseChildBoxes(namBox!!.payload).first { it.type == "data" }
            // data payload: 4 bytes type, 4 bytes locale, text UTF-8
            val parsedTitle = String(namData.payload, 8, namData.payload.size - 8, Charsets.UTF_8)
            assertEquals(testTitle, parsedTitle)

            // 2. Átomo ©ART (0xA9, 'A', 'R', 'T')
            val artType = String(byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte()), Charsets.ISO_8859_1)
            val artBox = ilstChildren.firstOrNull { it.type == artType }
            assertNotNull(artBox)
            val artData = parseChildBoxes(artBox!!.payload).first { it.type == "data" }
            val parsedAuthor = String(artData.payload, 8, artData.payload.size - 8, Charsets.UTF_8)
            assertEquals(testAuthor, parsedAuthor)

            // 3. Átomo ©alb (0xA9, 'a', 'l', 'b')
            val albType = String(byteArrayOf(0xA9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte()), Charsets.ISO_8859_1)
            val albBox = ilstChildren.firstOrNull { it.type == albType }
            assertNotNull(albBox)
            val albData = parseChildBoxes(albBox!!.payload).first { it.type == "data" }
            val parsedAlbum = String(albData.payload, 8, albData.payload.size - 8, Charsets.UTF_8)
            assertEquals(testAlbum, parsedAlbum)

            // 4. Átomo ©lyr (0xA9, 'l', 'y', 'r')
            val lyrType = String(byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()), Charsets.ISO_8859_1)
            val lyrBox = ilstChildren.firstOrNull { it.type == lyrType }
            assertNotNull(lyrBox)
            val lyrData = parseChildBoxes(lyrBox!!.payload).first { it.type == "data" }
            val parsedLrc = String(lyrData.payload, 8, lyrData.payload.size - 8, Charsets.UTF_8)
            assertEquals(testLrc, parsedLrc)

            // 5. Átomo covr
            val covrBox = ilstChildren.firstOrNull { it.type == "covr" }
            assertNotNull(covrBox)
            val covrData = parseChildBoxes(covrBox!!.payload).first { it.type == "data" }
            val parsedCover = ByteArray(covrData.payload.size - 8)
            System.arraycopy(covrData.payload, 8, parsedCover, 0, parsedCover.size)
            assertArrayEquals(testCover, parsedCover)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testEmbedAllIntoM4a_nonExistentOrTooShortFileReturnsFalse() {
        val nonExistent = File("non_existent_file_woods_12345.m4a")
        assertFalse(
            M4aMetadataEmbedder.embedAllIntoM4a(
                m4aFile = nonExistent,
                title = "T",
                author = "A",
                bookTitle = "B",
                synchronizedLyricsLrc = "L",
                coverJpegBytes = null
            )
        )

        val tinyFile = File.createTempFile("tiny", ".m4a")
        try {
            tinyFile.writeBytes(ByteArray(16)) // < 32 bytes
            assertFalse(
                M4aMetadataEmbedder.embedAllIntoM4a(
                    m4aFile = tinyFile,
                    title = "T",
                    author = "A",
                    bookTitle = "B",
                    synchronizedLyricsLrc = "L",
                    coverJpegBytes = null
                )
            )
        } finally {
            tinyFile.delete()
        }
    }
}
