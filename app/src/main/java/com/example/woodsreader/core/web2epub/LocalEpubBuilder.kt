package com.example.woodsreader.core.web2epub

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ScrapedChapterMeta(
    val index: Int,
    val fileName: String,
    val title: String,
    val sourceUrl: String
)

class LocalEpubBuilder(
    private val outputFile: File,
    private val bookTitle: String,
    private val bookAuthor: String = "Web Novel",
    private val languageCode: String = "es"
) : AutoCloseable {

    private val bookUuid = UUID.randomUUID().toString()
    private val zipOut = ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile)))
    private val chapters = ArrayList<ScrapedChapterMeta>()
    private var isClosed = false

    init {
        writeMimetypeEntry()
        writeContainerXml()
        writeStylesheet()
    }

    /**
     * El estándar EPUB exige que el primer archivo del ZIP sea 'mimetype'
     * almacenado sin compresión (STORED) y sin cabeceras extra.
     */
    private fun writeMimetypeEntry() {
        val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zipOut.putNextEntry(entry)
        zipOut.write(bytes)
        zipOut.closeEntry()
    }

    private fun writeContainerXml() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
            "    <rootfiles>\n" +
            "        <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
            "    </rootfiles>\n" +
            "</container>"
        putTextEntry("META-INF/container.xml", xml)
    }

    private fun writeStylesheet() {
        val css = "body { line-height: 1.6; margin: 1em; font-family: serif; }\n" +
            "h1, h2 { text-align: center; margin-bottom: 1.2em; font-weight: bold; }\n" +
            "p { margin-top: 0; margin-bottom: 0.9em; text-align: justify; }"
        putTextEntry("OEBPS/style.css", css)
    }

    fun appendChapter(rawTitle: String, paragraphs: List<String>, sourceUrl: String) {
        if (paragraphs.isEmpty()) return
        val idx = chapters.size + 1
        val fileName = "chapter_${"%04d".format(java.util.Locale.ROOT, idx)}.xhtml"
        val cleanTitle = rawTitle.trim().ifBlank { "Capítulo $idx" }

        val bodyBuilder = StringBuilder(paragraphs.sumOf { it.length + 16 })
        for (p in paragraphs) {
            val trimmed = p.trim()
            if (trimmed.isNotEmpty()) {
                bodyBuilder.append("    <p>").append(escapeXml(trimmed)).append("</p>\n")
            }
        }

        val xhtml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<!DOCTYPE html>\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"$languageCode\" lang=\"$languageCode\">\n" +
            "<head>\n" +
            "    <meta charset=\"utf-8\" />\n" +
            "    <title>${escapeXml(cleanTitle)}</title>\n" +
            "    <link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\" />\n" +
            "</head>\n" +
            "<body>\n" +
            "    <h2>${escapeXml(cleanTitle)}</h2>\n" +
            bodyBuilder.toString() +
            "</body>\n" +
            "</html>"

        putTextEntry("OEBPS/$fileName", xhtml)
        chapters.add(
            ScrapedChapterMeta(
                index = idx,
                fileName = fileName,
                title = cleanTitle,
                sourceUrl = sourceUrl
            )
        )
    }

    fun finishEpub(): Boolean {
        if (isClosed) return chapters.isNotEmpty()
        if (chapters.isEmpty()) {
            close()
            outputFile.delete()
            return false
        }
        writeContentOpf()
        writeTocNcx()
        close()
        return true
    }

    private fun writeContentOpf() {
        val manifestItems = StringBuilder()
        val spineItems = StringBuilder()

        manifestItems.append("        <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
        manifestItems.append("        <item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")

        for (ch in chapters) {
            val itemId = "ch_${ch.index}"
            manifestItems.append("        <item id=\"$itemId\" href=\"${ch.fileName}\" media-type=\"application/xhtml+xml\"/>\n")
            spineItems.append("        <itemref idref=\"$itemId\"/>\n")
        }

        val lastUrl = chapters.lastOrNull()?.sourceUrl.orEmpty()
        val opf = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"BookId\" version=\"2.0\">\n" +
            "    <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\">\n" +
            "        <dc:title>${escapeXml(bookTitle)}</dc:title>\n" +
            "        <dc:creator opf:role=\"aut\">${escapeXml(bookAuthor)}</dc:creator>\n" +
            "        <dc:language>$languageCode</dc:language>\n" +
            "        <dc:identifier id=\"BookId\" opf:scheme=\"UUID\">urn:uuid:$bookUuid</dc:identifier>\n" +
            (if (lastUrl.isNotBlank()) "        <dc:source>${escapeXml(lastUrl)}</dc:source>\n" else "") +
            "    </metadata>\n" +
            "    <manifest>\n" +
            manifestItems.toString() +
            "    </manifest>\n" +
            "    <spine toc=\"ncx\">\n" +
            spineItems.toString() +
            "    </spine>\n" +
            "</package>"

        putTextEntry("OEBPS/content.opf", opf)
    }

    private fun writeTocNcx() {
        val navPoints = StringBuilder()
        for (ch in chapters) {
            navPoints.append("        <navPoint id=\"navPoint-${ch.index}\" playOrder=\"${ch.index}\">\n")
            navPoints.append("            <navLabel><text>${escapeXml(ch.title)}</text></navLabel>\n")
            navPoints.append("            <content src=\"${ch.fileName}\"/>\n")
            navPoints.append("        </navPoint>\n")
        }

        val ncx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n" +
            "    <head>\n" +
            "        <meta name=\"dtb:uid\" content=\"urn:uuid:$bookUuid\"/>\n" +
            "        <meta name=\"dtb:depth\" content=\"1\"/>\n" +
            "        <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n" +
            "        <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n" +
            "    </head>\n" +
            "    <docTitle><text>${escapeXml(bookTitle)}</text></docTitle>\n" +
            "    <navMap>\n" +
            navPoints.toString() +
            "    </navMap>\n" +
            "</ncx>"

        putTextEntry("OEBPS/toc.ncx", ncx)
    }

    private fun putTextEntry(path: String, content: String) {
        val entry = ZipEntry(path)
        zipOut.putNextEntry(entry)
        zipOut.write(content.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length + 16)
        for (c in input) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (c.code == 0x09 || c.code == 0x0A || c.code == 0x0D || c.code in 0x20..0xD7FF || c.code in 0xE000..0xFFFD) {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            runCatching { zipOut.close() }
        }
    }
}
