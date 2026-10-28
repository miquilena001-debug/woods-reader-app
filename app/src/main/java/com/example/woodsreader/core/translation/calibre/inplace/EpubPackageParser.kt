package com.example.woodsreader.core.translation.calibre.inplace

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

object EpubPackageParser {

    fun parse(zipFile: ZipFile): EpubPackage {
        // 1. Parse META-INF/container.xml
        val containerEntry = zipFile.getEntry("META-INF/container.xml")
            ?: zipFile.entries().asSequence().firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
            ?: throw IllegalArgumentException("No se encontró META-INF/container.xml en el archivo EPUB")

        val opfRelativePath = zipFile.getInputStream(containerEntry).use { input ->
            parseContainerXml(input)
        }

        // 2. Locate OPF entry in zip
        val opfEntry = zipFile.getEntry(opfRelativePath)
            ?: zipFile.entries().asSequence().firstOrNull { it.name.equals(opfRelativePath, ignoreCase = true) }
            ?: zipFile.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
            ?: throw IllegalArgumentException("No se encontró el archivo OPF en $opfRelativePath")

        val opfPath = opfEntry.name.replace('\\', '/')
        val opfDir = opfPath.substringBeforeLast('/', "")

        // 3. Parse OPF
        return zipFile.getInputStream(opfEntry).use { input ->
            parseOpf(input, opfPath, opfDir)
        }
    }

    fun parseContainerXml(input: InputStream): String {
        val doc = Jsoup.parse(input, "UTF-8", "", Parser.xmlParser())
        val rootfile = doc.selectFirst("rootfiles > rootfile")
            ?: doc.selectFirst("rootfile")
            ?: throw IllegalArgumentException("container.xml no contiene un elemento rootfile")
        val fullPath = rootfile.attr("full-path")
        if (fullPath.isBlank()) {
            throw IllegalArgumentException("rootfile no contiene atributo full-path")
        }
        return normalizePath(fullPath)
    }

    fun parseOpf(input: InputStream, opfPath: String, opfDir: String): EpubPackage {
        val doc = Jsoup.parse(input, "UTF-8", "", Parser.xmlParser())
        val title = doc.selectFirst("metadata > dc|title, metadata > title")?.text().orEmpty()
        val creator = doc.selectFirst("metadata > dc|creator, metadata > creator")?.text().orEmpty()
        val language = doc.selectFirst("metadata > dc|language, metadata > language")?.text().orEmpty()

        // Manifest: id -> href, properties, media-type
        val idToHref = mutableMapOf<String, String>()
        var navHref: String? = null
        var ncxHref: String? = null

        val manifestItems = doc.select("manifest > item")
        for (item in manifestItems) {
            val id = item.attr("id")
            val href = item.attr("href")
            val mediaType = item.attr("media-type")
            val properties = item.attr("properties")

            if (id.isNotBlank() && href.isNotBlank()) {
                idToHref[id] = href
            }

            if (properties.split("\\s+".toRegex()).contains("nav") ||
                mediaType.equals("application/xhtml+xml", ignoreCase = true) && href.endsWith("nav.xhtml", ignoreCase = true)
            ) {
                navHref = href
            }

            if (mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) ||
                id.equals("ncx", ignoreCase = true) ||
                href.endsWith(".ncx", ignoreCase = true)
            ) {
                ncxHref = href
            }
        }

        // Spine: ordered itemrefs
        val spineItems = doc.select("spine > itemref")
        val readingOrder = mutableListOf<String>()
        for (itemref in spineItems) {
            val idref = itemref.attr("idref")
            val href = idToHref[idref] ?: continue
            val fullZipPath = resolveHref(opfDir, href)
            readingOrder.add(fullZipPath)
        }

        val tocAttr = doc.selectFirst("spine")?.attr("toc")
        if (!tocAttr.isNullOrBlank() && ncxHref == null) {
            idToHref[tocAttr]?.let { ncxHref = it }
        }

        val navZipPath = navHref?.let { resolveHref(opfDir, it) }
        val ncxZipPath = ncxHref?.let { resolveHref(opfDir, it) }

        return EpubPackage(
            opfPath = opfPath,
            opfDir = opfDir,
            title = title,
            creator = creator,
            language = language,
            readingOrderZipPaths = readingOrder.distinct(),
            ncxZipPath = ncxZipPath,
            navZipPath = navZipPath
        )
    }

    fun resolveHref(opfDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').substringBefore('?')
        val decoded = try {
            URLDecoder.decode(cleanHref.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            cleanHref
        }
        val combined = if (opfDir.isNotEmpty()) "$opfDir/$decoded" else decoded
        return normalizePath(combined)
    }

    fun normalizePath(path: String): String {
        val parts = path.replace('\\', '/').split('/')
        val stack = ArrayDeque<String>()
        for (part in parts) {
            when {
                part.isEmpty() || part == "." -> continue
                part == ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
