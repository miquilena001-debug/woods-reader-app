package com.example.woodsreader.core.pipeline

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.DefaultContentService
import org.readium.r2.shared.publication.services.content.contentServiceFactory
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.map

object EpubPackageSanitizer {
    private val itemTagRegex = Regex("""<(?:(?:[A-Za-z_][\w.-]*):)?item\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val spineTagRegex = Regex("""<(?:(?:[A-Za-z_][\w.-]*):)?spine\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val metaTagRegex = Regex("""<(?:(?:[A-Za-z_][\w.-]*):)?meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val packageTagRegex = Regex("""<(?:(?:[A-Za-z_][\w.-]*):)?package\b[^>]*>""", RegexOption.IGNORE_CASE)

    fun repairNcxReference(packageDocument: String): String {
        val manifestItems = itemTagRegex.findAll(packageDocument).map { it.value }.toList()
        val ncxItem = manifestItems.firstOrNull { item ->
            isNcxItem(item)
        } ?: return packageDocument

        val ncxId = attributeValue(ncxItem, "id")?.takeIf { it.isNotBlank() } ?: return packageDocument
        val spineMatch = spineTagRegex.find(packageDocument) ?: return packageDocument
        val currentTocId = attributeValue(spineMatch.value, "toc")
        if (currentTocId != null && manifestItems.any { item ->
                attributeValue(item, "id") == currentTocId && isNcxItem(item)
            }
        ) return packageDocument

        val repairedSpine = if (currentTocId == null) {
            spineMatch.value.replace(Regex("""\s*/?>$""")) { match ->
                " toc=\"$ncxId\"" + match.value
            }
        } else {
            val tocAttribute = Regex("""(?i)(\btoc\s*=\s*)(["'])(.*?)\2""")
                .find(spineMatch.value)
                ?: return packageDocument
            spineMatch.value.replaceRange(
                tocAttribute.range,
                "${tocAttribute.groupValues[1]}${tocAttribute.groupValues[2]}$ncxId${tocAttribute.groupValues[2]}"
            )
        }

        return packageDocument.replaceRange(spineMatch.range, repairedSpine)
    }

    fun repairCoverProperty(packageDocument: String): String {
        val packageTag = packageTagRegex.find(packageDocument)?.value ?: return packageDocument
        if (attributeValue(packageTag, "version")?.startsWith("3") != true) {
            return packageDocument
        }

        val metaTags = metaTagRegex.findAll(packageDocument).map { it.value }.toList()
        val coverId = metaTags.firstNotNullOfOrNull { meta ->
            val name = attributeValue(meta, "name")
            if (name.equals("cover", ignoreCase = true)) {
                attributeValue(meta, "content")?.takeIf { it.isNotBlank() }
            } else null
        } ?: return packageDocument

        val itemMatch = itemTagRegex.findAll(packageDocument).firstOrNull { match ->
            attributeValue(match.value, "id") == coverId
        } ?: return packageDocument

        val itemTag = itemMatch.value
        val properties = attributeValue(itemTag, "properties")
        if (properties?.split(Regex("""\s+"""))?.any { it.equals("cover-image", ignoreCase = true) } == true) {
            return packageDocument
        }

        val repairedItem = if (properties == null) {
            itemTag.replace(Regex("""\s*/?>$""")) { match ->
                " properties=\"cover-image\"" + match.value
            }
        } else {
            val propAttribute = Regex("""(?i)(\bproperties\s*=\s*)(["'])(.*?)\2""")
                .find(itemTag)
                ?: return packageDocument
            itemTag.replaceRange(
                propAttribute.range,
                "${propAttribute.groupValues[1]}${propAttribute.groupValues[2]}${propAttribute.groupValues[3]} cover-image${propAttribute.groupValues[2]}"
            )
        }

        return packageDocument.replaceRange(itemMatch.range, repairedItem)
    }

    fun repairPackageDocument(packageDocument: String): String {
        val withNcx = repairNcxReference(packageDocument)
        return repairCoverProperty(withNcx)
    }

    fun transformAsset(asset: Asset): Asset {
        val containerAsset = asset as? ContainerAsset ?: return asset
        return ContainerAsset(
            format = containerAsset.format,
            container = TransformingContainer(containerAsset.container) { url, resource ->
                when {
                    url.path?.endsWith(".opf", ignoreCase = true) == true -> resource.map { bytes ->
                        val charset = EpubHtmlSanitizer.detectCharset(bytes)
                        val original = bytes.toString(charset)
                        val repaired = repairPackageDocument(original)
                        if (repaired == original) {
                            Try.success(bytes)
                        } else {
                            Try.success(repaired.toByteArray(charset))
                        }
                    }
                    listOf(".html", ".xhtml", ".htm").any { extension ->
                        url.path?.endsWith(extension, ignoreCase = true) == true
                    } -> resource.map { bytes ->
                        val charset = EpubHtmlSanitizer.detectCharset(bytes)
                        val original = bytes.toString(charset)
                        val sanitized = EpubHtmlSanitizer.injectSelectionStyles(
                            EpubHtmlSanitizer.sanitize(original)
                        )
                        if (sanitized == original) {
                            Try.success(bytes)
                        } else {
                            Try.success(EpubHtmlSanitizer.encodeSanitized(original, sanitized, charset))
                        }
                    }
                    else -> resource
                }
            }
        )
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun configurePublication(builder: Publication.Builder) {
        builder.servicesBuilder.contentServiceFactory = DefaultContentService.createFactory(
            resourceContentIteratorFactories = listOf(
                HtmlResourceContentIterator.Factory()
            )
        )

        val chapterTitles = mutableMapOf<String, String>()
        fun addChapterTitles(link: Link) {
            val title = link.title?.trim()?.takeIf { it.isNotEmpty() }
            if (title != null) {
                chapterTitles[normalizeResourcePath(link.href.toString())] = title
            }
            link.children.forEach(::addChapterTitles)
        }
        builder.manifest.tableOfContents.forEach(::addChapterTitles)
        builder.manifest.readingOrder.forEach { link ->
            val title = link.title?.trim()?.takeIf { it.isNotEmpty() }
            if (title != null) {
                chapterTitles.putIfAbsent(normalizeResourcePath(link.href.toString()), title)
            }
        }

        builder.container = TransformingContainer(builder.container) { url, resource ->
            val path = url.path ?: url.toString()
            if (path.endsWith(".html", ignoreCase = true) ||
                path.endsWith(".xhtml", ignoreCase = true) ||
                path.endsWith(".htm", ignoreCase = true) ||
                path.endsWith(".xml", ignoreCase = true)
            ) {
                resource.map { bytes ->
                    val charset = EpubHtmlSanitizer.detectCharset(bytes)
                    val original = bytes.toString(charset)
                    val normalizedPath = normalizeResourcePath(path)
                    val fileName = normalizedPath.substringAfterLast('/')
                    val chapterTitle = chapterTitles[normalizedPath]
                        ?: chapterTitles.entries.firstOrNull { (href, _) ->
                            normalizedPath.endsWith("/$href") || href.endsWith("/$normalizedPath") ||
                            fileName.equals(href.substringAfterLast('/'), ignoreCase = true)
                        }?.value
                    val sanitized = EpubHtmlSanitizer.injectSelectionStyles(
                        EpubHtmlSanitizer.sanitize(original, chapterTitle)
                    )
                    if (sanitized != original) {
                        Try.success(EpubHtmlSanitizer.encodeSanitized(original, sanitized, charset))
                    } else {
                        Try.success(bytes)
                    }
                }
            } else {
                resource
            }
        }
    }

    fun normalizeResourcePath(path: String): String =
        path.substringBefore('#')
            .substringBefore('?')
            .replace('\\', '/')
            .trimStart('/')

    private fun attributeValue(tag: String, name: String): String? =
        Regex("""(?i)(?:^|\s)${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""")
            .find(tag)
            ?.groupValues
            ?.get(2)

    private fun isNcxItem(item: String): Boolean {
        val mediaType = attributeValue(item, "media-type").orEmpty()
        val href = attributeValue(item, "href").orEmpty()
        return mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) ||
            href.substringBefore('#').substringBefore('?').endsWith(".ncx", ignoreCase = true)
    }

    /**
     * Crea una versión ligera de Publication para EpubNavigatorFactory / EpubNavigatorFragment.
     * ⚠️ SOLUCIÓN CRÍTICA PARA LIBROS CON CIENTOS O MILES DE CAPÍTULOS:
     * Readium 3.0.3 tiene un bug severo en EpubNavigatorFragment.tableOfContentsTitleByHref$2 donde
     * realiza copias recursivas con el operador '+' sobre HashMaps por cada enlace del TOC en el hilo principal.
     * En libros con > 80 capítulos (ej. web novels con 2,225 capítulos), esto genera más de 2.5 millones de
     * entradas de mapa y congela la UI durante más de 40 segundos hasta provocar java.lang.OutOfMemoryError.
     * EpubNavigatorFragment solo usa tableOfContents para nombrar el Locator, no para renderizar el libro
     * (el renderizado se hace 100% sobre readingOrder).
     * Pasando tableOfContents vacío al navegador, el libro abre de forma INSTANTÁNEA (0 ms).
     * El TOC completo original se mantiene intacto en state.publication para TocBottomSheet.
     */
    fun toLightweightNavPublication(publication: Publication): Publication {
        if (publication.tableOfContents.size <= 60) return publication
        return try {
            val containerField = Publication::class.java.getDeclaredField("container").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val container = containerField.get(publication) as org.readium.r2.shared.util.data.Container<org.readium.r2.shared.util.resource.Resource>
            val servicesBuilderField = Publication::class.java.getDeclaredField("servicesBuilder").apply { isAccessible = true }
            val servicesBuilder = servicesBuilderField.get(publication) as? Publication.ServicesBuilder
            val safeManifest = publication.manifest.copy(tableOfContents = emptyList())
            Publication(
                manifest = safeManifest,
                container = container,
                servicesBuilder = servicesBuilder ?: Publication.ServicesBuilder()
            )
        } catch (e: Exception) {
            android.util.Log.w("EpubPackageSanitizer", "No se pudo crear publicación ligera para Navigator, usando original", e)
            publication
        }
    }
}
