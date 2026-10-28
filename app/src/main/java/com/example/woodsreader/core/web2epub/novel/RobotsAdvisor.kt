package com.example.woodsreader.core.web2epub.novel

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class RobotsAdvisor(private val fetcher: PageFetcher) {

    private val rulesCache = ConcurrentHashMap<String, List<String>>()

    suspend fun isDisallowed(url: String): Boolean {
        val uri = try {
            val clean = if (!url.contains("://")) "https://$url" else url
            URI(clean)
        } catch (_: Exception) {
            return false
        }

        val host = uri.host?.lowercase() ?: return false
        val path = if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath

        val disallowedPrefixes = rulesCache.getOrPut(host) {
            loadDisallowedRules(uri)
        }

        return disallowedPrefixes.any { rule ->
            if (rule == "/") true
            else path.startsWith(rule)
        }
    }

    private suspend fun loadDisallowedRules(targetUri: URI): List<String> {
        val scheme = targetUri.scheme ?: "https"
        val host = targetUri.host ?: return emptyList()
        val portPart = if (targetUri.port != -1) ":${targetUri.port}" else ""
        val robotsUrl = "$scheme://$host$portPart/robots.txt"

        val page = try {
            fetcher.get(robotsUrl)
        } catch (_: Exception) {
            return emptyList()
        }

        if (page.status != 200) return emptyList()

        val text = CharsetDetector.decode(page.bytes, page.contentType)
        return parseRobotsTxt(text)
    }

    private fun parseRobotsTxt(content: String): List<String> {
        val disallowed = mutableListOf<String>()
        var appliesToAll = false

        for (rawLine in content.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue

            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) continue

            val directive = line.substring(0, colonIdx).trim().lowercase()
            val value = line.substring(colonIdx + 1).trim()

            if (directive == "user-agent") {
                appliesToAll = (value == "*")
            } else if (appliesToAll && directive == "disallow") {
                if (value.isNotEmpty()) {
                    disallowed.add(value)
                }
            }
        }

        return disallowed
    }
}
