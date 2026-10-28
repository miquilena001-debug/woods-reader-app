package com.example.woodsreader.core.translation.calibre.inplace

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestEpubFactory {

    // 1x1 transparente PNG
    val DUMMY_PNG_1X1 = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
        0x42, 0x60, 0x82.toByte()
    )

    fun buildEpub(files: Map<String, ByteArray>): File {
        val tempFile = File.createTempFile("test_epub_", ".epub")
        tempFile.deleteOnExit()

        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
            // 1. mimetype STORED primero
            val mimetypeBytes = files["mimetype"] ?: "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimetypeBytes) }
            val mimetypeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                this.crc = crc.value
                extra = null
            }
            zos.putNextEntry(mimetypeEntry)
            zos.write(mimetypeBytes)
            zos.closeEntry()

            // 2. Resto de entradas (DEFLATED)
            for ((path, bytes) in files) {
                if (path == "mimetype") continue
                val entry = ZipEntry(path).apply {
                    method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        return tempFile
    }

    fun createMinimalEpub3(): File {
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="EPUB/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val opfXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="pub-id">urn:uuid:test-epub3</dc:identifier>
                    <dc:title>Test EPUB 3</dc:title>
                    <dc:creator>Author One</dc:creator>
                    <dc:language>en</dc:language>
                </metadata>
                <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="img" href="images/dot.png" media-type="image/png"/>
                </manifest>
                <spine>
                    <itemref idref="ch1"/>
                    <itemref idref="ch2"/>
                </spine>
            </package>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val navXhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <head><title>TOC</title></head>
            <body>
                <nav epub:type="toc" id="toc">
                    <h1>Table of Contents</h1>
                    <ol>
                        <li><a href="text/ch1.xhtml">Chapter 1</a></li>
                        <li><a href="text/ch2.xhtml">Chapter 2</a></li>
                    </ol>
                </nav>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val ch1Xhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head><title>Chapter 1</title></head>
            <body>
                <h1>Chapter 1: Dawn</h1>
                <p>The dawn was silent.</p>
                <p>A solitary figure appeared: <img src="../images/dot.png" alt="Dot"/> on the horizon.</p>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val ch2Xhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head><title>Chapter 2</title></head>
            <body>
                <h2>Chapter 2: Dusk</h2>
                <p>The night followed swiftly.</p>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        return buildEpub(
            mapOf(
                "META-INF/container.xml" to containerXml,
                "EPUB/content.opf" to opfXml,
                "EPUB/nav.xhtml" to navXhtml,
                "EPUB/text/ch1.xhtml" to ch1Xhtml,
                "EPUB/text/ch2.xhtml" to ch2Xhtml,
                "EPUB/images/dot.png" to DUMMY_PNG_1X1
            )
        )
    }

    fun createMinimalEpub2(): File {
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val opfXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="pub-id">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="pub-id">urn:uuid:test-epub2</dc:identifier>
                    <dc:title>Test EPUB 2</dc:title>
                    <dc:creator>Author Two</dc:creator>
                    <dc:language>en</dc:language>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="ch1"/>
                    <itemref idref="ch2"/>
                </spine>
            </package>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val tocNcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head><meta name="dtb:uid" content="urn:uuid:test-epub2"/></head>
                <docTitle><text>Test EPUB 2</text></docTitle>
                <navMap>
                    <navPoint id="np-1" playOrder="1">
                        <navLabel><text>Chapter 1</text></navLabel>
                        <content src="ch1.xhtml"/>
                    </navPoint>
                    <navPoint id="np-2" playOrder="2">
                        <navLabel><text>Chapter 2</text></navLabel>
                        <content src="ch2.xhtml"/>
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val ch1Xhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head><title>Chapter 1</title></head>
            <body>
                <h1>Chapter 1</h1>
                <p>First chapter of EPUB 2.</p>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val ch2Xhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head><title>Chapter 2</title></head>
            <body>
                <h2>Chapter 2</h2>
                <p>Second chapter of EPUB 2.</p>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        return buildEpub(
            mapOf(
                "META-INF/container.xml" to containerXml,
                "OEBPS/content.opf" to opfXml,
                "OEBPS/toc.ncx" to tocNcx,
                "OEBPS/ch1.xhtml" to ch1Xhtml,
                "OEBPS/ch2.xhtml" to ch2Xhtml
            )
        )
    }
}
