package com.example.woodsreader.core.web2epub.novel

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class EpubMetadata(
    val title: String,
    val author: String = "Desconocido",
    val description: String = "",
    val sourceUrl: String = "",
    val language: String = "es",
    val identifier: String = UUID.randomUUID().toString()
)

data class EpubChapterInput(
    val title: String,
    val fileName: String, // e.g. "chapter_0001.xhtml"
    val contentXhtml: String
)

data class EpubImageInput(
    val relativeZipPath: String, // e.g. "OEBPS/images/abc.jpg"
    val mimeType: String,
    val bytes: ByteArray,
    val isCover: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EpubImageInput
        return relativeZipPath == other.relativeZipPath
    }

    override fun hashCode(): Int {
        return relativeZipPath.hashCode()
    }
}

/**
 * Ensamblador puro de EPUB 3.0 estándar.
 * Genera EPUB con soporte dual (EPUB 3 nav + EPUB 2 NCX),
 * primera entrada mimetype en modo STORED y XML estrictamente sanitizado.
 */
class EpubAssembler {

    fun assemble(
        metadata: EpubMetadata,
        chapters: List<EpubChapterInput>,
        images: List<EpubImageInput>,
        outputFile: File
    ) {
        val parent = outputFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        FileOutputStream(outputFile).use { fos ->
            assembleToStream(metadata, chapters, images, fos)
        }
    }

    fun assembleToBytes(
        metadata: EpubMetadata,
        chapters: List<EpubChapterInput>,
        images: List<EpubImageInput>
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        assembleToStream(metadata, chapters, images, baos)
        return baos.toByteArray()
    }

    private fun assembleToStream(
        metadata: EpubMetadata,
        chapters: List<EpubChapterInput>,
        images: List<EpubImageInput>,
        outputStream: OutputStream
    ) {
        val cleanTitle = cleanXmlText(metadata.title).ifBlank { "Sin Título" }
        val cleanAuthor = cleanXmlText(metadata.author).ifBlank { "Desconocido" }
        val cleanDesc = cleanXmlText(metadata.description)
        val coverImage = images.firstOrNull { it.isCover }

        ZipOutputStream(outputStream).use { zos ->
            // 1. mimetype: Primera entrada, STORED, sin compresión
            writeMimetypeEntry(zos)

            // 2. META-INF/container.xml
            writeContainerXml(zos)

            // 3. OEBPS/style.css
            writeStyleCss(zos)

            // 4. Portada XHTML si existe imagen de portada
            if (coverImage != null) {
                writeCoverXhtml(zos, cleanTitle, coverImage, metadata.language)
            }

            // 5. Capítulos XHTML
            for (ch in chapters) {
                writeChapterXhtml(zos, ch, metadata.language)
            }

            // 6. OEBPS/nav.xhtml (EPUB 3 Navigation Document)
            writeNavXhtml(zos, cleanTitle, chapters, coverImage != null, metadata.language)

            // 7. OEBPS/toc.ncx (EPUB 2 backward compatibility)
            writeTocNcx(zos, metadata.identifier, cleanTitle, cleanAuthor, chapters, coverImage != null)

            // 8. Imágenes
            for (img in images) {
                writeZipEntry(zos, img.relativeZipPath, img.bytes)
            }

            // 9. OEBPS/content.opf
            writeContentOpf(zos, metadata, cleanTitle, cleanAuthor, cleanDesc, chapters, images, coverImage)
        }
    }

    private fun writeMimetypeEntry(zos: ZipOutputStream) {
        val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(bytes) }

        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            setCrc(crc.value)
        }
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun writeContainerXml(zos: ZipOutputStream) {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".trimIndent()
        writeZipEntry(zos, "META-INF/container.xml", xml.toByteArray(Charsets.UTF_8))
    }

    private fun writeStyleCss(zos: ZipOutputStream) {
        val css = """
body {
    font-family: serif;
    margin: 5% 5% 5% 5%;
    line-height: 1.6;
    text-align: justify;
}
h1, h2, h3, h4, h5, h6 {
    text-align: center;
    font-weight: bold;
    margin-top: 1.5em;
    margin-bottom: 1em;
}
p {
    margin-top: 0;
    margin-bottom: 0.8em;
    text-indent: 1.5em;
}
img {
    max-width: 100%;
    height: auto;
    display: block;
    margin: 1em auto;
}
.cover-container {
    text-align: center;
    padding: 0;
    margin: 0;
}
.cover-img {
    max-width: 100%;
    height: auto;
}
""".trimIndent()
        writeZipEntry(zos, "OEBPS/style.css", css.toByteArray(Charsets.UTF_8))
    }

    private fun writeCoverXhtml(zos: ZipOutputStream, title: String, coverImage: EpubImageInput, lang: String = "es") {
        val imgFileName = coverImage.relativeZipPath.substringAfterLast("/")
        val safeLang = lang.ifBlank { "es" }
        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$safeLang">
<head>
    <title>Portada</title>
    <link rel="stylesheet" type="text/css" href="style.css" />
</head>
<body class="cover-container">
    <div class="cover-container">
        <img class="cover-img" src="images/$imgFileName" alt="${escapeXmlAttr(title)}" />
    </div>
</body>
</html>""".trimIndent()
        writeZipEntry(zos, "OEBPS/cover.xhtml", xhtml.toByteArray(Charsets.UTF_8))
    }

    private fun writeChapterXhtml(zos: ZipOutputStream, chapter: EpubChapterInput, lang: String = "es") {
        val cleanTitle = cleanXmlText(chapter.title)
        val safeLang = lang.ifBlank { "es" }
        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$safeLang">
<head>
    <title>${escapeXmlText(cleanTitle)}</title>
    <link rel="stylesheet" type="text/css" href="style.css" />
</head>
<body>
    <h2>${escapeXmlText(cleanTitle)}</h2>
    ${chapter.contentXhtml}
</body>
</html>""".trimIndent()
        writeZipEntry(zos, "OEBPS/${chapter.fileName}", xhtml.toByteArray(Charsets.UTF_8))
    }

    private fun writeNavXhtml(
        zos: ZipOutputStream,
        title: String,
        chapters: List<EpubChapterInput>,
        hasCover: Boolean,
        lang: String = "es"
    ) {
        val sb = java.lang.StringBuilder()
        val safeLang = lang.ifBlank { "es" }
        sb.append("""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$safeLang">
<head>
    <title>${escapeXmlText(title)} - Tabla de Contenidos</title>
    <link rel="stylesheet" type="text/css" href="style.css" />
</head>
<body>
    <nav epub:type="toc" id="toc">
        <h1>Tabla de Contenidos</h1>
        <ol>
""")
        if (hasCover) {
            sb.append("            <li><a href=\"cover.xhtml\">Portada</a></li>\n")
        }
        for (ch in chapters) {
            sb.append("            <li><a href=\"${ch.fileName}\">${escapeXmlText(cleanXmlText(ch.title))}</a></li>\n")
        }
        sb.append("""        </ol>
    </nav>
</body>
</html>""")
        writeZipEntry(zos, "OEBPS/nav.xhtml", sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeTocNcx(
        zos: ZipOutputStream,
        identifier: String,
        title: String,
        author: String,
        chapters: List<EpubChapterInput>,
        hasCover: Boolean
    ) {
        val sb = java.lang.StringBuilder()
        sb.append("""<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
    <head>
        <meta name="dtb:uid" content="${escapeXmlAttr(identifier)}"/>
        <meta name="dtb:depth" content="1"/>
        <meta name="dtb:totalPageCount" content="0"/>
        <meta name="dtb:maxPageNumber" content="0"/>
    </head>
    <docTitle><text>${escapeXmlText(title)}</text></docTitle>
    <docAuthor><text>${escapeXmlText(author)}</text></docAuthor>
    <navMap>
""")
        var playOrder = 1
        if (hasCover) {
            sb.append("""        <navPoint id="navpoint-$playOrder" playOrder="$playOrder">
            <navLabel><text>Portada</text></navLabel>
            <content src="cover.xhtml"/>
        </navPoint>
""")
            playOrder++
        }
        for (ch in chapters) {
            sb.append("""        <navPoint id="navpoint-$playOrder" playOrder="$playOrder">
            <navLabel><text>${escapeXmlText(cleanXmlText(ch.title))}</text></navLabel>
            <content src="${ch.fileName}"/>
        </navPoint>
""")
            playOrder++
        }
        sb.append("""    </navMap>
</ncx>""")
        writeZipEntry(zos, "OEBPS/toc.ncx", sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeContentOpf(
        zos: ZipOutputStream,
        metadata: EpubMetadata,
        title: String,
        author: String,
        description: String,
        chapters: List<EpubChapterInput>,
        images: List<EpubImageInput>,
        coverImage: EpubImageInput?
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val modifiedDate = dateFormat.format(Date())

        val sb = java.lang.StringBuilder()
        sb.append("""<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
        <dc:identifier id="BookId">${escapeXmlText(metadata.identifier)}</dc:identifier>
        <dc:title>${escapeXmlText(title)}</dc:title>
        <dc:creator>${escapeXmlText(author)}</dc:creator>
        <dc:language>${escapeXmlText(metadata.language)}</dc:language>
        <meta property="dcterms:modified">$modifiedDate</meta>
""")
        if (description.isNotBlank()) {
            sb.append("        <dc:description>${escapeXmlText(description)}</dc:description>\n")
        }
        if (metadata.sourceUrl.isNotBlank()) {
            sb.append("        <dc:source>${escapeXmlText(metadata.sourceUrl)}</dc:source>\n")
        }
        if (coverImage != null) {
            sb.append("        <meta name=\"cover\" content=\"cover-image\" />\n")
        }
        sb.append("""    </metadata>
    <manifest>
        <item id="style" href="style.css" media-type="text/css"/>
        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
""")
        if (coverImage != null) {
            sb.append("        <item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        }
        for (ch in chapters) {
            val id = ch.fileName.substringBeforeLast(".")
            sb.append("        <item id=\"$id\" href=\"${ch.fileName}\" media-type=\"application/xhtml+xml\"/>\n")
        }
        for ((idx, img) in images.withIndex()) {
            val relativeOebps = img.relativeZipPath.removePrefix("OEBPS/")
            val coverProp = if (img.isCover) " properties=\"cover-image\"" else ""
            sb.append("        <item id=\"img_$idx\" href=\"$relativeOebps\" media-type=\"${img.mimeType}\"$coverProp/>\n")
        }
        sb.append("""    </manifest>
    <spine toc="ncx">
""")
        if (coverImage != null) {
            sb.append("        <itemref idref=\"cover\"/>\n")
        }
        for (ch in chapters) {
            val id = ch.fileName.substringBeforeLast(".")
            sb.append("        <itemref idref=\"$id\"/>\n")
        }
        sb.append("""    </spine>
</package>""")
        writeZipEntry(zos, "OEBPS/content.opf", sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun cleanXmlText(text: String): String {
        return text.replace(Regex("[\\r\\n\\t]+"), " ").trim()
    }

    private fun escapeXmlText(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeXmlAttr(text: String): String {
        return escapeXmlText(text)
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
