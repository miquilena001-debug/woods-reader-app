package com.example.woodsreader.core.web2epub.novel

import android.content.Context
import timber.log.Timber

class SiteProfileLoader(private val context: Context) {

    fun load(): SiteProfileRegistry {
        val profiles = mutableListOf<SiteProfile>()
        try {
            val assetFiles = context.assets.list("site_profiles") ?: emptyArray()
            for (fileName in assetFiles) {
                if (fileName.endsWith(".json", ignoreCase = true)) {
                    try {
                        val json = context.assets.open("site_profiles/$fileName").bufferedReader().use { it.readText() }
                        val profile = SiteProfileRegistry.parse(json)
                        profiles.add(profile)
                    } catch (e: Exception) {
                        Timber.w(e, "Error al parsear perfil de sitio: %s", fileName)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error al listar perfiles en assets/site_profiles")
        }
        return SiteProfileRegistry(profiles)
    }
}
